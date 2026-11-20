package com.smartcart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.repository.Db;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import static com.smartcart.repository.Db.fields;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="CARTIQ_OFFER_FRESHNESS_SECONDS=120")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class RetailerOperationsTest {
    @Autowired MockMvc mvc;
    @Autowired Db db;
    @Autowired ObjectMapper json;
    private static final String COVERAGE="/api/admin/retailer-operations/coverage";
    private static final String MAPPING="/api/admin/provider/mapping";
    private static final String AREA="p7-area", OTHER="p7-other", CATEGORY="p7-category";
    private static final String REVIEWER="p7-reviewer";
    private final RequestPostProcessor admin=jwt().jwt(j->j.subject(REVIEWER)).authorities(new SimpleGrantedAuthority("SCOPE_admin"));
    @BeforeEach void fixtures(){
        db.insert("users",fields("id",REVIEWER,"name","Reviewer fixture","email","p7-reviewer@example.invalid","password_hash","fixture"));
        db.insert("product_categories",fields("id",CATEGORY,"name","Phase 7 test category"));
        for(var area:List.of(AREA,OTHER))db.insert("delivery_locations",fields("id",area,"name",area,"city","Fixture","postcode","641018"));
        for(var id:List.of("p7-fresh","p7-stale","p7-missing","p7-unmapped")){
            db.insert("products",fields("id",id,"category_id",CATEGORY,"name",id,"brand","Fixture","variant_name","standard",
                "description","Synthetic","color","#fff","art","bag","active",true));
            db.insert("product_variants",fields("id",id,"product_id",id,"pack_label","500 g","amount_base",500,"base_unit","g"));
        }
    }
    private Map<String,Object> decision(String id,String sku,String pack,String code,String prior){
        return new HashMap<>(Map.of("variantId",id,"platform","blinkit","retailerSku",sku,
            "listingIdentity",fields("brand","Fixture","name",id,"pack",pack,"gtin",code),
            "mappingEvidence",fields("decisionId",UUID.randomUUID().toString(),"sourceProvider","QUICKCOMMERCEAPI","sourceRecordId","fixture-only",
                "evidenceReference","test fixture","evidenceSha256","a".repeat(64),"permissionReference","synthetic test","rationale","synthetic",
                "supersedesDecisionId",prior)));
    }
    private void approve(Map<String,Object> d) throws Exception {
        mvc.perform(put(MAPPING).with(admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(d))).andExpect(status().isOk());
    }
    private void price(String id,Long price,long age,String area){
        db.insert("location_prices",fields("id",id+"-"+area,"platform_product_id",id+"-blinkit","location_id",area,"price_minor",price,
            "availability","available","observed_at",Instant.now().minusSeconds(age),"source","SYNTHETIC","verification","VERIFIED"));
    }
    @Test void calculatesDistinctCoverageAndKeepsMappingSeparateFromOfferAndFees() throws Exception {
        mvc.perform(get(COVERAGE).param("locationId",AREA)).andExpect(status().isUnauthorized());
        mvc.perform(get(COVERAGE).with(jwt()).param("locationId",AREA)).andExpect(status().isForbidden());
        for(var id:List.of("p7-fresh","p7-stale","p7-missing"))approve(decision(id,id+"-sku","500 g",null,null));
        db.run("UPDATE grocery_platforms SET delivery_minor=NULL WHERE id='blinkit'");
        price("p7-fresh",4500L,10,AREA);price("p7-stale",4600L,300,AREA);price("p7-missing",null,20,AREA);
        mvc.perform(get(COVERAGE).with(admin).param("locationId",AREA).param("retailer","blinkit").param("category",CATEGORY))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.freshnessSeconds").value(120)).andExpect(jsonPath("$.counts.totalProducts").value(4))
            .andExpect(jsonPath("$.counts.approvedProducts").value(3)).andExpect(jsonPath("$.counts.productsWithoutMapping").value(1))
            .andExpect(jsonPath("$.counts.staleProducts").value(1)).andExpect(jsonPath("$.counts.missingPriceProducts").value(1))
            .andExpect(jsonPath("$.counts.uncertainStockProducts").value(3)).andExpect(jsonPath("$.counts.missingFeeProducts").value(4))
            .andExpect(jsonPath("$.retailers[0].recordedUsableProducts").value(1))
            .andExpect(jsonPath("$.retailers[0].usableLiveProductsForCurrentAccount").value(0))
            .andExpect(jsonPath("$.rows[?(@.status == 'STALE')].observationSource",hasItem("SYNTHETIC")));
        mvc.perform(get(COVERAGE).with(admin).param("locationId",OTHER).param("category",CATEGORY).param("retailer","blinkit").param("status","NO_OBSERVATION"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rows.length()").value(3))
            .andExpect(jsonPath("$.retailers[0].recordedUsableProducts").value(0));
    }

    @Test void conflictRemainsUnresolvedAndRevisionCannotReassignPriceHistory() throws Exception {
        db.insert("variant_barcodes",fields("variant_id","p7-fresh","gtin","04006381333931"));
        var conflicting=decision("p7-fresh","p7-sku","2 x 250 g","4006381333931",null);
        mvc.perform(post(MAPPING+"/preview").with(admin).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("variantId","p7-fresh","listingIdentity",conflicting.get("listingIdentity")))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.assessment.blockingReasons",hasItem("PACK_DIFFERENT")));
        mvc.perform(put(MAPPING).with(admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(conflicting))).andExpect(status().isConflict());
        conflicting.put("disposition","UNRESOLVED");
        mvc.perform(post(MAPPING+"/decisions").with(admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(conflicting))).andExpect(status().isOk());
        String previous=(String)((Map<?,?>)conflicting.get("mappingEvidence")).get("decisionId");
        mvc.perform(get(COVERAGE).with(admin).param("locationId",AREA).param("retailer","blinkit").param("category",CATEGORY).param("status","PACK_CONFLICT"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].status").value("PACK_CONFLICT"));
        var corrected=decision("p7-fresh","p7-sku","500 g","4006381333931",previous);
        ((Map<String,Object>)corrected.get("mappingEvidence")).put("evidenceSha256","b".repeat(64));
        approve(corrected);
        price("p7-fresh",4500L,10,AREA);
        mvc.perform(put(MAPPING).with(admin).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(decision("p7-fresh","replacement-sku","500 g","4006381333931",null)))).andExpect(status().isConflict());
        var rejected=decision("p7-fresh","p7-sku","500 g","4006381333931",(String)((Map<?,?>)corrected.get("mappingEvidence")).get("decisionId"));
        rejected.put("disposition","REJECTED");
        mvc.perform(post(MAPPING+"/decisions").with(admin).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(rejected))).andExpect(status().isOk());
        mvc.perform(get(MAPPING+"/decisions").with(admin).param("variantId","p7-fresh").param("platform","blinkit"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[*].disposition",contains("UNRESOLVED","APPROVED","REJECTED")));
        org.junit.jupiter.api.Assertions.assertEquals(1,db.count("SELECT COUNT(*) FROM location_prices WHERE id=?", "p7-fresh-"+AREA));
    }
}
