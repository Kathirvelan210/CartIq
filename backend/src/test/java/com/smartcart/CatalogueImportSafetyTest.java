package com.smartcart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.catalogimport.*;
import com.smartcart.repository.Db;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static com.smartcart.repository.Db.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Isolated H2 database; no Spring application startup, MySQL or external network.
class CatalogueImportSafetyTest {
 DriverManagerDataSource ds; FaultDb db; OpenFoodFactsAdapter adapter; CatalogueImportService service;
 String user; ObjectMapper json=new ObjectMapper();
 static class FaultDb extends Db {
  String failTable;
  FaultDb(JdbcTemplate jdbc){super(jdbc);}
  @Override public void insert(String table,Map<String,Object> values){super.insert(table,values);if(table.equals(failTable))throw new IllegalStateException("Injected insert failure");}
 }
 @BeforeEach void setup(){
  ds=new DriverManagerDataSource("jdbc:h2:mem:safety"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
  Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
  db=new FaultDb(new JdbcTemplate(ds));adapter=mock(OpenFoodFactsAdapter.class);
  service=new CatalogueImportService(db,adapter,json,new DataSourceTransactionManager(ds)){@Override protected void backoff(int attempt){}};
  user=id();db.insert("users",fields("id",user,"name","Test","email",user+"@example.invalid","password_hash","unused"));
  db.insert("product_categories",fields("id","test","name","Test"));
 }
 @AfterEach void close(){new JdbcTemplate(ds).execute("SHUTDOWN");}
 String candidate(String name,String code){String job=id(),candidate=id();db.insert("catalogue_import_jobs",fields("id",job,"source","OPEN_FOOD_FACTS","status","STAGED","requested_by",user,"source_code","3017620020117"));db.insert("catalogue_import_candidates",fields("id",candidate,"job_id",job,"source","OPEN_FOOD_FACTS","external_id",code,"source_url","https://world.openfoodfacts.org/product/3017620020117","payload_json","{\"product_name\":\""+name+"\",\"brands\":\"Test brand\",\"quantity\":\"1 kg\"}","status","PENDING_REVIEW","collected_at",Instant.now()));return candidate;}
 void rollback(String table){String c=candidate("Rice","03017620020117");db.failTable=table;assertThrows(IllegalStateException.class,()->service.approve(c,"test","Plain",user));for(String t:List.of("products","product_variants","variant_barcodes","external_product_sources"))assertEquals(0,db.count("SELECT COUNT(*) FROM "+t),t);assertEquals("PENDING_REVIEW",text(db.one("SELECT * FROM catalogue_import_candidates WHERE id=?",c),"status"));}
 @Test void rollbackProductInsertion(){rollback("products");}
 @Test void rollbackVariantInsertion(){rollback("product_variants");}
 @Test void duplicateNameDoesNotOverwrite(){String first=candidate("Rice","03017620020117");service.approve(first,"test","Plain",user);var before=db.rows("SELECT * FROM products ORDER BY id");String other=candidate("Rice","04006381333931");assertThrows(RuntimeException.class,()->service.approve(other,"test","Different flavour",user));assertEquals(before,db.rows("SELECT * FROM products ORDER BY id"));}
 @Test void barcodeConflictDoesNotOverwrite(){String first=candidate("Rice","03017620020117");service.approve(first,"test","Plain",user);db.run("DELETE FROM external_product_sources");var before=db.rows("SELECT * FROM products");String conflict=candidate("Different product","03017620020117");assertThrows(RuntimeException.class,()->service.approve(conflict,"test","Plain",user));assertEquals(before,db.rows("SELECT * FROM products"));}
 @Test void rejectionPreventsApproval(){String c=candidate("Rice","03017620020117");service.reject(c,user);assertThrows(RuntimeException.class,()->service.approve(c,"test","Plain",user));assertEquals(0,db.count("SELECT COUNT(*) FROM products"));}
 @Test void repeatedApprovalPreservesAttributionAndNoRetailerData(){String c=candidate("Rice","03017620020117");service.approve(c,"test","Plain",user);assertEquals("DUPLICATE",service.approve(c,"test","Plain",user).get("status"));assertEquals(1,db.count("SELECT COUNT(*) FROM products"));assertEquals(1,db.count("SELECT COUNT(*) FROM product_variants"));var provenance=db.one("SELECT * FROM external_product_sources");assertEquals("COMMUNITY_ADMIN_REVIEWED",text(provenance,"verification_status"));assertTrue(text(provenance,"attribution").contains("Open Food Facts"));assertEquals(0,db.count("SELECT COUNT(*) FROM products WHERE active=TRUE"));assertEquals(0,db.count("SELECT COUNT(*) FROM platform_products"));assertEquals(0,db.count("SELECT COUNT(*) FROM location_prices"));}
 @Test void incompletePayloadCannotPublish(){String c=candidate("","03017620020117");assertThrows(RuntimeException.class,()->service.approve(c,"test","Plain",user));assertEquals(0,db.count("SELECT COUNT(*) FROM products"));}
 @Test void failedRequestCanBeRetriedAfterBudgetExpires() throws Exception {
  when(adapter.fetch(anyString())).thenThrow(new IllegalStateException("permanent failure")).thenReturn(json.readTree("{\"code\":\"3017620020117\"}"));
  assertEquals("FAILED",service.fetch("3017620020117",user).get("status"));assertThrows(RuntimeException.class,()->service.fetch("3017620020117",user));
  db.run("UPDATE catalogue_import_locks SET next_request_at=?",Instant.now().minusSeconds(1));assertEquals("STAGED",service.fetch("3017620020117",user).get("status"));assertEquals("NEEDS_CORRECTION",text(db.one("SELECT * FROM catalogue_import_candidates"),"status"));verify(adapter,times(2)).fetch(anyString());
 }
 @Test void concurrentRequestsShareDatabaseBudget() throws Exception {
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  when(adapter.fetch(anyString())).thenAnswer(call->{entered.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Test timed out");return json.readTree("{\"code\":\"3017620020117\"}");});
  var pool=Executors.newSingleThreadExecutor();try{var first=pool.submit(()->service.fetch("3017620020117",user));assertTrue(entered.await(5,TimeUnit.SECONDS));assertThrows(RuntimeException.class,()->service.fetch("3017620020117",user));release.countDown();assertEquals("STAGED",first.get(5,TimeUnit.SECONDS).get("status"));assertEquals(1,db.count("SELECT COUNT(*) FROM catalogue_import_jobs"));verify(adapter,times(1)).fetch(anyString());}finally{release.countDown();pool.shutdownNow();}
 }

 @Test void transientRetriesAreBoundedAndRecorded() throws Exception {
  when(adapter.fetch(anyString())).thenThrow(new java.net.http.HttpTimeoutException("timeout"));
  var job=service.fetch("3017620020117",user);assertEquals("FAILED",job.get("status"));assertEquals(3,num(job,"attempts"));verify(adapter,times(3)).fetch(anyString());
  assertEquals(3,db.count("SELECT COUNT(*) FROM catalogue_import_attempts WHERE outcome='TEMPORARY_FAILURE'"));assertEquals(0,db.count("SELECT COUNT(*) FROM catalogue_import_candidates"));assertThrows(RuntimeException.class,()->service.resume(text(job,"id")));
 }
 @Test void temporaryFailureThenSuccessUsesOneJob() throws Exception {
  when(adapter.fetch(anyString())).thenThrow(new OpenFoodFactsAdapter.TemporaryFailure("503")).thenReturn(json.readTree("{\"code\":\"3017620020117\"}"));
  var job=service.fetch("3017620020117",user);assertEquals("STAGED",job.get("status"));assertEquals(2,num(job,"attempts"));assertEquals(1,db.count("SELECT COUNT(*) FROM catalogue_import_candidates"));service.resume(text(job,"id"));verify(adapter,times(2)).fetch(anyString());
 }
 @Test void expiredLeaseResumesFetchedCheckpointWithoutNetwork(){
  String job=id();db.insert("catalogue_import_jobs",fields("id",job,"source","OPEN_FOOD_FACTS","status","RUNNING","requested_by",user,"source_code","3017620020117","checkpoint","FETCHED","fetched_payload","{}","fetched_at",Instant.now()));
  db.run("UPDATE catalogue_import_locks SET owner_token=?,job_id=?,lease_until=?,next_request_at=?",id(),job,Instant.now().minusSeconds(1),Instant.now().minusSeconds(1));
  assertEquals("STAGED",service.resume(job).get("status"));service.resume(job);verifyNoInteractions(adapter);assertEquals(1,db.count("SELECT COUNT(*) FROM catalogue_import_candidates WHERE job_id=?",job));assertNull(text(db.one("SELECT * FROM catalogue_import_locks"),"owner_token"));
 }
 @Test void activeLeasePreventsRecovery(){
  String job=id();db.insert("catalogue_import_jobs",fields("id",job,"source","OPEN_FOOD_FACTS","status","RUNNING","requested_by",user,"source_code","3017620020117"));db.run("UPDATE catalogue_import_locks SET owner_token=?,job_id=?,lease_until=?",id(),job,Instant.now().plusSeconds(60));assertThrows(RuntimeException.class,()->service.resume(job));verifyNoInteractions(adapter);
 }
 @Test void correctionsAreValidatedAuditedAndPreserveSource(){
  String c=candidate("","03017620020117");String raw=text(db.one("SELECT * FROM catalogue_import_candidates WHERE id=?",c),"payload_json");
  assertThrows(RuntimeException.class,()->service.correct(c,"Rice","Test brand","unknown","Reviewed",user));assertEquals(0,db.count("SELECT COUNT(*) FROM catalogue_candidate_corrections"));
  service.correct(c,"Rice","Test brand","1 kg","Confirmed label",user);assertEquals(raw,text(db.one("SELECT * FROM catalogue_import_candidates WHERE id=?",c),"payload_json"));assertEquals(1,db.count("SELECT COUNT(*) FROM catalogue_candidate_corrections"));service.approve(c,"test","Plain",user);assertEquals(raw,text(db.one("SELECT * FROM external_product_sources"),"payload_json"));assertThrows(RuntimeException.class,()->service.correct(c,"Rice","Test brand","2 kg","Changed",user));
 }
 @Test void rejectedCandidateCannotBeCorrected(){String c=candidate("Rice","03017620020117");service.reject(c,user);assertThrows(RuntimeException.class,()->service.correct(c,"Rice","Test brand","1 kg","Reviewed",user));}


 @Test void expiredWorkerCannotCommitAfterRecovery() throws Exception {
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
  when(adapter.fetch(anyString())).thenAnswer(call->{if(calls.incrementAndGet()==1){entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}return json.readTree("{}");});
  var pool=Executors.newSingleThreadExecutor();try{var old=pool.submit(()->service.fetch("3017620020117",user));assertTrue(entered.await(5,TimeUnit.SECONDS));String job=text(db.one("SELECT * FROM catalogue_import_jobs"),"id");db.run("UPDATE catalogue_import_locks SET lease_until=?,next_request_at=?",Instant.now().minusSeconds(1),Instant.now().minusSeconds(1));assertEquals("STAGED",service.resume(job).get("status"));release.countDown();old.get(5,TimeUnit.SECONDS);assertEquals("STAGED",text(db.one("SELECT * FROM catalogue_import_jobs WHERE id=?",job),"status"));assertEquals(1,db.count("SELECT COUNT(*) FROM catalogue_import_candidates"));assertEquals(1,db.count("SELECT COUNT(*) FROM catalogue_import_attempts WHERE outcome='INTERRUPTED'"));}finally{release.countDown();pool.shutdownNow();}
 }

 @Test void repeatedSourceCannotSilentlyChangeIdentity(){String first=candidate("Rice","03017620020117");service.approve(first,"test","Plain",user);var before=db.rows("SELECT * FROM products");String conflicting=candidate("Different rice","03017620020117");assertThrows(RuntimeException.class,()->service.approve(conflicting,"test","Plain",user));assertEquals(before,db.rows("SELECT * FROM products"));assertEquals("PENDING_REVIEW",text(db.one("SELECT * FROM catalogue_import_candidates WHERE id=?",conflicting),"status"));}
}
