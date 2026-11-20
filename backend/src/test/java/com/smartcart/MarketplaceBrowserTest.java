package com.smartcart;

import com.smartcart.repository.Db;
import com.smartcart.catalogimport.ProductMatching;
import com.smartcart.integration.QuickCommerceProvider;
import com.smartcart.integration.ShoppingGeocoder;
import com.smartcart.service.AuthService;
import com.smartcart.service.ControlledPilot;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static com.smartcart.repository.Db.fields;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ActiveProfiles({"local","test"})
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "smartcart.sample-data=false","smartcart.import-starter-catalogue=true","smartcart.jwt-secret=phase9-browser-fixture-signing-value",
 "QUICKCOMMERCE_SHOPPING_ENABLED=true","QUICKCOMMERCE_PILOT_ENABLED=true",
 "QUICKCOMMERCE_OFFER_PERMISSION_REFERENCE=browser-fixture","QUICKCOMMERCE_OFFER_LOCATION_ID=blr-indiranagar",
 "QUICKCOMMERCE_OFFER_LAT=12.9784","QUICKCOMMERCE_OFFER_LON=77.6408",
 "spring.datasource.url=jdbc:h2:mem:phase9-browser;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="CARTIQ_MARKETPLACE_BROWSER",matches="true")
class MarketplaceBrowserTest {
 @LocalServerPort int port;
 @Autowired Db db;
 @Autowired AuthService auth;
 @Autowired ControlledPilot pilot;
 @MockitoBean QuickCommerceProvider provider;
 @MockitoBean ShoppingGeocoder geo;

 @Test void chromeAcrossThreeViewports() throws Exception {
   String secret=UUID.randomUUID()+"-aA5!";
   String shopper=auth.create("CartIQ fixture shopper","phase9-shopper@example.invalid",secret,null,false).session().user().id();
   String peer=auth.create("CartIQ fixture peer","phase9-peer@example.invalid",secret,null,false).session().user().id();
   String operator=auth.create("CartIQ fixture test operator","phase9-operator@example.invalid",secret,null,false).session().user().id();
   String area="blr-indiranagar",sku="fixture-milk",decision=UUID.randomUUID().toString();
   double lat=12.9784,lon=77.6408;
   String pin="560038";
   when(provider.configured()).thenReturn(true);
   db.insert("platform_products",fields("id","phase9-milk-listing","variant_id","milk","platform_id","blinkit","retailer_sku",sku));
   db.insert("provider_mappings",fields("platform_product_id","phase9-milk-listing","matched_by",operator,"matched_at",Instant.now()));
   db.insert("provider_mapping_decisions",fields("decision_id",decision,"variant_id","milk","platform_id","blinkit","retailer_sku",sku,
    "revision_no",1,"disposition","APPROVED","reviewed_by",operator,"source_provider","QUICKCOMMERCEAPI","source_record_id","fixture",
    "evidence_reference","fixture-only","evidence_sha256","a".repeat(64),"permission_reference","browser-fixture","rationale","Test fixture only",
    "listing_brand","Amul","listing_title","Amul Taaza","listing_pack","1 litre","canonical_gtins","",
    "canonical_pack","1 litre","canonical_brand","Amul","canonical_title","Amul Taaza","canonical_variant","Standard","blocking_reasons",""));
   db.run("UPDATE grocery_platforms SET delivery_minor=500,handling_minor=100,minimum_minor=0,fee_source='VERIFIED' WHERE id='blinkit'");
   var now=Instant.now();
   String id=pilot.create(operator,new ControlledPilot.Setup(area,"blinkit",now.minusSeconds(60),now.plusSeconds(1800),5,5,
    "browser-fixture",true,true,List.of(new ControlledPilot.Pack("milk",UUID.fromString(decision),ControlledPilot.Group.MILK))));
   pilot.enable(id,true);
   Map<String,String> queries=Map.of("apple","Fixture apples","tomato","Fixture tomatoes","milk","Amul Taaza","chips","Fixture chips","amul taaza milk","Amul Taaza");
   for(var row:queries.entrySet()){
     String term=row.getKey();boolean milk=term.equals("milk")||term.equals("amul taaza milk");
     pilot.authorizeDiscovery(operator,id,new ControlledPilot.Authorization(shopper,"milk",ControlledPilot.Operation.SEARCH,
      now.plusSeconds(600),"fixture-"+term,true),term,true);
     String pack=milk?"1 litre":"500 g";
     var listing=new QuickCommerceProvider.Product(milk?sku:"fixture-"+term,row.getValue(),milk?"Amul":"Fixture brand",pack,
       "INR",milk?6000L:null,milk?7000L:null,milk?true:null,milk?5:null,null,null,
       ProductMatching.pack(pack),null,null,null,null,null,null,null,null);
     when(provider.search("blinkit",term,lat,lon,pin)).thenReturn(new QuickCommerceProvider.Result("blinkit",91,200,
       new QuickCommerceProvider.Location(lat,lon,pin,pin),List.of(listing),Instant.now(),UUID.randomUUID().toString()));
   }
   var command=new ProcessBuilder("node",Path.of(System.getenv("CARTIQ_BROWSER_RUNNER")).toAbsolutePath().toString(),String.valueOf(port)).inheritIO();
   command.environment().put("PHASE9_FIXTURE_PASSWORD",secret);
   Process chrome=command.start();
   if(!chrome.waitFor(150,TimeUnit.SECONDS)){chrome.destroyForcibly();fail("Browser runner timed out");}
   assertEquals(0,chrome.exitValue());
   verify(provider,times(5)).search(anyString(),anyString(),anyDouble(),anyDouble(),any());
   assertEquals(1,db.count("SELECT COUNT(*) FROM cart_items i JOIN shopping_carts c ON c.id=i.cart_id WHERE c.user_id=?",shopper));
   assertEquals(0,db.count("SELECT COUNT(*) FROM cart_items i JOIN shopping_carts c ON c.id=i.cart_id WHERE c.user_id=?",peer));
   assertEquals(0,db.count("SELECT COUNT(*) FROM approved_price_observations"));
 }
}
