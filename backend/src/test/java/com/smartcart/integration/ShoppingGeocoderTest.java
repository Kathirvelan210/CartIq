package com.smartcart.integration;

import com.smartcart.controller.ShoppingLocationController;
import com.smartcart.exception.ApiErrors;
import com.smartcart.exception.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class ShoppingGeocoderTest {
    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder post(String path) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
            .header(ShoppingLocationController.CONSENT_HEADER, "delivery-geocoding-v1");
    }
    private static final String PIN_ENDPOINT = "/api/location-lookup/pin";
    private static final String VALID_PIN = "{\"postcode\":\"560034\"}";
    private final RestClient.Builder client = RestClient.builder().baseUrl("https://example.invalid");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
    private final org.springframework.test.web.servlet.MockMvc mvc =
        standaloneSetup(new ShoppingLocationController(new ShoppingGeocoder(client.build())))
            .setControllerAdvice(new ApiErrors()).build();

    @Test void validatesPinWithoutDialingTheGeocoder() throws Exception {
        for (String invalid : new String[]{"012345", "12345", "1234567", "1234ab", "१२३४५६"}) {
            mvc.perform(post(PIN_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
                .content("{\"postcode\":\"" + invalid + "\"}")).andExpect(status().isBadRequest());
        }
        server.verify();
    }

    @Test void returnsMatchingLocalitiesAndLabelsCoordinatesApproximate() throws Exception {
        server.expect(request -> org.junit.jupiter.api.Assertions.assertEquals("/search", request.getURI().getPath()))
            .andExpect(queryParam("postalcode", "560034")).andExpect(queryParam("countrycodes", "in"))
            .andExpect(queryParam("city", "Fixture")).andRespond(withSuccess("""
                [
                  {"lat":"12.91","lon":"77.61","address":{"suburb":"East","city":"Fixture","postcode":"560034","country_code":"in"}},
                  {"address":{"suburb":"West","city":"Fixture","postcode":"560034","country_code":"in"}},
                  {"lat":"12.94","lon":"77.64","address":{"postcode":"560035","country_code":"in"}},
                  {"address":{"postcode":"560034","country_code":"lk"}}
                ]
                """, MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/location-lookup/pin").contentType(MediaType.APPLICATION_JSON)
            .content("{\"postcode\":\"560034\",\"locality\":\"Fixture\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].locality").value("East"))
            .andExpect(jsonPath("$[0].approximate").value(true)).andExpect(jsonPath("$[0].latitude").value(12.91))
            .andExpect(jsonPath("$[1].locality").value("West")).andExpect(jsonPath("$[1].latitude").doesNotExist());
        server.verify();
    }

    @Test void returnsReverseAddressWithoutReplacingTheUsersCoordinates() throws Exception {
        server.expect(request -> org.junit.jupiter.api.Assertions.assertEquals("/reverse", request.getURI().getPath()))
            .andExpect(queryParam("lat", "12.9")).andExpect(queryParam("lon", "77.66"))
            .andRespond(withSuccess("""
                {"lat":"12.925","lon":"77.65","address":{"suburb":"East","city":"Fixture",
                 "state_district":"District","state":"State","postcode":"560034","country_code":"in"}}
                """, MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/location-lookup/reverse").contentType(MediaType.APPLICATION_JSON)
            .content("{\"latitude\":12.9,\"longitude\":77.66}")).andExpect(status().isOk())
            .andExpect(jsonPath("$.latitude").value(12.9)).andExpect(jsonPath("$.longitude").value(77.66))
            .andExpect(jsonPath("$.locality").value("East")).andExpect(jsonPath("$.district").value("District"))
            .andExpect(jsonPath("$.postcode").value("560034")).andExpect(jsonPath("$.approximate").value(false));
        mvc.perform(post("/api/location-lookup/reverse").contentType(MediaType.APPLICATION_JSON)
            .content("{\"latitude\":12.9,\"longitude\":77.66}")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.countryCode").value("in"));
        server.verify();
    }

    @Test void rejectsReverseAddressesOutsideIndia() throws Exception {
        server.expect(queryParam("lat", "10.942972")).andExpect(queryParam("lon", "76.953417"))
            .andRespond(withSuccess("""
                {"address":{"postcode":"641008","country_code":"lk"}}
                """, MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/location-lookup/reverse").contentType(MediaType.APPLICATION_JSON)
            .content("{\"latitude\":10.942972,\"longitude\":76.953417}"))
            .andExpect(status().isUnprocessableEntity());
        server.verify();
    }

    @Test void cachesIdenticalPinLookupsAndThrottlesNewLookupsAcrossOperations() throws Exception {
        server.expect(queryParam("postalcode", "641008")).andExpect(queryParam("format", "jsonv2"))
            .andExpect(queryParam("addressdetails", "1")).andExpect(queryParam("countrycodes", "in"))
            .andRespond(withSuccess("""
                [{"lat":"10.95","lon":"76.95","address":{"suburb":"Fixture Ward","city":"Coimbatore",
                   "state":"Tamil Nadu","postcode":"641008","country_code":"in"}}]
                """, MediaType.APPLICATION_JSON));
        for (int repeat = 0; repeat < 2; repeat++) {
            mvc.perform(post(PIN_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content("{\"postcode\":\"641008\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].postcode").value("641008"))
                .andExpect(jsonPath("$[0].countryCode").value("in"));
        }
        mvc.perform(post("/api/location-lookup/reverse").contentType(MediaType.APPLICATION_JSON)
            .content("{\"latitude\":10.942972,\"longitude\":76.953417}"))
            .andExpect(status().isTooManyRequests());
        server.verify();
    }

    @Test void providerQuotaCooldownBlocksAnImmediateSecondLookup() throws Exception {
        server.expect(queryParam("postalcode", "560034"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "90"));
        mvc.perform(post(PIN_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_PIN))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.message").value("Location lookup is busy. Try again later."));
        mvc.perform(post(PIN_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_PIN))
            .andExpect(status().isTooManyRequests());
        server.verify();
    }

    @Test void rejectsInvalidJsonWithoutReturningUpstreamContent() throws Exception {
        server.expect(queryParam("postalcode", "560034"))
            .andRespond(withSuccess("invalid upstream payload", MediaType.APPLICATION_JSON));
        mvc.perform(post(PIN_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_PIN))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.message").value("Location service returned an invalid response."));
        server.verify();
    }

    @Test void rejectsUnsupportedSearchShapeAsAnInvalidResponse() throws Exception {
        server.expect(queryParam("postalcode", "560034")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        mvc.perform(post(PIN_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_PIN))
            .andExpect(status().isBadGateway());
        server.verify();
    }

    @Test void providerFailureIsAnUnavailableLookup() throws Exception {
        server.expect(queryParam("postalcode", "560034")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        mvc.perform(post(PIN_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_PIN))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message").value("Location lookup is temporarily unavailable."));
        server.verify();
    }

    @Test void aValidEmptySearchIsAnEmptyList() throws Exception {
        server.expect(queryParam("postalcode", "560034")).andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        mvc.perform(post(PIN_ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(VALID_PIN))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        server.verify();
    }

    @Test void localityIsOneEncodedParameter() throws Exception {
        server.expect(requestTo("https://example.invalid/search?format=jsonv2&addressdetails=1&countrycodes=in&postalcode=560034&limit=25&city=A%26B%2BC"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        mvc.perform(post(PIN_ENDPOINT).contentType(MediaType.APPLICATION_JSON)
            .content("{\"postcode\":\"560034\",\"locality\":\"A&B+C\"}"))
            .andExpect(status().isOk());
        server.verify();
    }

    @Test void unconfiguredLookupFailsWithoutNetworkAccess() {
        var geocoder = new ShoppingGeocoder("", "", "");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(ApiException.class, () -> geocoder.pin("560034", null)).status);
    }

    @Test void invalidBaseUrlErrorDoesNotExposeOperatorInput() {
        var error = assertThrows(IllegalArgumentException.class,
            () -> new ShoppingGeocoder("https://invalid host/private-input", "", ""));
        assertFalse(error.getMessage().contains("private-input"));
        assertNull(error.getCause());
    }

    @Test void publicServiceRequiresOperatorContactAndRejectsUnapprovedInputsWithoutNetwork() throws Exception {
        var origin = "https://nominatim.openstreetmap.org";
        var missing = assertThrows(IllegalArgumentException.class, () -> new ShoppingGeocoder(origin, "", ""));
        assertTrue(missing.getMessage().contains("CARTIQ_GEOCODER_CONTACT"));
        var invalid = assertThrows(IllegalArgumentException.class,
            () -> new ShoppingGeocoder(origin, "private fixture text", ""));
        assertFalse(invalid.getMessage().contains("private fixture text"));
        try (var geocoder = new ShoppingGeocoder(origin, "fixture@example.invalid", "10.942972,76.953417")) {
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE,
                assertThrows(ApiException.class, () -> geocoder.reverse(10.9, 76.95)).status);
            assertEquals(HttpStatus.BAD_REQUEST,
                assertThrows(ApiException.class, () -> geocoder.pin("641008", "Unapproved free text")).status);
        }
    }

    @Test void sendsAnIdentifyingUserAgentWithConfiguredContactToLoopbackOnly() throws Exception {
        var captured = new java.util.concurrent.atomic.AtomicReference<String>();
        var peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        peer.createContext("/search", exchange -> {
            try (exchange) {
                captured.set(exchange.getRequestHeaders().getFirst("User-Agent"));
                exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
                exchange.sendResponseHeaders(200, 2);
                exchange.getResponseBody().write(new byte[]{'[', ']'});
            }
        });
        peer.start();
        try (var geocoder = new ShoppingGeocoder("http://127.0.0.1:" + peer.getAddress().getPort(), "fixture@example.invalid", "")) {
            assertTrue(geocoder.pin("641008", null).isEmpty());
            assertEquals("CartIQ/1.0 (contact: fixture@example.invalid)", captured.get());
        } finally {
            peer.stop(0);
        }
    }

    @Test void loopbackProviderThatStallsTheBodyTimesOut() throws Exception {
        var release = new CountDownLatch(1);
        var peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        peer.createContext("/geocoding/search", exchange -> {
            try (exchange) {
                exchange.getResponseHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);
                exchange.sendResponseHeaders(200, 2);
                exchange.getResponseBody().write('[');
                exchange.getResponseBody().flush();
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        peer.start();
        try (var geocoder = new ShoppingGeocoder("http://127.0.0.1:" + peer.getAddress().getPort() + "/geocoding/", "", "")) {
            assertTimeoutPreemptively(Duration.ofSeconds(8), () -> assertEquals(HttpStatus.GATEWAY_TIMEOUT,
                assertThrows(ApiException.class, () -> geocoder.pin("560034", null)).status));
        } finally {
            release.countDown();
            peer.stop(0);
        }
    }
}
