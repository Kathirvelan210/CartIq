package com.smartcart.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartcart.exception.ApiException;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/** Delivery-area geocoding through the configured provider. */
@Component
public class ShoppingGeocoder implements AutoCloseable {
    private static final String PUBLIC_HOST = "nominatim.openstreetmap.org";
    private static final long LOOKUP_INTERVAL = Duration.ofSeconds(1).toNanos();
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private static final int CACHE_LIMIT = 128;
    private final RestClient client;
    private final CloseableHttpClient transport;
    private final boolean publicService;
    private final boolean deviceGpsAllowed;
    private final boolean googleDemo;
    private final Point publicReversePoint;
    private final Map<Pin, Cached<List<Place>>> pinCache = new LinkedHashMap<>();
    private final Map<Point, Cached<Place>> reverseCache = new LinkedHashMap<>();
    private long nextLookup = System.nanoTime();
    private boolean inFlight;

    private record Pin(String postcode, String locality) {}
    private record Point(double latitude, double longitude) {}
    private record Cached<T>(T value, long expiresAt) {}

    public record Place(String locality, String city, String district, String state, String postcode, String countryCode,
                        Double latitude, Double longitude, boolean approximate) {}

    @Autowired
    public ShoppingGeocoder(@Value("${CARTIQ_GEOCODER_URL:}") String endpoint,
                            @Value("${CARTIQ_GEOCODER_CONTACT:}") String contact,
                            @Value("${CARTIQ_GEOCODER_PUBLIC_REVERSE_POINT:}") String reversePoint,
                            @Value("${CARTIQ_GEOCODER_ALLOW_DEVICE_GPS:false}") boolean allowDeviceGps,
                            @Value("${CARTIQ_GEOCODER_PROVIDER:nominatim}") String provider,
                            @Value("${CARTIQ_GOOGLE_GEOCODING_API_KEY:}") String googleKey,
                            Environment environment) {
        this(endpoint, contact, reversePoint, allowDeviceGps, provider, googleKey,
            environment.matchesProfiles("local & !prod", "test & !prod"));
    }

    ShoppingGeocoder(String endpoint, String contact, String reversePoint) {
        this(endpoint, contact, reversePoint, false, "nominatim", "", false);
    }

    ShoppingGeocoder(String endpoint, String contact, String reversePoint, boolean allowDeviceGps,
                    String provider, String googleKey, boolean demoProfile) {
        if (!List.of("nominatim", "google-demo").contains(provider)) {
            throw new IllegalArgumentException("Set CARTIQ_GEOCODER_PROVIDER to nominatim or google-demo.");
        }
        googleDemo = "google-demo".equals(provider);
        if (googleDemo && !demoProfile) throw new IllegalArgumentException("Google demo geocoding requires the local or test profile.");
        var builder = RestClient.builder();
        if (googleDemo) GoogleGeocoding.client(builder, googleKey);
        if (!googleDemo && endpoint.isBlank()) {
            client = null;
            transport = null;
            publicService = false;
            deviceGpsAllowed = false;
            publicReversePoint = null;
            return;
        }
        URI uri = googleDemo ? null : geocoderUri(endpoint);
        publicService = uri != null && PUBLIC_HOST.equalsIgnoreCase(uri.getHost().replaceFirst("\\.$", ""));
        deviceGpsAllowed = googleDemo || (!publicService && allowDeviceGps);
        if (publicService && ((uri.getPort() != -1 && uri.getPort() != 443)
            || !List.of("", "/").contains(uri.getPath()))) throw invalidEndpoint();
        publicReversePoint = publicService ? configuredPoint(reversePoint) : null;
        if (uri != null) builder.baseUrl(uri.toString().replaceAll("/+$", ""))
            .defaultHeader("User-Agent", userAgent(contact, publicService));
        var manager = PoolingHttpClientConnectionManagerBuilder.create()
            .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(2500)).build())
            .setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(Timeout.ofSeconds(3)).build()).build();
        transport = HttpClients.custom().setConnectionManager(manager).disableAutomaticRetries().disableRedirectHandling().build();
        var factory = new HttpComponentsClientHttpRequestFactory(transport);
        factory.setConnectionRequestTimeout(Duration.ofMillis(2500));
        factory.setReadTimeout(Duration.ofSeconds(3));
        client = builder.requestFactory(factory).build();
    }

    private static URI geocoderUri(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint.strip());
        } catch (IllegalArgumentException invalid) {
            throw invalidEndpoint();
        }
        String host = uri.getHost();
        if (host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null ||
            !("https".equals(uri.getScheme()) ||
                ("http".equals(uri.getScheme()) && List.of("localhost", "127.0.0.1", "[::1]").contains(host)))) {
            throw invalidEndpoint();
        }
        return uri;
    }

    private static IllegalArgumentException invalidEndpoint() {
        return new IllegalArgumentException("Configure an HTTPS geocoder URL or a loopback development geocoder.");
    }

    ShoppingGeocoder(RestClient client) {
        this(client, false);
    }

    ShoppingGeocoder(RestClient.Builder builder, String key) {
        this(GoogleGeocoding.client(builder, key).build(), true);
    }

    private ShoppingGeocoder(RestClient client, boolean googleDemo) {
        this.client = client;
        transport = null;
        this.googleDemo = googleDemo;
        publicService = false;
        deviceGpsAllowed = true;
        publicReversePoint = null;
    }

    private static String userAgent(String contact, boolean required) {
        String email = contact.strip();
        if (email.isEmpty() && !required) return "CartIQ/1.0 (user-initiated delivery location)";
        if (email.length() > 254 || !email.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+")) {
            throw new IllegalArgumentException("Set CARTIQ_GEOCODER_CONTACT to a monitored operator email address.");
        }
        return "CartIQ/1.0 (contact: " + email + ")";
    }

    private static Point configuredPoint(String input) {
        if (input.isBlank()) return null;
        String[] fields = input.split(",", -1);
        if (fields.length == 2 && isDecimal(fields[0]) && isDecimal(fields[1])) {
            var point = new Point(Double.parseDouble(fields[0].strip()), Double.parseDouble(fields[1].strip()));
            if (validPoint(point.latitude(), point.longitude())) return point;
        }
        throw new IllegalArgumentException("Set CARTIQ_GEOCODER_PUBLIC_REVERSE_POINT to a valid latitude,longitude pair.");
    }

    private static boolean isDecimal(String value) {
        return value.strip().matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)");
    }

    @jakarta.annotation.PreDestroy
    @Override
    public void close() throws java.io.IOException {
        if (transport != null) transport.close();
    }

    public List<Place> pin(String postcode, String locality) {
        if (postcode == null || !postcode.matches("[1-9][0-9]{5}")) {
            throw ApiException.bad("Enter a six-digit Indian PIN code that starts with 1–9.");
        }
        String term = locality == null ? "" : locality.strip();
        if (term.length() > 100 || term.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.bad("Enter a locality name up to 100 characters.");
        }
        if ((publicService || googleDemo) && !term.isEmpty()) {
            throw ApiException.bad("Search by PIN alone and select a locality from the results.");
        }
        return lookup(pinCache, new Pin(postcode, term), () -> search(postcode, term));
    }

    private List<Place> search(String postcode, String term) {
        if (googleDemo) return GoogleGeocoding.pins(fetch(builder -> GoogleGeocoding.pin(builder, postcode).build()), postcode);
        JsonNode body = fetch(builder -> {
            var uri = builder.path("/search").queryParam("format", "jsonv2").queryParam("addressdetails", 1)
                .queryParam("countrycodes", "in").queryParam("postalcode", postcode).queryParam("limit", 25);
            if (term.isEmpty()) return uri.build();
            return uri.queryParam("city", "{locality}").build(term);
        });
        if (!body.isArray()) throw invalidResponse();
        var places = new ArrayList<Place>();
        for (var result : body) {
            var address = result.path("address");
            if (!address.isObject()) throw invalidResponse();
            if (!"in".equalsIgnoreCase(first(address, "country_code"))
                || !postcode.equals(first(address, "postcode"))) continue;
            var place = place(result, coordinate(result, "lat", 90), coordinate(result, "lon", 180), true);
            if (!places.contains(place)) places.add(place);
        }
        return List.copyOf(places);
    }

    public Place reverse(double latitude, double longitude) {
        if (!validPoint(latitude, longitude)) {
            throw ApiException.bad("Provide valid latitude and longitude.");
        }
        var point = new Point(latitude, longitude);
        if (!googleDemo && !deviceGpsAllowed && !point.equals(publicReversePoint)) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Device location lookup is unavailable. Search by PIN instead.");
        }
        return reverse(point);
    }

    public boolean available() {
        return client != null;
    }

    public boolean googleDemo() {
        return googleDemo;
    }

    public boolean deviceGpsAllowed() {
        return deviceGpsAllowed;
    }

    public Place reversePinPlace(Place candidate) {
        if (googleDemo) throw new ApiException(HttpStatus.FORBIDDEN, "Use the separate forward and reverse lookups for the Google demo.");
        if (candidate == null || candidate.postcode() == null || candidate.latitude() == null || candidate.longitude() == null) {
            throw ApiException.bad("Select a PIN locality with coordinates.");
        }
        synchronized (this) {
            var cached = pinCache.get(new Pin(candidate.postcode(), ""));
            if (cached == null || System.nanoTime() - cached.expiresAt() >= 0 || !cached.value().contains(candidate)) {
                throw new ApiException(HttpStatus.CONFLICT, "Search the PIN again to verify this locality.");
            }
        }
        // Only reverse a provider-supplied, cached PIN point. Never forward arbitrary coordinates here.
        var address = reverse(new Point(candidate.latitude(), candidate.longitude()));
        if (!candidate.postcode().equals(address.postcode())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "The locality resolves to a different or unknown PIN.");
        }
        return address;
    }

    private Place reverse(Point point) {
        return lookup(reverseCache, point, () -> resolvePoint(point.latitude(), point.longitude()));
    }

    private static boolean validPoint(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
            && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180;
    }

    private Place resolvePoint(double latitude, double longitude) {
        if (googleDemo) {
            return GoogleGeocoding.point(fetch(builder -> GoogleGeocoding.reverse(builder, latitude, longitude).build()),
                latitude, longitude);
        }
        JsonNode body = fetch(builder -> builder.path("/reverse")
            .queryParam("format", "jsonv2").queryParam("addressdetails", 1)
            .queryParam("lat", latitude).queryParam("lon", longitude).build());
        if (!body.isObject() || !body.path("address").isObject()) throw invalidResponse();
        String country = first(body.path("address"), "country_code");
        if (country == null) throw invalidResponse();
        if (!"in".equalsIgnoreCase(country)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "This location is outside the Indian retailer lookup area.");
        }
        return place(body, latitude, longitude, false);
    }

    private <K, T> T lookup(Map<K, Cached<T>> cache, K key, Supplier<T> request) {
        synchronized (this) {
            if (client == null) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Location lookup is not configured.");
            long now = System.nanoTime();
            cache.values().removeIf(entry -> now - entry.expiresAt() >= 0);
            var cached = cache.get(key);
            if (cached != null) return cached.value();
            if (inFlight || now - nextLookup < 0) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Wait a moment before searching again.");
            }
            inFlight = true;
        }
        try {
            T value = request.get();
            if (!googleDemo) synchronized (this) {
                if (cache.size() >= CACHE_LIMIT) cache.remove(cache.keySet().iterator().next());
                cache.put(key, new Cached<>(value, System.nanoTime() + CACHE_TTL.toNanos()));
            }
            return value;
        } finally {
            synchronized (this) {
                // Measure from completion so even connection setup cannot compress arrivals.
                long afterRequest = System.nanoTime() + LOOKUP_INTERVAL;
                if (afterRequest - nextLookup > 0) nextLookup = afterRequest;
                inFlight = false;
            }
        }
    }

    private JsonNode fetch(Function<UriBuilder, URI> uri) {
        try {
            JsonNode body = client.get().uri(uri).accept(MediaType.APPLICATION_JSON).retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                    int status = response.getStatusCode().value();
                    if (status == 429) {
                        defer(retryDelay(response.getHeaders().getFirst("Retry-After")));
                        throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Location lookup is busy. Try again later.");
                    }
                    if (status == 404) throw new ApiException(HttpStatus.NOT_FOUND, "No location was found for this lookup.");
                    if (googleDemo && List.of(400, 401, 403).contains(status)) {
                        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Google demo geocoding was rejected. Check backend key and API access.");
                    }
                    throw unavailable();
                })
                .body(JsonNode.class);
            if (body == null) throw invalidResponse();
            return body;
        } catch (RestClientException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof SocketTimeoutException) {
                    throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "Location lookup timed out. Try again.");
                }
            }
            if (failure instanceof ResourceAccessException) throw unavailable();
            throw invalidResponse();
        }
    }

    private static long retryDelay(String raw) {
        if (raw == null) return 30;
        String retry = raw.strip();
        if (retry.matches("[0-9]{1,5}")) return Math.max(1, Math.min(300, Integer.parseInt(retry)));
        try {
            Instant retryAt = ZonedDateTime.parse(retry, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return Math.max(1, Math.min(300, Duration.between(Instant.now(), retryAt).toSeconds() + 1));
        } catch (DateTimeParseException invalid) {
            return 30;
        }
    }

    private synchronized void defer(long seconds) {
        long retryAt = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
        if (retryAt - nextLookup > 0) nextLookup = retryAt;
    }

    private static ApiException invalidResponse() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "Location service returned an invalid response.");
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Location lookup is temporarily unavailable.");
    }

    private static Place place(JsonNode result, Double latitude, Double longitude, boolean approximate) {
        var address = result.path("address");
        return new Place(first(address, "neighbourhood", "suburb", "quarter", "village", "hamlet", "town", "city_district"),
            first(address, "city", "town", "municipality", "village"), first(address, "state_district", "county", "district"),
            first(address, "state"), first(address, "postcode"), "in",
            latitude == null || longitude == null ? null : latitude,
            latitude == null || longitude == null ? null : longitude, approximate);
    }

    private static String first(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = node.path(key).textValue();
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static Double coordinate(JsonNode node, String field, int limit) {
        var input = node.path(field);
        if (!input.isNumber() && !input.isTextual()) return null;
        double value = input.asDouble(Double.NaN);
        return Double.isFinite(value) && Math.abs(value) <= limit ? value : null;
    }
}
