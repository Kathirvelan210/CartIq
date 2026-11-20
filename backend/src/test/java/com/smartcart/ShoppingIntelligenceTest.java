package com.smartcart;

import com.smartcart.repository.Db;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import static com.smartcart.repository.Db.fields;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ShoppingIntelligenceTest {
    @Autowired MockMvc mvc;
    @Autowired Db db;
    private static final String API="/api/me/shopping-intelligence";
    private static final String LOCATION="phase4-location";
    private static final String PACK="phase4-500g";
    private static final String LARGE="phase4-1000g";
    private static final String PRODUCT="phase4-canonical";
    private static final String LIMITS="""
        {"perPersonDaily":100,"storageCapacity":2000,"planningDays":5,"budgetMinor":20000}
        """;

    @BeforeEach void seed() {
        for(String user:new String[]{"p4-one","p4-two"})
            db.insert("users",fields("id",user,"name",user,"email",user+"@example.test","password_hash","fixture"));
        db.insert("delivery_locations",fields("id",LOCATION,"name","Fixture service area","city","Fixture city","postcode","641018"));
        db.insert("product_categories",fields("id","phase4-food","name","Fixture staples"));
        db.insert("products",fields("id",PRODUCT,"category_id","phase4-food","name","Fixture staple","brand","Fixture brand",
            "variant_name","Plain","description","Synthetic test only","color","#fff","art","bag","active",true));
        db.insert("product_variants",fields("id",PACK,"product_id",PRODUCT,"pack_label","500 g","amount_base",500,"base_unit","g"));
        db.insert("product_variants",fields("id",LARGE,"product_id",PRODUCT,"pack_label","1 kg","amount_base",1000,"base_unit","g"));
        quote(PACK,500,5500,10,"blinkit");
        quote(LARGE,1000,9000,4,"zepto");
    }

    private void quote(String variant,int grams,long price,int stock,String platform) {
        String listing=variant+"-"+platform, priceId=listing+"-price";
        Instant observed=Instant.now().minusSeconds(3);
        db.insert("platform_products",fields("id",listing,"platform_id",platform,"variant_id",variant,"retailer_sku",listing));
        db.insert("provider_mappings",fields("platform_product_id",listing,"matched_by","p4-one","matched_at",observed));
        db.insert("location_prices",fields("id",priceId,"platform_product_id",listing,"location_id",LOCATION,"price_minor",price,
            "availability","available","observed_at",observed,"source","TEST_FIXTURE","verification","VERIFIED"));
        db.insert("online_comparison_evidence",fields("price_id",priceId,"retailer_sku",listing,"product_name","Fixture staple",
            "brand","Fixture brand","variant_name","Plain","amount_base",grams,"base_unit","g","price_observed_at",observed,
            "stock_quantity",stock,"evidence_source","synthetic test","observed_at",observed,"verified_by","p4-one"));
    }

    private static RequestPostProcessor owner(String id) { return jwt().jwt(j->j.subject(id)); }

    private void preference(String user,String diet) throws Exception {
        mvc.perform(put(API+"/preferences").with(owner(user)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"householdSize\":2,\"diet\":\""+diet+"\",\"shoppingBudgetMinor\":20000}")).andExpect(status().isOk());
    }

    private void limits() throws Exception {
        preference("p4-one","ANY");
        mvc.perform(put(API+"/bulk/"+PACK+"/limits").with(owner("p4-one")).param("locationId",LOCATION)
            .contentType(MediaType.APPLICATION_JSON).content(LIMITS)).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions advice(String user,String location,String expiry) throws Exception {
        var req=get(API+"/bulk/"+PACK).with(owner(user)).param("locationId",location);
        if(expiry!=null)req.param("daysBeforeExpiry",expiry);
        return mvc.perform(req).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    }

    @Test void bulkComparesVerifiedPacksAndProtectsPantryStorageExpiryAndAccount() throws Exception {
        limits();
        advice("p4-one",LOCATION,"7").andExpect(jsonPath("$.action").value("BUY_LARGER_PACK"))
            .andExpect(jsonPath("$.recommended.subtotalMinor").value(9000)).andExpect(jsonPath("$.recommended.quote.stockPacks").value(4));
        advice("p4-one",LOCATION,"3").andExpect(jsonPath("$.action").value("DO_NOT_BUY_EXTRA"));
        advice("p4-one",LOCATION,null).andExpect(jsonPath("$.action").value("INSUFFICIENT_INFORMATION"));
        advice("p4-two",LOCATION,"7").andExpect(jsonPath("$.action").value("INSUFFICIENT_INFORMATION"));
        advice("p4-one","blr-indiranagar","7").andExpect(jsonPath("$.action").value("INSUFFICIENT_INFORMATION"));
        db.insert("user_pantry",fields("id","phase4-pantry","user_id","p4-one","product_id",PRODUCT,"amount_base",1000,"base_unit","g"));
        advice("p4-one",LOCATION,"7").andExpect(jsonPath("$.action").value("DO_NOT_BUY_EXTRA"));
    }

    @Test void excludesFormulationConflictsAndNeverUsesUnknownStockForExtraPacks() throws Exception {
        limits();
        db.insert("grocery_variant_details",fields("variant_id",LARGE,"formulation","Different formulation","evidence_reference","synthetic"));
        advice("p4-one",LOCATION,"7").andExpect(jsonPath("$.action").value("BUY_MULTIPLE_PACKS"))
            .andExpect(jsonPath("$.recommended.packCount").value(2));
        db.run("UPDATE online_comparison_evidence SET stock_quantity=NULL WHERE price_id=?",PACK+"-blinkit-price");
        advice("p4-one",LOCATION,"7").andExpect(jsonPath("$.action").value("INSUFFICIENT_INFORMATION"));
        db.run("UPDATE location_prices SET observed_at=? WHERE id=?",Instant.now().minusSeconds(3600),PACK+"-blinkit-price");
        advice("p4-one",LOCATION,"7").andExpect(jsonPath("$.reason",containsString("fresh")));
    }

    @Test void privateRecommendationsRespectPreferencesAndPantry() throws Exception {
        limits();
        db.insert("saved_products",fields("user_id","p4-one","variant_id",PACK));
        mvc.perform(get(API+"/recommendations").param("locationId",LOCATION)).andExpect(status().isUnauthorized());
        mvc.perform(get(API+"/recommendations").with(owner("p4-one")).param("locationId",LOCATION))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products[0].reason").value("In your saved products."))
            .andExpect(jsonPath("$.pantryGaps[0].name").value("Fixture staple"));
        mvc.perform(get(API+"/recommendations").with(owner("p4-two")).param("locationId",LOCATION))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products[0].reason").value("Fresh recorded offer in the catalogue for this location."))
            .andExpect(jsonPath("$.pantryGaps").isEmpty());
        preference("p4-two","VEGETARIAN");
        mvc.perform(get(API+"/recommendations").with(owner("p4-two")).param("locationId",LOCATION))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products").isEmpty());
        db.insert("grocery_variant_details",fields("variant_id",PACK,"dietary_attributes","vegetarian","evidence_reference","synthetic label"));
        mvc.perform(get(API+"/recommendations").with(owner("p4-two")).param("locationId",LOCATION))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products[0].product.id").value(PACK));
    }

    @Test void basketNeedsKnownFeesAndQuantityAndAssistantUsesExactAlternative() throws Exception {
        mvc.perform(put("/api/me/cart").with(owner("p4-one")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"productId\":\""+PACK+"\",\"locationId\":\""+LOCATION+"\",\"quantity\":2,\"preferences\":{\"intent\":\"NORMAL\"}}"))
            .andExpect(status().isOk());
        db.run("UPDATE grocery_platforms SET handling_minor=NULL WHERE id='blinkit'");
        mvc.perform(get(API+"/basket").with(owner("p4-one")).param("locationId",LOCATION).param("budgetMinor","13000"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INCOMPLETE"));
        db.run("UPDATE grocery_platforms SET delivery_minor=1000,handling_minor=500,minimum_minor=1000,free_delivery_minor=NULL,fee_source='VERIFIED' WHERE id='blinkit'");
        mvc.perform(get(API+"/basket").with(owner("p4-one")).param("locationId",LOCATION).param("budgetMinor","13000"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHIN_BUDGET"))
            .andExpect(jsonPath("$.comparison.recommended.totalMinor").value(12500));
        mvc.perform(get(API+"/basket").with(owner("p4-two")).param("locationId",LOCATION))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EMPTY"));
        mvc.perform(post("/api/me/assistant").with(owner("p4-one")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"Find cheaper alternatives for my shopping list\",\"locationId\":\""+LOCATION+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products[0].id").value(LARGE))
            .andExpect(jsonPath("$.text",containsString("90.00")));
        db.run("UPDATE online_comparison_evidence SET stock_quantity=NULL WHERE price_id=?",PACK+"-blinkit-price");
        mvc.perform(get(API+"/basket").with(owner("p4-one")).param("locationId",LOCATION))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INCOMPLETE"));
    }

    @Test void assistantProposesACompleteBasketWithinBudgetWithoutRemovingExistingItems() throws Exception {
        db.run("UPDATE grocery_platforms SET delivery_minor=1000,handling_minor=500,minimum_minor=0,free_delivery_minor=NULL,fee_source='VERIFIED' WHERE id='blinkit'");
        mvc.perform(post("/api/me/assistant").with(owner("p4-one")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"Suggest groceries under ₹100\",\"locationId\":\""+LOCATION+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products[0].id").value(PACK))
            .andExpect(jsonPath("$.text",containsString("70.00"))).andExpect(jsonPath("$.text",containsString("fits")));
        mvc.perform(post("/api/me/assistant").with(owner("p4-one")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"message\":\"Suggest groceries under ₹30\",\"locationId\":\""+LOCATION+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products").isEmpty())
            .andExpect(jsonPath("$.text",containsString("couldn't confirm")));
    }
}
