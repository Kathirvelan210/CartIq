package com.smartcart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.repository.Db;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes=LocalStoreHttpAuditTest.Config.class,properties={
    "spring.datasource.url=jdbc:h2:mem:securityaudit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "smartcart.sample-data=false","smartcart.import-starter-catalogue=true",
    "smartcart.jwt-secret=local-verification-secret-at-least-32-characters"})
@ActiveProfiles({"test","local-store-audit"}) @AutoConfigureMockMvc @Transactional
@org.springframework.test.context.TestExecutionListeners(listeners={
    org.springframework.test.context.web.ServletTestExecutionListener.class,
    org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,
    org.springframework.test.context.transaction.TransactionalTestExecutionListener.class},
    mergeMode=org.springframework.test.context.TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class ComparisonSecurityTest {
    @Autowired Db db; @Autowired MockMvc mvc; @Autowired JwtEncoder encoder; @Autowired ObjectMapper json;
    String token(String role){
        String id=Db.id();db.insert("users",Db.fields("id",id,"name","Audit","email",id+"@example.invalid","password_hash","unused","role",role));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),JwtClaimsSet.builder().issuer("smartcart-ai").subject(id).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(120)).claim("version",0L).claim("scope",role).build())).getTokenValue();
    }
    @Test void evidenceRequiresAdminAndRejectsMissingObservation()throws Exception{
        String endpoint="/api/admin/provider/observations/missing/comparison-evidence", body="{}";
        mvc.perform(put(endpoint).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(put(endpoint).header("Authorization","Bearer "+token("customer")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(put(endpoint).header("Authorization","Bearer "+token("admin")).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
    }
    @Test void invalidIdentityAndMissingMappingAreRejected()throws Exception {
        String admin=token("admin"),owner=Db.id(),endpoint="/api/admin/provider/observations/fixture-price/comparison-evidence";
        db.insert("users",Db.fields("id",owner,"name","Fixture","email",owner+"@example.invalid","password_hash","unused","role","admin"));
        var product=db.one("SELECT p.* FROM products p JOIN product_variants v ON v.product_id=p.id WHERE v.id=?","milk");
        db.run("UPDATE products SET variant_name=? WHERE id=?","Plain",Db.text(product,"id"));
        var variant=db.one("SELECT amount_base,base_unit FROM product_variants WHERE id=?","milk");
        db.insert("platform_products",Db.fields("id","fixture-mapping","platform_id","blinkit","variant_id","milk","retailer_sku","fixture-sku"));
        db.insert("location_prices",Db.fields("id","fixture-price","platform_product_id","fixture-mapping","location_id","blr-indiranagar","price_minor",100,"availability","available","observed_at",Instant.now(),"source","REWALTO","verification","VERIFIED"));
        Map<String,Object> input=new HashMap<>();
        input.put("sku","fixture-sku");input.put("name",Db.text(product,"name"));input.put("brand",Db.text(product,"brand"));
        input.put("variantName","Plain");input.put("amountBase",Db.decimal(variant,"amount_base"));input.put("baseUnit",Db.text(variant,"base_unit"));
        input.put("stockQuantity",2);input.put("deliveryChargeMinor",0);input.put("handlingChargeMinor",0);input.put("minimumMinor",0);input.put("evidenceSource","test fixture");
        mvc.perform(put(endpoint).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input))).andExpect(status().isNotFound());
        db.insert("provider_mappings",Db.fields("platform_product_id","fixture-mapping","matched_by",owner,"matched_at",Instant.now()));
        for(var change:List.of(Map.entry("sku",""),Map.entry("sku","wrong"),Map.entry("name","wrong"),Map.entry("brand","wrong"),Map.entry("variantName","Chocolate"),Map.entry("variantName","Standard"),Map.entry("amountBase",-1),Map.entry("baseUnit","kg"),Map.entry("stockQuantity",-1),Map.entry("deliveryChargeMinor",-1),Map.entry("handlingChargeMinor",-1),Map.entry("minimumMinor",-1),Map.entry("evidenceSource",""))) {
            var invalid=new HashMap<>(input);invalid.put(change.getKey(),change.getValue());
            mvc.perform(put(endpoint).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(invalid))).andExpect(status().isBadRequest());
            assertEquals(0,db.count("SELECT COUNT(*) FROM online_comparison_evidence WHERE price_id=?","fixture-price"));
        }
        var missing=new HashMap<>(input);missing.remove("sku");
        mvc.perform(put(endpoint).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(missing))).andExpect(status().isBadRequest());
        mvc.perform(put(endpoint).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isBadRequest());
        mvc.perform(put(endpoint).header("Authorization","Bearer "+admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input))).andExpect(status().isNoContent());
        assertEquals(1,db.count("SELECT COUNT(*) FROM online_comparison_evidence WHERE price_id=?","fixture-price"));
    }
}
