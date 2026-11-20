package com.smartcart;

import com.smartcart.catalogimport.ProductMatching;
import com.smartcart.integration.QuickCommerceProvider;
import com.smartcart.repository.Db;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import static com.smartcart.repository.Db.fields;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles({"local","test"})
@SpringBootTest(properties={"QUICKCOMMERCE_SHOPPING_ENABLED=true","QUICKCOMMERCE_OFFER_PERMISSION_REFERENCE=synthetic-grant",
    "QUICKCOMMERCE_LEGACY_LOOKUPS_ENABLED=true",
    "QUICKCOMMERCE_OFFER_LOCATION_ID=phase6-area","QUICKCOMMERCE_OFFER_LAT=12.9","QUICKCOMMERCE_OFFER_LON=77.66",
    "smartcart.sample-data=false","smartcart.jwt-secret=synthetic-local-phase-six-signing-value","smartcart.import-starter-catalogue=true",
    "spring.datasource.url=jdbc:h2:mem:phase6-live;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@Transactional
class LiveOfferIntegrationTest {
    @MockitoBean QuickCommerceProvider provider;
    @Autowired MockMvc mvc;
    @Autowired Db db;
    private static final String PACK="phase6-500",SKU="fixture-listing",AREA="phase6-area";
    private static final String API="/api/shopping/retailers/offers/"+PACK;
    private static final String SEARCH="Fixture Brand Fixture rice 500 g";
    private static final String REFRESH="""
        {"locationId":"phase6-area","lat":12.9,"lon":77.66,"pincode":"641018","retailers":["blinkit"]}
        """;
    private static final String PLAN="""
        {"meals":[],"items":[{"productId":"phase6-500","quantity":2}],"diet":"ANY","onHand":{},"budgetMinor":13000}
        """;
    @BeforeEach void fixtures() {
        when(provider.configured()).thenReturn(true);
        for(String user:List.of("phase6-owner","phase6-other"))
            db.insert("users",fields("id",user,"name",user,"email",user+"@example.invalid","password_hash","fixture"));
        db.insert("delivery_locations",fields("id",AREA,"name","Phase 6 fixture area","city","Fixture","postcode","641018"));
        db.insert("product_categories",fields("id","phase6-category","name","Phase 6 fixture groceries"));
        db.insert("products",fields("id","phase6-rice","name","Fixture rice","brand","Fixture Brand","variant_name","Plain",
            "category_id","phase6-category","description","Synthetic","color","#fff","art","bag","active",true));
        db.insert("product_variants",fields("id",PACK,"product_id","phase6-rice","pack_label","500 g","amount_base",500,"base_unit","g"));
        db.insert("platform_products",fields("id","phase6-listing","variant_id",PACK,"platform_id","blinkit","retailer_sku",SKU));
        db.insert("provider_mappings",fields("platform_product_id","phase6-listing","matched_by","phase6-owner","matched_at",Instant.now()));
        db.insert("provider_mapping_decisions",fields("decision_id","phase6-decision","variant_id",PACK,"platform_id","blinkit","retailer_sku",SKU,
            "revision_no",1,"disposition","APPROVED","reviewed_by","phase6-owner","source_provider","QUICKCOMMERCEAPI","source_record_id","fixture",
            "evidence_reference","fixture-only","evidence_sha256","a".repeat(64),"permission_reference","synthetic-grant","rationale","Fixture",
            "listing_brand","Fixture Brand","listing_title","Fixture rice","listing_pack","500 g","canonical_gtins","",
            "canonical_pack","500 g","canonical_brand","Fixture Brand","canonical_title","Fixture rice","canonical_variant","Plain","blocking_reasons",""));
        db.run("UPDATE grocery_platforms SET delivery_minor=500,handling_minor=100,minimum_minor=0,free_delivery_minor=NULL,fee_source='VERIFIED' WHERE id='blinkit'");
    }
    private RequestPostProcessor jwtFor(String owner){return jwt().jwt(j->j.subject(owner));}
    private QuickCommerceProvider.Product product(String pack,long price,boolean available,String timestamp){
        return new QuickCommerceProvider.Product(SKU,"Fixture rice","Fixture Brand",pack,"INR",price,7500L,available,4,"fixture-store",timestamp,
            ProductMatching.pack(pack),null,null,null,null,null,null,null,null);
    }
    private QuickCommerceProvider.Result response(QuickCommerceProvider.Product row){
        return new QuickCommerceProvider.Result("blinkit",91,200,new QuickCommerceProvider.Location(12.9,77.66,"641018","641018"),
            List.of(row),Instant.now(),UUID.randomUUID().toString());
    }
    private org.springframework.test.web.servlet.ResultActions preview(String owner) throws Exception {
        return preview(owner,"12.9,77.66,641018");
    }
    private org.springframework.test.web.servlet.ResultActions preview(String owner,String position) throws Exception {
        return mvc.perform(post("/api/me/shopping-intelligence/weekly/preview").with(jwtFor(owner)).param("locationId",AREA)
            .header("X-CartIQ-Offer-Position",position)
            .contentType(MediaType.APPLICATION_JSON).content(PLAN)).andExpect(status().isOk());
    }
    @Test void verifiedRefreshRepricesAnOwnedPlanAndUnavailableDetailsRemoveTheQuote() throws Exception {
        when(provider.search("blinkit",SEARCH,12.9,77.66,"641018")).thenReturn(response(product("500 g",6000,true,null)));
        preview("phase6-owner").andExpect(jsonPath("$.plan.comparison.recommended.totalMinor").value(nullValue()));
        mvc.perform(post(API+"/refresh").with(jwtFor("phase6-owner")).contentType(MediaType.APPLICATION_JSON).content(REFRESH))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.retailers[0].offers[0].match").value("VERIFIED"))
            .andExpect(jsonPath("$.quotes[0].origin").value("LIVE_RESPONSE"))
            .andExpect(jsonPath("$.quotes[0].unitPriceMinor").value(12000));
        preview("phase6-owner").andExpect(jsonPath("$.plan.comparison.recommended.totalMinor").value(12600));
        preview("phase6-owner","12.9,76.66,641018").andExpect(jsonPath("$.plan.comparison.recommended.totalMinor").value(nullValue()));
        preview("phase6-owner","12.9,77.66,560038").andExpect(jsonPath("$.plan.comparison.recommended.totalMinor").value(nullValue()));
        preview("phase6-owner","").andExpect(jsonPath("$.plan.comparison.recommended.totalMinor").value(nullValue()));
        preview("phase6-other").andExpect(jsonPath("$.plan.comparison.recommended.totalMinor").value(nullValue()));
        mvc.perform(get(API).with(jwtFor("phase6-owner")).param("locationId","blr-indiranagar")).andExpect(status().isOk())
            .andExpect(jsonPath("$.configured").value(false)).andExpect(jsonPath("$.quotes").isEmpty());
        when(provider.item("blinkit",SKU,12.9,77.66,"641018")).thenReturn(response(product("500 g",6200,false,null)));
        var item="{\"scope\":"+REFRESH+",\"nativeId\":\""+SKU+"\"}";
        mvc.perform(post(API+"/item").with(jwtFor("phase6-owner")).contentType(MediaType.APPLICATION_JSON).content(item))
            .andExpect(status().isOk()).andExpect(jsonPath("$.quotes").isEmpty());
        preview("phase6-owner").andExpect(jsonPath("$.plan.comparison.recommended.totalMinor").value(nullValue()));
        org.junit.jupiter.api.Assertions.assertEquals(0,db.count("SELECT COUNT(*) FROM approved_price_observations WHERE variant_id=?",PACK));
        org.junit.jupiter.api.Assertions.assertEquals(0,db.count("SELECT COUNT(*) FROM location_prices WHERE platform_product_id='phase6-listing'"));
    }
    @Test void staleAndConflictingOffersStayOutOfPlansAndBadScopesDoNotDispatch() throws Exception {
        mvc.perform(post(API+"/refresh").with(jwtFor("phase6-owner")).contentType(MediaType.APPLICATION_JSON)
            .content(REFRESH.replace("77.66","76.66"))).andExpect(status().isBadRequest());
        mvc.perform(post(API+"/refresh").contentType(MediaType.APPLICATION_JSON).content(REFRESH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(provider);
        when(provider.search("blinkit",SEARCH,12.9,77.66,"641018"))
            .thenReturn(response(product("2 x 250 g",6000,true,null)))
            .thenReturn(response(product("500 g",6000,true,Instant.now().minusSeconds(3600).toString())));
        mvc.perform(post(API+"/refresh").with(jwtFor("phase6-owner")).contentType(MediaType.APPLICATION_JSON).content(REFRESH))
            .andExpect(status().isOk()).andExpect(jsonPath("$.retailers[0].offers[0].match",not("VERIFIED")))
            .andExpect(jsonPath("$.quotes").isEmpty());
        mvc.perform(post(API+"/refresh").with(jwtFor("phase6-owner")).contentType(MediaType.APPLICATION_JSON).content(REFRESH))
            .andExpect(status().isOk()).andExpect(jsonPath("$.quotes").isEmpty());
        preview("phase6-owner").andExpect(jsonPath("$.plan.comparison.recommended.totalMinor").value(nullValue()));
    }

    @Test void aBarcodeAssignedToADifferentVariantInvalidatesAnOtherwiseApprovedOffer() throws Exception {
        db.insert("product_variants",fields("id","phase6-other-pack","product_id","phase6-rice","pack_label","1 kg","amount_base",1000,"base_unit","g"));
        var row=new QuickCommerceProvider.Product(SKU,"Fixture rice","Fixture Brand","500 g","INR",6000L,7500L,true,4,
            "fixture-store",null,ProductMatching.pack("500 g"),null,null,null,"4006381333931","04006381333931",null,null,null);
        when(provider.search("blinkit",SEARCH,12.9,77.66,"641018")).thenReturn(response(row));
        mvc.perform(post(API+"/refresh").with(jwtFor("phase6-owner")).contentType(MediaType.APPLICATION_JSON).content(REFRESH))
            .andExpect(status().isOk()).andExpect(jsonPath("$.quotes[0].origin").value("LIVE_RESPONSE"));
        db.insert("variant_barcodes",fields("variant_id","phase6-other-pack","gtin","04006381333931"));
        preview("phase6-owner").andExpect(jsonPath("$.plan.comparison.recommended.totalMinor").value(nullValue()));
    }
}
