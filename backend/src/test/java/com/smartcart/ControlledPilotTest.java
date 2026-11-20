package com.smartcart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.catalogimport.ProductMatching;
import com.smartcart.integration.QuickCommerceProvider;
import com.smartcart.repository.Db;
import com.smartcart.exception.ApiException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.*;
import org.springframework.transaction.annotation.Transactional;
import static com.smartcart.repository.Db.fields;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles({"local","test"})
@SpringBootTest(properties={"QUICKCOMMERCE_SHOPPING_ENABLED=true","QUICKCOMMERCE_PILOT_ENABLED=true",
    "QUICKCOMMERCE_OFFER_PERMISSION_REFERENCE=fixture-grant","QUICKCOMMERCE_OFFER_LOCATION_ID=p8-area",
    "QUICKCOMMERCE_OFFER_LAT=12.9","QUICKCOMMERCE_OFFER_LON=77.66","smartcart.sample-data=false",
    "smartcart.jwt-secret=synthetic-local-phase-eight-signing-value",
    "spring.datasource.url=jdbc:h2:mem:p8-pilot;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc @Transactional
class ControlledPilotTest {
    @MockitoBean QuickCommerceProvider provider;
    @Autowired MockMvc mvc;
    @Autowired Db db;
    @Autowired ObjectMapper json;
    private static final String PILOT="/api/admin/retailer-pilot", OFFER="/api/shopping/retailers/offers/p8-pack",SKU="p8-native";
    private static final String REF="11111111-1111-4111-8111-111111111111";
    private final RequestPostProcessor admin=jwt().jwt(j->j.subject("p8-admin")).authorities(new SimpleGrantedAuthority("SCOPE_admin"));
    private final RequestPostProcessor shopper=jwt().jwt(j->j.subject("p8-shopper"));
    private final RequestPostProcessor other=jwt().jwt(j->j.subject("p8-other"));

    @BeforeEach void fixture() throws Exception {
        when(provider.configured()).thenReturn(true);
        for(String user:List.of("p8-admin","p8-shopper","p8-other"))
            db.insert("users",fields("id",user,"name",user,"email",user+"@example.invalid","password_hash","fixture"));
        db.insert("delivery_locations",fields("id","p8-area","name","Synthetic area","city","Fixture","postcode","641018"));
        db.insert("products",fields("id","p8-rice","name","Fixture rice","brand","Fixture","variant_name","Standard",
            "category_id","pantry","description","Synthetic test","color","#fff","art","bag","active",true));
        db.insert("product_variants",fields("id","p8-pack","product_id","p8-rice","pack_label","500 g","amount_base",500,"base_unit","g"));
        db.insert("platform_products",fields("id","p8-listing","variant_id","p8-pack","platform_id","blinkit","retailer_sku",SKU));
        db.insert("provider_mappings",fields("platform_product_id","p8-listing","matched_by","p8-admin","matched_at",Instant.now()));
        db.insert("provider_mapping_decisions",fields("decision_id",REF,"variant_id","p8-pack","platform_id","blinkit","retailer_sku",SKU,
            "revision_no",1,"disposition","APPROVED","reviewed_by","p8-admin","source_provider","QUICKCOMMERCEAPI","source_record_id","fixture",
            "evidence_reference","fixture-only","evidence_sha256","a".repeat(64),"permission_reference","fixture-grant","rationale","Fixture",
            "listing_brand","Fixture","listing_title","Fixture rice","listing_pack","500 g","canonical_gtins","",
            "canonical_pack","500 g","canonical_brand","Fixture","canonical_title","Fixture rice","canonical_variant","Standard","blocking_reasons",""));
        db.run("UPDATE grocery_platforms SET delivery_minor=500,handling_minor=100,minimum_minor=0,fee_source='VERIFIED' WHERE id='blinkit'");
        var fix=fields("latitude",12.9,"longitude",77.66,"accuracy",8,"source","gps","postcode","641018","observedAt",System.currentTimeMillis());
        for(var who:List.of(shopper,other))
            send(put("/api/me/shopping-position"),who,fix).andExpect(status().isOk());
    }
    private ResultActions send(MockHttpServletRequestBuilder req,RequestPostProcessor who,Map<String,Object> data)throws Exception {
        return mvc.perform(req.with(who).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)));
    }
    private Map<String,Object> setup(){return fields("location","p8-area","retailer","blinkit","startsAt",Instant.now().minusSeconds(60),
        "endsAt",Instant.now().plusSeconds(1800),"maxAttempts",2,"maxCredits",1,"permissionReference","fixture-grant",
        "displayAndTemporaryJoinApproved",true,"operationalMetadataApproved",true,
        "packs",List.of(fields("variant","p8-pack","mappingDecision",REF,"group","RICE")));}
    private Map<String,Object> authorization(String name){return fields("accountId","p8-shopper","variant","p8-pack","operation","SEARCH",
        "expiresAt",Instant.now().plusSeconds(300),"authorizationReference",name,"separatelyAuthorized",true);}
    private Map<String,Object> refresh(String ticket){return fields("locationId","p8-area","lat",12.9,"lon",77.66,
        "pincode","641018","retailers",List.of("blinkit"),"authorizationId",ticket);}
    private String id(ResultActions result)throws Exception{
        return json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText();
    }
    private String openedPilot()throws Exception{
        var pilot=id(send(post(PILOT),admin,setup()));
        send(put(PILOT+"/"+pilot+"/enabled"),admin,fields("enabled",true)).andExpect(status().isOk());
        return pilot;
    }

    @Test void defaultsToDisabledAndRequiresSeparateAccountBoundApprovalBeforeRefreshing() throws Exception{
        send(post(PILOT),shopper,setup()).andExpect(status().isForbidden());
        var noRights=setup();noRights.put("operationalMetadataApproved",false);
        send(post(PILOT),admin,noRights).andExpect(status().isBadRequest());
        var pilot=id(send(post(PILOT),admin,setup()));
        mvc.perform(get(PILOT+"/"+pilot).with(admin)).andExpect(jsonPath("$.pilot.enabled").value(false));
        send(post(PILOT+"/"+pilot+"/authorize"),admin,authorization("separate-ref")).andExpect(status().isForbidden());
        send(put(PILOT+"/"+pilot+"/enabled"),admin,fields("enabled",true)).andExpect(status().isOk());
        var noApproval=authorization("missing");noApproval.put("separatelyAuthorized",false);
        send(post(PILOT+"/"+pilot+"/authorize"),admin,noApproval).andExpect(status().isBadRequest());
        var ticket=id(send(post(PILOT+"/"+pilot+"/authorize"),admin,authorization("separate-ref")));
        send(post(OFFER+"/refresh"),shopper,refresh(null)).andExpect(status().isForbidden());
        send(post(OFFER+"/refresh"),other,refresh(ticket)).andExpect(status().isForbidden());
        verify(provider,never()).search(anyString(),anyString(),anyDouble(),anyDouble(),any());
        mvc.perform(get(OFFER).with(shopper).param("locationId","p8-area")).andExpect(jsonPath("$.authorizations[0].id").value(ticket));
        mvc.perform(get(OFFER).with(other).param("locationId","p8-area")).andExpect(jsonPath("$.authorizations").isEmpty());
    }

    @Test void storesSingleConfirmedDebitAndRepricesWithoutWritingAnObservation() throws Exception{
        String pilot=openedPilot(),ticket=id(send(post(PILOT+"/"+pilot+"/authorize"),admin,authorization("one-search")));
        var product=new QuickCommerceProvider.Product(SKU,"Fixture rice","Fixture","500 g","INR",6000L,7000L,true,4,null,null,
            ProductMatching.pack("500 g"),null,null,null,null,null,null,null,null);
        var providerId="22222222-2222-4222-8222-222222222222";
        when(provider.search("blinkit","Fixture Fixture rice 500 g",12.9,77.66,"641018")).thenReturn(
            new QuickCommerceProvider.Result("blinkit",91,200,new QuickCommerceProvider.Location(12.9,77.66,"641018","641018"),
                List.of(product),Instant.now(),providerId));
        send(post(OFFER+"/refresh"),shopper,refresh(ticket)).andExpect(status().isOk()).andExpect(jsonPath("$.quotes[0].origin").value("LIVE_RESPONSE"))
            .andExpect(jsonPath("$.receipt.confirmedDebit").value(1)).andExpect(jsonPath("$.receipt.providerRequestId").value(providerId));
        send(post(OFFER+"/refresh"),shopper,refresh(ticket)).andExpect(status().isForbidden());
        send(post(PILOT+"/"+pilot+"/authorize"),admin,authorization("second-search")).andExpect(status().isConflict());
        var draft=fields("meals",List.of(),"items",List.of(fields("productId","p8-pack","quantity",2)),"diet","ANY","onHand",Map.of());
        var request=post("/api/me/shopping-intelligence/weekly/preview").with(shopper).param("locationId","p8-area")
            .header("X-CartIQ-Offer-Position","12.9,77.66,641018").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(draft));
        mvc.perform(request).andExpect(jsonPath("$.plan.comparison.recommended.totalMinor").value(12600));
        mvc.perform(get(PILOT+"/"+pilot).with(admin)).andExpect(jsonPath("$.attempts[0].verified_count").value(1))
            .andExpect(jsonPath("$.attempts[0].credits_remaining").value(91));
        verify(provider,times(1)).search(anyString(),anyString(),anyDouble(),anyDouble(),any());
        assertEquals(0,db.count("SELECT COUNT(*) FROM approved_price_observations WHERE variant_id='p8-pack'"));
    }

    @Test void deniesChangesToAuthorizedScopeAndExpiredTicketsWithoutCallingProvider() throws Exception{
        String pilot=openedPilot();
        String authorize=PILOT+"/"+pilot+"/authorize";
        var expired=authorization("already-expired");
        expired.put("expiresAt",Instant.now().minusSeconds(60));
        send(post(authorize),admin,expired).andExpect(status().isBadRequest());
        var ticket=id(send(post(authorize),admin,authorization("scoped-search")));

        var wrongRetailer=refresh(ticket);
        wrongRetailer.put("retailers",List.of("zepto"));
        send(post(OFFER+"/refresh"),shopper,wrongRetailer).andExpect(status().isForbidden());
        var wrongArea=refresh(ticket);
        wrongArea.put("locationId","different-area");
        send(post(OFFER+"/refresh"),shopper,wrongArea).andExpect(status().isServiceUnavailable());
        var wrongCoordinates=refresh(ticket);
        wrongCoordinates.put("lon",76.66);
        send(post(OFFER+"/refresh"),shopper,wrongCoordinates).andExpect(status().isBadRequest());
        send(post(OFFER+"/item"),shopper,fields("scope",refresh(ticket),"nativeId",SKU))
            .andExpect(status().isForbidden());

        db.insert("product_variants",fields("id","p8-other-pack","product_id","p8-rice","pack_label","1 kg",
            "amount_base",1000,"base_unit","g"));
        send(post("/api/shopping/retailers/offers/p8-other-pack/refresh"),shopper,refresh(ticket))
            .andExpect(status().isForbidden());
        mvc.perform(get(OFFER).with(shopper).param("locationId","p8-area"))
            .andExpect(jsonPath("$.authorizations[0].id").value(ticket));

        db.run("UPDATE retailer_pilot_attempts SET expires_at=? WHERE id=?",Instant.now().minusSeconds(60),ticket);
        mvc.perform(get(OFFER).with(shopper).param("locationId","p8-area"))
            .andExpect(jsonPath("$.authorizations").isEmpty());
        send(post(OFFER+"/refresh"),shopper,refresh(ticket)).andExpect(status().isForbidden());
        verifyNoMoreInteractions(ignoreStubs(provider));
    }

    @Test void uncertainDispatchBlocksTheNextAuthorizedAction() throws Exception{
        String pilot=openedPilot(),first=id(send(post(PILOT+"/"+pilot+"/authorize"),admin,authorization("one"))),
            second=id(send(post(PILOT+"/"+pilot+"/authorize"),admin,authorization("two")));
        when(provider.search(anyString(),anyString(),anyDouble(),anyDouble(),any())).thenThrow(new ApiException(HttpStatus.GATEWAY_TIMEOUT,"fixture timeout"));
        send(post(OFFER+"/refresh"),shopper,refresh(first)).andExpect(status().isOk()).andExpect(jsonPath("$.paused").value(true))
            .andExpect(jsonPath("$.receipt.confirmedDebit").value(nullValue()));
        send(post(OFFER+"/refresh"),shopper,refresh(second)).andExpect(status().isConflict());
        verify(provider,times(1)).search(anyString(),anyString(),anyDouble(),anyDouble(),any());
    }

    @Test void marketplaceScopeCachesScopedListingsAndAddsOnlyTheApprovedPack() throws Exception{
        var limited=setup();limited.put("maxCredits",2);
        String pilot=id(send(post(PILOT),admin,limited)),root="/api/shopping/retailers/marketplace";
        send(put(PILOT+"/"+pilot+"/enabled"),admin,fields("enabled",true)).andExpect(status().isOk());
        var approval=authorization("category-search");
        send(post(PILOT+"/"+pilot+"/authorize-discovery"),admin,
            fields("approval",approval,"query","rice","discoveryDisplayApproved",false)).andExpect(status().isBadRequest());
        String ticket=id(send(post(PILOT+"/"+pilot+"/authorize-discovery"),admin,
            fields("approval",approval,"query","rice","discoveryDisplayApproved",true)));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
            ()->db.run("UPDATE retailer_pilot_attempts SET claimed_at=? WHERE id=?",Instant.now(),ticket));
        var position=fields("locationId","p8-area","lat",12.9,"lon",77.66,"pincode","641018");
        var search=fields("location",position,"query","rice","variant","p8-pack","retailer","blinkit","authorizationId",ticket);
        var wrong=new LinkedHashMap<>(search);wrong.put("query","apple");
        send(post(root+"/search"),shopper,wrong).andExpect(status().isForbidden());
        send(post(root+"/search"),other,search).andExpect(status().isForbidden());
        send(post(OFFER+"/refresh"),shopper,refresh(ticket)).andExpect(status().isForbidden());
        verify(provider,never()).search(anyString(),anyString(),anyDouble(),anyDouble(),any());
        var product=new QuickCommerceProvider.Product(SKU,"Fixture rice","Fixture","500 g","INR",6000L,7000L,true,4,null,null,
            ProductMatching.pack("500 g"),null,null,null,null,null,null,null,null);
        var different=new QuickCommerceProvider.Product("unapproved","Fixture rice","Fixture","1 kg","INR",null,null,null,null,null,null,
            ProductMatching.pack("1 kg"),null,null,null,null,null,null,null,null);
        when(provider.search("blinkit","rice",12.9,77.66,"641018")).thenReturn(
            new QuickCommerceProvider.Result("blinkit",91,200,new QuickCommerceProvider.Location(12.9,77.66,"641018","641018"),
                List.of(product,different,different),Instant.now(),REF));
        send(post(root+"/search"),shopper,search).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("SUCCESS"))
            .andExpect(jsonPath("$.marketplace.recorded[0].listings.length()").value(2));
        send(post(root+"/search"),shopper,search).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("CACHED"))
            .andExpect(jsonPath("$.debit").value(0));
        String unrelated=id(send(post(PILOT+"/"+pilot+"/authorize-discovery"),admin,
            fields("approval",authorization("another-query"),"query","snacks","discoveryDisplayApproved",true)));
        when(provider.search("blinkit","snacks",12.9,77.66,"641018")).thenReturn(
            new QuickCommerceProvider.Result("blinkit",90,200,new QuickCommerceProvider.Location(12.9,77.66,"641018","641018"),
                List.of(different),Instant.now(),"22222222-2222-4222-8222-222222222222"));
        send(post(root+"/search"),shopper,fields("location",position,"query","snacks","variant","p8-pack","retailer","blinkit",
            "authorizationId",unrelated)).andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("SUCCESS"));
        var add=fields("location",position,"query","rice","variant","p8-pack","retailer","blinkit","nativeId",SKU);
        send(post(root+"/cart"),other,add).andExpect(status().isConflict());
        var distinct=new LinkedHashMap<>(add);distinct.put("nativeId","unapproved");
        send(post(root+"/cart"),shopper,distinct).andExpect(status().isConflict());
        send(post(root+"/cart"),shopper,add).andExpect(status().isOk()).andExpect(jsonPath("$.item.quantity").value(1))
            .andExpect(jsonPath("$.offer.nativeId").value(SKU));
        mvc.perform(get("/api/me/cart").with(shopper)).andExpect(jsonPath("$[0].productId").value("p8-pack"));
        mvc.perform(get("/api/me/cart").with(shopper)).andExpect(jsonPath("$[0].listing.nativeId").value(SKU));
        send(post(root+"/cart-quotes"),shopper,position).andExpect(status().isOk()).andExpect(jsonPath("$[0].priceMinor").value(6000));
        send(post(root+"/cart-quotes"),other,position).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/me/cart").with(other)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/me/shopping-intelligence/weekly").with(shopper).param("locationId","p8-area"))
            .andExpect(jsonPath("$.cart[0].productId").value("p8-pack"));
        var preview=fields("meals",List.of(),"items",List.of(fields("productId","p8-pack","quantity",1)),"diet","ANY","onHand",Map.of());
        mvc.perform(post("/api/me/shopping-intelligence/weekly/preview").with(shopper).param("locationId","p8-area")
            .header("X-CartIQ-Offer-Position","12.9,77.66,641018").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(preview))).andExpect(status().isOk())
            .andExpect(jsonPath("$.plan.comparison.recommended.totalMinor").value(6600))
            .andExpect(jsonPath("$.plan.comparison.recommended.packs[0].quote.origin").value("RETRIEVED_OFFER"));
        send(put(PILOT+"/"+pilot+"/enabled"),admin,fields("enabled",false)).andExpect(status().isOk());
        send(post(root+"/cart"),shopper,add).andExpect(status().isConflict());
        verify(provider,times(2)).search(anyString(),anyString(),anyDouble(),anyDouble(),any());
        assertEquals(0,db.count("SELECT COUNT(*) FROM approved_price_observations WHERE variant_id='p8-pack'"));
    }

    @Test void anExpiredMarketplaceResultIsNotReusedOrAdmittedToCart() throws Exception{
        String pilot=openedPilot(),root="/api/shopping/retailers/marketplace";
        String ticket=id(send(post(PILOT+"/"+pilot+"/authorize-discovery"),admin,fields("approval",authorization("expired-response"),
            "query","expired rice","discoveryDisplayApproved",true)));
        var position=fields("locationId","p8-area","lat",12.9,"lon",77.66,"pincode","641018");
        var product=new QuickCommerceProvider.Product(SKU,"Fixture rice","Fixture","500 g","INR",6000L,7000L,true,4,null,null,
            ProductMatching.pack("500 g"),null,null,null,null,null,null,null,null);
        when(provider.search("blinkit","expired rice",12.9,77.66,"641018")).thenReturn(
            new QuickCommerceProvider.Result("blinkit",91,200,new QuickCommerceProvider.Location(12.9,77.66,"641018","641018"),
                List.of(product),Instant.now().minusSeconds(1200),REF));
        var request=fields("location",position,"query","expired rice","variant","p8-pack","retailer","blinkit","authorizationId",ticket);
        send(post(root+"/search"),shopper,request).andExpect(status().isOk()).andExpect(jsonPath("$.marketplace.recorded").isEmpty());
        send(post(root+"/search"),shopper,request).andExpect(status().isForbidden());
        send(post(root+"/cart"),shopper,fields("location",position,"query","expired rice","variant","p8-pack","retailer","blinkit","nativeId",SKU))
            .andExpect(status().isConflict());
        verify(provider,times(1)).search(anyString(),anyString(),anyDouble(),anyDouble(),any());
    }
}
