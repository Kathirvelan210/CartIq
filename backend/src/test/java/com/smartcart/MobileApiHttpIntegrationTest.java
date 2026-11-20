package com.smartcart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.repository.Db;
import com.smartcart.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "server.address=127.0.0.1",
    "smartcart.sample-data=false",
    "smartcart.ai-url=http://127.0.0.1:1",
    "smartcart.price-forecast-enabled=true",
    "smartcart.import-starter-catalogue=true",
    "smartcart.secure-cookies=true",
    "smartcart.allowed-origins=http://localhost:8092"
})
@ActiveProfiles("test")
class MobileApiHttpIntegrationTest {
    private static final String LOCATION = "blr-indiranagar";
    private static final String PASSWORD = "Local-test-password!";
    private static final String REFRESH_COOKIE = "cartiq_refresh";
    private static final String EXPO_ORIGIN = "http://localhost:8092";
    private static final String DATABASE_NAME = "mobile_http_" + UUID.randomUUID().toString().replace("-", "");
    private static final String SIGNING_KEY = UUID.randomUUID().toString();

    @DynamicPropertySource
    static void localDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
            () -> "jdbc:h2:mem:" + DATABASE_NAME + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        registry.add("smartcart.jwt-secret", () -> SIGNING_KEY);
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired Db db;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder jwtEncoder;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void registrationCommitsHashedCredentialsAndLoginReturnsOwnProfile() throws Exception {
        String email = "Mobile-" + UUID.randomUUID() + "@example.test";
        Map<String, Object> registration = Map.of(
            "name", "  Mobile shopper  ", "email", email, "password", PASSWORD, "phone", "+919123456789");

        JsonNode registered = body(send("POST", "/api/auth/register", 201, null, registration));
        JsonNode profile = registered.path("user");
        String id = profile.path("id").asText();
        assertFalse(id.isBlank());
        assertEquals("Mobile shopper", profile.path("name").asText());
        assertEquals(email.toLowerCase(Locale.ROOT), profile.path("email").asText());
        assertEquals("customer", profile.path("role").asText());
        assertFalse(profile.path("sample").asBoolean(true));
        assertFalse(profile.has("passwordHash"));

        var stored = db.one("SELECT email,password_hash FROM users WHERE id=?", id);
        assertEquals(profile.path("email").asText(), Db.text(stored, "email"));
        String hash = Db.text(stored, "password_hash");
        assertTrue(hash.startsWith("$2"));
        assertNotEquals(PASSWORD, hash);
        assertTrue(passwords.matches(PASSWORD, hash));
        assertEquals("+919123456789", Db.text(db.one("SELECT phone FROM user_profiles WHERE user_id=?", id), "phone"));
        assertEquals(1, db.count("SELECT COUNT(*) FROM user_preferences WHERE user_id=?", id));

        assertError("CONFLICT", send("POST", "/api/auth/register", 409, null, registration));
        assertError("VALIDATION", send("POST", "/api/auth/register", 400, null,
            Map.of("name", "Shopper", "email", "invalid", "password", "short")));
        assertError("UNAUTHORIZED", send("POST", "/api/auth/login", 401, null,
            Map.of("email", email, "password", "Incorrect password")));
        assertEquals(1, db.count("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=?", id));

        JsonNode login = body(send("POST", "/api/auth/login", 200, null, Map.of("email", email, "password", PASSWORD)));
        assertEquals(profile, login.path("user"));
        assertEquals(profile, body(send("GET", "/api/me", 200, login.path("token").asText(), null)));
        assertError("UNAUTHORIZED", send("GET", "/api/me", 401, null, null));
        assertError("FORBIDDEN", send("GET", "/api/admin/local-stores", 403, login.path("token").asText(), null));
    }

    @Test
    void passwordChecksUseTheBCryptUtf8ByteLimit() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        String exactLimit = "\u00e9".repeat(36);
        String tooLong = exactLimit + "x";
        String endpoint = "/api/auth/register";
        send("POST", endpoint, 400, null, Map.of("name", "Mobile shopper", "email", email, "password", tooLong));
        JsonNode session = body(send("POST", endpoint, 201, null,
            Map.of("name", "Mobile shopper", "email", email, "password", exactLimit)));
        send("POST", "/api/auth/login", 200, null, Map.of("email", email, "password", exactLimit));
        assertError("UNAUTHORIZED", send("POST", "/api/auth/login", 401, null, Map.of("email", email, "password", tooLong)));
        send("POST", "/api/me/password", 400, session.path("token").asText(),
            Map.of("currentPassword", tooLong, "newPassword", PASSWORD));
        send("POST", "/api/auth/login", 200, null, Map.of("email", email, "password", exactLimit));
    }

    @Test
    void refreshRotatesAndLogoutRevokesAccessAndRefreshCredentials() throws Exception {
        HttpResponse<String> registration = registerShopper();
        JsonNode session = body(registration);
        String user = session.path("user").path("id").asText();
        HttpCookie first = refreshCookie(registration);
        String rawCookie = registration.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(first.isHttpOnly());
        assertTrue(first.getSecure());
        assertEquals("/api/auth", first.getPath());
        assertTrue(rawCookie.contains("SameSite=Strict"));
        assertEquals(30 * 86_400, first.getMaxAge());
        assertEquals(1, db.count("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash=? AND user_id=?",
            AuthService.hash(first.getValue()), user));
        assertEquals(0, db.count("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash=?", first.getValue()));

        String initialToken = session.path("token").asText();
        int signatureOffset = initialToken.lastIndexOf('.') + 1;
        String changedSignature = initialToken.charAt(signatureOffset) == 'A' ? "B" : "A";
        String tampered = initialToken.substring(0, signatureOffset) + changedSignature + initialToken.substring(signatureOffset + 1);
        assertError("UNAUTHORIZED", send("GET", "/api/me", 401, tampered, null));
        assertError("UNAUTHORIZED", send("GET", "/api/me", 401, expiredAccessToken(user), null));
        HttpResponse<String> refreshed = send("POST", "/api/auth/refresh", 200, null, null, "Cookie", cookieHeader(first));
        HttpCookie second = refreshCookie(refreshed);
        String accessToken = body(refreshed).path("token").asText();
        assertEquals(session.path("user"), body(refreshed).path("user"));
        assertNotEquals(first.getValue(), second.getValue());
        assertError("UNAUTHORIZED", send("POST", "/api/auth/refresh", 401, null, null, "Cookie", cookieHeader(first)));
        assertEquals(1, db.count("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=?", user));
        assertEquals(session.path("user"), body(send("GET", "/api/me", 200, accessToken, null)));

        var logout = send("POST", "/api/auth/logout", 204, accessToken, null, "Cookie", cookieHeader(second));
        assertEquals(0, refreshCookie(logout).getMaxAge());
        assertEquals(0, db.count("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=?", user));
        assertError("UNAUTHORIZED", send("GET", "/api/me", 401, accessToken, null));
        assertError("UNAUTHORIZED", send("POST", "/api/auth/refresh", 401, null, null, "Cookie", cookieHeader(second)));

        HttpCookie expired = refreshCookie(registerShopper());
        assertEquals(1, db.run("UPDATE refresh_tokens SET expires_at=? WHERE token_hash=?",
            Instant.now().minusSeconds(120), AuthService.hash(expired.getValue())));
        assertError("UNAUTHORIZED", send("POST", "/api/auth/refresh", 401, null, null, "Cookie", cookieHeader(expired)));
    }

    @Test
    void catalogueSearchDetailsAndUnpricedComparisonUseDatabaseRecords() throws Exception {
        String page = "/api/products/page?locationId=" + LOCATION;
        JsonNode catalogue = body(send("GET", page + "&pageSize=10", 200, null, null));
        assertEquals(db.count("SELECT COUNT(*) FROM product_variants v JOIN products p ON p.id=v.product_id WHERE p.active=TRUE"),
            catalogue.path("totalElements").asLong());
        assertEquals(10, catalogue.path("items").size());
        assertTrue(catalogue.path("hasNext").asBoolean());

        JsonNode categories = body(send("GET", "/api/categories", 200, null, null));
        var storedCategories = db.rows("SELECT id,name FROM product_categories ORDER BY name");
        assertFalse(storedCategories.isEmpty());
        assertEquals(storedCategories.size(), categories.size());
        for (int i = 0; i < categories.size(); i++) {
            assertEquals(Db.text(storedCategories.get(i), "id"), categories.get(i).path("id").asText());
            assertEquals(Db.text(storedCategories.get(i), "name"), categories.get(i).path("name").asText());
        }

        var milk = db.one("SELECT p.name,p.brand,v.pack_label,c.name AS category FROM product_variants v "
            + "JOIN products p ON v.product_id=p.id JOIN product_categories c ON c.id=p.category_id WHERE v.id=?", "milk");
        String details = "/api/products/milk?locationId=" + LOCATION;
        JsonNode product = body(send("GET", details, 200, null, null));
        assertEquals("milk", product.path("id").asText());
        assertEquals(Db.text(milk, "name"), product.path("name").asText());
        assertEquals(Db.text(milk, "brand"), product.path("brand").asText());
        assertEquals(Db.text(milk, "pack_label"), product.path("quantity").asText());
        assertEquals(Db.text(milk, "category"), product.path("category").asText());
        assertTrue(product.path("offers").isArray() && product.path("offers").isEmpty());

        JsonNode matches = body(send("GET", page + "&q=Milk&category=dairy", 200, null, null)).path("items");
        assertFalse(matches.isEmpty());
        boolean sawMilk = false;
        for (JsonNode item : matches) {
            assertEquals(product.path("category"), item.path("category"));
            sawMilk |= product.equals(item);
        }
        assertTrue(sawMilk);
        JsonNode reorderedWords = body(send("GET", page + "&q=milk%20amul%201%20litre&category=dairy", 200, null, null)).path("items");
        assertEquals(json.createArrayNode().add(product), reorderedWords);
        assertTrue(body(send("GET", page + "&q=%25_%25", 200, null, null)).path("items").isEmpty());
        assertTrue(body(send("GET", page + "&q=not-in-the-catalogue-01", 200, null, null)).path("items").isEmpty());
        assertError("NOT_FOUND", send("GET", "/api/products/unknown?locationId=" + LOCATION, 404, null, null));

        String compare = "/api/products/milk/comparison?locationId=" + LOCATION;
        JsonNode comparison = body(send("GET", compare + "&latitude=12.975&longitude=77.639&radiusKm=5&quantity=2&fulfillment=delivery",
            200, null, null));
        assertEquals(product, comparison.path("product"));
        assertEquals(2, comparison.path("quantity").asInt());
        assertEquals("delivery", comparison.path("fulfillment").asText());
        assertTrue(comparison.path("lowestEligible").isNull());
        assertTrue(comparison.path("savingsMinor").isNull());
        var platformNames = new HashMap<String, String>();
        db.rows("SELECT id,name FROM grocery_platforms").forEach(row ->
            platformNames.put(Db.text(row, "id"), Db.text(row, "name")));
        assertEquals(platformNames.size(), comparison.path("options").size());
        for (JsonNode option : comparison.path("options")) {
            assertEquals(platformNames.get(option.path("source").asText()), option.path("name").asText());
            assertTrue(option.path("priceMinor").isNull());
            assertFalse(option.path("eligible").asBoolean(true));
        }
        assertError("VALIDATION", send("GET", compare, 400, null, null));
    }

    @Test
    void analyticsReturnsLegacyObservedHistoryWithoutAnUnapprovedEstimate() throws Exception {
        String platformProductId = "mobile-test-milk";
        db.insert("platform_products", Db.fields("id", platformProductId, "variant_id", "milk", "platform_id", "blinkit",
            "retailer_sku", "test-milk"));
        for (int i = 0; i < 8; i++) {
            db.insert("price_history", Db.fields("id", UUID.randomUUID().toString(), "platform_product_id", platformProductId,
                "location_id", LOCATION, "price_minor", 6000 + i * 100, "observed_at", Instant.now().minus(Duration.ofDays(i)),
                "source", "TEST_FIXTURE", "verification", "VERIFIED"));
        }

        JsonNode analytics = body(send("GET", "/api/products/milk/analytics?locationId=" + LOCATION, 200, null, null));
        assertEquals(8, analytics.path("history").size());
        assertEquals(6000, analytics.path("minMinor").asLong());
        assertEquals(6700, analytics.path("maxMinor").asLong());
        JsonNode forecast = analytics.path("forecast");
        assertTrue(forecast.path("estimateMinor").isNull());
        assertTrue(forecast.path("lowMinor").isNull());
        assertTrue(forecast.path("highMinor").isNull());
        assertEquals("Forecasts disabled", forecast.path("method").asText());
    }

    @Test
    void cartWriteReadUpdateAndDeleteStayScopedToEachShopper() throws Exception {
        JsonNode first = body(registerShopper());
        JsonNode second = body(registerShopper());
        String firstId = first.path("user").path("id").asText();
        String secondId = second.path("user").path("id").asText();
        String owner = first.path("token").asText();
        String other = second.path("token").asText();
        String path = "/api/me/cart";

        assertError("UNAUTHORIZED", send("GET", path, 401, null, null));
        assertError("UNAUTHORIZED", send("PUT", path, 401, null, cartInput(1)));
        assertEquals(json.createArrayNode(), body(send("GET", path, 200, owner, null)));
        assertError("VALIDATION", send("PUT", path, 400, owner, cartInput(0)));
        assertEquals(0, db.count("SELECT COUNT(*) FROM shopping_carts WHERE user_id=?", firstId));

        JsonNode added = body(send("PUT", path, 200, owner, cartInput(1)));
        String lineId = added.path("id").asText();
        assertFalse(lineId.isBlank());
        assertEquals("milk", added.path("productId").asText());
        assertEquals(LOCATION, added.path("locationId").asText());
        assertEquals(1, added.path("quantity").asInt());
        assertEquals(1, storedQuantity(firstId, lineId));
        assertEquals(json.createArrayNode(), body(send("GET", path, 200, other, null)));

        JsonNode updated = body(send("PUT", path, 200, owner, cartInput(3)));
        assertEquals(lineId, updated.path("id").asText());
        assertEquals(3, storedQuantity(firstId, lineId));
        assertEquals(json.createArrayNode().add(updated), body(send("GET", path, 200, owner, null)));

        JsonNode otherLine = body(send("PUT", path, 200, other, cartInput(2)));
        String otherLineId = otherLine.path("id").asText();
        assertNotEquals(lineId, otherLineId);
        assertEquals(2, storedQuantity(secondId, otherLineId));
        assertError("NOT_FOUND", send("DELETE", path + "/" + lineId, 404, other, null));
        assertEquals(3, storedQuantity(firstId, lineId));
        assertEquals(json.createArrayNode().add(otherLine), body(send("GET", path, 200, other, null)));

        send("DELETE", path + "/" + lineId, 204, owner, null);
        assertEquals(0, db.count("SELECT COUNT(*) FROM cart_items WHERE id=?", lineId));
        assertEquals(0, db.count("SELECT COUNT(*) FROM purchase_preferences WHERE cart_item_id=?", lineId));
        assertEquals(json.createArrayNode(), body(send("GET", path, 200, owner, null)));
        assertEquals(2, storedQuantity(secondId, otherLineId));
    }

    @Test
    void expoOriginCanPreflightCredentialsWhileUnconfiguredOriginsAreRejected() throws Exception {
        var response = send("OPTIONS", "/api/me/cart", 200, null, null,
            "Origin", EXPO_ORIGIN, "Access-Control-Request-Method", "PUT",
            "Access-Control-Request-Headers", "authorization,content-type");
        assertEquals(EXPO_ORIGIN, response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
        assertEquals("true", response.headers().firstValue("Access-Control-Allow-Credentials").orElseThrow());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Headers").orElseThrow().contains("authorization"));
        for (String rejected : new String[]{"https://example.test", "http://localhost:8188"}) {
            var forbidden = send("OPTIONS", "/api/me/cart", 403, null, null,
                "Origin", rejected, "Access-Control-Request-Method", "PUT");
            assertTrue(forbidden.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
        }
    }

    private String expiredAccessToken(String user) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("smartcart-ai").subject(user)
            .issuedAt(now.minusSeconds(3600)).expiresAt(now.minusSeconds(120))
            .claim("scope", "customer").claim("version", 0).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private HttpResponse<String> registerShopper() throws Exception {
        return send("POST", "/api/auth/register", 201, null,
            Map.of("name", "Mobile shopper", "email", UUID.randomUUID() + "@example.test", "password", PASSWORD));
    }

    private HttpCookie refreshCookie(HttpResponse<String> response) {
        var cookie = HttpCookie.parse(response.headers().firstValue("Set-Cookie").orElseThrow()).get(0);
        assertEquals(REFRESH_COOKIE, cookie.getName());
        return cookie;
    }

    private String cookieHeader(HttpCookie cookie) {
        return cookie.getName() + "=" + cookie.getValue();
    }

    private Map<String, Object> cartInput(int quantity) {
        return Map.of("productId", "milk", "locationId", LOCATION, "quantity", quantity,
            "preferences", Map.of("intent", "ESSENTIAL", "monitorPrice", false));
    }

    private long storedQuantity(String user, String lineId) {
        return Db.num(db.one("SELECT i.quantity FROM cart_items i JOIN shopping_carts c ON c.id=i.cart_id "
            + "WHERE c.user_id=? AND i.id=?", user, lineId), "quantity");
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        return json.readTree(response.body());
    }

    private void assertError(String code, HttpResponse<String> response) throws Exception {
        JsonNode error = body(response);
        assertEquals(code, error.path("code").asText());
        assertFalse(error.path("message").asText().isBlank());
    }

    private HttpResponse<String> send(String method, String path, int status, String token, Object payload,
                                      String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(10)).header("Accept", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (headers.length > 0) request.headers(headers);
        if (payload != null) request.header("Content-Type", "application/json");
        request.method(method, payload == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), method + " " + path);
        return response;
    }
}
