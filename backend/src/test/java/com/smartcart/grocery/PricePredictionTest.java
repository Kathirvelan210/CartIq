package com.smartcart.grocery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.dto.Models.Preferences;
import com.smartcart.repository.Db;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import static com.smartcart.repository.Db.fields;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"smartcart.sample-data=false", "smartcart.jwt-secret=fixture-only-long-local-test-jwt-key-forecasts",
    "spring.datasource.url=jdbc:h2:mem:forecast;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class PricePredictionTest {
    private static final String ADMIN="/api/admin/price-intelligence";
    private static final String SHOP="/api/me/price-intelligence/forecast-pack";
    private static final AtomicInteger requests=new AtomicInteger();
    private static HttpServer ai;
    @DynamicPropertySource static void mock(DynamicPropertyRegistry registry) throws Exception {
        ai=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        ai.createContext("/price-intelligence/experiment",exchange->{
            requests.incrementAndGet();
            try(exchange){
                var mapper=new ObjectMapper();
                var request=mapper.readTree(exchange.getRequestBody());
                if (!"fixture-key".equals(exchange.getRequestHeaders().getFirst("X-Service-Token")) || request.path("points").size()!=65) {
                    exchange.sendResponseHeaders(400,-1); return;
                }
                byte[] body=mapper.writeValueAsString(Map.ofEntries(Map.entry("datasetVersion",request.path("datasetVersion").asText()),
                    Map.entry("status","TRAINED"),Map.entry("intervalEligible",true),Map.entry("estimateMinor",5000),Map.entry("lowMinor",4500),
                    Map.entry("highMinor",5500),Map.entry("trainedAt",Instant.now().toString()),Map.entry("forecastDate",LocalDate.now(ZoneOffset.UTC).toString()),
                    Map.entry("modelVersion","rolling-price-v1-naive"),Map.entry("empiricalTestCoverage",0.9),Map.entry("warning","Synthetic test result")))
                    .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body);
            }
        });
        ai.start();
        registry.add("smartcart.ai-url",()->"http://127.0.0.1:"+ai.getAddress().getPort());
        registry.add("smartcart.ai-secret",()->"fixture-key");
        registry.add("CARTIQ_APPROVED_FORECAST_ENABLED",()->"true");
    }
    @AfterAll static void shutdown(){if(ai!=null)ai.stop(0);}

    @Autowired Db db; @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    final String reviewer=UUID.randomUUID().toString(), decision=UUID.randomUUID().toString(), grant=UUID.randomUUID().toString();
    final Instant now=Instant.now().truncatedTo(ChronoUnit.SECONDS);
    @BeforeEach void fixture() {
        requests.set(0);
        db.insert("users",fields("id",reviewer,"email",reviewer+"@fixture.invalid","password_hash","unusable","name","Synthetic reviewer"));
        db.insert("products",fields("id","forecast-product","category_id","dairy","name","Synthetic milk","brand","Fixture","variant_name","Standard","description","Tests", "art","bag","color","#ffffff"));
        db.insert("product_variants",fields("id","forecast-pack","product_id","forecast-product","pack_label","1 l","amount_base",1000,"base_unit","ml"));
        for (String area:List.of("forecast-area","forecast-other")) db.insert("delivery_locations",fields("id",area,"name","Fixture","city","Fixture","postcode","123456"));
        db.insert("platform_products",fields("id","forecast-listing","platform_id","blinkit","variant_id","forecast-pack","retailer_sku","fixture-sku"));
        db.insert("provider_mapping_decisions",fields("decision_id",decision,"variant_id","forecast-pack","platform_id","blinkit","retailer_sku","fixture-sku",
            "revision_no",1,"disposition","APPROVED","reviewed_by",reviewer,"source_provider","SYNTHETIC_TEST_ONLY","source_record_id","test-mapping",
            "evidence_reference","synthetic test","evidence_sha256","a".repeat(64),"permission_reference","synthetic test","rationale","Fixture",
            "listing_pack","1 l","canonical_pack","1 l","canonical_gtins","[]","canonical_brand","Fixture","canonical_title","Synthetic milk",
            "canonical_variant","Standard","blocking_reasons","[]"));
        db.insert("price_retention_grants",fields("id",grant,"listing_id","forecast-listing","location_id","forecast-area","mapping_decision_id",decision,
            "provider","SYNTHETIC_TEST_ONLY","permission_reference","test fixture grant","evidence_reference","fixture","evidence_sha256","b".repeat(64),
            "first_observation_at",now.minus(90,ChronoUnit.DAYS),"last_observation_at",now,"retain_until",now.plus(365,ChronoUnit.DAYS),
            "history_allowed",true,"dataset_allowed",true,"approved_by",reviewer,"approved_at",now));
    }

    @Test void endToEndPermissionsModelAndRevocation() throws Exception {
        for(int i=65;i>=1;i--) observe(i,4200L);
        experiment().andExpect(jsonPath("$.evaluation.status").value("INSUFFICIENT_DATA"));
        assertEquals(0,requests.get());
        approve(false);
        experiment().andExpect(jsonPath("$.evaluation.status").value("TRAINED"));
        scoped("blinkit","forecast-area").andExpect(jsonPath("$.status").value("INSUFFICIENT_DATA"));
        db.run("DELETE FROM price_forecast_permissions WHERE grant_id=?",grant);
        approve(true);
        mvc.perform(get(ADMIN+"/coverage").with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.trainingAllowed").value(65));
        experiment().andExpect(jsonPath("$.evaluation.status").value("TRAINED"));
        scoped("blinkit","forecast-area").andExpect(jsonPath("$.status").value("FORECAST")).andExpect(jsonPath("$.predictedMinor").value(5000));
        scoped("blinkit","forecast-other").andExpect(jsonPath("$.latestObservation").doesNotExist());
        scoped("zepto","forecast-area").andExpect(jsonPath("$.status").value("INSUFFICIENT_DATA"));
        mvc.perform(delete(ADMIN+"/forecast-permissions/"+grant).with(admin())).andExpect(status().isNoContent());
        scoped("blinkit","forecast-area").andExpect(jsonPath("$.predictedMinor").doesNotExist());
        assertEquals(2,requests.get());
    }

    @Test void coverageChecksAuthenticationAndMissingValues() throws Exception {
        observe(1,null); observe(2,4500L);
        mvc.perform(get(SHOP).param("retailer","blinkit").param("locationId","forecast-area")).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN+"/coverage").with(jwt())).andExpect(status().isForbidden());
        mvc.perform(get(ADMIN+"/coverage").with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.products").value(1))
            .andExpect(jsonPath("$.missingPrice").value(1)).andExpect(jsonPath("$.trainingAllowed").value(0));
        scoped("blinkit","forecast-area").andExpect(jsonPath("$.latestObservation.priceMinor").doesNotExist());
    }

    @Test void historicalPreviewAndCommitPreserveAsOfEvidenceAndAudit() throws Exception {
        String api="/api/admin/price-acquisition", route=api+"/batches";
        Instant at=now.truncatedTo(ChronoUnit.DAYS).minus(4,ChronoUnit.DAYS).plusSeconds(10000);
        var line=new LinkedHashMap<String,Object>(Map.ofEntries(Map.entry("dataClass","GENUINE"),Map.entry("sourceRecordId","0001"),
            Map.entry("sourceEvidence","Synthetic test reference"),Map.entry("observedAt",at.toString()),Map.entry("variantId","forecast-pack"),
            Map.entry("retailer","blinkit"),Map.entry("locationId","forecast-area"),Map.entry("retailerSku","fixture-sku"),
            Map.entry("pack","1000 ml"),Map.entry("currency","INR"),Map.entry("priceMinor",4200),Map.entry("stock","AVAILABLE"),
            Map.entry("sourceAvailableAt",at.plusSeconds(30).toString()),Map.entry("sourceAsOfEvidence","Synthetic point-in-time fixture")));
        var simulated=new LinkedHashMap<>(line); simulated.put("dataClass","SIMULATED");
        var invalid=new LinkedHashMap<>(line); invalid.put("locationId","forecast-other");
        var input=new MockMultipartFile("file","fixture.json","application/json",json.writeValueAsBytes(Map.of("rows",List.of(line,simulated,invalid,line))));
        mvc.perform(multipart(route).file(input).with(admin()).param("grantId",grant).param("format","JSON"))
            .andExpect(status().isForbidden());
        var approval=new HistoricalPriceIntake.Authorization("Synthetic authorized export","Synthetic agreement","d".repeat(64),"Fixture only",
            "Fixture revocation",true,true,true,true,false,true,false,true);
        mvc.perform(put(api+"/sources/"+grant).with(admin()).contentType("application/json").content(json.writeValueAsBytes(approval)))
            .andExpect(status().isNoContent());
        mvc.perform(multipart(route).file(input).with(admin()).param("grantId",grant).param("format","JSON").param("commit","true"))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart(route).file(input).with(admin()).param("grantId",grant).param("format","JSON").param("evidenceReference","private file"))
            .andExpect(status().isBadRequest());
        mvc.perform(multipart(route).file(input).with(admin()).param("grantId",grant).param("format","JSON"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].disposition").value("VALID"))
            .andExpect(jsonPath("$.rows[1].disposition").value("SIMULATED")).andExpect(jsonPath("$.rows[2].disposition").value("QUARANTINED"))
            .andExpect(jsonPath("$.rows[3].disposition").value("DUPLICATE"));
        assertEquals(0,db.count("SELECT COUNT(*) FROM approved_price_observations WHERE grant_id=?",grant));
        String original=null;
        for(int i=0;i<2;i++){
            var result=mvc.perform(multipart(route).file(input).with(admin()).param("grantId",grant).param("format","JSON")
                .param("commit","true").param("reviewed","true")).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].disposition").value("IMPORTED")).andExpect(jsonPath("$.rows[3].disposition").value("DUPLICATE")).andReturn();
            String id=json.readTree(result.getResponse().getContentAsByteArray()).path("batchId").asText();
            if(i==0)original=id;else assertEquals(original,id);
        }
        assertEquals(1,db.count("SELECT COUNT(*) FROM approved_price_observations WHERE grant_id=?",grant));
        mvc.perform(get(route).with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$[0].decided").value(4));
        mvc.perform(get(route+"/"+original).with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        approve(false);
        mvc.perform(get(api+"/coverage/series").with(admin()).param("variantId","forecast-pack").param("retailer","blinkit").param("locationId","forecast-area"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.eligibleDays").value(1)).andExpect(jsonPath("$.missingDates.length()").value(3));
        mvc.perform(get(api+"/coverage").with(jwt())).andExpect(status().isForbidden());
        mvc.perform(get(api+"/coverage").with(admin())).andExpect(status().isOk()).andExpect(jsonPath("$.coverage.observations").value(1));
        mvc.perform(delete(api+"/sources/"+grant).with(admin())).andExpect(status().isNoContent());
        assertEquals(0,db.count("SELECT COUNT(*) FROM approved_price_observations WHERE grant_id=?",grant));
    }

    @Test void csvParserPreservesQuotedEvidenceAndRejectsUnknownColumnsAndDuplicateJsonKeys() {
        String header="dataClass,sourceRecordId,sourceEvidence,observedAt,variantId,retailer,locationId,retailerSku,pack,currency,priceMinor,stock";
        String line="GENUINE,0001,\"proof, \"\"signed\"\"\",2026-01-01T00:00:00Z,p,blinkit,loc,0012,1 kg,INR,100,AVAILABLE";
        var rows=IntakeFile.parse(json,(header+"\r\n"+line+"\r\n").getBytes(StandardCharsets.UTF_8),"CSV");
        assertTrue(IntakeFile.rowShape(rows.get(0)));
        assertEquals("0001",rows.get(0).path("sourceRecordId").asText());
        assertEquals("proof, \"signed\"",rows.get(0).path("sourceEvidence").asText());
        assertThrows(com.smartcart.exception.ApiException.class,()->IntakeFile.parse(json,"[{\"id\":1,\"id\":2}]".getBytes(StandardCharsets.UTF_8),"JSON"));
        assertThrows(com.smartcart.exception.ApiException.class,()->IntakeFile.parse(json,(header+",bad\n"+line).getBytes(StandardCharsets.UTF_8),"CSV"));
    }

    @Test void uncertaintyNeverDelaysEssentials() {
        var current=new PricePrediction.Reading(now,6500L,"AVAILABLE");
        var essential=new Preferences("ESSENTIAL",null,null,null,null,null,null,false);
        var nonEssential=new Preferences("NON_ESSENTIAL",null,null,null,null,null,null,false);
        assertEquals("NO_TIMING_SIGNAL",PricePrediction.advice(current,"FRESH",4000L,5000L,200L,essential).action());
        assertEquals("CONSIDER_WAITING",PricePrediction.advice(current,"FRESH",4000L,5000L,200L,nonEssential).action());
        assertEquals("CHECK_AVAILABILITY",PricePrediction.advice(current,"STALE",4000L,5000L,200L,nonEssential).action());
    }

    private void observe(int days, Long price) {
        Instant observed=now.truncatedTo(ChronoUnit.DAYS).minus(days,ChronoUnit.DAYS).plusSeconds(1000);
        db.insert("approved_price_observations",fields("id",UUID.randomUUID().toString(),"grant_id",grant,"listing_id","forecast-listing",
            "location_id","forecast-area","variant_id","forecast-pack","retailer_sku","fixture-sku","observed_at",observed,"recorded_at",observed.plusSeconds(10),
            "source_provider","SYNTHETIC_TEST_ONLY","source_record_id","fixture-"+days,"source_evidence","fixture","price_minor",price,
            "stock_status","AVAILABLE","pack_label","1 l","pack_count",1,"each_amount",1000,"base_unit","ml"));
    }
    private void approve(boolean serve) throws Exception {
        var approval=new PricePrediction.Approval("Synthetic test training rights","c".repeat(64),now.plus(7,ChronoUnit.DAYS),true,serve);
        mvc.perform(post(ADMIN+"/forecast-permissions/"+grant).with(admin()).contentType("application/json")
            .content(json.writeValueAsBytes(approval))).andExpect(status().isNoContent());
    }
    private org.springframework.test.web.servlet.ResultActions experiment() throws Exception {
        return mvc.perform(post(ADMIN+"/experiment").with(admin()).param("variantId","forecast-pack").param("retailer","blinkit")
            .param("locationId","forecast-area")).andExpect(status().isOk());
    }
    private org.springframework.test.web.servlet.ResultActions scoped(String retailer,String location) throws Exception {
        return mvc.perform(get(SHOP).with(jwt().jwt(j->j.subject(reviewer))).param("retailer",retailer).param("locationId",location)).andExpect(status().isOk());
    }
    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor admin() {
        return jwt().jwt(j->j.subject(reviewer)).authorities(new SimpleGrantedAuthority("SCOPE_admin"));
    }
}
