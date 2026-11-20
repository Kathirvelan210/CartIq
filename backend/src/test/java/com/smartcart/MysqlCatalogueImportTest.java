package com.smartcart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.repository.Db;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.smartcart.repository.Db.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "server.address=127.0.0.1", "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=validate",
    "smartcart.sample-data=false", "smartcart.import-starter-catalogue=false",
    "smartcart.catalogue-import.off-enabled=false",
    "smartcart.jwt-secret=isolated-catalogue-test-key-for-jwt-only"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "CARTIQ_CATALOGUE_MYSQL_VERIFY", matches = "true")
class MysqlCatalogueImportTest {
    private static final String SCRATCH_URL =
        "jdbc:mysql://127.0.0.1:13316/cartiq_catalogue_verify_retry?connectionTimeZone=UTC";
    private static final String IMPORTS = "/api/admin/catalogue-imports";
    private static final String BATCHES = IMPORTS + "/batches";

    @DynamicPropertySource
    static void scratchMysql(DynamicPropertyRegistry properties) {
        // Hardcode the disposable address and schema so a generic DB_URL cannot select shared MySQL.
        properties.add("spring.datasource.url", () -> SCRATCH_URL);
        properties.add("spring.datasource.username", () -> required("CARTIQ_CATALOGUE_MYSQL_USERNAME"));
        properties.add("spring.datasource.password", () -> required("CARTIQ_CATALOGUE_MYSQL_PASSWORD"));
        properties.add("spring.flyway.enabled", () -> false);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("BOOTSTRAP_ADMIN_EMAIL", () -> "");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set " + name + " for disposable MySQL verification.");
        return value;
    }

    @LocalServerPort int port;
    @Autowired DataSource dataSource;
    @Autowired Db db;
    @Autowired ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Test
    void appliedMigrationHistoryAndConstraintsValidate() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("cartiq_catalogue_verify_retry", connection.getCatalog());
            assertEquals(8, connection.getMetaData().getDatabaseMajorVersion());
            assertEquals(SCRATCH_URL, connection.getMetaData().getURL());
        }
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("10").load().validate();
        var applied = db.rows("SELECT version FROM flyway_schema_history WHERE type='SQL' AND success=1 ORDER BY installed_rank")
            .stream().map(row -> text(row, "version")).toList();
        assertEquals(List.of("1","2","3","4","5","6","7","8","9","10"), applied);
        assertEquals(3, db.count("""
            SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
            AND table_name='catalogue_import_locks' AND column_name IN ('owner_token','job_id','lease_until')
            """));
        assertEquals(2, db.count("""
            SELECT COUNT(DISTINCT table_name) FROM information_schema.referential_constraints
            WHERE constraint_schema=DATABASE()
            AND table_name IN ('catalogue_import_attempts','catalogue_candidate_corrections')
            """));
        assertEquals(1, db.count("""
            SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
            AND table_name='catalogue_import_batches' AND column_name='snapshot_sha256' AND non_unique=0
            """));
        var orphan = assertThrows(DataIntegrityViolationException.class, () -> db.run(
            "INSERT INTO catalogue_batch_rows(batch_id,`row_number`,raw_record,outcome) VALUES (?,1,?,'INVALID')",
            id(), "unlinked fixture"));
        assertEquals(1452,((java.sql.SQLException)orphan.getMostSpecificCause()).getErrorCode());
    }

    @Test
    void batchCheckpointAndReviewedPublicationWorkOverHttp() throws Exception {
        long prices = db.count("SELECT COUNT(*) FROM location_prices");
        long mappings = db.count("SELECT COUNT(*) FROM platform_products");
        String suffix = id().substring(0, 8);
        String name = "MySQL fixture rice " + suffix;
        String brand = "Test fixture " + suffix;
        String location = "fixture-" + suffix;
        String barcode = fixtureBarcode();
        String normalized = "0" + barcode;
        String valid = json.writeValueAsString(Map.of(
            "code", barcode, "product_name", name, "brands", brand, "quantity", "500 g",
            "ingredients_text", "Rice", "categories_tags", List.of("en:rices")));
        String needsCorrection = valid.replace("500 g", "unknown pack");
        String digest = id().replace("-", "") + id().replace("-", "");
        db.insert("delivery_locations",fields("id",location,"name","Test location","city","Test city","postcode","560038"));

        String email = suffix + "@example.invalid";
        var credentials = Map.of("email",email,"password", "Disposable fixture password");
        var registered = call("POST","/api/auth/register",null,Map.of(
            "email",email,"password",credentials.get("password"),"name","Catalogue test"),201);
        String user = registered.path("user").path("id").asText();
        assertFalse(user.isEmpty());
        call("POST",BATCHES,null,Map.of("sha256",digest,"rows",4),401);
        call("POST",BATCHES,registered.path("token").asText(),Map.of("sha256",digest,"rows",4),403);
        db.run("UPDATE users SET role='admin' WHERE id=?", user);
        String token = call("POST","/api/auth/login",null,credentials,200).path("token").asText();
        assertFalse(token.isEmpty());

        String source = "https://in.openfoodfacts.org/api/v2/search?countries_tags=en:india";
        var snapshot = Map.of("sha256",digest,"rows",4,"sourceUrl",source);
        call("POST",BATCHES,token,Map.of("sha256",digest,"rows",4,"sourceUrl","https://example.invalid/api/v2/search?q=rice"),400);
        var batch = call("POST",BATCHES,token,snapshot,200);
        assertEquals(source,batch.path("snapshot_url").asText());
        String batchId = batch.path("id").asText();
        String batchPath = BATCHES + "/" + batchId;
        String rows = batchPath + "/rows";
        assertEquals(1, call("PUT", rows, token, Map.of("offset",0,"records",List.of(valid)),200).path("committed_rows").asInt());
        call("POST",BATCHES,token,Map.of("sha256",digest,"rows",4),400);
        var resumed = call("POST",BATCHES,token,snapshot,200);
        assertEquals(batchId,resumed.path("id").asText());
        assertEquals(1,resumed.path("committed_rows").asInt());

        var chunk = request("PUT",rows,token,Map.of("offset",1,"records",List.of("invalid JSON",needsCorrection)));
        var first = http.sendAsync(chunk, HttpResponse.BodyHandlers.ofString());
        var concurrent = http.sendAsync(chunk,HttpResponse.BodyHandlers.ofString());
        assertEquals(List.of(200,409),java.util.stream.IntStream.of(
            first.get(30,TimeUnit.SECONDS).statusCode(),concurrent.get(30,TimeUnit.SECONDS).statusCode()).sorted().boxed().toList());
        assertEquals(3, call("GET",batchPath,token,null,200).path("committed_rows").asInt());
        var complete = call("PUT",rows,token,Map.of("offset",3,"records",List.of(valid)),200);
        assertEquals(4,complete.path("committed_rows").asInt());
        assertEquals(2,complete.path("staged_rows").asInt());
        assertEquals(1,complete.path("invalid_rows").asInt());
        assertEquals(1,complete.path("correction_rows").asInt());
        assertEquals(2,call("GET",batchPath+"/issues",token,null,200).size());
        assertEquals(valid,text(db.one("SELECT raw_record FROM catalogue_batch_rows WHERE batch_id=? AND `row_number`=1",batchId),"raw_record"));

        String search = "/api/products/page?locationId=" + location + "&q=" + suffix + "&pageSize=1";
        assertEquals(0,call("GET",search,null,null,200).path("totalElements").asInt());
        var candidates = call("GET",IMPORTS+"/review?batchId="+batchId,token,null,200);
        assertEquals(3,candidates.size());
        String selected = IMPORTS+"/candidates/"+candidates.get(0).path("id").asText();
        String correction = IMPORTS+"/candidates/"+candidates.get(1).path("id").asText();
        String duplicate = IMPORTS+"/candidates/"+candidates.get(2).path("id").asText();
        assertEquals("pantry",candidates.get(0).path("suggestedCategory").asText());
        String correctionId = candidates.get(1).path("id").asText();
        call("PUT",correction,token,Map.of("name",name,"brand",brand,"quantity","500 g","reason","Test fixture source review"),200);
        assertEquals(1,db.count("SELECT COUNT(*) FROM catalogue_candidate_corrections WHERE candidate_id=?",correctionId));
        assertEquals(needsCorrection,text(db.one("SELECT payload_json FROM catalogue_import_candidates WHERE id=?",correctionId),"payload_json"));

        var approval = Map.of("categoryId","pantry","variantName","Plain");
        var accepted = call("POST",selected+"/approve",token,approval,200);
        String variant = accepted.path("variantId").asText();
        assertEquals("IMPORTED",accepted.path("status").asText());
        String variantEndpoint = "/api/products/" + variant;
        String locationQuery = "?locationId=" + location;
        String barcodeLookup = "/api/products/lookup/barcode"+locationQuery+"&gtin="+barcode;
        call("GET",variantEndpoint+locationQuery,null,null,404);
        call("GET",barcodeLookup,null,null,404);
        assertEquals("DUPLICATE",call("POST",duplicate+"/approve",token,approval,200).path("status").asText());
        assertEquals(1,db.count("SELECT COUNT(*) FROM variant_barcodes WHERE gtin=?",normalized));
        var repeatedBarcode = assertThrows(DataIntegrityViolationException.class, () -> db.insert("variant_barcodes",
            fields("gtin",normalized,"variant_id",variant)));
        assertEquals(1062,((java.sql.SQLException)repeatedBarcode.getMostSpecificCause()).getErrorCode());

        assertEquals("PUBLISHED",call("POST",selected+"/publish",token,null,200).path("status").asText());
        assertEquals(variant,call("POST",selected+"/publish",token,null,200).path("variantId").asText());
        String filtered = search+"&category=pantry&brand="+URLEncoder.encode(brand, StandardCharsets.UTF_8)+"&pack=0.5+kg";
        assertEquals(variant,call("GET",filtered,null,null,200).path("items").get(0).path("id").asText());
        assertEquals(0,call("GET",search+"&pack=500+ml",null,null,200).path("totalElements").asInt());
        assertEquals(variant,call("GET",barcodeLookup,null,null,200).path("id").asText());
        assertEquals(0,call("GET",search+"&page=1",null,null,200).path("items").size());
        assertEquals(variant,call("GET",variantEndpoint+"/variants"+locationQuery,null,null,200).get(0).path("id").asText());
        var metadata = call("GET",variantEndpoint+"/metadata"+locationQuery,null,null,200);
        assertEquals(normalized,metadata.path("gtins").get(0).asText());
        assertEquals("Rice",metadata.path("sources").get(0).path("ingredients").asText());
        assertEquals(valid,text(db.one("SELECT payload_json FROM external_product_sources WHERE variant_id=?",variant),"payload_json"));
        assertEquals(0,call("GET",variantEndpoint+locationQuery,null,null,200).path("offers").size());
        assertEquals(prices,db.count("SELECT COUNT(*) FROM location_prices"));
        assertEquals(mappings,db.count("SELECT COUNT(*) FROM platform_products"));
    }

    private HttpRequest request(String method,String path,String token,Map<String,?> body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15));
        if (token != null) request.header("Authorization","Bearer " + token);
        if (body != null) request.header("Content-Type","application/json");
        return request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
    }

    private JsonNode call(String method,String path,String token,Map<String,?> body,int status) throws Exception {
        var response = http.send(request(method,path,token,body),HttpResponse.BodyHandlers.ofString());
        assertEquals(status,response.statusCode(),method+" "+path);
        return response.body().isBlank() ? json.nullNode() : json.readTree(response.body());
    }

    private String fixtureBarcode() {
        // A restricted-circulation 13-digit code for this disposable fixture only.
        String prefix = String.format(Locale.ROOT,"29%010d",ThreadLocalRandom.current().nextLong(10_000_000_000L));
        int total = 0;
        for (int pos=0; pos<prefix.length(); pos++) total += (prefix.charAt(pos)-'0')*(pos%2==0?1:3);
        return prefix+(10-total%10)%10;
    }
}
