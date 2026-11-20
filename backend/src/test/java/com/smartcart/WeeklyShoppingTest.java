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
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import org.springframework.transaction.annotation.Transactional;
import static com.smartcart.repository.Db.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class WeeklyShoppingTest {
    @Autowired MockMvc mvc;
    @Autowired Db db;
    @Autowired ObjectMapper json;
    private static final String AREA="phase5-area";
    private static final String BASE="/api/me/shopping-intelligence";
    private static final String PLAN=BASE+"/weekly";
    private static final String ADMIN="/api/admin/shopping-intelligence/feedback";

    @BeforeEach void fixtures() {
        for(int i=0;i<5;i++)db.insert("users",fields("id","week-"+i,"name","Phase 5 fixture","email","phase5-"+i+"@example.invalid","password_hash","fixture"));
        db.insert("delivery_locations",fields("id",AREA,"name","Fixture area","city","Fixture","postcode","641018"));
        db.insert("product_categories",fields("id","week-grocery","name","Fixture groceries"));
        product("week-rice","Test basmati",500);
        product("week-oil","Test cooking oil",500);
        offer("week-rice","Test basmati","blinkit",6000,5);
        offer("week-rice","Test basmati","zepto",16000,5);
        offer("week-oil","Test cooking oil","zepto",3500,4);
        db.run("UPDATE grocery_platforms SET delivery_minor=500,handling_minor=200,minimum_minor=0,free_delivery_minor=NULL,fee_source='VERIFIED' WHERE id IN ('blinkit','zepto')");
    }
    private void product(String id,String name,int grams) {
        db.insert("products",fields("id",id,"name",name,"brand","Fixture","variant_name","Plain","category_id","week-grocery",
            "description","Synthetic test fixture","color","#FFFFFF","art","bag","active",true));
        db.insert("product_variants",fields("id",id+"-pack","product_id",id,"pack_label",grams+" g","amount_base",grams,"base_unit","g"));
    }
    private void offer(String id,String title,String store,int price,Integer stock) {
        String variant=id+"-pack",listing=id+"-"+store;
        var at=Instant.now().minusSeconds(5);
        db.insert("platform_products",fields("id",listing,"variant_id",variant,"platform_id",store,"retailer_sku",listing));
        db.insert("provider_mappings",fields("platform_product_id",listing,"matched_by","week-0","matched_at",at));
        db.insert("location_prices",fields("id",listing,"platform_product_id",listing,"location_id",AREA,"price_minor",price,"availability","available",
            "observed_at",at,"source","SYNTHETIC_TEST","verification","VERIFIED"));
        db.insert("online_comparison_evidence",fields("price_id",listing,"retailer_sku",listing,"product_name",title,"brand","Fixture","variant_name","Plain",
            "amount_base",500,"base_unit","g","price_observed_at",at,"stock_quantity",stock,"evidence_source","Synthetic regression","observed_at",at,"verified_by","week-0"));
    }
    private RequestPostProcessor owner(int i){return jwt().jwt(j->j.subject("week-"+i));}
    private static Map<String,Object> line(String id,int quantity){return Map.of("productId",id+"-pack","quantity",quantity);}
    private static Map<String,Object> draft(List<?> meals,List<?> items,Map<String,Integer> onHand){
        return new HashMap<>(Map.of("meals",meals,"items",items,"onHand",onHand,"diet","ANY","budgetMinor",12000));
    }
    private ResultActions submit(MockHttpServletRequestBuilder method,Object input,int user) throws Exception {
        return mvc.perform(method.with(owner(user)).param("locationId",AREA).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input)));
    }

    @Test void previewOptimizesExactSingleAndSplitBasketsAndShowsEvidence() throws Exception {
        var d=draft(List.of(),List.of(line("week-rice",1),line("week-oil",1)),Map.of());
        submit(post(PLAN+"/preview"),d,0).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.plan.comparison.recommended.label").value("Split retailers"))
            .andExpect(jsonPath("$.plan.comparison.recommended.totalMinor").value(10900))
            .andExpect(jsonPath("$.plan.comparison.singleRetailers[?(@.label == 'Zepto')].totalMinor",hasItem(20200)))
            .andExpect(jsonPath("$.plan.comparison.recommended.packs[0].quote.unitPriceMinor").value(12000))
            .andExpect(jsonPath("$.plan.comparison.recommended.packs[0].quote.observedAt").isString())
            .andExpect(jsonPath("$.plan.comparison.recommended.packs[0].quote.stockPacks").value(5))
            .andExpect(jsonPath("$.plan.budgetStatus").value("WITHIN_BUDGET"))
            .andExpect(jsonPath("$.plan.method").value("RULE_BASED"));
        d.put("budgetMinor",10000);
        submit(post(PLAN+"/preview"),d,0).andExpect(jsonPath("$.plan.budgetStatus").value("OVER_BUDGET"));
        db.run("UPDATE grocery_platforms SET delivery_minor=NULL WHERE id='zepto'");
        submit(post(PLAN+"/preview"),d,0).andExpect(jsonPath("$.plan.comparison.recommended.totalMinor").value(nullValue()))
            .andExpect(jsonPath("$.plan.comparison.recommended.pricedItemsMinor").value(9500))
            .andExpect(jsonPath("$.plan.budgetStatus").value("BUDGET_UNKNOWN"));
        db.run("UPDATE online_comparison_evidence SET stock_quantity=0 WHERE price_id='week-oil-zepto'");
        db.run("UPDATE location_prices SET observed_at=? WHERE id='week-rice-blinkit'",Instant.now().minusSeconds(3600));
        submit(post(PLAN+"/preview"),d,0).andExpect(jsonPath("$.plan.comparison.recommended.totalMinor").value(nullValue()))
            .andExpect(jsonPath("$.plan.comparison.recommended.pricedItemsMinor").value(16000))
            .andExpect(jsonPath("$.plan.comparison.recommended.missing[0]",containsString("Test cooking oil")));
    }

    @Test void scalesConsolidatesAndExcludesPantryCoveredItemsAndIncompatibleUnits() throws Exception {
        var meal1=Map.of("recipeId","jeera-rice","day",1,"servings",2);
        var meal2=Map.of("recipeId","jeera-rice","day",5,"servings",4);
        db.insert("grocery_ingredients",fields("id","week-basmati","name","Basmati rice","evidence_reference","Test fixture"));
        db.insert("grocery_ingredient_products",fields("ingredient_id","week-basmati","product_id","week-rice","evidence_reference","Test fixture"));
        db.insert("product_variants",fields("id","wrong-rice-unit","product_id","week-rice","pack_label","1 litre","amount_base",1000,"base_unit","ml"));
        db.insert("user_pantry",fields("id","phase5-pantry","user_id","week-0","product_id","week-rice","amount_base",300,"base_unit","g"));
        var d=draft(List.of(meal1,meal2),List.of(),Map.of("water/ml",990,"salt/g",9,"ghee/g",30,"cumin seeds/g",12));
        submit(post(PLAN+"/preview"),d,0).andExpect(status().isOk())
            .andExpect(jsonPath("$.plan.ingredients[?(@.key == 'basmati rice/g')].required",hasItem(540.0)))
            .andExpect(jsonPath("$.plan.ingredients[?(@.key == 'basmati rice/g')].pantry",hasItem(300.0)))
            .andExpect(jsonPath("$.plan.ingredients[?(@.key == 'basmati rice/g')].cataloguePacks[0].variantId",hasItem("week-rice-pack")))
            .andExpect(jsonPath("$.plan.comparison.recommended.packs.length()").value(1))
            .andExpect(jsonPath("$.plan.comparison.recommended.packs[0].count").value(1));
        submit(post(PLAN+"/preview"),d,1).andExpect(status().isOk())
            .andExpect(jsonPath("$.plan.comparison.recommended.packs[0].count").value(2));
    }

    @Test void weeklyDraftEditValidationOwnershipAndRevision() throws Exception {
        var d=draft(List.of(),List.of(line("week-rice",1)),Map.of());
        mvc.perform(get(PLAN).param("locationId",AREA)).andExpect(status().isUnauthorized());
        submit(put(PLAN),Map.of("draft",d,"revision",0),0).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
        submit(put(PLAN),Map.of("draft",d,"revision",0),0).andExpect(status().isConflict());
        mvc.perform(get(PLAN).with(owner(1)).param("locationId",AREA)).andExpect(jsonPath("$.draft.items").isEmpty());
        mvc.perform(get(PLAN).with(owner(0)).param("locationId","blr-indiranagar")).andExpect(jsonPath("$.revision").value(0));
        mvc.perform(get(PLAN).with(owner(0)).param("locationId",AREA)).andExpect(jsonPath("$.draft.items[0].quantity").value(1));
        var meal=Map.of("recipeId","egg-bhurji","day",1,"servings",2);
        var mismatch=draft(List.of(meal),List.of(),Map.of());mismatch.put("diet","VEGETARIAN");
        submit(post(PLAN+"/preview"),mismatch,0).andExpect(status().isBadRequest());
        mvc.perform(delete(PLAN).with(owner(0)).param("locationId",AREA)).andExpect(status().isNoContent());
        mvc.perform(get(PLAN).with(owner(0)).param("locationId",AREA)).andExpect(jsonPath("$.revision").value(0));
    }

    @Test void offlineAssistantRoutesToScopedDataAndKeepsUncertainty() throws Exception {
        for(var pair:Map.of("What can I cook?","RECIPES","Show my pantry","PANTRY","Which product should I buy this week?","WEEKLY_PLAN",
            "Should I buy Test basmati now or wait?","TIMING","Which retailer has milk?","PRODUCT","Should I buy in bulk?","BULK",
            "hello","HELP","Show cheaper alternatives","PERSONALIZED").entrySet())
            submit(post(PLAN+"/assistant"),Map.of("message",pair.getKey()),0)
                .andExpect(status().isOk()).andExpect(jsonPath("$.intent").value(pair.getValue())).andExpect(jsonPath("$.method").value("RULE_BASED"));
        submit(post(PLAN+"/assistant"),Map.of("message","Plan groceries under ₹100","draft",draft(List.of(),List.of(line("week-rice",2)),Map.of())),0)
            .andExpect(status().isOk()).andExpect(jsonPath("$.intent").value("BUDGET"))
            .andExpect(jsonPath("$.plan.budgetMinor").value(10000)).andExpect(jsonPath("$.plan.budgetStatus").value("OVER_BUDGET"));
        submit(post(PLAN+"/assistant"),Map.of("message","Should I buy Test basmati now or wait?"),0)
            .andExpect(jsonPath("$.text",containsString("no approved forecast")));
    }

    @Test void feedbackUsesLatestVoteAndOnlyAggregateCohorts() throws Exception {
        String reference=UUID.randomUUID().toString(),path=BASE+"/feedback/"+reference;
        for(int i=0;i<5;i++)submit(put(path),Map.of("category","PLAN","outcome","ACCEPTED"),i).andExpect(status().isOk());
        var admin=jwt().jwt(j->j.subject("week-0")).authorities(new SimpleGrantedAuthority("SCOPE_admin"));
        mvc.perform(get(ADMIN).with(owner(0))).andExpect(status().isForbidden());
        mvc.perform(get(ADMIN).with(admin)).andExpect(status().isOk()).andExpect(jsonPath("$.results[0].count").value(5))
            .andExpect(content().string(not(containsString("week-0"))));
        submit(put(path),Map.of("category","PLAN","outcome","MODIFIED"),1).andExpect(status().isOk());
        submit(put(path),Map.of("category","BASKET","outcome","REJECTED"),1).andExpect(status().isBadRequest());
        mvc.perform(get(ADMIN).with(admin)).andExpect(jsonPath("$.results").isEmpty());
        mvc.perform(delete(BASE+"/feedback").with(owner(1))).andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals(4,db.count("SELECT COUNT(*) FROM recommendation_feedback WHERE reference_id=?",reference));
    }
}
