package com.smartcart;

import com.fasterxml.jackson.databind.*;
import com.smartcart.repository.Db;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
@org.springframework.test.context.TestExecutionListeners(
    listeners={org.springframework.test.context.web.ServletTestExecutionListener.class,
        org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,
        org.springframework.test.context.transaction.TransactionalTestExecutionListener.class},
    mergeMode=org.springframework.test.context.TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class WorkspaceIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired Db db;
    String token,user;
    @BeforeEach void shopper()throws Exception {var session=body(mvc.perform(post("/api/auth/demo")).andExpect(status().isOk()).andReturn());token=session.path("token").asText();user=session.path("user").path("id").asText();}
    JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString());}
    String encode(Object object)throws Exception{return json.writeValueAsString(object);}
    Map<String,Object> essential(long maximum){return new LinkedHashMap<>(Map.of("intent","ESSENTIAL","maxPriceMinor",maximum,"monitorPrice",false));}
    Map<String,Object> item(String id,int quantity,Map<String,Object> preferences){return Map.of("productId",id,"locationId","blr-indiranagar","quantity",quantity,"preferences",preferences);}
    JsonNode save(String id,int quantity,Map<String,Object> preferences)throws Exception{return body(mvc.perform(put("/api/me/cart").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(encode(item(id,quantity,preferences)))).andExpect(status().isOk()).andReturn());}
    String another()throws Exception{return body(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(encode(Map.of("name","Another shopper","email",UUID.randomUUID()+"@example.test","password","A-long-test-password!")))).andExpect(status().isCreated()).andReturn()).path("token").asText();}

    @Test void localStoreAdminAuthorizationAndValidation() throws Exception {
        var admin=org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_admin"));
        String endpoint="/api/admin/local-stores";
        mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(get(endpoint).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(get(endpoint).with(admin)).andExpect(status().isOk());
        Map<String,Object> store=new HashMap<>(Map.of("name","Fixture store","address","Test road","postcode","560038","latitude",12.97,"longitude",77.64,"pickupEnabled",true,"deliveryEnabled",false,"active",true));
        String storeId=body(mvc.perform(post(endpoint).with(admin).contentType(MediaType.APPLICATION_JSON).content(encode(store))).andExpect(status().isCreated()).andReturn()).path("id").asText();
        mvc.perform(get(endpoint).with(admin)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(storeId));
        Map<String,Object> missing=new HashMap<>(store);missing.remove("latitude");
        mvc.perform(post(endpoint).with(admin).contentType(MediaType.APPLICATION_JSON).content(encode(missing))).andExpect(status().isBadRequest());
        missing=new HashMap<>(store);missing.put("postcode","000000");
        mvc.perform(post(endpoint).with(admin).contentType(MediaType.APPLICATION_JSON).content(encode(missing))).andExpect(status().isBadRequest());
        missing=new HashMap<>(store);missing.put("latitude",91);
        mvc.perform(post(endpoint).with(admin).contentType(MediaType.APPLICATION_JSON).content(encode(missing))).andExpect(status().isBadRequest());
        String listing=endpoint+"/"+storeId+"/listings";
        for(Map<String,?> invalid:List.of(Map.of("variantId","milk","priceMinor",-1,"stockQuantity",1),Map.of("variantId","milk","priceMinor",1,"stockQuantity",-1),Map.of("variantId","milk","stockQuantity",1)))
            mvc.perform(put(listing).with(admin).contentType(MediaType.APPLICATION_JSON).content(encode(invalid))).andExpect(status().isBadRequest());
        mvc.perform(put(listing).with(admin).contentType(MediaType.APPLICATION_JSON).content(encode(Map.of("variantId","missing","priceMinor",1,"stockQuantity",1)))).andExpect(status().isNotFound());
        mvc.perform(put(endpoint+"/missing/listings").with(admin).contentType(MediaType.APPLICATION_JSON).content(encode(Map.of("variantId","milk","priceMinor",1,"stockQuantity",1)))).andExpect(status().isNotFound());
        for(int price:new int[]{100,200})mvc.perform(put(listing).with(admin).contentType(MediaType.APPLICATION_JSON).content(encode(Map.of("variantId","milk","priceMinor",price,"stockQuantity",1)))).andExpect(status().isNoContent());
        assertEquals(1,db.count("SELECT COUNT(*) FROM local_store_listings WHERE store_id=? AND variant_id=?",storeId,"milk"));
        assertEquals(200,((Number)db.one("SELECT price_minor FROM local_store_listings WHERE store_id=?",storeId).get("price_minor")).longValue());
    }

    @Test void purchaseIntentAliasesPersistAndRemainOwnerScoped() throws Exception {
        for (String intent : List.of("NORMAL", "PLANNED_BULK", "ESSENTIAL", "BULK")) {
            boolean bulk=intent.equals("PLANNED_BULK") || intent.equals("BULK");
            Map<String,Object> preference=new HashMap<>();
            preference.put("intent",intent);
            preference.put("monitorPrice",false);
            if(bulk) preference.put("consumptionDays",30);
            JsonNode saved=save("milk",4,preference);
            String expected=bulk?"BULK":"ESSENTIAL";
            assertEquals(expected,db.one("SELECT p.intent FROM purchase_preferences p JOIN cart_items i ON i.id=p.cart_item_id JOIN shopping_carts c ON c.id=i.cart_id WHERE c.user_id=? AND i.variant_id=?",user,"milk").get("intent"));
            assertEquals(expected,saved.path("preferences").path("intent").asText());
        }
        String other=another();
        mvc.perform(get("/api/me/cart").header("Authorization","Bearer "+other)).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void cataloguePreservesMatchingIdentitySourceUnitsAndThreePlatforms()throws Exception{
        var product=body(mvc.perform(get("/api/products/milk?locationId=blr-indiranagar")).andExpect(status().isOk()).andReturn());
        assertEquals("1 litre",product.path("quantity").asText());assertEquals(3,product.path("offers").size());
        for(var offer:product.path("offers")){assertEquals("milk",offer.path("productId").asText());assertEquals("blr-indiranagar",offer.path("locationId").asText());assertEquals("SAMPLE",offer.path("verificationStatus").asText());assertTrue(offer.path("unitPriceMinor").asLong()>0);}
        mvc.perform(get("/api/products?locationId=unknown")).andExpect(status().isNotFound());
    }
    @Test void accountsCannotReadOrChangeEachOthersCart()throws Exception{
        JsonNode saved=save("milk",2,essential(7000));String other=another();
        assertEquals(0,body(mvc.perform(get("/api/me/cart").header("Authorization","Bearer "+other)).andExpect(status().isOk()).andReturn()).size());
        mvc.perform(delete("/api/me/cart/"+saved.path("id").asText()).header("Authorization","Bearer "+other)).andExpect(status().isNotFound());
        assertEquals(1,body(mvc.perform(get("/api/me/cart").header("Authorization","Bearer "+token)).andReturn()).size());
    }
    @Test void preferencesEditWithoutDuplicatingTheCartLine()throws Exception{
        save("milk",1,essential(7000));save("milk",3,essential(7500));var cart=body(mvc.perform(get("/api/me/cart").header("Authorization","Bearer "+token)).andReturn());assertEquals(1,cart.size());assertEquals(3,cart.path(0).path("quantity").asInt());assertEquals(7500,cart.path(0).path("preferences").path("maxPriceMinor").asLong());
    }
    @Test void essentialAboveLimitIsNeverAutomaticallyDelayed()throws Exception{
        save("milk",1,essential(5000));var decisions=body(mvc.perform(get("/api/me/decisions?locationId=blr-indiranagar").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn());assertEquals("CHOOSE_ALTERNATIVE_PRODUCT",decisions.path(0).path("action").asText());assertTrue(decisions.path(0).path("reason").asText().contains("not been delayed"));
    }
    @Test void optionalWaitRequiresObservedHistoricalOpportunity()throws Exception{
        save("coffee",1,Map.of("intent","NON_ESSENTIAL","targetPriceMinor",34000,"monitorPrice",true));var d=body(mvc.perform(get("/api/me/decisions?locationId=blr-indiranagar").header("Authorization","Bearer "+token)).andReturn());assertEquals("WAIT_AND_MONITOR",d.path(0).path("action").asText());assertTrue(d.path(0).path("reason").asText().contains("not guaranteed"));
        save("coffee",1,Map.of("intent","NON_ESSENTIAL","targetPriceMinor",10000,"monitorPrice",true));d=body(mvc.perform(get("/api/me/decisions?locationId=blr-indiranagar").header("Authorization","Bearer "+token)).andReturn());assertEquals("COMPARE_ANOTHER_STORE",d.path(0).path("action").asText());
    }
    @Test void bulkDecisionChecksUsefulQuantityAndRealUnitAdvantage()throws Exception{
        var p=Map.<String,Object>of("intent","BULK","maxUsefulPacks",1,"consumptionDays",90,"bulkBudgetMinor",60000,"maxUnitPriceMinor",11000,"monitorPrice",false);
        save("detergent-5kg",2,p);var d=body(mvc.perform(get("/api/me/decisions?locationId=blr-indiranagar").header("Authorization","Bearer "+token)).andReturn());assertEquals("CHOOSE_ALTERNATIVE_PRODUCT",d.path(0).path("action").asText());
        save("detergent-5kg",1,p);d=body(mvc.perform(get("/api/me/decisions?locationId=blr-indiranagar").header("Authorization","Bearer "+token)).andReturn());assertEquals("BUY_NOW",d.path(0).path("action").asText());
    }
    @Test void optimizerBeatsCheapestItemSplittingAfterFeesAndMinimumOrders()throws Exception{
        db.run("UPDATE location_prices SET price_minor=6600 WHERE platform_product_id='milk-zepto' AND location_id='blr-indiranagar'");
        var input=Map.of("locationId","blr-indiranagar","items",List.of(Map.of("productId","milk","quantity",1),Map.of("productId","oil","quantity",1)));
        var result=body(mvc.perform(post("/api/cart/optimize").contentType(MediaType.APPLICATION_JSON).content(encode(input))).andExpect(status().isOk()).andReturn());assertTrue(result.path("searchComplete").asBoolean());assertEquals(23700,result.path("recommended").path("totalMinor").asLong());for(var assignment:result.path("recommended").path("assignments"))assertEquals("blinkit",assignment.path("platform").asText());
    }
    @Test void incompleteOrUnknownFeeBasketsAreNotCalledCheapestComplete()throws Exception{
        db.run("UPDATE grocery_platforms SET delivery_minor=NULL");var input=Map.of("locationId","blr-indiranagar","items",List.of(Map.of("productId","milk","quantity",2)));var result=body(mvc.perform(post("/api/cart/optimize").contentType(MediaType.APPLICATION_JSON).content(encode(input))).andReturn());assertFalse(result.path("recommended").path("feasible").asBoolean());assertTrue(result.path("recommended").path("totalMinor").isNull());
    }
    @Test void unavailableBasketVariantsAreNamedAndSavingsStayUnknown() throws Exception {
        db.run("UPDATE location_prices SET availability='out_of_stock' WHERE platform_product_id='milk-zepto' AND location_id='blr-indiranagar'");
        var input=Map.of("locationId","blr-indiranagar","items",List.of(Map.of("productId","milk","quantity",2)));
        var result=body(mvc.perform(post("/api/cart/optimize").contentType(MediaType.APPLICATION_JSON).content(encode(input)))
            .andExpect(status().isOk()).andReturn());
        var zepto=java.util.stream.StreamSupport.stream(result.path("singleStores").spliterator(),false)
            .filter(row->row.path("label").asText().equalsIgnoreCase("Zepto")).findFirst().orElseThrow();
        assertFalse(zepto.path("feasible").asBoolean());
        assertTrue(zepto.path("reason").asText().contains("1 litre"));
        assertTrue(zepto.path("totalMinor").isNull());
    }
    @Test void watchThresholdTriggersOnceAndIsPrivate()throws Exception{
        var watch=body(mvc.perform(put("/api/me/watchlist").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(encode(Map.of("productId","milk","locationId","blr-indiranagar","targetMinor",7000)))).andExpect(status().isOk()).andReturn());
        for(int i=0;i<2;i++)mvc.perform(post("/api/me/watchlist/check").header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
        var notices=body(mvc.perform(get("/api/me/notifications").header("Authorization","Bearer "+token)).andReturn());int targets=0;for(var n:notices)if("TARGET".equals(n.path("kind").asText())){targets++;assertTrue(n.path("sample").asBoolean());assertEquals("IN_APP",n.path("deliveryStatus").asText());}assertEquals(1,targets);
        mvc.perform(delete("/api/me/watchlist/"+watch.path("id").asText()).header("Authorization","Bearer "+another())).andExpect(status().isNotFound());
    }
    @Test void recipeServingScalingSubtractsPantryAndOwnedIngredients()throws Exception{
        var request=Map.of("servings",8,"locationId","blr-indiranagar","ownedProductIds",List.of("chicken"));var plan=body(mvc.perform(post("/api/recipes/biryani/plan").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(encode(request))).andExpect(status().isOk()).andReturn());for(var i:plan.path("ingredients")){if(List.of("rice","oil","chicken").contains(i.path("productId").asText()))assertEquals(0,i.path("missingBase").asDouble());}
        mvc.perform(post("/api/recipes/biryani/cart").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(encode(Map.of("plan",request,"selectedVariantIds",Map.of())))).andExpect(status().isOk());var cart=body(mvc.perform(get("/api/me/cart").header("Authorization","Bearer "+token)).andReturn());for(var i:cart)assertFalse(List.of("rice","oil","chicken").contains(i.path("productId").asText()));
    }
    @Test void invalidUnitsAndPurchaseConditionsAreRejected()throws Exception{
        mvc.perform(put("/api/me/pantry").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(encode(Map.of("productId","milk","amountBase",1000,"baseUnit","g")))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/me/cart").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(encode(item("milk",1,Map.of("intent","ESSENTIAL","targetPriceMinor",6000,"monitorPrice",true))))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/cart")).andExpect(status().isUnauthorized());mvc.perform(get("/api/admin/users").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @Test void recordedPurchaseIsIdempotentAndBudgetTotalsReconcile()throws Exception{
        var input=Map.of("requestKey","test-order","locationId","blr-indiranagar","platform","blinkit","items",List.of(Map.of("productId","milk","quantity",2)));
        long before=body(mvc.perform(get("/api/me/budget").header("Authorization","Bearer "+token)).andReturn()).path("spentMinor").asLong();String id=null;for(int i=0;i<2;i++){var purchase=body(mvc.perform(post("/api/me/history/sample").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(encode(input))).andExpect(status().isCreated()).andReturn());if(id==null)id=purchase.path("id").asText();else assertEquals(id,purchase.path("id").asText());}
        long after=body(mvc.perform(get("/api/me/budget").header("Authorization","Bearer "+token)).andReturn()).path("spentMinor").asLong();assertEquals(13600,after-before);
    }
    @Test void assistantUsesRecordedPricesAndScopedSpending()throws Exception{
        var answer=body(mvc.perform(post("/api/me/assistant").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(encode(Map.of("message","Find the cheapest cooking oil","locationId","blr-indiranagar")))).andExpect(status().isOk()).andReturn());assertTrue(answer.path("text").asText().contains("₹139.00"));assertTrue(answer.path("sample").asBoolean());assertFalse(answer.path("sources").isEmpty());
    }

    @Test void assistantReportsOnlyLikeForLikeHistoricalChangesForTheSelectedLocation() throws Exception {
        String area="assistant-history";
        db.insert("delivery_locations",Db.fields("id",area,"name","Assistant history fixture","city","Fixture","postcode","500001"));
        var now=java.time.Instant.now();
        for(int days : new int[]{2,1}) db.insert("price_history",Db.fields("id",Db.id(),"platform_product_id","milk-blinkit",
            "location_id",area,"price_minor",days==2?5000:5500,"observed_at",now.minus(java.time.Duration.ofDays(days)),
            "source","FIXTURE","verification","SAMPLE"));
        db.insert("price_history",Db.fields("id",Db.id(),"platform_product_id","milk-zepto","location_id",area,
            "price_minor",9900,"observed_at",now.minusSeconds(400),"source","FIXTURE","verification","SAMPLE"));
        var answer=body(mvc.perform(post("/api/me/assistant").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(encode(Map.of("message","Which products have become more expensive?","locationId",area))))
            .andExpect(status().isOk()).andReturn());
        var text=answer.path("text").asText();
        assertTrue(text.contains("blinkit increased from ₹50.00 to ₹55.00"));
        assertFalse(text.contains("zepto"));
        assertFalse(text.contains("₹99.00"));
        assertEquals(1,answer.path("sources").size());
    }

    @Test void assistantBuyNowUsesTheSignedInShoppersCartAndLocation() throws Exception {
        save("milk",1,essential(10000));
        String question=encode(Map.of("message","What should I buy now?","locationId","blr-indiranagar"));
        var answer=body(mvc.perform(post("/api/me/assistant").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(question)).andExpect(status().isOk()).andReturn());
        assertEquals("milk",answer.path("products").path(0).path("id").asText());
        assertTrue(answer.path("text").asText().contains("per pack"));
        var other=body(mvc.perform(post("/api/me/assistant").header("Authorization","Bearer "+another())
            .contentType(MediaType.APPLICATION_JSON).content(question)).andExpect(status().isOk()).andReturn());
        assertTrue(other.path("products").isEmpty());
        assertTrue(other.path("text").asText().contains("No items in your cart"));
    }

    @Test void priceHistoryRemainsVisibleWithForecastingDisabled() throws Exception {
        mvc.perform(get("/api/products/milk/analytics?locationId=blr-indiranagar"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.history[0].priceMinor").isNumber())
            .andExpect(jsonPath("$.forecast.estimateMinor").doesNotExist())
            .andExpect(jsonPath("$.forecast.method").value("Forecasts disabled"));
    }
    @Test void recipeAndAssistantCanFindProductsBeyondTheFirstCataloguePage() throws Exception {
        for (int index=0;index<100;index++) {
            String id="catalogue-page-fixture-"+index;
            db.insert("products",Db.fields("id",id,"name","AAA fixture "+index,"brand","Fixture",
                "category_id","pantry","variant_name","Plain","description","Fixture","color","#123456","art","bag"));
            db.insert("product_variants",Db.fields("id",id,"product_id",id,"pack_label","100 g","amount_base",100,"base_unit","g"));
        }
        var answer=body(mvc.perform(post("/api/me/assistant").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(encode(Map.of("message","Find the cheapest cooking oil",
                "locationId","blr-indiranagar")))).andExpect(status().isOk()).andReturn());
        assertTrue(answer.path("text").asText().contains("₹139.00"));
        var plan=body(mvc.perform(post("/api/recipes/biryani/plan").header("Authorization","Bearer "+another())
            .contentType(MediaType.APPLICATION_JSON).content(encode(Map.of("servings",4,"locationId","blr-indiranagar",
                "ownedProductIds",List.of())))).andExpect(status().isOk()).andReturn());
        var oil=java.util.stream.StreamSupport.stream(plan.path("ingredients").spliterator(),false)
            .filter(i->i.path("productId").asText().equals("oil")).findFirst().orElseThrow();
        assertTrue(oil.path("missingBase").asDouble()>0);
        assertEquals("oil",oil.path("suggestedVariantId").asText());
    }
    @Test void refreshRotatesCookieAndLogoutRevokesAccess()throws Exception{
        var result=mvc.perform(post("/api/auth/demo")).andReturn();String cookie=result.getResponse().getHeader("Set-Cookie").split(";",2)[0].substring("cartiq_refresh=".length());String access=body(result).path("token").asText();mvc.perform(post("/api/auth/refresh").cookie(new jakarta.servlet.http.Cookie("cartiq_refresh",cookie))).andExpect(status().isOk());mvc.perform(post("/api/auth/refresh").cookie(new jakarta.servlet.http.Cookie("cartiq_refresh",cookie))).andExpect(status().isUnauthorized());mvc.perform(post("/api/auth/logout").header("Authorization","Bearer "+access)).andExpect(status().isNoContent());mvc.perform(get("/api/me").header("Authorization","Bearer "+access)).andExpect(status().isUnauthorized());
    }
    @Test void noRoleInjectionAndNoPasswordExposure()throws Exception{
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(encode(Map.of("name","Attacker","email","a@example.test","password","Strong-long-password","role","admin")))).andExpect(status().isBadRequest());var me=body(mvc.perform(get("/api/me").header("Authorization","Bearer "+token)).andReturn());assertFalse(me.has("passwordHash"));assertFalse(me.has("password"));
    }
}
