package com.smartcart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.repository.Db;
import com.smartcart.service.LocalStoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=${CARTIQ_MYSQL_VERIFY_URL:jdbc:mysql://127.0.0.1:3306/cartiq_db?connectionTimeZone=UTC}",
    "spring.datasource.username=${CARTIQ_MYSQL_VERIFY_USERNAME:cartiq_user}","spring.datasource.password=${CARTIQ_DB_PASSWORD}",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver","smartcart.sample-data=false",
    "smartcart.import-starter-catalogue=false","smartcart.jwt-secret=local-verification-secret-at-least-32-characters"})
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="CARTIQ_MYSQL_VERIFY",matches="true")
class MysqlMilestone4Test {
    @Autowired Db db; @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired LocalStoreService stores;
    @Test void priceObservationHistoryUsesMysqlAndRollsBackFixtures() {
        assertEquals("cartiq_catalogue_verify_retry",db.one("SELECT DATABASE() AS schema_name").get("schema_name"));
        PriceObservationIntegrationTest.exercise(db);
    }
    @Test void migrationAndComparisonWorkWithMysqlAndRollbackFixtures() throws Exception {
        assertEquals(7,db.count("SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('1','2','3','4','5','6','7') AND success=1"));
        long products=db.count("SELECT COUNT(*) FROM products"), variants=db.count("SELECT COUNT(*) FROM product_variants");
        String prefix="v"+UUID.randomUUID().toString().replace("-","").substring(0,20), user=UUID.randomUUID().toString();
        String category=db.one("SELECT id FROM product_categories ORDER BY id LIMIT 1").get("id").toString();
        String location=db.one("SELECT id FROM delivery_locations ORDER BY id LIMIT 1").get("id").toString();
        db.insert("users",Db.fields("id",user,"name","Test admin","email",user+"@example.invalid","password_hash","unused","role","admin"));
        db.insert("products",Db.fields("id",prefix,"category_id",category,"name","Audit item","brand","Audit brand","variant_name","Plain","description","Fixture","color","#123456","art","bag"));
        db.insert("product_variants",Db.fields("id",prefix,"product_id",prefix,"pack_label","1 kg","amount_base",1000,"base_unit","g"));
        var timestamp=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var auth=jwt().jwt(j->j.subject(user)).authorities(new SimpleGrantedAuthority("SCOPE_admin"));
        for(String platform:List.of("blinkit","instamart","zepto")){
            String listing=prefix+"-"+platform,price=prefix+"-"+platform+"-price",sku=prefix+"-"+platform;
            db.insert("platform_products",Db.fields("id",listing,"variant_id",prefix,"platform_id",platform,"retailer_sku",sku));
            db.insert("provider_mappings",Db.fields("platform_product_id",listing,"matched_by",user,"matched_at",timestamp));
            db.insert("location_prices",Db.fields("id",price,"platform_product_id",listing,"location_id",location,"price_minor",1000,"availability","available","observed_at",timestamp,"source","REWALTO","verification","VERIFIED"));
            var input=Map.ofEntries(Map.entry("sku",sku),Map.entry("name","Audit item"),Map.entry("brand","Audit brand"),Map.entry("variantName","Plain"),Map.entry("amountBase",1000),Map.entry("baseUnit","g"),Map.entry("stockQuantity",4),Map.entry("deliveryChargeMinor",0),Map.entry("handlingChargeMinor",0),Map.entry("minimumMinor",0),Map.entry("evidenceSource","test fixture"));
            String endpoint="/api/admin/provider/observations/"+price+"/comparison-evidence";
            mvc.perform(put(endpoint).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input))).andExpect(status().isUnauthorized());
            mvc.perform(put(endpoint).with(jwt()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input))).andExpect(status().isForbidden());
            mvc.perform(put(endpoint).with(auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input))).andExpect(status().isNoContent());
        }
        String store=stores.create(new LocalStoreService.Store(prefix,"Test","560038",0.0,0.01,true,true,0L,true));
        stores.saveListing(store,new LocalStoreService.Listing(prefix,1000L,null,4));
        db.insert("local_store_comparison_terms",Db.fields("store_id",store,"pickup_charge_minor",0,"delivery_charge_minor",0,"pickup_minimum_minor",0,"delivery_minimum_minor",0,"observed_at",timestamp));
        var response=mvc.perform(get("/api/products/"+prefix+"/comparison").param("locationId",location).param("latitude","0").param("longitude","0").param("fulfillment","delivery").param("quantity","2")).andExpect(status().isOk()).andReturn();
        var result=json.readTree(response.getResponse().getContentAsString());
        assertEquals("blinkit",result.path("lowestEligible").path("source").asText());
        assertEquals(2000,result.path("lowestEligible").path("totalMinor").asInt());
        assertEquals(0,result.path("savingsMinor").asInt());
        assertEquals(3,db.count("SELECT COUNT(*) FROM online_comparison_evidence WHERE price_id LIKE ?",prefix+"%"));
        assertEquals(products+1,db.count("SELECT COUNT(*) FROM products"));assertEquals(variants+1,db.count("SELECT COUNT(*) FROM product_variants"));
    }
}

