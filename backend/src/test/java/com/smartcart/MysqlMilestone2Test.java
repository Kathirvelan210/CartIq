package com.smartcart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.repository.Db;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=${CARTIQ_MYSQL_VERIFY_URL:jdbc:mysql://127.0.0.1:3306/cartiq_db?connectionTimeZone=UTC}",
    "spring.datasource.username=${CARTIQ_MYSQL_VERIFY_USERNAME:cartiq_user}","spring.datasource.password=${CARTIQ_DB_PASSWORD}",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver","smartcart.sample-data=false",
    "smartcart.jwt-secret=local-verification-secret-at-least-32-characters"})
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="CARTIQ_MYSQL_VERIFY",matches="true")
class MysqlMilestone2Test {
    @Autowired Db db;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test void mysqlCatalogueAndCartContracts() throws Exception {
        long before=db.count("SELECT COUNT(*) FROM product_variants");
        String prefix="verify"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        String user=UUID.randomUUID().toString(), other=UUID.randomUUID().toString();
        db.insert("delivery_locations",Db.fields("id",prefix,"name","Verification fixture","city","Fixture city","postcode","234567"));
        for(String id:new String[]{user,other}) db.insert("users",Db.fields("id",id,"name","Verification","email",id+"@example.invalid","password_hash","unused","role","customer"));
        for(int i=0;i<100;i++){
            String id=prefix+String.format("%03d",i);
            db.insert("products",Db.fields("id",id,"category_id","pantry","name",id,"brand","Verification fixture","variant_name","Standard","description","Temporary verification","color","#123456","art","bag"));
            db.insert("product_variants",Db.fields("id",id,"product_id",id,"pack_label","1 kg","amount_base",1000,"base_unit","g"));
        }
        String url="/api/products/page?locationId="+prefix+"&q="+prefix+"&pageSize=100";
        var exact=json.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(100,exact.path("totalElements").asInt()); assertEquals(1,exact.path("totalPages").asInt()); assertFalse(exact.path("hasNext").asBoolean());
        String extra=prefix+"100";
        db.insert("products",Db.fields("id",extra,"category_id","pantry","name",extra,"brand","Verification fixture","variant_name","Standard","description","Temporary verification","color","#123456","art","bag"));
        db.insert("product_variants",Db.fields("id",extra,"product_id",extra,"pack_label","1 kg","amount_base",1000,"base_unit","g"));
        var first=json.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(101,first.path("totalElements").asInt()); assertEquals(2,first.path("totalPages").asInt());
        assertEquals(100,first.path("items").size()); assertTrue(first.path("hasNext").asBoolean()); assertFalse(first.path("hasPrevious").asBoolean());
        assertEquals(prefix+"000",first.path("items").get(0).path("id").asText());
        var descending=json.readTree(mvc.perform(get(url+"&sort=name_desc")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(prefix+"100",descending.path("items").get(0).path("id").asText());
        var last=json.readTree(mvc.perform(get(url+"&page=1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1,last.path("items").size()); assertTrue(last.path("hasPrevious").asBoolean()); assertFalse(last.path("hasNext").asBoolean());
        String target=prefix+"100";
        mvc.perform(get("/api/products/"+target+"/variants").param("locationId", prefix)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(target));
        String cartLine=null;
        for(String intent:new String[]{"NORMAL","PLANNED_BULK","ESSENTIAL","BULK"}){
            boolean bulk=intent.equals("PLANNED_BULK")||intent.equals("BULK");
            var preferences=bulk?Map.of("intent",intent,"consumptionDays",30,"monitorPrice",false):Map.of("intent",intent,"monitorPrice",false);
            var body=Map.of("productId",target,"locationId",prefix,"quantity",2,"preferences",preferences);
            var saved=mvc.perform(put("/api/me/cart").with(jwt().jwt(j->j.subject(user))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isOk()).andReturn();
            cartLine=json.readTree(saved.getResponse().getContentAsString()).path("id").asText();
            assertEquals(bulk?"BULK":"ESSENTIAL",db.one("SELECT p.intent FROM purchase_preferences p JOIN cart_items i ON i.id=p.cart_item_id JOIN shopping_carts c ON c.id=i.cart_id WHERE c.user_id=? AND i.variant_id=?",user,target).get("intent"));
        }
        mvc.perform(get("/api/me/cart").with(jwt().jwt(j->j.subject(other)))).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(delete("/api/me/cart/{id}", cartLine).with(jwt().jwt(j->j.subject(other)))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/me/cart/{id}", cartLine).with(jwt().jwt(j->j.subject(user)))).andExpect(status().isNoContent());
        assertEquals(0,db.count("SELECT COUNT(*) FROM cart_items WHERE id=?",cartLine));
        assertEquals(0,db.count("SELECT COUNT(*) FROM purchase_preferences WHERE cart_item_id=?",cartLine));
        assertEquals(before+101,db.count("SELECT COUNT(*) FROM product_variants"));
    }
}
