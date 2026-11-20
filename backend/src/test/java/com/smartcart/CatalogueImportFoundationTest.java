package com.smartcart;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.catalogimport.*;
import com.smartcart.repository.Db;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import static com.smartcart.repository.Db.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:importfoundation;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class CatalogueImportFoundationTest {
 @Autowired Db db; @Autowired CatalogueImportService service; @Autowired MockMvc mvc;
 @Autowired CatalogueBatchService batches;
 @Autowired ObjectMapper json;
 @Test void adminBoundary() throws Exception {mvc.perform(get("/api/admin/catalogue-imports")).andExpect(status().isUnauthorized());mvc.perform(get("/api/admin/catalogue-imports").with(jwt())).andExpect(status().isForbidden());}
 @Test void matchingIsAdminOnlyAndReadOnly() throws Exception {
  String route="/api/admin/catalogue-matching/compare";
  String request="""
   {"left":{"gtin":"8901719134852","brand":"Parle","name":"Parle-G","pack":"90 g"},
    "right":{"gtin":"08901719134852","brand":"Parle","name":"Parle G","pack":"0.09 kg"}}
   """;
  var admin=jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_admin"));
  long products=db.count("SELECT COUNT(*) FROM products"), variants=db.count("SELECT COUNT(*) FROM product_variants");
  mvc.perform(post(route).contentType("application/json").content(request)).andExpect(status().isUnauthorized());
  mvc.perform(post(route).with(jwt()).contentType("application/json").content(request)).andExpect(status().isForbidden());
  mvc.perform(post(route).with(admin).contentType("application/json").content(request))
     .andExpect(status().isOk()).andExpect(jsonPath("$.decision").value("EXACT_GTIN"));
  mvc.perform(post(route).with(admin).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
  String review="/api/admin/catalogue-matching/review";
  String batch="{\"pairs\":[{\"reference\":\"source-row-83\",\"comparison\":"+request+"}]}";
  mvc.perform(post(review).with(jwt()).contentType("application/json").content(batch)).andExpect(status().isForbidden());
  mvc.perform(post(review).with(admin).contentType("application/json").content(batch))
     .andExpect(status().isOk()).andExpect(jsonPath("$[0].reference").value("source-row-83"))
     .andExpect(jsonPath("$[0].evidence.decision").value("EXACT_GTIN"));
  mvc.perform(post(review).param("method","hybrid").with(admin).contentType("application/json").content(batch))
     .andExpect(status().isOk()).andExpect(jsonPath("$[0].evidence.reasons").isArray());
  mvc.perform(post(review).param("method","unknown").with(admin).contentType("application/json").content(batch))
     .andExpect(status().isBadRequest());
  mvc.perform(post(review).with(admin).contentType("application/json").content("{\"pairs\":[null]}"))
     .andExpect(status().isBadRequest());
  assertEquals(products,db.count("SELECT COUNT(*) FROM products"));
  assertEquals(variants,db.count("SELECT COUNT(*) FROM product_variants"));
 }
 @Test void providerMappingPreviewChecksCanonicalVariantWithoutWriting() throws Exception {
  String route="/api/admin/provider/mapping/review";
  String body="""
   {"variantId":"milk","listingIdentity":{"name":"Milk","brand":"Fixture","pack":"2 x 500 ml","gtin":"bad"}}
   """;
  var admin=jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_admin"));
  long mappings=db.count("SELECT COUNT(*) FROM provider_mappings");
  mvc.perform(post(route).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
  mvc.perform(post(route).with(jwt()).contentType("application/json").content(body)).andExpect(status().isForbidden());
  mvc.perform(post(route).with(admin).contentType("application/json").content(body))
     .andExpect(status().isOk()).andExpect(jsonPath("$.variantId").value("milk"))
     .andExpect(jsonPath("$.blockingReasons").isArray());
  assertEquals(mappings,db.count("SELECT COUNT(*) FROM provider_mappings"));
 }
 @Test void providerMappingAuditIsAdminOnlyAndRecordsTheAuthenticatedReviewer() throws Exception {
  String reviewer=id(), variant=id(), decision=id(), sku="fixture-"+variant;
  String route="/api/admin/provider/mapping", decisions=route+"/decisions";
  db.insert("users",fields("id",reviewer,"name","Mapping fixture reviewer","email",reviewer+"@example.invalid",
    "password_hash","unused","role","admin"));
  db.insert("products",fields("id",variant,"category_id","pantry","name","Fixture rice","brand","Fixture brand",
    "variant_name","Plain","description","Fixture","color","#123456","art","bag"));
  db.insert("product_variants",fields("id",variant,"product_id",variant,"pack_label","1 kg","amount_base",1000,"base_unit","g"));
  var admin=jwt().jwt(token->token.subject(reviewer))
    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_admin"));
  var request=json.createObjectNode().put("variantId",variant).put("platform","blinkit").put("retailerSku",sku);
  request.putObject("listingIdentity").put("name","Fixture rice").put("brand","Fixture brand").put("pack","1 kg").put("variant","Plain");
  request.putObject("mappingEvidence").put("decisionId",decision).put("sourceProvider","TEST_FIXTURE")
    .put("sourceRecordId","fixture-record").put("evidenceReference","https://example.invalid/mapping-evidence")
    .put("evidenceSha256","a".repeat(64)).put("permissionReference","test-fixture-only")
    .put("rationale","Awaiting exact sellable unit confirmation");
  var unresolved=request.deepCopy().put("disposition","UNRESOLVED");
  String payload=json.writeValueAsString(unresolved);

  mvc.perform(post(decisions).contentType("application/json").content(payload)).andExpect(status().isUnauthorized());
  mvc.perform(post(decisions).with(jwt()).contentType("application/json").content(payload)).andExpect(status().isForbidden());
  mvc.perform(get(decisions).param("variantId",variant).param("platform","blinkit")).andExpect(status().isUnauthorized());
  mvc.perform(get(decisions).with(jwt()).param("variantId",variant).param("platform","blinkit"))
    .andExpect(status().isForbidden());
  mvc.perform(post(decisions).with(admin).contentType("application/json").content(payload)).andExpect(status().isOk());
  mvc.perform(put(route).with(admin).contentType("application/json").content(json.writeValueAsString(request)))
    .andExpect(status().isConflict());
  mvc.perform(get(decisions).with(admin).param("variantId",variant).param("platform","blinkit"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
    .andExpect(jsonPath("$[0].decision_id").value(decision)).andExpect(jsonPath("$[0].disposition").value("UNRESOLVED"))
    .andExpect(jsonPath("$[0].reviewed_by").value(reviewer)).andExpect(jsonPath("$[0].reviewed_at").exists());
  assertEquals(0,db.count("SELECT COUNT(*) FROM platform_products WHERE variant_id=?",variant));
 }
 @Test void insertIsIdempotentAndNeverCreatesPrices(){
  String user=id(),job=id(),candidate=id();String category=text(db.one("SELECT id FROM product_categories ORDER BY id LIMIT 1"),"id");
  db.insert("users",fields("id",user,"name","Import admin","email",user+"@example.invalid","password_hash","unused","role","admin"));
  db.insert("catalogue_import_jobs",fields("id",job,"source","OPEN_FOOD_FACTS","status","STAGED","requested_by",user,"source_code","3017620020117"));
  db.insert("catalogue_import_candidates",fields("id",candidate,"job_id",job,"source","OPEN_FOOD_FACTS","external_id","03017620020117","source_url","https://world.openfoodfacts.org/product/3017620020117","payload_json","{\"product_name\":\"Fixture flour\",\"brands\":\"Fixture brand\",\"quantity\":\"1 kg\"}","status","PENDING_REVIEW","collected_at",Instant.now()));
  long products=db.count("SELECT COUNT(*) FROM products"),prices=db.count("SELECT COUNT(*) FROM location_prices"),mappings=db.count("SELECT COUNT(*) FROM platform_products");
  var result=service.approve(candidate,category,"Plain",user);assertEquals("IMPORTED",result.get("status"));assertEquals(products+1,db.count("SELECT COUNT(*) FROM products"));
  assertEquals("DUPLICATE",service.approve(candidate,category,"Plain",user).get("status"));assertEquals(products+1,db.count("SELECT COUNT(*) FROM products"));
  assertEquals(prices,db.count("SELECT COUNT(*) FROM location_prices"));assertEquals(mappings,db.count("SELECT COUNT(*) FROM platform_products"));
  assertEquals(0,db.count("SELECT COUNT(*) FROM products WHERE id=? AND active=TRUE",result.get("productId")));
 }

 @Test void correctionAndRecoveryAreAdminOnly() throws Exception {
  mvc.perform(put("/api/admin/catalogue-imports/candidates/missing").contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
  mvc.perform(put("/api/admin/catalogue-imports/candidates/missing").with(jwt()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(post("/api/admin/catalogue-imports/missing/resume")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/admin/catalogue-imports/missing/resume").with(jwt())).andExpect(status().isForbidden());
  mvc.perform(put("/api/admin/catalogue-imports/candidates/missing").with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_admin"))).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
 }

 @Test void snapshotCheckpointAndPublicationPreserveRawRecords() throws Exception {
  String user=id(),digest="a".repeat(64);
  String category=text(db.one("SELECT id FROM product_categories ORDER BY id LIMIT 1"),"id");
  db.insert("users",fields("id",user,"name","Import admin","email",user+"@example.invalid","password_hash","unused","role","admin"));
  String raw="""
   {"code":"3017620020117","product_name":"Batch fixture","brands":"Fixture brand","quantity":"1 litre",
    "ingredients_text":"Fixture ingredients","nutrition":{"aggregated_set":{"per":"100ml","nutrients":{
     "energy-kcal":{"unit":"kcal","source":"packaging","value":25},
     "fat":{"unit":"g","source":"estimate","value":1}}}}}
   """;
  String batch=text(batches.start(digest,4,user),"id");
  assertEquals(1,num(batches.append(batch,0,java.util.List.of(raw),user),"committed_rows"));
  assertEquals(batch,text(batches.start(digest,4,user),"id"));
  assertThrows(com.smartcart.exception.ApiException.class,()->batches.append(batch,0,java.util.List.of(raw),user));
  String alternate=raw.replace("3017620020117","4006381333931").replace("1 litre","500 ml");
  String needsCorrection=raw.replace("1 litre","1");
  var progress=batches.append(batch,1,java.util.List.of("not JSON",needsCorrection,alternate),user);
  assertEquals(4,num(progress,"committed_rows"));
  assertEquals(2,num(progress,"staged_rows"));assertEquals(1,num(progress,"invalid_rows"));assertEquals(1,num(progress,"correction_rows"));
  assertEquals(2,batches.issues(batch,0).size());
  assertEquals(raw,text(db.one("SELECT raw_record FROM catalogue_batch_rows WHERE batch_id=? AND `row_number`=1",batch),"raw_record"));
  var review=service.review(batch,0,20);
  String selected=text(review.get(0),"id"), second=text(review.get(2),"id");
  var created=service.approve(selected,category,"Plain",user);
  String product=text(created,"productId"),variant=text(created,"variantId");
  service.approve(second,category,"Plain",product,user);
  assertEquals(2,db.count("SELECT COUNT(*) FROM product_variants WHERE product_id=?",product));
  String location=text(db.one("SELECT id FROM delivery_locations ORDER BY id LIMIT 1"),"id");
  String item="/api/products/"+variant;
  mvc.perform(get(item+"/metadata").param("locationId",location)).andExpect(status().isNotFound());
  mvc.perform(post("/api/admin/catalogue-imports/candidates/"+selected+"/publish").with(jwt())).andExpect(status().isForbidden());
  long prices=db.count("SELECT COUNT(*) FROM location_prices");
  service.publish(selected,user);
  assertEquals("PUBLISHED",service.publish(selected,user).get("status"));
  mvc.perform(get(item+"/metadata").param("locationId",location)).andExpect(status().isOk())
   .andExpect(jsonPath("$.gtins[0]").value("03017620020117"))
   .andExpect(jsonPath("$.sources[0].nutritionBasis").value("100ml"))
   .andExpect(jsonPath("$.sources[0].nutritionPer100['energy-kcal']").value(25))
   .andExpect(jsonPath("$.sources[0].nutritionPer100.fat").doesNotExist())
   .andExpect(jsonPath("$.sources[0].ingredients").value("Fixture ingredients"));
  mvc.perform(get(item+"/variants").param("locationId",location)).andExpect(status().isOk())
   .andExpect(jsonPath("$.length()").value(2));
  assertEquals(prices,db.count("SELECT COUNT(*) FROM location_prices"));
  assertEquals(raw,text(db.one("SELECT payload_json FROM external_product_sources WHERE variant_id=?",variant),"payload_json"));
 }
}
