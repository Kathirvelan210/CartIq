package com.smartcart.integration;

import com.smartcart.exception.ApiException;
import com.smartcart.exception.ApiErrors;
import com.smartcart.controller.ShoppingLocationController;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@ExtendWith(OutputCaptureExtension.class)
class GoogleGeocodingTest {
    private static final String KEY = "fixture-google-secret";
    private static final String ORIGIN = "https://geocode.googleapis.com/v4/geocode/";
    private static final double LAT = 10.942972, LON = 76.953417;
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ShoppingGeocoder geocoder = new ShoppingGeocoder(builder, KEY);

    @Test void sendsForwardKeyOnlyInHeaderAndFiltersToTheExactIndianPin() {
        server.expect(requestTo(ORIGIN + "address?address.postalCode=641008&address.regionCode=IN&languageCode=en"))
            .andExpect(header("X-Goog-Api-Key", KEY))
            .andExpect(header("X-Goog-FieldMask", "results.addressComponents,results.location,results.postalAddress"))
            .andRespond(withSuccess(results(place("641008", "IN", "Fixture Ward"), place("641009", "IN", "Other Ward"),
                place("641008", "LK", "Foreign Ward")), MediaType.APPLICATION_JSON));
        var candidates = geocoder.pin("641008", null);
        assertEquals(1, candidates.size());
        assertEquals("Fixture Ward", candidates.get(0).locality());
        assertEquals("Coimbatore", candidates.get(0).city());
        assertTrue(candidates.get(0).approximate());
        server.verify();
    }

    @Test void forwardsOtherIndianPostcodes() {
        server.expect(queryParam("address.postalCode", "560034"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertTrue(geocoder.pin("560034", null).isEmpty());
        server.verify();
    }

    @Test void supportsStructuredPostalAddressWithoutASublocality() {
        server.expect(queryParam("address.postalCode", "560034")).andRespond(withSuccess("""
            {"results":[{"location":{"latitude":12.9,"longitude":77.66},
             "postalAddress":{"regionCode":"IN","locality":"Fixture City","postalCode":"560034","administrativeArea":"Fixture State"}}]}
            """, MediaType.APPLICATION_JSON));
        var location = geocoder.pin("560034", null).get(0);
        assertEquals("Fixture City", location.city());
        assertNull(location.locality());
        server.verify();
    }

    @Test void forwardsEveryEligible641008CandidateAndKeepsAnUnnamedResult(CapturedOutput output) {
        var postalTown = place("641008", "IN", "").replace("\"locality\",\"political\"", "\"postal_town\",\"political\"");
        var stateOnly = """
            {"location":{"latitude":10.95,"longitude":76.95},"postalAddress":{
             "regionCode":"IN","postalCode":"641008","administrativeArea":"Tamil Nadu"}}
            """;
        var stateConflict = place("641008", "IN", "Conflicting State").replace("\"location\":",
            "\"postalAddress\":{\"regionCode\":\"IN\",\"postalCode\":\"641008\",\"administrativeArea\":\"Kerala\"},\"location\":");
        server.expect(queryParam("address.postalCode", "641008")).andRespond(withSuccess(
            results(place("641008", "IN", "Fixture East"), place("641008", "IN", "Fixture West"), postalTown,
                stateOnly, stateConflict, place("641009", "IN", "Other PIN")), MediaType.APPLICATION_JSON));
        var candidates = geocoder.pin("641008", null);
        assertEquals(4, candidates.size());
        assertEquals("Fixture East", candidates.get(0).locality());
        assertEquals("Fixture West", candidates.get(1).locality());
        assertEquals("Coimbatore", candidates.get(2).city());
        assertNull(candidates.get(3).city());
        assertNull(candidates.get(3).locality());
        assertTrue(candidates.stream().allMatch(place -> place.approximate() && "in".equals(place.countryCode())));
        assertTrue(output.getAll().contains("indian_pin_matches=4 named_candidates=3"));
        assertFalse(output.getAll().contains("641008"));
        assertFalse(output.getAll().contains("10.95"));
        assertFalse(output.getAll().contains("Fixture East"));
        assertFalse(output.getAll().contains(KEY));
        server.verify();
    }

    @Test void reversePreservesTheSubmittedPointAndNormalizesTheDeliveryArea() {
        server.expect(requestTo(ORIGIN + "location?location.latitude=10.942972&location.longitude=76.953417&languageCode=en&regionCode=IN"))
            .andExpect(header("X-Goog-Api-Key", KEY))
            .andRespond(withSuccess(results(place("641008", "IN", "Fixture Ward")), MediaType.APPLICATION_JSON));
        var area = geocoder.reverse(LAT, LON);
        assertEquals(LAT, area.latitude());
        assertEquals(LON, area.longitude());
        assertEquals("641008", area.postcode());
        assertEquals("Tamil Nadu", area.state());
        assertFalse(area.approximate());
        server.verify();
    }

    @Test void conflictingReverseLocalitiesCannotProduceAVerifiedArea(CapturedOutput output) {
        server.expect(queryParam("location.latitude", "10.942972"))
            .andRespond(withSuccess(results(place("641008", "IN", "Fixture Ward"), place("641008", "IN", "Other Ward")),
                MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, assertThrows(ApiException.class, () -> geocoder.reverse(LAT, LON)).status);
        assertTrue(output.getAll().contains("outcome=CONFLICTING_AREAS"));
        assertFalse(output.getAll().contains(String.valueOf(LAT)));
        assertFalse(output.getAll().contains(String.valueOf(LON)));
        assertFalse(output.getAll().contains(KEY));
        server.verify();
    }

    @Test void recordsMissingReverseLocalityWithoutLoggingTheUserPoint(CapturedOutput output) {
        server.expect(queryParam("location.latitude", "10.942972")).andRespond(withSuccess("""
            {"results":[{"postalAddress":{"regionCode":"IN","postalCode":"641008","administrativeArea":"Tamil Nadu"}}]}
            """, MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, assertThrows(ApiException.class, () -> geocoder.reverse(LAT, LON)).status);
        assertTrue(output.getAll().contains("outcome=MISSING_AREA_COMPONENTS"));
        assertFalse(output.getAll().contains(String.valueOf(LAT)));
        server.verify();
    }

    @Test void invalidKeyErrorDoesNotEmitUpstreamBodyOrCredential(CapturedOutput output) {
        server.expect(queryParam("address.postalCode", "641008"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"message\":\"" + KEY + "\"}}"));
        var error = assertThrows(ApiException.class, () -> geocoder.pin("641008", null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.status);
        assertFalse(error.getMessage().contains(KEY));
        assertNull(error.getCause());
        assertFalse(output.getAll().contains(KEY));
        server.verify();
    }

    @Test void exhaustedQuotaPausesFurtherRequests() {
        server.expect(queryParam("address.postalCode", "641008"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "90"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ApiException.class, () -> geocoder.pin("641008", null)).status);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertThrows(ApiException.class, () -> geocoder.reverse(LAT, LON)).status);
        server.verify();
    }

    @Test void timeoutStaysAnError() {
        server.expect(queryParam("location.latitude", "10.942972")).andRespond(request -> {
            throw new SocketTimeoutException("fixture timeout");
        });
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, assertThrows(ApiException.class, () -> geocoder.reverse(LAT, LON)).status);
        server.verify();
    }

    @Test void googleDemoRemainsLocalAndRequiresAKey() {
        assertThrows(IllegalArgumentException.class, () -> new ShoppingGeocoder("", "", "", false, "google-demo", KEY, false));
        assertThrows(IllegalArgumentException.class, () -> new ShoppingGeocoder("", "", "", false, "google-demo", "", true));
    }

    @Test void pickerAdvertisesGoogleAndDispatchesOnlyAfterConsent() throws Exception {
        var mvc = standaloneSetup(new ShoppingLocationController(geocoder)).setControllerAdvice(new ApiErrors()).build();
        mvc.perform(get("/api/location-lookup/options")).andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true)).andExpect(jsonPath("$.deviceGpsAllowed").value(true))
            .andExpect(jsonPath("$.googleDemo").value(true));
        mvc.perform(post("/api/location-lookup/pin").contentType(MediaType.APPLICATION_JSON).content("{\"postcode\":\"641008\"}"))
            .andExpect(status().isForbidden());
        server.expect(queryParam("address.postalCode", "641008"))
            .andRespond(withSuccess(results(place("641008", "IN", "Fixture Ward")), MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/location-lookup/pin").contentType(MediaType.APPLICATION_JSON).content("{\"postcode\":\"641008\"}")
            .header(ShoppingLocationController.CONSENT_HEADER, "delivery-geocoding-v1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].postcode").value("641008"));
        server.verify();
    }

    @Test void pickerReverseAcceptsAnIndianCityAndPinWhenGoogleHasNoSublocality() throws Exception {
        var mvc = standaloneSetup(new ShoppingLocationController(geocoder)).setControllerAdvice(new ApiErrors()).build();
        server.expect(queryParam("location.latitude", "10.942972"))
            .andRespond(withSuccess(results(place("641008", "IN", "")), MediaType.APPLICATION_JSON));
        mvc.perform(post("/api/location-lookup/reverse").contentType(MediaType.APPLICATION_JSON)
            .content("{\"latitude\":10.942972,\"longitude\":76.953417}")
            .header(ShoppingLocationController.CONSENT_HEADER, "delivery-geocoding-v1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.postcode").value("641008"))
            .andExpect(jsonPath("$.city").value("Coimbatore"));
        server.verify();
    }

    private static String results(String... entries) {
        return "{\"results\":[" + String.join(",", entries) + "]}";
    }

    private static String place(String postcode, String country, String locality) {
        return """
            {"location":{"latitude":10.95,"longitude":76.95},"addressComponents":[
            {"longText":"%s","types":["postal_code"]},
            {"shortText":"%s","types":["country","political"]},
            {"longText":"%s","types":["sublocality_level_1","political"]},
            {"longText":"Coimbatore","types":["locality","political"]},
            {"longText":"Tamil Nadu","types":["administrative_area_level_1","political"]}]}
            """.formatted(postcode, country, locality);
    }
}
