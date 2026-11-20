package com.smartcart.integration;

import com.smartcart.catalogimport.ProductMatching;
import com.smartcart.catalogimport.ProductMatching.Identity;
import com.smartcart.controller.QuickCommerceController;
import com.smartcart.exception.ApiException;
import com.smartcart.exception.ApiErrors;
import com.smartcart.service.ComparisonService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(OutputCaptureExtension.class)
class QuickCommerceProviderTest {
    @TempDir Path directory;
    private int ledgerNumber;
    private static final String KEY = "test-header-credential";
    private final RestClient.Builder client = RestClient.builder().baseUrl("https://example.invalid");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();

    @Test void accountBalanceReadsTheProviderWithoutCreatingAReservation() throws Exception {
        var state = directory.resolve("account-preflight");
        var provider = provider(state, 96);
        var before = Files.readString(state);
        server.expect(request -> assertEquals("/v1/credits", request.getURI().getPath()))
            .andExpect(header("X-API-Key", KEY))
            .andRespond(withSuccess("{\"summary\":{\"total_available\":96}}", MediaType.APPLICATION_JSON)
                .header("X-Credits-Remaining", "96"));
        assertEquals(new QuickCommerceProvider.CreditBalance(96, 200), provider.creditBalance());
        assertEquals(before, Files.readString(state));
        server.verify();
    }

    @Test void preservesOptionalIdentityEvidenceWithoutTreatingListingIdsAsBarcodes() {
        var provider = provider(97);
        expect("/v1/search", "q", "milk", "BlinkIt", "12.9", "77.66", "560034", 96, """
            "products":[
            {"id":"8901234567890","name":"Fixture Milk","gtin":"08901234567890","barcode":8901234567890,
             "flavour":"Plain","formulation":"Full cream","category":"Dairy"},
            {"id":"8901234567890-alt","name":"Fixture with missing identity data","barcode":null,"flavour":false}]
            """);
        var rows=provider.search("blinkit","milk",12.9,77.66,"560034").products();
        assertEquals("08901234567890",rows.get(0).gtin());
        assertEquals("8901234567890",rows.get(0).barcode());
        assertEquals("Full cream",rows.get(0).formulation());
        assertEquals("Dairy",rows.get(0).category());
        assertNull(rows.get(1).gtin());
        assertNull(rows.get(1).barcode());
        assertNull(rows.get(1).flavour());
        server.verify();
    }

    @Test void continuesOnlyAfterTheFailedPlatformEnvelopeProvesThereWasNoCharge() throws Exception {
        var state = directory.resolve("failed-platform-envelope");
        var provider = provider(state, 97);
        var comparison = new ComparisonService(null, null, null, Optional.of(provider));
        String failed = """
            {"status":"success","credits_remaining":97,"request_id":"11111111-1111-4111-8111-111111111111",
             "data":{"platform":"BlinkIt","lat":12.9,"lon":77.66,"query":"dal",
             "products":[],"total_results":0,"failed_platforms":["BlinkIt"]}}
            """;
        server.expect(queryParam("platform", "BlinkIt"))
            .andRespond(withSuccess(failed, MediaType.APPLICATION_JSON).header("X-Credits-Remaining", "97"));
        expect("/v1/search", "q", "dal", "Zepto", "12.9", "77.66", "560034", 96, "\"products\":[]");
        var result = comparison.compareRetailerSearch("dal", 12.9, 77.66, "560034", List.of("blinkit", "zepto"));
        assertFalse(result.paused());
        assertEquals(List.of(QuickCommerceProvider.Outcome.PROVIDER_ERROR, QuickCommerceProvider.Outcome.NO_RESULTS),
            result.retailers().stream().map(ComparisonService.RetailerCandidates::outcome).toList());
        assertEquals(List.of("96", "settled"), Files.readAllLines(state).subList(1, 3));
        assertEquals(6, Files.readAllLines(state).size());
        assertEquals("11111111-1111-4111-8111-111111111111",result.retailers().get(0).providerRequestId());
        assertEquals(200,result.retailers().get(0).upstreamStatus());
        assertTrue(Files.readAllLines(state).stream().anyMatch(line->line.endsWith("11111111-1111-4111-8111-111111111111\tprovider-no-charge")));
        assertThrows(QuickCommerceProvider.DuplicateLookup.class, () -> provider.search("blinkit", "dal", 12.9, 77.66, "560034"));
        server.verify();
    }

    @Test void swiggyResponseAliasKeepsExactRequestedPlatformAndPersistsTheChargedRequestId() throws Exception {
        var state=directory.resolve("swiggy-alias");
        var provider=provider(state,97);
        String body="""
            {"status":"success","credits_remaining":96,"request_id":"22222222-2222-4222-8222-222222222222",
            "data":{"platform":"Swiggy Instamart","lat":12.9,"lon":77.66,"query":"rice",
             "products":[{"id":23,"name":"Fixture rice","platform":{"name":"Swiggy Instamart","storeId":12}}]}}
            """;
        server.expect(queryParam("platform","Swiggy"))
            .andRespond(withSuccess(body,MediaType.APPLICATION_JSON).header("X-Credits-Remaining","96"));
        var result=provider.search("instamart","rice",12.9,77.66,null);
        assertEquals("23",result.products().get(0).nativeId());
        assertEquals("12",result.products().get(0).storeId());
        assertNull(result.products().get(0).priceMinor());
        assertTrue(Files.readAllLines(state).stream().anyMatch(line->line.endsWith("22222222-2222-4222-8222-222222222222\tprovider-charged")));
        assertThrows(QuickCommerceProvider.DuplicateLookup.class,()->provider(state,96).search("instamart","rice",12.9,77.66,null));
        server.verify();
    }

    @Test void leavesAFailedPlatformEnvelopePendingWithoutMatchingHeader() throws Exception {
        var state = directory.resolve("failed-platform-without-header");
        var provider = provider(state, 97);
        String failed = """
            {"status":"success","credits_remaining":97,
             "data":{"platform":"BlinkIt","lat":12.9,"lon":77.66,"products":[],"failed_platforms":["BlinkIt"]}}
            """;
        server.expect(queryParam("platform", "BlinkIt")).andRespond(withSuccess(failed, MediaType.APPLICATION_JSON));
        var error = assertThrows(ApiException.class, () -> provider.search("blinkit", "dal", 12.9, 77.66, null));
        assertEquals(502, error.status.value());
        assertEquals("pending", Files.readAllLines(state).get(2));
        unavailable(provider);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\"credits_remaining\":null,", "\"credits_remaining\":\"96\","})
    void usesTheDocumentedChargeHeaderForAbsentOrEquivalentBodyEvidence(String fields) throws Exception {
        var state = directory.resolve("charge-header");
        var provider = provider(state, 97);
        String body = """
            {%s "status":"success","data":{"platform":"BlinkIt","lat":12.9,"lon":77.66,"products":[]}}
            """.formatted(fields);
        server.expect(queryParam("platform", "BlinkIt"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON).header("X-Credits-Remaining", "96"));
        assertEquals(96, provider.search("blinkit", "fixture", 12.9, 77.66, null).creditsRemaining());
        assertEquals(List.of("96", "settled"), Files.readAllLines(state).subList(1, 3));
        server.verify();
    }

    @Test void preservesAnUncertainChargeAndLogsOnlySanitizedBillingEvidence(CapturedOutput output) throws Exception {
        var state = directory.resolve("conflicting-charge");
        var provider = provider(state, 97);
        String id = "11111111-1111-4111-8111-111111111111";
        String body = """
            {"status":"success","request_id":"%s","credits_remaining":"private-test-marker",
             "data":{"platform":"BlinkIt","lat":12.9,"lon":77.66,"products":[]}}
            """.formatted(id);
        server.expect(queryParam("platform", "BlinkIt"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON).header("X-Credits-Remaining", "96"));
        var error = assertThrows(ApiException.class, () -> provider.search("blinkit", "fixture", 12.9, 77.66, null));
        assertEquals(502, error.status.value());
        assertEquals("pending", Files.readAllLines(state).get(2));
        assertTrue(output.toString().contains("requestId=" + id));
        assertTrue(output.toString().contains("creditBodyKind=STRING"));
        assertTrue(output.toString().contains("creditHeaderMatches=true"));
        assertFalse(output.toString().contains("private-test-marker"));
        unavailable(provider);
        server.verify();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CARTIQ_CAPTURED_BLINKIT_BODY", matches = ".+")
    void normalizesTheOperatorSuppliedBlinkitBodyWithoutRetainingIt() throws Exception {
        var state = directory.resolve("operator-capture");
        var provider = provider(state, 97);
        var body = Files.readString(Path.of(System.getenv("CARTIQ_CAPTURED_BLINKIT_BODY")));
        // The capture contains the genuine JSON body only. Transport status and header are fixture values.
        server.expect(request -> assertEquals("/v1/search", request.getURI().getPath()))
            .andExpect(queryParam("platform", "BlinkIt"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON).header("X-Credits-Remaining", "96"));
        var result = provider.search("blinkit", "Milk", 10.942972, 76.953417, null);
        assertEquals(24, result.products().size());
        assertEquals(96, result.creditsRemaining());
        assertNotNull(result.requestId());
        assertTrue(result.products().stream().allMatch(product -> product.storeId() != null && product.storeId().matches("[0-9]+")));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void returnsTheBlinkitSuccessShapeThroughTheAdminController(boolean topLevelStore) throws Exception {
        var state = directory.resolve("blinkit-fixture");
        var provider = provider(state, 97);
        var mvc = standaloneSetup(new QuickCommerceController(provider, null))
            .setControllerAdvice(new ApiErrors()).build();
        // Use dummy IDs and locations while covering both provider identifier shapes.
        String identity = topLevelStore ? "\"id\":\"00123\",\"store_id\":456" : "\"id\":123";
        String platform = topLevelStore ? "\"name\":\"BlinkIt\"" : "\"name\":\"BlinkIt\",\"storeId\":456";
        String products = IntStream.range(1, 24)
            .mapToObj(id -> "{\"id\":\"fixture-" + id + "\",\"name\":\"Fixture listing\",\"store_id\":"
                + (topLevelStore ? "456" : "null") + "}")
            .collect(Collectors.joining(","));
        String body = """
            {"status":"success","request_id":"11111111-1111-4111-8111-111111111111","credits_remaining":96,
             "data":{"platform":"BlinkIt","query":"Milk","lat":12.9,"lon":77.66,
             "total_results":24,"failed_platforms":[],"products":[
               {%s,"name":"Fixture milk","quantity":"500 mL","brand":null,"offer_price":null,
                "available":null,"inventory":"unknown","platform":{%s}},
               %s
             ]}}
            """.formatted(identity, platform, products);
        server.expect(queryParam("platform", "BlinkIt")).andExpect(queryParam("q", "Milk"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON).header("X-Credits-Remaining", "96"));
        mvc.perform(get("/api/admin/providers/quickcommerce/search").param("retailer", "blinkit")
            .param("q", "Milk").param("lat", "12.9").param("lon", "77.66"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.upstreamStatus").value(200)).andExpect(jsonPath("$.products.length()").value(24))
            .andExpect(jsonPath("$.products[0].nativeId").value(topLevelStore ? "00123" : "123"))
            .andExpect(jsonPath("$.products[0].storeId").value("456"))
            .andExpect(jsonPath("$.products[23].nativeId").value("fixture-23"))
            .andExpect(jsonPath("$.products[0].normalizedPack.each.unit").value("ml"))
            .andExpect(jsonPath("$.products[0].priceMinor").doesNotExist())
            .andExpect(jsonPath("$.products[0].available").doesNotExist())
            .andExpect(jsonPath("$.products[0].inventory").doesNotExist());
        var ledger = Files.readAllLines(state);
        assertEquals(List.of("96", "settled"), ledger.subList(1, 3));
        assertEquals(5, ledger.size());
        assertTrue(ledger.get(4).endsWith("11111111-1111-4111-8111-111111111111\tprovider-charged"));
        server.verify();
    }

    @Test void serializesPartialRowsAndKeepsAbsentFieldsUnknown() throws Exception {
        var mvc = standaloneSetup(new QuickCommerceController(provider(97), null))
            .setControllerAdvice(new ApiErrors()).build();
        expect("/v1/search", "q", "milk", "BlinkIt", "12.9", "77.66", "560034", 96, """
            "products":[{"id":"0001","name":"Fixture without optional fields"},
              {"id":"0002","name":"Fixture with explicit nulls","store_id":null,"brand":null,"images":null,
               "mrp":null,"offer_price":null,"inventory":null,"available":null,"rating":null,"deeplink":null},
              {"id":"0003","name":"Fixture with unexpected store type","store_id":true},
              {"id":"0004","name":"Fixture with string store","store_id":"0042","rating":4.2}]
            """);
        var response = mvc.perform(get("/api/admin/providers/quickcommerce/search").param("retailer", "blinkit")
            .param("q", "milk").param("lat", "12.9").param("lon", "77.66").param("pincode", "560034"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products.length()").value(4))
            .andExpect(jsonPath("$.products[0].nativeId").value("0001"));
        for (int product : new int[]{0, 1}) {
            for (String field : List.of("storeId", "brand", "imageUrl", "mrpMinor", "priceMinor", "inventory",
                "available", "rating", "productUrl")) {
                response.andExpect(jsonPath("$.products[" + product + "]." + field).doesNotExist());
            }
        }
        response.andExpect(jsonPath("$.products[2].storeId").doesNotExist())
            .andExpect(jsonPath("$.products[3].storeId").value("0042"))
            .andExpect(jsonPath("$.products[3].rating").doesNotExist()); // Rating is outside the existing DTO.
        server.verify();
    }

    @Test void returnsAnEmptyProductArrayAsHttp200() throws Exception {
        var mvc = standaloneSetup(new QuickCommerceController(provider(97), null)).build();
        expect("/v1/search", "q", "milk", "BlinkIt", "12.9", "77.66", "560034", 96, "\"products\":[]");
        mvc.perform(get("/api/admin/providers/quickcommerce/search").param("retailer", "blinkit")
            .param("q", "milk").param("lat", "12.9").param("lon", "77.66").param("pincode", "560034"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products").isArray())
            .andExpect(jsonPath("$.products.length()").value(0));
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({
        "200, malformed, JSON decoding",
        "200, missing-status, success envelope",
        "200, wrong-envelope, success envelope",
        "200, missing-products, listing collection",
        "200, wrong-products, listing collection",
        "200, incomplete-listing, listing identity verification",
        "200, invalid-id, listing identity verification",
        "200, duplicate-listing, duplicate listing identity",
        "503, error, provider HTTP"
    })
    void returnsSanitizedHttp502ForUnusableResponses(int upstream, String fixture, String stage, CapturedOutput output)
        throws Exception {
        var state = directory.resolve("bad-" + fixture);
        var mvc = standaloneSetup(new QuickCommerceController(provider(state, 97), null))
            .setControllerAdvice(new ApiErrors()).build();
        String body = switch (fixture) {
            case "malformed" -> "{\"message\":\"private-test-marker\"";
            case "missing-status" -> "{\"message\":\"private-test-marker\"}";
            case "wrong-envelope" -> "[\"private-test-marker\"]";
            case "missing-products" -> successData("\"message\":\"private-test-marker\"");
            case "wrong-products" -> successData("\"products\":{\"message\":\"private-test-marker\"}");
            case "incomplete-listing" -> successData("\"products\":[{\"name\":\"private-test-marker\"}]");
            case "invalid-id" -> successData("\"products\":[{\"id\":true,\"name\":\"private-test-marker\"}]");
            case "duplicate-listing" -> successData("""
                "products":[{"id":"same","name":"Fixture"},{"id":"same","name":"private-test-marker"}]
                """);
            default -> "{\"message\":\"private-test-marker\"}";
        };
        server.expect(queryParam("platform", "BlinkIt")).andRespond(withStatus(HttpStatus.valueOf(upstream))
            .contentType(MediaType.APPLICATION_JSON).header("X-Credits-Remaining", "96").body(body));
        var response = mvc.perform(get("/api/admin/providers/quickcommerce/search").param("retailer", "blinkit")
            .param("q", "milk").param("lat", "12.9").param("lon", "77.66"))
            .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("BAD_GATEWAY"))
            .andExpect(jsonPath("$.message").value(upstream == 200
                ? "QuickCommerce " + stage + " failed (upstream HTTP 200)." : "QuickCommerce returned HTTP 503."))
            .andReturn().getResponse().getContentAsString();
        assertNoPrivateDetails(response, output);
        assertEquals("pending", Files.readAllLines(state).get(2));
        server.verify();
    }

    @Test void serializationFailureReturnsSafeHttp500AfterTheSyntheticChargeSettles(CapturedOutput output)
        throws Exception {
        var state = directory.resolve("serialization");
        var converter = new MappingJackson2HttpMessageConverter() {
            @Override
            protected void writeInternal(Object value, java.lang.reflect.Type type, org.springframework.http.HttpOutputMessage out)
                throws java.io.IOException {
                if (value instanceof QuickCommerceProvider.Result) {
                    throw new HttpMessageNotWritableException("private-test-marker", new IllegalStateException(KEY));
                }
                super.writeInternal(value, type, out);
            }
        };
        var mvc = standaloneSetup(new QuickCommerceController(provider(state, 97), null))
            .setControllerAdvice(new ApiErrors()).setMessageConverters(converter).build();
        expect("/v1/search", "q", "milk", "BlinkIt", "12.9", "77.66", "560034", 96,
            "\"products\":[{\"id\":\"001\",\"name\":\"private-test-marker\"}]");
        var response = mvc.perform(get("/api/admin/providers/quickcommerce/search").param("retailer", "blinkit")
            .param("q", "milk").param("lat", "12.9").param("lon", "77.66").param("pincode", "560034"))
            .andExpect(status().isInternalServerError()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.code").value("INTERNAL"))
            .andExpect(jsonPath("$.message").value("QuickCommerce result could not be serialized."))
            .andReturn().getResponse().getContentAsString();
        assertNoPrivateDetails(response, output);
        assertEquals(List.of("96", "settled"), Files.readAllLines(state).subList(1, 3));
        server.verify();
    }

    private static String successData(String fields) {
        return """
            {"status":"success","credits_remaining":96,"data":{"platform":"BlinkIt","lat":12.9,"lon":77.66,%s}}
            """.formatted(fields);
    }

    private static void assertNoPrivateDetails(String response, CapturedOutput output) {
        for (String value : List.of("private-test-marker", KEY, "12.9", "77.66", "560034")) {
            assertFalse(response.contains(value), "Error response exposed a test marker");
            assertFalse(output.toString().contains(value), "Application output exposed a test marker");
        }
    }

    @Test void rejectsConflictingBillingOrRetailerDataWithSanitizedDiagnostic() throws Exception {
        for (boolean wrongHeader : List.of(true, false)) {
            var state = directory.resolve("conflicting-" + wrongHeader);
            var provider = provider(state, 97);
            String body = """
                {"status":"success","credits_remaining":96,"data":{"platform":"BlinkIt","lat":12.9,"lon":77.66,
                 "products":[],"failed_platforms":%s}}
                """.formatted(wrongHeader ? "[]" : "[\"BlinkIt\"]");
            server.expect(queryParam("platform", "BlinkIt")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON)
                .header("X-Credits-Remaining", wrongHeader ? "95" : "96"));
            var error = assertThrows(ApiException.class, () -> provider.search("blinkit", "milk", 12.9, 77.66, null));
            assertEquals(502, error.status.value());
            assertEquals("QuickCommerce " + (wrongHeader ? "balance header" : "retailer success")
                + " verification failed (upstream HTTP 200).", error.getMessage());
            assertEquals("pending", Files.readAllLines(state).get(2));
            unavailable(provider);
            server.verify();
            server.reset();
        }
    }

    @Test void preservesReconciliationEvidenceAcrossRestartAndAnotherMockReservation() throws Exception {
        var state = directory.resolve("reconciled");
        var fingerprint = "a".repeat(64);
        var evidence = "evidence\t" + fingerprint + "\t11111111-1111-4111-8111-111111111111\tfixture-evidence";
        Files.write(state, List.of("cartiq-qc-budget-v2", "96", "settled", fingerprint, evidence));
        new QuickCommerceBudget(state, 96).spend("b".repeat(64), expectedBalance -> {
            assertEquals(95, expectedBalance);
            try {
                assertEquals(List.of("cartiq-qc-budget-v2", "95", "pending", fingerprint, "b".repeat(64), evidence),
                    Files.readAllLines(state));
            } catch (java.io.IOException failure) {
                throw new java.io.UncheckedIOException(failure);
            }
            return null;
        });
        assertEquals(List.of("cartiq-qc-budget-v2", "95", "settled", fingerprint, "b".repeat(64), evidence),
            Files.readAllLines(state));
        assertThrows(QuickCommerceProvider.DuplicateLookup.class, () -> new QuickCommerceBudget(state, 95)
            .spend(fingerprint, ignored -> fail("reconciled attempt was repeated")));
    }

    @Test void normalizesSearchMoneyAndMissingValuesAndSuppressesRepeatedLookups() {
        var provider = provider(97);
        expect("/v1/search", "q", "milk & oats", "BlinkIt", "12.9", "77.66", "560034", 96, """
            "products":[{"id":"001","name":"Fixture milk","brand":"Fixture","quantity":"1 L",
            "offer_price":50.25,"mrp":54,"available":true,"inventory":3,"platform":{"name":"BlinkIt"},
            "discount_percent":6.5,"images":["http://images.example.invalid/sample","https://images.example.invalid/milk"],
            "deeplink":"https://blinkit.com/prn/milk/prid/001"},
            {"id":"002","name":"Fixture dairy","offer_price":null,"available":false,
            "deeplink":"https://blinkit.com@unrelated.example.invalid/fixture"}],"pincode":"560034"
            """);
        var results = provider.search("blinkit", "milk & oats", 12.9, 77.66, "560034");
        assertEquals(96, results.creditsRemaining());
        assertEquals("001", results.products().get(0).nativeId());
        assertEquals(5025L, results.products().get(0).priceMinor());
        assertEquals(5400L, results.products().get(0).mrpMinor());
        assertEquals(6.5, results.products().get(0).discountPercent().doubleValue());
        assertEquals("https://images.example.invalid/milk", results.products().get(0).imageUrl());
        assertEquals("https://blinkit.com/prn/milk/prid/001", results.products().get(0).productUrl());
        assertEquals("560034", results.location().responsePincode());
        assertNull(results.products().get(1).productUrl());
        assertNull(results.products().get(1).discountPercent());
        assertNull(results.products().get(1).priceMinor());
        assertNull(results.products().get(1).inventory());
        assertEquals(false, results.products().get(1).available());
        assertEquals(409, assertThrows(ApiException.class, () ->
            provider.search("blinkit", " MILK  & oats ", 12.9, 77.66, "560034")).status.value());
        server.verify();
    }

    @Test void keepsRetailersLocationsAndMultipackVariantsDistinct() {
        var provider = provider(97);
        expect("/v1/search", "q", "milk", "Zepto", "12.9", "77.66", "560034", 96, "\"products\":[]");
        expect("/v1/search", "q", "milk", "Zepto", "13.0", "77.66", "560001", 95, "\"products\":[]");
        expect("/v1/item", "item_id", "source-id", "Swiggy", "12.9", "77.66", "560034", 94, """
            "item_id":"source-id","items":[
            {"item_id":"v1","name":"Fixture milk","quantity":"1 L","price":50,"store_id":"01","platform":"Swiggy",
            "fetched_at":"16-03-2026 06:52:12 AM","gallery":[{"type":"image","url":"https://images.example.invalid/variant"}]},
            {"item_id":"v2","name":"Fixture milk","quantity":"2 x 500 mL","price":52,"available":false}]
            """);
        assertTrue(provider.search("zepto", "milk", 12.9, 77.66, "560034").products().isEmpty());
        assertTrue(provider.search("zepto", "milk", 13, 77.66, "560001").products().isEmpty());
        var variants = provider.item("instamart", "source-id", 12.9, 77.66, "560034").products();
        assertEquals("1 L", variants.get(0).pack());
        assertEquals("2 x 500 mL", variants.get(1).pack());
        assertEquals(2, variants.get(1).normalizedPack().count());
        assertEquals("ml", variants.get(1).normalizedPack().each().unit());
        assertEquals(0, new java.math.BigDecimal("500").compareTo(variants.get(1).normalizedPack().each().amount()));
        assertEquals("01", variants.get(0).storeId());
        assertEquals("16-03-2026 06:52:12 AM", variants.get(0).providerFetchedAt());
        assertEquals("https://images.example.invalid/variant", variants.get(0).imageUrl());
        assertNull(variants.get(0).available());
        assertEquals(5200L, variants.get(1).priceMinor());
        var single = new Identity(null, "Fixture", "Fixture milk", variants.get(0).pack(), null, null);
        var multipack = new Identity(null, "Fixture", "Fixture milk", variants.get(1).pack(), null, null);
        assertEquals(ProductMatching.Decision.DISTINCT, ProductMatching.compare(single, multipack).decision());
        server.verify();
    }

    @Test void oneComparisonUsesTheSameKeySeriallyAndKeepsRetailerResultsSeparate() {
        var comparison = new ComparisonService(null, null, null, Optional.of(provider(97)));
        expect("/v1/search", "q", "milk", "BlinkIt", "12.9", "77.66", "560034", 96,
            "\"products\":[{\"id\":\"1\",\"name\":\"Fixture milk\",\"quantity\":\"1 L\",\"offer_price\":50}]");
        expect("/v1/search", "q", "milk", "Zepto", "12.9", "77.66", "560034", 95,
            "\"products\":[{\"id\":\"1\",\"name\":\"Fixture milk\",\"quantity\":\"2 x 500 mL\"}]");
        expect("/v1/search", "q", "milk", "Swiggy", "12.9", "77.66", "560034", 94,
            "\"products\":[{\"id\":\"1\",\"name\":\"Fixture milk\",\"quantity\":\"500 mL\",\"available\":false}]");
        var rows = comparison.compareRetailerSearch("milk", 12.9, 77.66, "560034").retailers();
        assertEquals(List.of("blinkit", "zepto", "instamart"), rows.stream().map(ComparisonService.RetailerCandidates::retailer).toList());
        assertEquals(5000L, rows.get(0).result().products().get(0).priceMinor());
        assertNull(rows.get(1).result().products().get(0).priceMinor());
        assertEquals(2, rows.get(1).result().products().get(0).normalizedPack().count());
        assertFalse(rows.get(2).result().products().get(0).available());
        assertEquals(94, rows.get(2).result().creditsRemaining());
        server.verify();
    }

    @Test void comparisonReportsPartialResultsAndStopsOnProviderError() {
        var state = directory.resolve("partial-compare");
        var comparison = new ComparisonService(null, null, null, Optional.of(provider(state, 97)));
        expect("/v1/search", "q", "milk", "BlinkIt", "12.9", "77.66", "560034", 96,"\"products\":[]");
        server.expect(queryParam("platform", "Zepto")).andExpect(header("X-API-Key", KEY))
            .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        var rows = comparison.compareRetailerSearch("milk", 12.9, 77.66, "560034").retailers();
        assertEquals(List.of("success", "error", "not_attempted"), rows.stream().map(ComparisonService.RetailerCandidates::status).toList());
        assertNull(rows.get(1).result());
        assertNull(rows.get(2).result());
        unavailable(provider(state, 95));
        server.verify();
    }

    @Test void comparisonKeepsGoingAfterAnExplicitlyUnchargedRetailerTimeout() throws Exception {
        var state = directory.resolve("confirmed-no-charge");
        var comparison = new ComparisonService(null, null, null, Optional.of(provider(state, 97)));
        expect("/v1/search", "q", "milk", "BlinkIt", "12.9", "77.66", "560034", 96,
            "\"products\":[{\"id\":\"001\",\"name\":\"Fixture milk\",\"available\":true}]");
        server.expect(queryParam("platform", "Zepto"))
            .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT).header("X-Credits-Remaining", "96"));
        expect("/v1/search", "q", "milk", "Swiggy", "12.9", "77.66", "560034", 95, "\"products\":[]");
        var rows = comparison.compareRetailerSearch("milk", 12.9, 77.66, "560034").retailers();
        assertEquals(List.of(QuickCommerceProvider.Outcome.SUCCESS, QuickCommerceProvider.Outcome.TIMEOUT,
            QuickCommerceProvider.Outcome.NO_RESULTS), rows.stream().map(ComparisonService.RetailerCandidates::outcome).toList());
        assertEquals("001", rows.get(0).result().products().get(0).nativeId());
        assertNotNull(rows.get(0).result().fetchedAt());
        assertEquals(List.of("95", "settled"), Files.readAllLines(state).subList(1, 3));
        assertEquals(6, Files.readAllLines(state).size()); // The free timeout still consumes an experiment attempt.
        server.verify();
    }

    @Test void skipsASettledDuplicateWithoutSpendingOrBlockingOtherRetailers() {
        var provider = provider(97);
        var comparison = new ComparisonService(null, null, null, Optional.of(provider));
        expect("/v1/search", "q", "milk", "BlinkIt", "12.9", "77.66", "560034", 96,"\"products\":[]");
        expect("/v1/search", "q", "milk", "Zepto", "12.9", "77.66", "560034", 95,"\"products\":[]");
        expect("/v1/search", "q", "milk", "Swiggy", "12.9", "77.66", "560034", 94,"\"products\":[]");
        provider.search("blinkit", "milk", 12.9, 77.66, "560034");
        var rows = comparison.compareRetailerSearch("milk", 12.9, 77.66, "560034").retailers();
        assertEquals(List.of("duplicate", "success", "success"), rows.stream().map(ComparisonService.RetailerCandidates::status).toList());
        server.verify();
    }

    @Test void explicitRetailerSubsetSkipsOthersAndDoesNotBypassTheLedger() throws Exception {
        var state = directory.resolve("selected-retailers");
        var provider = provider(state, 97);
        var service = new ComparisonService(null, null, null, Optional.of(provider));
        var mvc = standaloneSetup(new QuickCommerceController(provider, service))
            .setControllerAdvice(new ApiErrors()).build();
        expect("/v1/search", "q", "toor dal", "Zepto", "12.9", "77.66", "560034", 96,
            "\"products\":[{\"id\":\"1\",\"name\":\"Fixture dal\",\"quantity\":\"500 g\"}]");
        var route = "/api/shopping/retailers/compare";
        String zepto = """
            {"q":"toor dal","lat":12.9,"lon":77.66,"pincode":"560034","retailers":["zepto"]}
            """;
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(zepto))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.paused").value(false)).andExpect(jsonPath("$.retailers.length()").value(1))
            .andExpect(jsonPath("$.retailers[0].retailer").value("zepto"))
            .andExpect(jsonPath("$.retailers[0].result.fetchedAt").exists());
        var after = Files.readString(state);
        mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content(zepto.replace("toor dal", " TOOR   DAL ")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.retailers[0].status").value("duplicate"))
            .andExpect(jsonPath("$.retailers[0].result").doesNotExist());
        assertEquals(after, Files.readString(state));
        server.verify();
    }

    @Test void invalidRetailerChoicesNeverDispatchOrReserve() throws Exception {
        var state = directory.resolve("invalid-retailers");
        var service = new ComparisonService(null, null, null, Optional.of(provider(state, 97)));
        String before = Files.readString(state);
        for (List<String> subset : List.of(List.<String>of(), List.of("unsupported"), List.of("blinkit", "blinkit"))) {
            assertEquals(400, assertThrows(ApiException.class,
                () -> service.compareRetailerSearch("milk", 12.9, 77.66, null, subset)).status.value());
        }
        assertEquals(before, Files.readString(state));
        server.verify();
    }

    @Test void disabledMissingKeyInvalidInputAndCreditFloorRequireNoTransport() {
        var off = new QuickCommerceProvider(client.build(), KEY, false, new QuickCommerceBudget(directory.resolve("off"), 97));
        var missing = new QuickCommerceProvider(client.build(), "", true, new QuickCommerceBudget(directory.resolve("missing"), 97));
        for (var provider : new QuickCommerceProvider[]{off, missing, provider(-1), provider(0)}) {
            unavailable(provider);
        }
        assertFalse(Files.exists(directory.resolve("off")));
        assertFalse(Files.exists(directory.resolve("missing")));
        assertEquals(400, assertThrows(ApiException.class, () ->
            provider(97).search("all", "milk", 12.9, 77.66, null)).status.value());
        server.verify();
    }

    @Test void stopsAtZeroAndNeverMakesMoreThan20PaidAttempts() {
        var state = directory.resolve("limit");
        var provider = provider(state, 98);
        for (int i = 0; i < 20; i++) {
            expect("/v1/search", "q", "test" + i, "BlinkIt", "12.9", "77.66", "560034", 97-i, "\"products\":[]");
        }
        for (int i = 0; i < 20; i++) provider.search("blinkit", "test" + i, 12.9, 77.66, "560034");
        unavailable(provider(state, 78));
        server.reset();
        var nearFloor = provider(1);
        expect("/v1/search", "q", "last", "BlinkIt", "12.9", "77.66", "560034", 0, "\"products\":[]");
        nearFloor.search("blinkit", "last", 12.9, 77.66, "560034");
        unavailable(nearFloor);
        server.verify();
    }

    @Test void rejectsWrongLocationAndMalformedPricesAndSuspendsSession() {
        var provider = provider(97);
        expect("/v1/search", "q", "wrong location", "BlinkIt", "12.9", "77.66", "560034", 96,
            "\"products\":[]", "13.0");
        assertEquals(502, assertThrows(ApiException.class, () -> provider.search("blinkit", "wrong location",
            12.9, 77.66, "560034")).status.value());
        unavailable(provider);
        server.verify();
        server.reset();
        var wrongPrice = provider(97);
        expect("/v1/search", "q", "wrong price", "BlinkIt", "12.9", "77.66", "560034", 96,
            "\"products\":[{\"id\":\"test\",\"name\":\"Fixture\",\"offer_price\":-1}]");
        assertEquals(502, assertThrows(ApiException.class, () -> wrongPrice.search("blinkit", "wrong price",
            12.9, 77.66, "560034")).status.value());
        unavailable(wrongPrice);
        server.verify();
    }

    @Test void handlesUpstreamTimeoutWithoutRetryingOrReturningUpstreamBody() {
        var state = directory.resolve("timeout");
        var provider = provider(state, 97);
        server.expect(queryParam("q", "milk"))
            .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT).body("internal fixture error"));
        var error = assertThrows(ApiException.class, () -> provider.search("blinkit", "milk", 12.9, 77.66, null));
        assertEquals(504, error.status.value());
        assertFalse(error.getMessage().contains("internal fixture error"));
        unavailable(provider(state, 97));
        server.verify();
    }

    @Test void persistsFingerprintsAndRemainingBalanceAcrossAdaptersWithoutStoringResponses() throws Exception {
        var state = directory.resolve("restart");
        expect("/v1/search", "q", "milk", "BlinkIt", "12.9", "77.66", "560034", 78,
            "\"products\":[{\"id\":\"unpersisted\",\"name\":\"Fixture\",\"offer_price\":99999999999999.91}]");
        expect("/v1/search", "q", "bread", "Zepto", "12.9", "77.66", "560034", 77, "\"products\":[]");
        var response = provider(state, 79).search("blinkit", "milk", 12.9, 77.66, "560034");
        assertEquals(9999999999999991L, response.products().get(0).priceMinor());
        unavailable(provider(state, 120));
        var restarted = provider(state, 78);
        assertEquals(409, assertThrows(ApiException.class, () ->
            restarted.search("blinkit", "ＭＩＬＫ", 12.9, 77.66, "560034")).status.value());
        restarted.search("zepto", "bread", 12.9, 77.66, "560034");
        unavailable(provider(state, 120));
        var ledger = Files.readString(state);
        for (var sensitive : new String[]{KEY, "milk", "560034", "unpersisted", "99999999999999"}) {
            assertFalse(ledger.contains(sensitive));
        }
        server.verify();
    }

    @Test void rejectsUnexpectedBalanceAndCorruptLedgerWithoutFurtherCalls() throws Exception {
        var state = directory.resolve("uncertain-balance");
        expect("/v1/search", "q", "milk", "BlinkIt", "12.9", "77.66", "560034", 94, "\"products\":[]");
        assertEquals(502, assertThrows(ApiException.class, () ->
            provider(state, 97).search("blinkit", "milk", 12.9, 77.66, "560034")).status.value());
        unavailable(provider(state, 97));
        Files.writeString(state, "invalid ledger");
        unavailable(provider(state, 97));
        server.verify();
    }

    @Test void acceptsAnOfflineCheckpointWithZeroAttemptsAndBlocksAnEmptyPendingLedger() throws Exception {
        var state = directory.resolve("offline-checkpoint");
        Files.writeString(state, "cartiq-qc-budget-v1\n1\nsettled\n");
        unavailable(provider(state, 97));
        expect("/v1/search", "q", "milk", "BlinkIt", "12.9", "77.66", "560034", 0, "\"products\":[]");
        provider(state, 1).search("blinkit", "milk", 12.9, 77.66, "560034");
        unavailable(provider(state, 0));
        Files.writeString(state, "cartiq-qc-budget-v1\n1\npending\n");
        unavailable(provider(state, 1));
        server.verify();
    }

    @Test void rejectsAnOverlappingReservationAndContinuesOnceTheFirstCallSettles() {
        var state = directory.resolve("serialized");
        provider(state, 97); // Prepare the same offline checkpoint used by the launcher workflow.
        var second = new QuickCommerceBudget(state, 96);
        new QuickCommerceBudget(state, 97).spend("a".repeat(64), expectedBalance -> {
            assertEquals(96, expectedBalance);
            assertEquals(409, assertThrows(ApiException.class, () ->
                second.spend("b".repeat(64), ignored -> fail("parallel request"))).status.value());
            return null;
        });
        second.spend("b".repeat(64), expectedBalance -> {
            assertEquals(95, expectedBalance);
            return null;
        });
    }

    @Test void missingLedgerCannotSilentlyReinitializeTheExperiment() throws Exception {
        var state = directory.resolve("deleted-ledger");
        var provider = provider(state, 97);
        Files.delete(state);
        unavailable(provider);
        assertFalse(Files.exists(state));
        server.verify();
    }

    private QuickCommerceProvider provider(int balance) {
        return provider(directory.resolve("ledger-" + ledgerNumber++), balance);
    }

    private QuickCommerceProvider provider(Path state, int balance) {
        if (balance >= QuickCommerceBudget.FLOOR && !Files.exists(state)) {
            try {
                Files.writeString(state, "cartiq-qc-budget-v1\n" + balance + "\nsettled\n");
            } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        }
        return new QuickCommerceProvider(client.build(), KEY, true, new QuickCommerceBudget(state, balance));
    }

    private static void unavailable(QuickCommerceProvider provider) {
        assertEquals(503, assertThrows(ApiException.class, () ->
            provider.search("blinkit", "probe", 12.9, 77.66, "560034")).status.value());
    }

    private void expect(String path, String name, String value, String platform, String lat, String lon,
                        String pin, int balance, String rows) {
        expect(path, name, value, platform, lat, lon, pin, balance, rows, lat);
    }

    private void expect(String path, String name, String value, String platform, String lat, String lon,
                        String pin, int balance, String rows, String responseLat) {
        String body = """
            {"status":"success","credits_remaining":%d,"data":{"platform":"%s","lat":%s,"lon":%s,%s}}
            """.formatted(balance, platform, responseLat, lon, rows);
        server.expect(request -> {
            assertEquals(path, request.getURI().getPath());
            assertFalse(request.getURI().toString().contains(KEY));
        }).andExpect(header("X-API-Key", KEY)).andExpect(queryParam(name, value.replace(" ", "%20").replace("&", "%26")))
            .andExpect(queryParam("platform", platform)).andExpect(queryParam("lat", lat))
            .andExpect(queryParam("lon", lon)).andExpect(queryParam("pincode", pin))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON).header("X-Credits-Remaining", Integer.toString(balance)));
    }
}
