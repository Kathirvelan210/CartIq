package com.smartcart.catalogimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.smartcart.repository.Db;
import com.smartcart.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Instant;
import java.util.*;
import static com.smartcart.repository.Db.*;

@Service
public class CatalogueImportService {
 private final Db db;private final OpenFoodFactsAdapter source;private final ObjectMapper json;private final TransactionTemplate tx;
 public CatalogueImportService(Db db,OpenFoodFactsAdapter source,ObjectMapper json,PlatformTransactionManager manager){this.db=db;this.source=source;this.json=json;this.tx=new TransactionTemplate(manager);}
 public Map<String,Object> fetch(String code,String user){
  ImportNormalizer.gtin(code);String job=id(),token=id();
  tx.executeWithoutResult(status->{claim(job,token);db.insert("catalogue_import_jobs",fields("id",job,"source","OPEN_FOOD_FACTS","status","RUNNING","requested_by",user,"source_code",code));});
  return execute(job,token);
 }
 public Map<String,Object> resume(String job){
  String token=id();boolean claimed=Boolean.TRUE.equals(tx.execute(status->{db.one("SELECT * FROM catalogue_import_locks WHERE source='OPEN_FOOD_FACTS' FOR UPDATE");var row=db.one("SELECT * FROM catalogue_import_jobs WHERE id=? FOR UPDATE",job);if("STAGED".equals(text(row,"checkpoint")))return false;
   if(num(row,"attempts")>=3&&!"FETCHED".equals(text(row,"checkpoint")))throw ApiException.bad("Retry budget exhausted. Review failure before starting a new job.");
   claim(job,token);db.run("UPDATE catalogue_import_jobs SET status='RUNNING',error_message=NULL,finished_at=NULL WHERE id=?",job);return true;}));
  return claimed?execute(job,token):db.one("SELECT * FROM catalogue_import_jobs WHERE id=?",job);
 }
 private void claim(String job,String token){
  var lock=db.one("SELECT * FROM catalogue_import_locks WHERE source='OPEN_FOOD_FACTS' FOR UPDATE");Instant now=Instant.now();
  if(time(lock,"lease_until")!=null&&time(lock,"lease_until").isAfter(now))throw ApiException.bad("An import worker holds an active lease.");
  if(time(lock,"next_request_at").isAfter(now))throw ApiException.bad("Import request budget is occupied. Retry later.");
  String prior=text(lock,"job_id");if(prior!=null){db.run("UPDATE catalogue_import_jobs SET status='INTERRUPTED' WHERE id=? AND status='RUNNING'",prior);db.run("UPDATE catalogue_import_attempts SET outcome='INTERRUPTED',finished_at=? WHERE job_id=? AND outcome='STARTED'",now,prior);}
  db.run("UPDATE catalogue_import_locks SET owner_token=?,job_id=?,lease_until=?,next_request_at=? WHERE source='OPEN_FOOD_FACTS'",token,job,now.plusSeconds(120),now.plusSeconds(30));
 }
 private void fence(String token){var lock=db.one("SELECT * FROM catalogue_import_locks WHERE source='OPEN_FOOD_FACTS' FOR UPDATE");if(!token.equals(text(lock,"owner_token"))||time(lock,"lease_until")==null||!time(lock,"lease_until").isAfter(Instant.now()))throw ApiException.bad("Worker lease expired; stale worker cannot commit.");}
 protected void backoff(int attempt) throws InterruptedException {Thread.sleep(attempt==1?5000:10000);}
 private Map<String,Object> execute(String job,String token){
  try{
   var row=db.one("SELECT * FROM catalogue_import_jobs WHERE id=?",job);
   while(!"FETCHED".equals(text(row,"checkpoint"))){
    int attempt=tx.execute(status->{fence(token);var current=db.one("SELECT * FROM catalogue_import_jobs WHERE id=?",job);int n=(int)num(current,"attempts")+1;if(n>3)throw ApiException.bad("Retry budget exhausted.");db.run("UPDATE catalogue_import_jobs SET attempts=? WHERE id=?",n,job);db.insert("catalogue_import_attempts",fields("job_id",job,"attempt_number",n,"outcome","STARTED","started_at",Instant.now()));return n;});
    try{var payload=source.fetch(text(row,"source_code"));tx.executeWithoutResult(status->{fence(token);db.run("UPDATE catalogue_import_jobs SET checkpoint='FETCHED',fetched_payload=?,fetched_at=? WHERE id=?",payload.toString(),Instant.now(),job);db.run("UPDATE catalogue_import_attempts SET outcome='FETCHED',finished_at=? WHERE job_id=? AND attempt_number=?",Instant.now(),job,attempt);});}
    catch(Exception e){boolean retry=e instanceof OpenFoodFactsAdapter.TemporaryFailure||e instanceof java.io.IOException&&!(e instanceof com.fasterxml.jackson.core.JsonProcessingException);tx.executeWithoutResult(status->{fence(token);db.run("UPDATE catalogue_import_attempts SET outcome=?,finished_at=? WHERE job_id=? AND attempt_number=?",retry?"TEMPORARY_FAILURE":"PERMANENT_FAILURE",Instant.now(),job,attempt);});if(!retry||attempt>=3)throw e;backoff(attempt);}
    row=db.one("SELECT * FROM catalogue_import_jobs WHERE id=?",job);
   }
   final var fetched=row;var payload=json.readTree(text(fetched,"fetched_payload"));
   tx.executeWithoutResult(status->{fence(token);if(db.count("SELECT COUNT(*) FROM catalogue_import_candidates WHERE job_id=?",job)==0)stage(job,text(fetched,"source_code"),payload.toString(),payload,time(fetched,"fetched_at"));db.run("UPDATE catalogue_import_jobs SET checkpoint='STAGED',status='STAGED',finished_at=? WHERE id=?",Instant.now(),job);});
  }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();tx.executeWithoutResult(status->{var lock=db.one("SELECT * FROM catalogue_import_locks WHERE source='OPEN_FOOD_FACTS' FOR UPDATE");if(token.equals(text(lock,"owner_token")))db.run("UPDATE catalogue_import_jobs SET status='FAILED',error_message=?,finished_at=? WHERE id=?",safeError(e),Instant.now(),job);});}
  finally{db.run("UPDATE catalogue_import_locks SET owner_token=NULL,job_id=NULL,lease_until=NULL WHERE source='OPEN_FOOD_FACTS' AND owner_token=?",token);}
  return db.one("SELECT * FROM catalogue_import_jobs WHERE id=?",job);
 }
 record Candidate(String id,String issue){}
 Candidate stage(String job,String code,String raw,JsonNode payload,Instant collectedAt){
  String issue=null;
  try{ImportNormalizer.parse(payload);}catch(ApiException error){issue=error.getMessage();}
  String candidate=id();
  db.insert("catalogue_import_candidates",fields("id",candidate,"job_id",job,"source","OPEN_FOOD_FACTS",
   "external_id",ImportNormalizer.gtin(code),"source_url","https://world.openfoodfacts.org/product/"+code,
   "payload_json",raw,"status",issue==null?"PENDING_REVIEW":"NEEDS_CORRECTION",
   "validation_message",issue,"collected_at",collectedAt));
  return new Candidate(candidate,issue);
 }
 Candidate stageSnapshot(String code,String raw,JsonNode payload,String user,Instant collectedAt){
  String job=id();
  db.insert("catalogue_import_jobs",fields("id",job,"source","OPEN_FOOD_FACTS","status","STAGED",
   "checkpoint","STAGED","requested_by",user,"source_code",code,"fetched_at",collectedAt,"finished_at",collectedAt));
  return stage(job,code,raw,payload,collectedAt);
 }
 public void correct(String candidate,String name,String brand,String quantity,String reason,String user){
  if(reason==null||reason.isBlank()||reason.length()>1000)throw ApiException.bad("Correction reason is required.");
  tx.executeWithoutResult(status->{var row=db.one("SELECT * FROM catalogue_import_candidates WHERE id=? FOR UPDATE",candidate);if(!List.of("PENDING_REVIEW","NEEDS_CORRECTION").contains(text(row,"status")))throw ApiException.bad("Only pending candidates can be corrected.");
   com.fasterxml.jackson.databind.node.ObjectNode payload;try{payload=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(text(row,"payload_json"));}catch(Exception e){throw ApiException.bad("Invalid source payload.");}
   payload.put("product_name",name);payload.put("brands",brand);payload.put("quantity",quantity);ImportNormalizer.parse(payload);
   db.insert("catalogue_candidate_corrections",fields("id",id(),"candidate_id",candidate,"corrected_by",user,"corrected_at",Instant.now(),"payload_json",payload.toString(),"reason",reason));
   db.run("UPDATE catalogue_import_candidates SET corrected_payload=?,status='PENDING_REVIEW',validation_message=NULL WHERE id=?",payload.toString(),candidate);
  });
 }
 private String safeError(Exception e){String message=e instanceof IllegalStateException?e.getMessage():"Source request or staging failed; inspect server diagnostics and retry.";if(message==null)message="Import failed.";return message.substring(0,Math.min(message.length(),1000));}
 public List<Map<String,Object>> jobs(){return db.rows("SELECT * FROM catalogue_import_jobs ORDER BY created_at DESC,id LIMIT 100");}
 public List<Map<String,Object>> candidates(){return db.rows("SELECT * FROM catalogue_import_candidates ORDER BY collected_at DESC,id LIMIT 100");}
 public List<Map<String,Object>> review(String batchId,int offset,int limit){
  if(offset<0||limit<1||limit>50)throw ApiException.bad("Invalid review pagination.");
  var rows=batchId==null
   ?db.rows("SELECT * FROM catalogue_import_candidates ORDER BY collected_at DESC,id LIMIT ? OFFSET ?",limit,offset)
   :db.rows("SELECT c.* FROM catalogue_batch_rows r JOIN catalogue_import_candidates c ON c.id=r.candidate_id WHERE r.batch_id=? ORDER BY r.`row_number` LIMIT ? OFFSET ?",batchId,limit,offset);
  return rows.stream().map(row->{
   JsonNode payload=effectivePayload(row);
   return fields("id",text(row,"id"),"barcode",text(row,"external_id"),"sourceUrl",text(row,"source_url"),
    "status",text(row,"status"),"issue",text(row,"validation_message"),"name",preview(payload,"product_name",150),
    "brand",preview(payload,"brands",100),"quantity",preview(payload,"quantity",60),
    "suggestedCategory",ImportNormalizer.categorySuggestion(payload));
  }).toList();
 }
 private String preview(JsonNode payload,String key,int limit){
  String value=payload.path(key).asText();
  return value.substring(0,Math.min(value.length(),limit));
 }
 private JsonNode effectivePayload(Map<String,Object> row){
  try{return json.readTree(text(row,"corrected_payload")!=null?text(row,"corrected_payload"):text(row,"payload_json"));}
  catch(com.fasterxml.jackson.core.JsonProcessingException error){throw ApiException.bad("Invalid staged payload.");}
 }
 public Map<String,Object> approve(String candidate,String category,String flavour,String user){
  return approve(candidate,category,flavour,null,user);
 }
 public Map<String,Object> approve(String candidate,String category,String flavour,String existingProductId,String user){
  if(flavour==null||ImportNormalizer.clean(flavour).isEmpty()||flavour.length()>100)throw ApiException.bad("An admin must confirm the exact flavour/variant identity.");
  return tx.execute(status->{
   db.one("SELECT * FROM catalogue_import_locks WHERE source='OPEN_FOOD_FACTS' FOR UPDATE");
   var row=db.one("SELECT * FROM catalogue_import_candidates WHERE id=? FOR UPDATE",candidate);
   String code=text(row,"external_id");
   if("REJECTED".equals(text(row,"status")))throw ApiException.bad("Rejected candidate cannot be imported.");
   var prior=db.maybe("SELECT variant_id FROM external_product_sources WHERE source='OPEN_FOOD_FACTS' AND external_id=?",code);
   
   var payload=effectivePayload(row);
   var p=ImportNormalizer.parse(payload);db.one("SELECT id FROM product_categories WHERE id=?",category);
   if(prior.isPresent()){
    var existing=db.one("SELECT p.name,p.brand,p.variant_name,v.amount_base,v.base_unit FROM product_variants v JOIN products p ON p.id=v.product_id WHERE v.id=?",text(prior.get(),"variant_id"));
    if(!p.name().equalsIgnoreCase(text(existing,"name"))||!p.brand().equalsIgnoreCase(text(existing,"brand"))||!ImportNormalizer.clean(flavour).equalsIgnoreCase(text(existing,"variant_name"))||p.amount().compareTo(decimal(existing,"amount_base"))!=0||!p.unit().equals(text(existing,"base_unit")))throw ApiException.bad("Source barcode conflicts with existing product identity; manual reconciliation required.");
    if(!List.of("IMPORTED","PUBLISHED").contains(text(row,"status")))mark(candidate,user,"DUPLICATE");
    return fields("status","DUPLICATE","variantId",text(prior.get(),"variant_id"));
   }

   // Existing catalogue records are never overwritten or silently merged by fuzzy name matching.
   if(db.count("SELECT COUNT(*) FROM variant_barcodes WHERE gtin=?",code)>0)throw ApiException.bad("Barcode is already attached to a variant; manual identity reconciliation required.");
   String product=existingProductId,variant=id();
   if(product==null||product.isBlank()){
    if(db.count("SELECT COUNT(*) FROM products WHERE LOWER(TRIM(name))=? AND LOWER(TRIM(brand))=?",p.name().toLowerCase(Locale.ROOT),p.brand().toLowerCase(Locale.ROOT))>0)throw ApiException.bad("Possible existing product; review the existing product ID before approving another pack.");
    product=id();
    db.insert("products",fields("id",product,"category_id",category,"name",p.name(),"brand",p.brand(),"variant_name",ImportNormalizer.clean(flavour),"description","","color","#E6EEE9","art","box","image_url",p.image(),"active",false));
   }else{
    var parent=db.one("SELECT * FROM products WHERE id=? FOR UPDATE",product);
    if(bool(parent,"active")||!p.name().equalsIgnoreCase(text(parent,"name"))||!p.brand().equalsIgnoreCase(text(parent,"brand"))
      ||!category.equals(text(parent,"category_id"))||!ImportNormalizer.clean(flavour).equalsIgnoreCase(text(parent,"variant_name")))
     throw ApiException.bad("Additional packs require a matching inactive product, category and flavour.");
    if(db.count("SELECT COUNT(*) FROM product_variants WHERE product_id=? AND amount_base=? AND base_unit=?",product,p.amount(),p.unit())>0)
     throw ApiException.bad("This pack already exists with a different barcode. Review the identity manually.");
    // An image from a specific pack should never stand in for all sizes.
    db.run("UPDATE products SET image_url=NULL WHERE id=?",product);
   }
   db.insert("product_variants",fields("id",variant,"product_id",product,"pack_label",p.pack(),"amount_base",p.amount(),"base_unit",p.unit()));
   db.insert("variant_barcodes",fields("gtin",code,"variant_id",variant));
   db.insert("external_product_sources",fields("source","OPEN_FOOD_FACTS","external_id",code,"variant_id",variant,"source_url",text(row,"source_url"),"collected_at",time(row,"collected_at"),"verification_status","COMMUNITY_ADMIN_REVIEWED","attribution","Open Food Facts contributors — https://world.openfoodfacts.org","data_license","ODbL-1.0 / DbCL-1.0","image_license",p.image()==null?null:"CC-BY-SA-3.0","payload_json",text(row,"payload_json")));
   mark(candidate,user,"IMPORTED");return fields("status","IMPORTED","productId",product,"variantId",variant);
  });
 }
 public Map<String,Object> publish(String candidate,String user){
  return tx.execute(status->{
   db.one("SELECT * FROM catalogue_import_locks WHERE source='OPEN_FOOD_FACTS' FOR UPDATE");
   var row=db.one("SELECT * FROM catalogue_import_candidates WHERE id=? FOR UPDATE",candidate);
   if(!List.of("IMPORTED","PUBLISHED").contains(text(row,"status")))throw ApiException.bad("Approve the candidate before publishing.");
   var target=db.one("SELECT v.product_id,v.id FROM external_product_sources s JOIN product_variants v ON v.id=s.variant_id WHERE s.source=? AND s.external_id=?",text(row,"source"),text(row,"external_id"));
   if(!"PUBLISHED".equals(text(row,"status"))){
    db.run("UPDATE products SET active=TRUE,updated_at=? WHERE id=?",Instant.now(),text(target,"product_id"));
    mark(candidate,user,"PUBLISHED");
   }
   return fields("status","PUBLISHED","productId",text(target,"product_id"),"variantId",text(target,"id"));
  });
 }
 public void reject(String candidate,String user){tx.executeWithoutResult(status->{var row=db.one("SELECT * FROM catalogue_import_candidates WHERE id=? FOR UPDATE",candidate);if(List.of("IMPORTED","DUPLICATE","PUBLISHED").contains(text(row,"status")))throw ApiException.bad("Already processed; catalogue records will not be deleted.");mark(candidate,user,"REJECTED");});}
 private void mark(String id,String user,String status){db.run("UPDATE catalogue_import_candidates SET status=?,reviewed_by=?,reviewed_at=? WHERE id=?",status,user,Instant.now(),id);}
}
