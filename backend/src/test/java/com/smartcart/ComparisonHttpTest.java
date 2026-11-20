package com.smartcart;

import com.smartcart.config.*;
import com.smartcart.controller.ComparisonController;
import com.smartcart.exception.ApiErrors;
import com.smartcart.repository.Db;
import com.smartcart.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class ComparisonHttpTest {
    @Test void returnsFourSourcesWithoutInventingAWinner() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:comparison"+java.util.UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        org.flywaydb.core.Flyway.configure().dataSource(source).load().migrate();
        var db=new Db(new JdbcTemplate(source));
        new ReferenceData(db,false).run(new DefaultApplicationArguments());
        var json=new ObjectMapper();
        new StarterCatalogue(db,json).run(new DefaultApplicationArguments());
        var stores=new LocalStoreService(db);
        String store=stores.create(new LocalStoreService.Store("Nearby","Address","600001",0.0,0.01,true,false,null,true));
        stores.saveListing(store,new LocalStoreService.Listing("milk",7500L,null,4));
        var service=new ComparisonService(new CatalogService(db,false),stores,db);
        var http=MockMvcBuilders.standaloneSetup(new ComparisonController(service)).setControllerAdvice(new ApiErrors()).build();
        var response=http.perform(get("/api/products/milk/comparison?locationId=blr-indiranagar&latitude=0&longitude=0&radiusKm=3")).andReturn().getResponse();
        assertEquals(200,response.getStatus());
        var body=json.readTree(response.getContentAsString());
        assertEquals(4,body.path("options").size());
        assertTrue(body.path("lowestEligible").isNull());
        assertEquals("offline",body.path("options").get(3).path("source").asText());
        assertEquals(7500,body.path("options").get(3).path("priceMinor").asLong());
        assertTrue(body.path("options").get(3).path("totalMinor").isNull());
        assertFalse(body.path("options").get(3).path("eligible").asBoolean());
        db.insert("local_store_comparison_terms",Db.fields("store_id",store,"pickup_charge_minor",0,"pickup_minimum_minor",0,"delivery_charge_minor",250,"delivery_minimum_minor",10000,"observed_at",java.time.Instant.now()));
        var pickup=json.readTree(http.perform(get("/api/products/milk/comparison?locationId=blr-indiranagar&latitude=0&longitude=0&radiusKm=3&quantity=2")).andReturn().getResponse().getContentAsString());
        assertEquals(15000,pickup.path("lowestEligible").path("totalMinor").asLong());
        assertEquals("offline",pickup.path("lowestEligible").path("source").asText());
        var delivery=json.readTree(http.perform(get("/api/products/milk/comparison?locationId=blr-indiranagar&latitude=0&longitude=0&fulfillment=delivery&quantity=2")).andReturn().getResponse().getContentAsString());
        assertTrue(delivery.path("lowestEligible").isNull());
        var insufficient=json.readTree(http.perform(get("/api/products/milk/comparison?locationId=blr-indiranagar&latitude=0&longitude=0&quantity=5")).andReturn().getResponse().getContentAsString());
        assertTrue(insufficient.path("lowestEligible").isNull());
        // Test-only observations and attestations: these do not represent live provider data.
        var product=db.one("SELECT p.* FROM products p JOIN product_variants v ON v.product_id=p.id WHERE v.id=?","milk");
        db.run("UPDATE products SET variant_name=? WHERE id=?","Unflavoured",Db.text(product,"id"));
        String user=Db.id();
        db.insert("users",Db.fields("id",user,"name","Test admin","email",user+"@example.invalid","password_hash","unused","role","admin"));
        String mapping="milk-blinkit";
        db.insert("platform_products",Db.fields("id",mapping,"variant_id","milk","platform_id","blinkit","retailer_sku","test-sku"));
        db.insert("provider_mappings",Db.fields("platform_product_id",mapping,"matched_by",user,"matched_at",java.time.Instant.now()));
        var observed=java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        db.insert("location_prices",Db.fields("id","test-price","platform_product_id",mapping,"location_id","blr-indiranagar","price_minor",7000,"availability","available","observed_at",observed,"source","REWALTO","verification","VERIFIED"));
        var variant=db.one("SELECT amount_base,base_unit FROM product_variants WHERE id=?","milk");
        db.insert("online_comparison_evidence",Db.fields("price_id","test-price","retailer_sku","test-sku","product_name",Db.text(product,"name"),"brand",Db.text(product,"brand"),"variant_name","Unflavoured","amount_base",Db.decimal(variant,"amount_base"),"base_unit",Db.text(variant,"base_unit"),"price_observed_at",observed,"stock_quantity",3,"delivery_charge_minor",200,"handling_charge_minor",100,"minimum_minor",0,"evidence_source","TEST FIXTURE","observed_at",observed,"verified_by",user));
        var online=json.readTree(http.perform(get("/api/products/milk/comparison?locationId=blr-indiranagar&latitude=0&longitude=0&fulfillment=delivery&quantity=2")).andReturn().getResponse().getContentAsString());
        assertEquals("blinkit",online.path("lowestEligible").path("source").asText());
        assertEquals(14300,online.path("lowestEligible").path("totalMinor").asLong());
        assertEquals(7000,online.path("lowestEligible").path("unitPriceMinor").asLong());
        String url="/api/products/milk/comparison?locationId=blr-indiranagar&latitude=0&longitude=0&fulfillment=delivery&quantity=2";
        for(String provider:java.util.List.of("instamart","zepto")) {
            String listing="fixture-"+provider, price=listing+"-price", sku=listing+"-sku";
            db.insert("platform_products",Db.fields("id",listing,"variant_id","milk","platform_id",provider,"retailer_sku",sku));
            db.insert("provider_mappings",Db.fields("platform_product_id",listing,"matched_by",user,"matched_at",observed));
            db.insert("location_prices",Db.fields("id",price,"platform_product_id",listing,"location_id","blr-indiranagar","price_minor",7000,"availability","available","observed_at",observed,"source","REWALTO","verification","VERIFIED"));
            db.insert("online_comparison_evidence",Db.fields("price_id",price,"retailer_sku",sku,"product_name",Db.text(product,"name"),"brand",Db.text(product,"brand"),"variant_name","Unflavoured","amount_base",Db.decimal(variant,"amount_base"),"base_unit",Db.text(variant,"base_unit"),"price_observed_at",observed,"stock_quantity",3,"delivery_charge_minor",200,"handling_charge_minor",100,"minimum_minor",0,"evidence_source","TEST FIXTURE","observed_at",observed,"verified_by",user));
        }
        var tied=json.readTree(http.perform(get(url)).andReturn().getResponse().getContentAsString());
        assertEquals("blinkit",tied.path("lowestEligible").path("source").asText());
        assertEquals(0,tied.path("savingsMinor").asInt());
        db.run("UPDATE local_stores SET delivery_enabled=TRUE WHERE id=?",store);
        db.run("UPDATE local_store_listings SET price_minor=6000 WHERE store_id=?",store);
        assertEquals("offline",json.readTree(http.perform(get(url)).andReturn().getResponse().getContentAsString()).path("lowestEligible").path("source").asText());
        db.run("UPDATE local_store_listings SET price_minor=7025 WHERE store_id=?",store);
        assertEquals("blinkit",json.readTree(http.perform(get(url)).andReturn().getResponse().getContentAsString()).path("lowestEligible").path("source").asText());
        db.run("UPDATE local_stores SET delivery_enabled=FALSE WHERE id=?",store);
        db.run("UPDATE grocery_platforms SET enabled=FALSE WHERE id IN ('blinkit','zepto')");
        assertEquals("instamart",json.readTree(http.perform(get(url)).andReturn().getResponse().getContentAsString()).path("lowestEligible").path("source").asText());
        db.run("UPDATE grocery_platforms SET enabled=(id='zepto') WHERE id IN ('blinkit','instamart','zepto')");
        assertEquals("zepto",json.readTree(http.perform(get(url)).andReturn().getResponse().getContentAsString()).path("lowestEligible").path("source").asText());
        db.run("UPDATE grocery_platforms SET enabled=(id='blinkit') WHERE id IN ('blinkit','instamart','zepto')");
        for(String sql:new String[]{
            "UPDATE online_comparison_evidence SET stock_quantity=NULL WHERE price_id='test-price'",
            "UPDATE online_comparison_evidence SET stock_quantity=1 WHERE price_id='test-price'",
            "UPDATE online_comparison_evidence SET delivery_charge_minor=NULL WHERE price_id='test-price'",
            "UPDATE online_comparison_evidence SET handling_charge_minor=NULL WHERE price_id='test-price'",
            "UPDATE online_comparison_evidence SET minimum_minor=NULL WHERE price_id='test-price'",
            "UPDATE online_comparison_evidence SET minimum_minor=20000 WHERE price_id='test-price'",
            "UPDATE online_comparison_evidence SET amount_base=999 WHERE price_id='test-price'",
            "UPDATE online_comparison_evidence SET brand='Other brand' WHERE price_id='test-price'",
            "UPDATE online_comparison_evidence SET observed_at=TIMESTAMP '2000-01-01 00:00:00' WHERE price_id='test-price'",
            "UPDATE location_prices SET availability='out_of_stock' WHERE id='test-price'",
            "UPDATE location_prices SET verification='UNVERIFIED' WHERE id='test-price'"
        }) {
            db.run(sql);
            var rejected=json.readTree(http.perform(get(url)).andReturn().getResponse().getContentAsString());
            assertTrue(rejected.path("lowestEligible").isNull(),sql);
            db.run("UPDATE online_comparison_evidence SET stock_quantity=3,delivery_charge_minor=200,handling_charge_minor=100,minimum_minor=0,amount_base=?,brand=?,observed_at=? WHERE price_id='test-price'",Db.decimal(variant,"amount_base"),Db.text(product,"brand"),observed);
            db.run("UPDATE location_prices SET availability='available',verification='VERIFIED' WHERE id='test-price'");
        }
        for(var invalidTime:java.util.List.of(java.time.Instant.now().minusSeconds(1200),java.time.Instant.now().plusSeconds(120))) {
            db.run("UPDATE online_comparison_evidence SET observed_at=? WHERE price_id=?",invalidTime,"test-price");
            assertTrue(json.readTree(http.perform(get(url)).andReturn().getResponse().getContentAsString()).path("lowestEligible").isNull());
            db.run("UPDATE online_comparison_evidence SET observed_at=? WHERE price_id=?",observed,"test-price");
            db.run("UPDATE location_prices SET observed_at=? WHERE id=?",invalidTime,"test-price");
            db.run("UPDATE online_comparison_evidence SET price_observed_at=? WHERE price_id=?",invalidTime,"test-price");
            assertTrue(json.readTree(http.perform(get(url)).andReturn().getResponse().getContentAsString()).path("lowestEligible").isNull());
            db.run("UPDATE location_prices SET observed_at=? WHERE id=?",observed,"test-price");
            db.run("UPDATE online_comparison_evidence SET price_observed_at=? WHERE price_id=?",observed,"test-price");
        }
        db.run("UPDATE online_comparison_evidence SET retailer_sku=? WHERE price_id=?","wrong-sku","test-price");
        assertTrue(json.readTree(http.perform(get(url)).andReturn().getResponse().getContentAsString()).path("lowestEligible").isNull());
        db.run("UPDATE online_comparison_evidence SET retailer_sku=? WHERE price_id=?","test-sku","test-price");
        db.run("UPDATE online_comparison_evidence SET variant_name=? WHERE price_id=?","Chocolate","test-price");
        var mismatch=json.readTree(http.perform(get("/api/products/milk/comparison?locationId=blr-indiranagar&latitude=0&longitude=0&fulfillment=delivery")).andReturn().getResponse().getContentAsString());
        assertTrue(mismatch.path("lowestEligible").isNull());
        var fixtureProvider=new com.smartcart.integration.GroceryDataProvider(){
            public String name(){return "REWALTO";}
            public java.util.List<Observation> observations(String retailer,String sku,String pin){return java.util.List.of();}
        };
        var sync=new com.smartcart.integration.ProviderSyncService(db,fixtureProvider);
        var persist=com.smartcart.integration.ProviderSyncService.class.getDeclaredMethod("persist",String.class,String.class,com.smartcart.integration.GroceryDataProvider.Observation.class,boolean.class);
        persist.setAccessible(true);
        var replacement=new com.smartcart.integration.GroceryDataProvider.Observation("blinkit","test-sku","560038",observed.plusSeconds(1),"Test","Test","1 litre",6900L,null,true,null);
        persist.invoke(sync,mapping,"blr-indiranagar",replacement,true);
        assertEquals(0,db.count("SELECT COUNT(*) FROM online_comparison_evidence WHERE price_id=?","test-price"));
        assertTrue(json.readTree(http.perform(get(url)).andReturn().getResponse().getContentAsString()).path("lowestEligible").isNull());
        assertEquals(400,http.perform(get("/api/products/milk/comparison?locationId=blr-indiranagar&latitude=0&longitude=0&quantity=0")).andReturn().getResponse().getStatus());
    }

    @Test void observedListingCannotBeReassignedToAnotherSku() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:remap"+java.util.UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        org.flywaydb.core.Flyway.configure().dataSource(source).load().migrate();
        var db=new Db(new JdbcTemplate(source));
        new ReferenceData(db,false).run(new DefaultApplicationArguments());
        new StarterCatalogue(db,new ObjectMapper()).run(new DefaultApplicationArguments());
        String listing="milk-blinkit";
        if (db.count("SELECT COUNT(*) FROM platform_products WHERE id=?",listing)==0)
            db.insert("platform_products",Db.fields("id",listing,"platform_id","blinkit","variant_id","milk","retailer_sku","fixture-original"));
        String original=Db.text(db.one("SELECT retailer_sku FROM platform_products WHERE id=?",listing),"retailer_sku");
        db.insert("location_prices",Db.fields("id","fixture-remap-price","platform_product_id",listing,
            "location_id","blr-indiranagar","availability","no_data","observed_at",java.time.Instant.now(),
            "source","TEST_FIXTURE","verification","UNVERIFIED"));
        var provider=new com.smartcart.integration.GroceryDataProvider() {
            public String name(){return "TEST_FIXTURE";}
            public java.util.List<Observation> observations(String retailer,String sku,String pin){return java.util.List.of();}
        };
        var sync=new com.smartcart.integration.ProviderSyncService(db,provider);
        var conflict=assertThrows(com.smartcart.exception.ApiException.class,
            ()->sync.map("unused-admin", "milk", "blinkit", "fixture-replacement"));
        assertEquals(409,conflict.status.value());
        assertEquals(original,Db.text(db.one("SELECT retailer_sku FROM platform_products WHERE id=?",listing),"retailer_sku"));
        assertEquals(1,db.count("SELECT COUNT(*) FROM location_prices WHERE platform_product_id=?",listing));
    }
}

