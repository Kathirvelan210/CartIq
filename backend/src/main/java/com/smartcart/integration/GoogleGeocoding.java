package com.smartcart.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartcart.exception.ApiException;
import com.smartcart.integration.ShoppingGeocoder.Place;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

final class GoogleGeocoding {
    private static final Logger LOG = LoggerFactory.getLogger(GoogleGeocoding.class);
    private GoogleGeocoding() {}

    static RestClient.Builder client(RestClient.Builder builder, String key) {
        if (!key.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Set CARTIQ_GOOGLE_GEOCODING_API_KEY in the backend environment.");
        }
        return builder.baseUrl("https://geocode.googleapis.com")
            .defaultHeader("X-Goog-Api-Key", key)
            .defaultHeader("X-Goog-FieldMask", "results.addressComponents,results.location,results.postalAddress");
    }

    static UriBuilder pin(UriBuilder builder, String postcode) {
        return builder.path("/v4/geocode/address").queryParam("address.postalCode", postcode)
            .queryParam("address.regionCode", "IN").queryParam("languageCode", "en");
    }

    static UriBuilder reverse(UriBuilder builder, double latitude, double longitude) {
        return builder.path("/v4/geocode/location").queryParam("location.latitude", latitude)
            .queryParam("location.longitude", longitude).queryParam("languageCode", "en").queryParam("regionCode", "IN");
    }

    static List<Place> pins(JsonNode body, String postcode) {
        var results = results(body);
        var candidates = results.valueStream().map(result -> {
            var location = result.path("location");
            Double latitude = coordinate(location, "latitude", 90), longitude = coordinate(location, "longitude", 180);
            return place(result, latitude, longitude, true);
        }).filter(Objects::nonNull).filter(place -> postcode.equals(place.postcode())).distinct().toList();
        long named = candidates.stream().filter(place -> place.locality() != null || place.city() != null).count();
        long states = candidates.stream().map(Place::state).filter(Objects::nonNull).map(GoogleGeocoding::normalized).distinct().count();
        long points = candidates.stream().filter(place -> place.latitude() != null && place.longitude() != null).count();
        LOG.info("google_geocode operation=pin results={} indian_pin_matches={} named_candidates={} distinct_states={} candidates_with_coordinates={}",
            results.size(), candidates.size(), named, states, points);
        return candidates;
    }

    static Place point(JsonNode body, double latitude, double longitude) {
        var results = results(body);
        if (results.isEmpty()) {
            logReverse("NO_RESULTS", 0, 0, 0);
            throw new ApiException(HttpStatus.NOT_FOUND, "No location was found for this lookup.");
        }
        var places = results.valueStream().map(result -> place(result, latitude, longitude, false))
            .filter(Objects::nonNull).toList();
        var complete = places.stream().filter(place -> place.city() != null && place.state() != null).toList();
        if (complete.isEmpty()) {
            logReverse("MISSING_AREA_COMPONENTS", results.size(), places.size(), 0);
            throw unresolved();
        }
        Place candidate = complete.stream().filter(GoogleGeocoding::nonCityLocality).findFirst()
            .orElse(complete.get(0));
        if (places.stream().anyMatch(place -> !candidate.postcode().equals(place.postcode())
            || conflict(candidate.city(), place.city()) || conflict(candidate.state(), place.state())
            || conflict(candidate.district(), place.district())
            || (nonCityLocality(place) && conflict(candidate.locality(), place.locality())))) {
            logReverse("CONFLICTING_AREAS", results.size(), places.size(), complete.size());
            throw unresolved();
        }
        logReverse("RESOLVED", results.size(), places.size(), complete.size());
        return candidate;
    }

    private static void logReverse(String outcome, int results, int indianWithPin, int complete) {
        LOG.info("google_geocode operation=reverse outcome={} results={} indian_with_pin={} complete_areas={}",
            outcome, results, indianWithPin, complete);
    }

    private static boolean nonCityLocality(Place place) {
        return place.locality() != null && !normalized(place.locality()).equals(normalized(place.city()));
    }

    private static boolean conflict(String first, String second) {
        return first != null && second != null && !normalized(first).equals(normalized(second));
    }

    private static String normalized(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
            .strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static JsonNode results(JsonNode body) {
        var results = body.path("results");
        if (body.isObject() && body.isEmpty()) return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        if (!results.isArray() || results.valueStream().anyMatch(result -> !result.isObject())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Location service returned an invalid response.");
        }
        return results;
    }

    private static Place place(JsonNode result, Double latitude, Double longitude, boolean approximate) {
        var components = result.path("addressComponents");
        var postal = result.path("postalAddress");
        String country = component(components, "shortText", "country"), region = postal.path("regionCode").textValue();
        if (!"IN".equalsIgnoreCase(country == null ? region : country) || (region != null && !"IN".equalsIgnoreCase(region))) return null;
        String code = component(components, "longText", "postal_code"), postalCode = postal.path("postalCode").textValue();
        if (code != null && postalCode != null && !code.equals(postalCode)) return null;
        String postcode = code == null ? postalCode : code;
        if (postcode == null || !postcode.matches("[1-9][0-9]{5}")) return null;
        String state = component(components, "longText", "administrative_area_level_1");
        String postalState = prefer(null, postal, "administrativeArea");
        String shortState = component(components, "shortText", "administrative_area_level_1");
        if (conflict(state, postalState) && (shortState == null ||
            !normalized(postalState).replaceFirst("^in-", "").equals(normalized(shortState).replaceFirst("^in-", "")))) return null;
        return new Place(prefer(component(components, "longText", "sublocality_level_1", "sublocality_level_2",
                "sublocality_level_3", "sublocality_level_4", "sublocality_level_5", "sublocality", "neighborhood"),
            postal, "sublocality"), prefer(component(components, "longText", "locality", "postal_town"), postal, "locality"),
            component(components, "longText", "administrative_area_level_2"),
            state == null ? postalState : state, postcode, "in",
            latitude == null || longitude == null ? null : latitude,
            latitude == null || longitude == null ? null : longitude, approximate);
    }

    private static String prefer(String component, JsonNode postal, String field) {
        if (component != null) return component;
        String value = postal.path(field).textValue();
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String component(JsonNode components, String field, String... types) {
        for (var type : types) {
            for (var component : components) {
                var node = component.path("types");
                if (node.isArray() && node.valueStream().anyMatch(entry -> type.equals(entry.textValue()))) {
                    String text = component.path(field).textValue();
                    if (text != null && !text.isBlank()) return text.strip();
                }
            }
        }
        return null;
    }

    private static Double coordinate(JsonNode location, String key, int limit) {
        var node = location.path(key);
        double value = node.isNumber() ? node.asDouble() : Double.NaN;
        return Double.isFinite(value) && Math.abs(value) <= limit ? value : null;
    }

    private static ApiException unresolved() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "The location could not be matched to one Indian delivery area.");
    }
}
