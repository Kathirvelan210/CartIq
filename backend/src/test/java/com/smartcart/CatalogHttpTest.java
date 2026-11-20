package com.smartcart;

import com.smartcart.config.ReferenceData;
import com.smartcart.config.StarterCatalogue;
import com.smartcart.controller.CatalogController;
import com.smartcart.exception.ApiErrors;
import com.smartcart.repository.Db;
import com.smartcart.service.CatalogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class CatalogHttpTest {
    private final DriverManagerDataSource source = new DriverManagerDataSource(
        "jdbc:h2:mem:cataloghttp" + java.util.UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    private final Db db = new Db(new JdbcTemplate(source));
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc http;

    @BeforeEach void prepare() throws Exception {
        org.flywaydb.core.Flyway.configure().dataSource(source).load().migrate();
        new ReferenceData(db, false).run(new DefaultApplicationArguments());
        new StarterCatalogue(db, json).run(new DefaultApplicationArguments());
        http = MockMvcBuilders.standaloneSetup(new CatalogController(new CatalogService(db, false))).setControllerAdvice(new ApiErrors()).build();
    }

    private JsonNode page(String parameters) throws Exception {
        var response = http.perform(get("/api/products/page?locationId=blr-indiranagar" + parameters)).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        return json.readTree(response.getContentAsString());
    }

    @Test void paginationMetadataAndHundredRecordBoundaries() throws Exception {
        var initial = page("");
        assertEquals(27, initial.path("totalElements").asInt());
        assertEquals(2, initial.path("totalPages").asInt());
        assertEquals(24, initial.path("items").size());
        assertTrue(initial.path("hasNext").asBoolean());
        assertEquals(3, page("&page=1").path("items").size());
        assertFalse(page("&page=1").path("hasNext").asBoolean());
        for (int index = 0; index < 73; index++) addProduct(index);
        var exact = page("&pageSize=100");
        assertEquals(100, exact.path("totalElements").asInt());
        assertEquals(1, exact.path("totalPages").asInt());
        assertFalse(exact.path("hasNext").asBoolean());
        addProduct(73);
        var first = page("&pageSize=100");
        assertEquals(101, first.path("totalElements").asInt());
        assertEquals(2, first.path("totalPages").asInt());
        assertTrue(first.path("hasNext").asBoolean());
        var last = page("&page=1&pageSize=100");
        assertEquals(1, last.path("items").size());
        assertTrue(last.path("hasPrevious").asBoolean());
        assertFalse(last.path("hasNext").asBoolean());
        assertEquals(0, page("&page=2&pageSize=100").path("items").size());
    }

    @Test void variantsAreFoundBeyondFirstHundredProducts() throws Exception {
        for (int index = 0; index < 110; index++) addProduct(index);
        var response = http.perform(get("/api/products/p109/variants?locationId=blr-indiranagar")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("p109", json.readTree(response.getContentAsString()).get(0).path("canonicalProductId").asText());
        assertEquals(404, http.perform(get("/api/products/missing/variants?locationId=blr-indiranagar")).andReturn().getResponse().getStatus());
    }

    @Test void pageValidationAndSortingAreStable() throws Exception {
        var empty=page("&q=does-not-exist");
        assertEquals(0,empty.path("totalElements").asInt());
        assertEquals(0,empty.path("totalPages").asInt());
        assertFalse(empty.path("hasNext").asBoolean());
        assertFalse(empty.path("hasPrevious").asBoolean());
        var middle=page("&page=1&pageSize=10");
        assertEquals(27,middle.path("totalElements").asInt());
        assertEquals(3,middle.path("totalPages").asInt());
        assertTrue(middle.path("hasNext").asBoolean());
        assertTrue(middle.path("hasPrevious").asBoolean());
        assertEquals(middle,page("&page=1&pageSize=10"));
        var ascending=page("&pageSize=100&sort=name_asc").path("items");
        var descending=page("&pageSize=100&sort=name_desc").path("items");
        assertEquals(ascending.get(0).path("name"),descending.get(descending.size()-1).path("name"));
        for(String args : new String[]{"&page=-1","&pageSize=0","&pageSize=101","&sort=invalid"})
            assertEquals(400,http.perform(get("/api/products/page?locationId=blr-indiranagar"+args)).andReturn().getResponse().getStatus());
    }

    @Test void brandCategoryAndEquivalentPackFiltersApplyBeforePaging() throws Exception {
        var request=get("/api/products/page").param("locationId","blr-indiranagar")
            .param("q","taaza").param("category","dairy").param("brand","  aMuL  ")
            .param("pack","1000 ml").param("pageSize","1");
        var response=http.perform(request).andReturn().getResponse();
        assertEquals(200,response.getStatus());
        var result=json.readTree(response.getContentAsString());
        assertEquals(1,result.path("totalElements").asInt());
        assertEquals("milk",result.path("items").get(0).path("id").asText());
        assertFalse(result.path("hasNext").asBoolean());
        assertEquals(0,page("&q=taaza&brand=Amu").path("totalElements").asInt());
        var wrongUnit=http.perform(get("/api/products/page").param("locationId","blr-indiranagar")
            .param("q","taaza").param("pack","1 kg")).andReturn().getResponse();
        assertEquals(200,wrongUnit.getStatus());
        assertEquals(0,json.readTree(wrongUnit.getContentAsString()).path("totalElements").asInt());
        assertEquals(400,http.perform(get("/api/products/page")
            .param("locationId","blr-indiranagar").param("pack","2 cartons")).andReturn().getResponse().getStatus());
    }

    @Test void barcodeLookupReturnsOnlyPublishedVariants() throws Exception {
        db.insert("variant_barcodes",Db.fields("gtin","03017620020117","variant_id","milk"));
        var published=http.perform(get("/api/products/lookup/barcode")
            .param("locationId","blr-indiranagar").param("gtin","3017620020117")).andReturn().getResponse();
        assertEquals(200,published.getStatus());
        assertEquals("milk",json.readTree(published.getContentAsString()).path("id").asText());
        assertEquals(400,barcodeStatus("3017620020118"));
        assertEquals(404,barcodeStatus("4006381333931"));
        db.run("UPDATE products SET active=FALSE WHERE id='milk'");
        assertEquals(404,barcodeStatus("03017620020117"));
    }

    @Test void barcodeLookupExplainsUnavailableRegistryAtV7() throws Exception {
        var legacySource=new DriverManagerDataSource(
            "jdbc:h2:mem:catalogv7"+java.util.UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        org.flywaydb.core.Flyway.configure().dataSource(legacySource).target("7").load().migrate();
        var legacyDb=new Db(new JdbcTemplate(legacySource));
        new ReferenceData(legacyDb,false).run(new DefaultApplicationArguments());
        new StarterCatalogue(legacyDb,json).run(new DefaultApplicationArguments());
        http=MockMvcBuilders.standaloneSetup(new CatalogController(new CatalogService(legacyDb,false)))
            .setControllerAdvice(new ApiErrors()).build();
        assertEquals(503,barcodeStatus("03017620020117"));
    }

    private int barcodeStatus(String gtin) throws Exception {
        return http.perform(get("/api/products/lookup/barcode").param("locationId","blr-indiranagar")
            .param("gtin",gtin)).andReturn().getResponse().getStatus();
    }

    private void addProduct(int index) {
        String id = "p" + index;
        if (db.count("SELECT COUNT(*) FROM products WHERE id=?", id) > 0) return;
        db.insert("products", Db.fields("id", id, "category_id", "pantry", "name", "Test product " + index,
            "brand", "Fixture", "variant_name", "Standard", "description", "HTTP test fixture", "color", "#123456", "art", "bag"));
        db.insert("product_variants", Db.fields("id", id, "product_id", id, "pack_label", "1 kg", "amount_base", 1000, "base_unit", "g"));
    }
}
