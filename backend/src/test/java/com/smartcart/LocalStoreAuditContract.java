package com.smartcart;

import com.fasterxml.jackson.databind.*;
import com.smartcart.repository.Db;
import com.smartcart.service.LocalStoreService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Same contract runs on isolated H2 and opt-in actual MySQL. */
@org.springframework.test.context.TestExecutionListeners(
    listeners={org.springframework.test.context.web.ServletTestExecutionListener.class,
        org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,
        org.springframework.test.context.transaction.TransactionalTestExecutionListener.class},
    mergeMode=org.springframework.test.context.TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
abstract class LocalStoreAuditContract {
    @Autowired MockMvc mvc;
    @Autowired Db db;
    @Autowired ObjectMapper json;
    @Autowired JwtEncoder encoder;
    @Autowired LocalStoreService stores;
    static final String ADMIN="/api/admin/local-stores";
    List<Map<String,Object>> products, variants;

    @BeforeEach void snapshot() {
        products=db.rows("SELECT * FROM products ORDER BY id");
        variants=db.rows("SELECT * FROM product_variants ORDER BY id");
        assertFalse(variants.isEmpty(),"Existing catalogue is required");
    }
    @AfterEach void catalogueUnchanged() {
        assertEquals(products,db.rows("SELECT * FROM products ORDER BY id"));
        assertEquals(variants,db.rows("SELECT * FROM product_variants ORDER BY id"));
    }
    String variant(){return variants.get(0).get("id").toString();}
    String token(String scope) {
        String user=Db.id();
        db.insert("users",Db.fields("id",user,"name","Audit","email",user+"@example.invalid","password_hash","unused","role",scope));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
            JwtClaimsSet.builder().issuer("smartcart-ai").subject(user).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(120)).claim("version",0L).claim("scope",scope).build())).getTokenValue();
    }
    LocalStoreService.Store store(double lat,double lon,String pin,boolean active) {
        return new LocalStoreService.Store("Audit "+Db.id(),"Verification fixture",pin,lat,lon,true,false,null,active);
    }
    JsonNode body(MvcResult result)throws Exception{return json.readTree(result.getResponse().getContentAsString());}
    ResultActions write(String method,String path,String token,Object payload)throws Exception {
        return mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method),path)
            .header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)));
    }

    @Test @Transactional void signedAuthenticationCrudAndValidation()throws Exception {
        String admin=token("admin"), customer=token("customer");
        var payload=store(12.97,77.64,"560038",true);
        for(String operation:List.of("GET ","POST ","PUT /missing","PUT /missing/listings")){
            String method=operation.substring(0,operation.indexOf(' '));
            String path=ADMIN+operation.substring(operation.indexOf(' ')+1);
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method),path)).andExpect(status().isUnauthorized());
            write(method,path,customer,payload).andExpect(status().isForbidden());
        }
        mvc.perform(get(ADMIN).header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        String id=body(write("POST",ADMIN,admin,payload).andExpect(status().isCreated()).andReturn()).path("id").asText();
        mvc.perform(get(ADMIN).header("Authorization","Bearer "+admin)).andExpect(status().isOk());
        write("PUT",ADMIN+"/"+id,admin,payload).andExpect(status().isNoContent());
        for(String field:List.of("name","address","postcode","latitude","longitude")){
            var missing=json.valueToTree(payload);((com.fasterxml.jackson.databind.node.ObjectNode)missing).remove(field);
            write("POST",ADMIN,admin,missing).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").exists());
        }
        write("PUT",ADMIN+"/missing",admin,payload).andExpect(status().isNotFound());
        String path=ADMIN+"/"+id+"/listings";
        for(Map<String,?> invalid:List.of(
            Map.of("variantId",variant(),"priceMinor",-1,"stockQuantity",1),
            Map.of("variantId",variant(),"priceMinor",1,"stockQuantity",-1),
            Map.of("variantId",variant(),"stockQuantity",1),
            Map.of("variantId",variant(),"priceMinor",1),
            Map.of("variantId",variant(),"priceMinor",1,"stockQuantity",1,"mrpMinor",-1))) {
            write("PUT",path,admin,invalid).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").exists());
        }
        write("PUT",path,admin,new LocalStoreService.Listing("missing",10L,null,1)).andExpect(status().isNotFound());
        write("PUT",ADMIN+"/missing/listings",admin,new LocalStoreService.Listing(variant(),10L,null,1)).andExpect(status().isNotFound());
        for(long price:new long[]{100,200})write("PUT",path,admin,new LocalStoreService.Listing(variant(),price,null,2)).andExpect(status().isNoContent());
        assertEquals(1,db.count("SELECT COUNT(*) FROM local_store_listings WHERE store_id=?",id));
        assertEquals(200,Db.num(db.one("SELECT price_minor FROM local_store_listings WHERE store_id=?",id),"price_minor"));
        mvc.perform(get("/api/local-stores/"+id+"/listings")).andExpect(status().isOk()).andExpect(jsonPath("$[0].price_minor").value(200));
    }

    @Test @Transactional void coordinateDistanceIsIndependentOfPinCode()throws Exception {
        String near=stores.create(store(0,0.01,"560038",true));
        String farther=stores.create(store(0,0.02,"600001",true));
        String outside=stores.create(store(0,1,"560038",true));
        String inactive=stores.create(store(0,0,"560038",false));
        var results=body(mvc.perform(get("/api/local-stores?latitude=0&longitude=0&radiusKm=3")).andExpect(status().isOk()).andReturn());
        List<String> ids=new ArrayList<>();double previous=-1;
        for(var row:results){ids.add(row.path("id").asText());double distance=row.path("distanceKm").asDouble();assertTrue(distance>=previous);previous=distance;}
        assertTrue(ids.contains(near));assertTrue(ids.contains(farther));assertFalse(ids.contains(outside));assertFalse(ids.contains(inactive));
        assertTrue(ids.indexOf(near)<ids.indexOf(farther));
        for(var row:results)if(row.path("id").asText().equals(near))assertEquals(1.11,row.path("distanceKm").asDouble(),0.01);
        for(String query:List.of("latitude=91&longitude=0","latitude=0&longitude=181","latitude=0","latitude=abc&longitude=0","latitude=NaN&longitude=0","latitude=0&longitude=0&radiusKm=0","latitude=0&longitude=0&radiusKm=101","postcode=000000"))
            mvc.perform(get("/api/local-stores?"+query)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").exists());
    }

    @Test @Transactional void databaseRejectsDuplicatesOrphansAndNegativeValues() {
        String id=stores.create(store(0,0,"560038",true));
        stores.saveListing(id,new LocalStoreService.Listing(variant(),100L,null,1));
        assertThrows(DataIntegrityViolationException.class,()->insert(id,variant(),100,1));
        assertThrows(DataIntegrityViolationException.class,()->insert(Db.id(),variant(),100,1));
        assertThrows(DataIntegrityViolationException.class,()->insert(id,"missing",100,1));
        assertThrows(org.springframework.dao.DataAccessException.class,()->db.run("UPDATE local_store_listings SET price_minor=-1 WHERE store_id=?",id));
        var listing=db.one("SELECT price_minor,stock_quantity FROM local_store_listings WHERE store_id=?",id);
        assertEquals(100,Db.num(listing,"price_minor"));
        assertEquals(1,Db.num(listing,"stock_quantity"));
        assertThrows(org.springframework.dao.DataAccessException.class,()->db.run("UPDATE local_store_listings SET stock_quantity=-1 WHERE store_id=?",id));
        listing=db.one("SELECT price_minor,stock_quantity FROM local_store_listings WHERE store_id=?",id);
        assertEquals(100,Db.num(listing,"price_minor"));
        assertEquals(1,Db.num(listing,"stock_quantity"));
    }
    void insert(String store,String variant,long price,int stock){
        db.insert("local_store_listings",Db.fields("id",Db.id(),"store_id",store,"variant_id",variant,"price_minor",price,"stock_quantity",stock,"observed_at",Instant.now()));
    }

    @Test void concurrentUpsertsCommitOneWholeListing()throws Exception {
        // Only this newly created fixture is committed; cleanup never touches existing stores/catalogue.
        String id=stores.create(store(0,0,"560038",false));
        var pool=Executors.newFixedThreadPool(2);
        try {
            for(int round=0;round<3;round++){
                var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
                List<Future<?>> jobs=new ArrayList<>();
                for(int i=1;i<=2;i++){final int value=i;
                    jobs.add(pool.submit(()->{ready.countDown();try{assertTrue(start.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}
                        stores.saveListing(id,new LocalStoreService.Listing(variant(),value*100L,null,value));}));
                }
                assertTrue(ready.await(10,TimeUnit.SECONDS));start.countDown();
                for(var job:jobs)job.get(20,TimeUnit.SECONDS);
                assertEquals(1,db.count("SELECT COUNT(*) FROM local_store_listings WHERE store_id=?",id));
                var row=db.one("SELECT price_minor,stock_quantity FROM local_store_listings WHERE store_id=?",id);
                assertEquals(Db.num(row,"stock_quantity")*100,Db.num(row,"price_minor"));
            }
        } finally {
            pool.shutdownNow();assertTrue(pool.awaitTermination(30,TimeUnit.SECONDS));
            db.run("DELETE FROM local_store_listings WHERE store_id=?",id);
            db.run("DELETE FROM local_stores WHERE id=?",id);
            assertEquals(0,db.count("SELECT COUNT(*) FROM local_store_listings WHERE store_id=?",id));
            assertEquals(0,db.count("SELECT COUNT(*) FROM local_stores WHERE id=?",id));
        }
    }
}

