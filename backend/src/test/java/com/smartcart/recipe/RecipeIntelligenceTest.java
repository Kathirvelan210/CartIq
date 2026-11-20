package com.smartcart.recipe;

import com.fasterxml.jackson.databind.*;
import com.smartcart.repository.Db;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.smartcart.repository.Db.fields;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class RecipeIntelligenceTest {
    private static final String API="/api/me/recipe-intelligence";
    private static HttpServer server;
    private static final AtomicBoolean pantryReceived=new AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicInteger diagnosticsCalls=new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicReference<String> correlation=new java.util.concurrent.atomic.AtomicReference<>();
    private static final String DRAFT="""
        {"name":"Fixture semolina","description":"Synthetic test recipe","cuisine":"South Indian","meal":"BREAKFAST",
         "diet":"VEGETARIAN","servings":2,"prepMinutes":5,"cookMinutes":10,"difficulty":"EASY",
         "ingredients":[{"name":"Semolina","quantity":0.2,"unit":"kg"}],"steps":["Toast the semolina."],"substitutions":[]}
        """;

    @DynamicPropertySource static void mock(DynamicPropertyRegistry registry) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/recipes/generate",request->{
            try (request) {
                String body=new String(request.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                if (!"fixture-internal-key".equals(request.getRequestHeaders().getFirst("X-Service-Token"))) {request.sendResponseHeaders(401,-1);return;}
                if(body.contains("diagnostics fixture")) {
                    diagnosticsCalls.incrementAndGet();
                    correlation.set(request.getRequestHeaders().getFirst("X-Request-Id"));
                    assertEquals(0,new ObjectMapper().readTree(body).get("pantry").size());
                    boolean quota=body.contains("quota");
                    request.getResponseHeaders().set("X-CartIQ-AI-Error",quota?"GEMINI_QUOTA":"GEMINI_AUTH");
                    request.getResponseHeaders().set("X-Gemini-HTTP-Status",quota?"429":"401");
                    request.getResponseHeaders().set("X-Gemini-Request-Id","fixture-provider-123");
                    request.sendResponseHeaders(quota?429:503,-1);
                    return;
                }
                if (body.contains("slow recipe fixture")) {
                    try { Thread.sleep(21_000); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                }
                pantryReceived.set(body.contains("Fixture semolina product"));
                String content=body.contains("invalid draft")?DRAFT.replace("\"kg\"","\"cup\""):DRAFT;
                byte[] bytes=content.getBytes(StandardCharsets.UTF_8);
                request.getResponseHeaders().set("Content-Type","application/json");
                request.sendResponseHeaders(200,bytes.length);
                request.getResponseBody().write(bytes);
            }
        });
        server.start();
        registry.add("smartcart.ai-url",()->"http://127.0.0.1:"+server.getAddress().getPort());
        registry.add("smartcart.ai-secret",()->"fixture-internal-key");
        registry.add("CARTIQ_RECIPE_GENERATION_ENABLED",()->"true");
    }

    @AfterAll static void stop() { if(server!=null)server.stop(0); }

    @Autowired MockMvc mvc; @Autowired Db db; @Autowired ObjectMapper mapper;
    final String owner=UUID.randomUUID().toString(),other=UUID.randomUUID().toString();

    @BeforeEach void setup() {
        for (String user:List.of(owner,other)) db.insert("users",fields("id",user,"email",user+"@fixture.invalid","password_hash","unused","name","Fixture"));
        db.insert("delivery_locations",fields("id","recipe-fixture","name","Fixture","city","Fixture","postcode","123456"));
        db.insert("grocery_ingredients",fields("id","recipe-semolina","name","Ｓｅｍｏｌｉｎａ","evidence_reference","Synthetic test evidence"));
        db.insert("grocery_ingredients",fields("id","recipe-swap","name","Fixture substitute","evidence_reference","Synthetic test evidence"));
        db.insert("grocery_ingredient_substitutions",fields("ingredient_id","recipe-semolina","substitute_id","recipe-swap","quantity_ratio",0.5,"base_unit","g","evidence_reference","Synthetic test evidence"));
        db.insert("products",fields("id","recipe-product","category_id","pantry","name","Fixture semolina product","brand","Fixture","variant_name","Standard","description","Fixture","art","bag","color","#ffffff"));
        db.insert("grocery_ingredient_products",fields("ingredient_id","recipe-semolina","product_id","recipe-product","evidence_reference","Synthetic test evidence"));
        db.insert("user_pantry",fields("id",UUID.randomUUID().toString(),"user_id",owner,"product_id","recipe-product","base_unit","g","amount_base",70));
        addPack("fixture-100",100,900,"blinkit");
        addPack("fixture-250",250,1500,"blinkit");
        addPack("fixture-z250",251,2000,"zepto");
    }

    @Test void discoveryAndUnitConversion() throws Exception {
        assertEquals(0,RecipeCard.scale(new RecipeCard.Part("Oil",new BigDecimal("2"),"tbsp"),4,2).amount().compareTo(new BigDecimal("60")));
        assertEquals("ml",RecipeCard.scale(new RecipeCard.Part("Oil",BigDecimal.ONE,"tsp"),4,2).unit());
        assertEquals(0,RecipeCard.scale(new RecipeCard.Part("Rice",new BigDecimal("0.5"),"kg"),4,2).amount().compareTo(new BigDecimal("1000")));
        mvc.perform(get(API).with(as(owner)).param("q","onion semolina").param("cuisine","South Indian").param("meal","BREAKFAST")
            .param("diet","VEGETARIAN").param("maxMinutes","30"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value("rava-upma"));
        mvc.perform(get(API).with(as(owner)).param("q","rava").param("budgetMinor","5000").param("locationId","recipe-fixture"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void combinesMealsAndPackSizesChecksBudgetAndAddsOnlyForOwner() throws Exception {
        var plan=plan(List.of(new MealPlanner.Meal("rava-upma",1,2),new MealPlanner.Meal("rava-upma",2,4)),Map.of(),2000);
        mvc.perform(post(API+"/plan").with(as(owner)).contentType("application/json").content(mapper.writeValueAsBytes(plan)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ingredients[0].required").value(420))
            .andExpect(jsonPath("$.ingredients[0].pantry").value(70)).andExpect(jsonPath("$.ingredients[0].missing").value(350))
            .andExpect(jsonPath("$.ingredients[0].choices[0].packs.length()").value(2))
            .andExpect(jsonPath("$.baskets[0].estimatedSubtotalMinor").value(2400)).andExpect(jsonPath("$.baskets[2].estimatedSubtotalMinor").value(4000))
            .andExpect(jsonPath("$.budgetStatus").value("OVER_BUDGET"));
        mvc.perform(post(API+"/plan").with(as(other)).contentType("application/json").content(mapper.writeValueAsBytes(plan)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ingredients[0].pantry").value(0));
        mvc.perform(post(API+"/cart").with(as(owner)).contentType("application/json")
            .content(mapper.writeValueAsBytes(new MealPlanner.CartRequest(plan,"blinkit")))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/me/cart").with(as(other))).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void checkedIngredientCoversOnlyItsMealAndDoesNotDoubleCountPantry() throws Exception {
        var meals=List.of(new MealPlanner.Meal("rava-upma",1,2,Set.of(0)),new MealPlanner.Meal("rava-upma",2,2));
        var json=mvc.perform(post(API+"/plan").with(as(owner)).contentType("application/json")
            .content(mapper.writeValueAsBytes(plan(meals,Map.of(),2000))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var first=mapper.readTree(json).get("ingredients").get(0);
        assertEquals("semolina/g",first.get("key").asText());
        assertEquals(first.get("required").asDouble()/2,first.get("missing").asDouble());
        var invalid=List.of(new MealPlanner.Meal("rava-upma",1,2,Set.of(19)));
        mvc.perform(post(API+"/plan").with(as(owner)).contentType("application/json")
            .content(mapper.writeValueAsBytes(plan(invalid,Map.of(),2000)))).andExpect(status().isBadRequest());
    }

    @Test void approvedSubstitutionScalesAndUnknownPriceStaysUnknown() throws Exception {
        var plan=plan(List.of(new MealPlanner.Meal("rava-upma",1,2)),Map.of("semolina/g","recipe-swap"),2000);
        mvc.perform(post(API+"/plan").with(as(owner)).contentType("application/json").content(mapper.writeValueAsBytes(plan)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ingredients[0].required").value(70))
            .andExpect(jsonPath("$.ingredients[0].cataloguePacks.length()").value(0)).andExpect(jsonPath("$.baskets[0].complete").value(false))
            .andExpect(jsonPath("$.budgetStatus").value("INSUFFICIENT_PRICE_DATA"));
        var invalid=plan(List.of(new MealPlanner.Meal("rava-upma",1,2)),Map.of("semolina/g","unknown"),2000);
        mvc.perform(post(API+"/plan").with(as(owner)).contentType("application/json").content(mapper.writeValueAsBytes(invalid)))
            .andExpect(status().isBadRequest());
    }

    @Test void allowsSlowRecipeToFinishWithoutFailingAtOldDeadline() throws Exception {
        mvc.perform(post(API+"/generate").with(as(owner)).contentType("application/json")
            .content("{\"prompt\":\"slow recipe fixture\",\"usePantry\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.recipe.name").value("Fixture semolina"));
    }

    @Test void generatedDraftIsPrivateAndRejectsMalformedModelOutput() throws Exception {
        var result=mvc.perform(post(API+"/generate").with(as(owner)).contentType("application/json")
            .content("{\"prompt\":\"Suggest breakfast\",\"usePantry\":true}")).andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("GENERATED")).andReturn();
        assertTrue(pantryReceived.get());
        String draft=mapper.readTree(result.getResponse().getContentAsByteArray()).path("id").asText();
        var plan=plan(List.of(new MealPlanner.Meal(draft,1,2)),Map.of(),2000);
        mvc.perform(post(API+"/plan").with(as(other)).contentType("application/json").content(mapper.writeValueAsBytes(plan))).andExpect(status().isNotFound());
        mvc.perform(post(API+"/generate").with(as(other)).contentType("application/json")
            .content("{\"prompt\":\"invalid draft\"}")).andExpect(status().isBadGateway());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void geminiErrorsKeepUpstreamStatusAndCartiqCorrelation(boolean quota) throws Exception {
        int before=diagnosticsCalls.get();
        var result=mvc.perform(post(API+"/generate").with(as(owner)).contentType("application/json")
            .content(mapper.writeValueAsBytes(Map.of("prompt","diagnostics fixture "+(quota?"quota":"auth"),"usePantry",false))))
            .andExpect(status().is(quota?429:503))
            .andExpect(jsonPath("$.code").value(quota?"GEMINI_QUOTA":"GEMINI_AUTH"))
            .andExpect(header().string("X-Gemini-HTTP-Status",quota?"429":"401"))
            .andExpect(header().string("X-Gemini-Request-Id","fixture-provider-123")).andReturn();
        assertNotNull(correlation.get());
        assertEquals(result.getResponse().getHeader("X-Request-Id"),correlation.get());
        assertEquals(before+1,diagnosticsCalls.get());
    }

    private MealPlanner.Request plan(List<MealPlanner.Meal> meals,Map<String,String> changes,long budget) {
        return new MealPlanner.Request("recipe-fixture",meals,budget,Map.of("onion/g",BigDecimal.valueOf(1000),"cooking oil/ml",BigDecimal.valueOf(1000),
            "mustard seeds/g",BigDecimal.valueOf(100),"salt/g",BigDecimal.valueOf(100),"water/ml",BigDecimal.valueOf(10000)),changes);
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor as(String user) {
        return jwt().jwt(j->j.subject(user));
    }

    private void addPack(String id,int size,long price,String retailer) {
        db.insert("product_variants",fields("id",id,"product_id","recipe-product","pack_label",size+" g","amount_base",size,"base_unit","g"));
        db.insert("platform_products",fields("id",id+"-listing","platform_id",retailer,"variant_id",id,"retailer_sku",id+"-sku"));
        db.insert("location_prices",fields("id",id+"-price","platform_product_id",id+"-listing","location_id","recipe-fixture","price_minor",price,
            "availability","available","observed_at",Instant.now().minusSeconds(30),"source","SYNTHETIC_TEST","verification","VERIFIED"));
    }
}
