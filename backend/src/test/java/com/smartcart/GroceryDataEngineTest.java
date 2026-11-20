package com.smartcart;

import com.fasterxml.jackson.databind.*;
import com.smartcart.catalogimport.ImportNormalizer;
import com.smartcart.catalogimport.ProductMatching;
import com.smartcart.grocery.PriceObservations;
import com.smartcart.repository.Db;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static com.smartcart.repository.Db.fields;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class GroceryDataEngineTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Db db;
    @Autowired PriceObservations collection;
    final String account=UUID.randomUUID().toString(), decision=UUID.randomUUID().toString();
    final Instant now=Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @BeforeEach void fixture() {
        db.insert("users",fields("id",account,"email",account+"@fixture.invalid","password_hash","unusable","name","Fixture reviewer"));
        db.insert("products",fields("id","engine-product","category_id","dairy","name","Example Milk","brand","Ｆｉｘｔｕｒｅ", "variant_name","Standard","description","Fixture","color","#ffffff","art","bag"));
        db.insert("product_variants",fields("id","engine-pack","product_id","engine-product","pack_label","1 l","amount_base",1000,"base_unit","ml"));
        for (String location : List.of("engine-area","engine-other")) {
            db.insert("delivery_locations",fields("id",location,"name","Fixture locality","city","Fixture city","postcode","123456"));
        }
        db.insert("platform_products",fields("id","engine-listing","platform_id","blinkit","variant_id","engine-pack","retailer_sku","fixture-listing"));
        db.insert("provider_mapping_decisions",fields("decision_id",decision,"variant_id","engine-pack","platform_id","blinkit",
            "retailer_sku","fixture-listing","revision_no",1,"disposition","APPROVED","reviewed_by",account,"source_provider","FIXTURE_ONLY",
            "source_record_id","synthetic-mapping","evidence_reference","synthetic evidence","evidence_sha256","a".repeat(64),"permission_reference","synthetic grant",
            "rationale","Fixture only","listing_pack","1000 ml","canonical_pack","1 l","canonical_gtins","[]","canonical_brand","Ｆｉｘｔｕｒｅ",
            "canonical_title","Example Milk","canonical_variant","Standard","blocking_reasons","[]"));
    }

    @Test void retainsOnlyExplicitlyAuthorizedObservationsAndDeduplicates() throws Exception {
        String grant=grant("engine-area",true,false);
        Instant time=now.minus(5,ChronoUnit.DAYS);
        JsonNode first=record(grant,"one",time,4200L,"1 l",200);
        assertTrue(first.path("created").asBoolean());
        JsonNode copy=record(grant,"one",time,4200L,"1000 ml",200);
        assertFalse(copy.path("created").asBoolean());
        assertEquals(first.path("id"),copy.path("id"));
        record(grant,"one",time,4300L,"1 l",409);
        record(grant,"multipack",time.plusSeconds(1),7000L,"2 x 500 ml",409);
        record(grant,"invalid",time.plusSeconds(1),-1L,"1 l",400);
        record(grant,"future",now.plusSeconds(100),1700L,"1 l",400);
        record(grant,"expired-window",now.minus(100,ChronoUnit.DAYS),1700L,"1 l",400);
        mvc.perform(delete("/api/admin/grocery/retention-grants/"+grant).with(admin())).andExpect(status().isNoContent());
        record(grant,"revoked",time,4200L,"1 l",409);
        assertEquals(0,db.count("SELECT COUNT(*) FROM approved_price_observations WHERE grant_id=?",grant));
    }

    @Test void separatesLocationsUsesNullableMetricsAndExcludesExpiredGrants() throws Exception {
        String here=grant("engine-area",true,false), other=grant("engine-other",true,false);
        record(here,"one",now.minus(5,ChronoUnit.DAYS),4100L,"1 l",200);
        record(here,"two",now.minusSeconds(400),5100L,"1 l",200);
        record(here,"three",now.minusSeconds(300),null,"1 l",200);
        record(other,"away",now.minus(4,ChronoUnit.DAYS),9900L,"1 l",200);
        mvc.perform(get("/api/grocery/variants/engine-pack/price-history").with(jwt()).param("locationId","engine-area"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.stats.averageMinor").value(4600)).andExpect(jsonPath("$.stats.priced").value(2))
            .andExpect(jsonPath("$.retailers[0].freshness").value("FRESH")).andExpect(jsonPath("$.retailers[0].days[0].stats.observations").value(1))
            .andExpect(jsonPath("$.retailers[0].priceChangeMinor").doesNotExist());
        mvc.perform(get("/api/grocery/variants/engine-pack/price-history").with(jwt()).param("locationId","engine-area")
            .param("until",now.minusSeconds(350).toString())).andExpect(status().isOk())
            .andExpect(jsonPath("$.retailers[0].priceChangeMinor").value(1000));
        mvc.perform(get("/api/grocery/variants/engine-pack/price-history").with(jwt()).param("locationId","engine-other"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.stats.minimumMinor").value(9900))
            .andExpect(jsonPath("$.retailers[0].freshness").value("STALE"));
        db.run("UPDATE price_retention_grants SET retain_until=? WHERE id=?",now.minusSeconds(2),here);
        mvc.perform(get("/api/grocery/variants/engine-pack/price-history").with(jwt()).param("locationId","engine-area"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.stats.observations").value(0));
        collection.purgeExpired();
        assertEquals(0,db.count("SELECT COUNT(*) FROM approved_price_observations WHERE grant_id=?",here));
    }

    @Test void datasetIsVersionedChronologicalAndProtected() throws Exception {
        String allowed=grant("engine-area",false,true), displayOnly=grant("engine-area",true,false);
        Instant train=now.minus(7,ChronoUnit.DAYS), validate=now.minus(5,ChronoUnit.DAYS), test=now.minus(3,ChronoUnit.DAYS);
        record(allowed,"train",train,1000L,"1 l",200);
        record(allowed,"validate",validate,null,"1 l",200);
        record(allowed,"test",test,3000L,"1 l",200);
        record(displayOnly,"history-only",test.plusSeconds(120),8000L,"1 l",200);
        String route="/api/admin/grocery/dataset";
        mvc.perform(get(route).with(jwt())).andExpect(status().isForbidden());
        var request=get(route).with(admin()).param("variantId","engine-pack").param("locationId","engine-area")
            .param("from",train.toString()).param("validationStart",validate.toString()).param("testStart",test.toString()).param("until",now.toString());
        var result=mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(3))
            .andExpect(jsonPath("$.rows[0].split").value("TRAIN")).andExpect(jsonPath("$.rows[1].split").value("VALIDATION"))
            .andExpect(jsonPath("$.rows[2].split").value("TEST")).andExpect(jsonPath("$.rows[2].previousPriceMinor").doesNotExist())
            .andExpect(jsonPath("$.rows[2].pricePerBaseUnitMinor").value(3.0)).andReturn();
        assertTrue(json.readTree(result.getResponse().getContentAsByteArray()).path("version").asText().matches("[a-f0-9]{64}"));
    }

    @Test void preservesCanonicalIdentityAndSharedBarcodeCannotOverridePack() throws Exception {
        assertEquals("00012345678905",ImportNormalizer.gtin("012345678905"));
        assertThrows(RuntimeException.class,()->ImportNormalizer.gtin("012345678906"));
        var left=new ProductMatching.Identity("012345678905","Fixture","Example Milk","1 l",null,null);
        var right=new ProductMatching.Identity("012345678905","Fixture","Example Milk","2 x 500 ml",null,null);
        assertTrue(ProductMatching.compareHybrid(left,right).reasons().contains("PACK_DIFFERENT"));
        mvc.perform(get("/api/grocery/variants/engine-pack/identity").with(jwt()).param("locationId","engine-area"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.normalizedBrand").value("fixture"))
            .andExpect(jsonPath("$.normalizedPack.each.amount").value(1000));
    }

    @Test void linksRecipeIngredientsWithoutExposingPantryAcrossAccounts() throws Exception {
        String pantry="/api/me/pantry";
        var demo=mvc.perform(post("/api/auth/demo")).andExpect(status().isOk()).andReturn();
        String demoToken=json.readTree(demo.getResponse().getContentAsByteArray()).path("token").asText();
        String demoId=json.readTree(demo.getResponse().getContentAsByteArray()).path("user").path("id").asText();
        db.insert("user_pantry",fields("id",UUID.randomUUID().toString(),"user_id",demoId,"product_id","engine-product","amount_base",150,"base_unit","ml"));
        mvc.perform(get(pantry).header("Authorization","Bearer "+demoToken)).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.productId=='engine-product')]").isNotEmpty());
        mvc.perform(get(pantry).with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.productId=='engine-product')]").isEmpty());
        var inserted=mvc.perform(post("/api/admin/grocery/ingredients").with(admin()).contentType("application/json")
            .content("{\"name\":\"Fixture ingredient\",\"evidenceReference\":\"Synthetic evidence\"}")).andExpect(status().isOk()).andReturn();
        String ingredient=json.readTree(inserted.getResponse().getContentAsByteArray()).path("id").asText();
        db.insert("recipes",fields("id","engine-recipe","name","Fixture recipe","servings",2,"instructions","Fixture only","source","Fixture","sample",true,"color","#ffffff"));
        String component=UUID.randomUUID().toString();
        db.insert("recipe_ingredients",fields("id",component,"recipe_id","engine-recipe","product_id","engine-product","amount_base",150,"base_unit","ml"));
        mvc.perform(put("/api/admin/grocery/recipe-components/"+component+"/ingredient/"+ingredient).with(admin())).andExpect(status().isNoContent());
        mvc.perform(get("/api/grocery/recipes/engine-recipe/components").with(jwt())).andExpect(status().isOk())
            .andExpect(jsonPath("$.servings").value(2)).andExpect(jsonPath("$.ingredients[0].name").value("Fixture ingredient"))
            .andExpect(jsonPath("$.ingredients[0].amount").value(150));
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor admin() {
        return jwt().jwt(j->j.subject(account)).authorities(new SimpleGrantedAuthority("SCOPE_admin"));
    }
    private String grant(String location,boolean history,boolean dataset) throws Exception {
        var grant=new PriceObservations.Grant("engine-listing",location,decision,"FIXTURE_ONLY","synthetic permission","synthetic evidence",
            "a".repeat(64),now.minus(30,ChronoUnit.DAYS),now.plus(1,ChronoUnit.DAYS),now.plus(30,ChronoUnit.DAYS),history,dataset);
        var result=mvc.perform(post("/api/admin/grocery/retention-grants").with(admin()).contentType("application/json")
            .content(json.writeValueAsBytes(grant))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).path("id").asText();
    }
    private JsonNode record(String grant,String key,Instant time,Long price,String pack,int code) throws Exception {
        var request=new PriceObservations.Input(grant,key,"synthetic evidence",time,"fixture-listing",pack,"INR",price,null,null,PriceObservations.Stock.AVAILABLE);
        var result=mvc.perform(post("/api/admin/grocery/observations").with(admin()).contentType("application/json")
            .content(json.writeValueAsBytes(request))).andExpect(status().is(code)).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
}
