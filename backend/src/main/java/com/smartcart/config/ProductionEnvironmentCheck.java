package com.smartcart.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

/** Fails before Spring creates connections, credentials clients, or the HTTP listener. */
public class ProductionEnvironmentCheck implements EnvironmentPostProcessor, Ordered {
    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication ignored) {
        if (env.acceptsProfiles(Profiles.of("prod"))) validate(env);
    }

    static void validate(Environment env) {
        require(!env.acceptsProfiles(Profiles.of("local", "mysql-local", "test")),
            "Activate prod separately from local and test profiles.");
        String jwt = property(env, "smartcart.jwt-secret");
        require(keyLength(jwt) >= 32, "Set JWT_SECRET to at least 32 bytes.");
        String origins = property(env, "smartcart.allowed-origins");
        require(!origins.isBlank() && Arrays.stream(origins.split(",", -1)).map(String::trim)
            .allMatch(ProductionEnvironmentCheck::httpsOrigin), "Set explicit HTTPS values in ALLOWED_ORIGINS.");
        require(!property(env, "spring.datasource.username").isBlank()
            && !"root".equalsIgnoreCase(property(env, "spring.datasource.username").trim())
            && !property(env, "spring.datasource.password").isBlank(), "Set the dedicated runtime database identity.");
        validateJdbc(property(env, "spring.datasource.url"));
        require("false".equals(property(env, "spring.flyway.enabled"))
            && "true".equals(property(env, "spring.flyway.clean-disabled"))
            && "validate".equals(property(env, "spring.jpa.hibernate.ddl-auto"))
            && !"always".equalsIgnoreCase(property(env, "spring.sql.init.mode")),
            "Keep production startup schema mutations disabled.");
        require("true".equals(property(env, "smartcart.secure-cookies"))
            && "false".equals(property(env, "smartcart.sample-data"))
            && "false".equals(property(env, "smartcart.import-starter-catalogue"))
            && !"true".equalsIgnoreCase(property(env, "QUICKCOMMERCE_SHOPPING_ENABLED"))
            && !"true".equalsIgnoreCase(property(env, "smartcart.quickcommerce.enabled")),
            "Keep production cookies secure and sample and paid retailer features disabled.");
        String ai = property(env, "smartcart.ai-secret");
        if (!ai.isBlank() || "true".equals(property(env, "smartcart.price-forecast-enabled"))
            || "true".equals(property(env, "CARTIQ_APPROVED_FORECAST_ENABLED"))) {
            require(keyLength(ai) >= 32 && !ai.equals(jwt), "Set a separate AI_SERVICE_SECRET of at least 32 bytes.");
        }
        if ("true".equals(property(env, "smartcart.firebase-enabled"))) {
            require(property(env, "smartcart.firebase-project-id").matches("[a-z][a-z0-9-]{4,28}[a-z0-9]"),
                "Set FIREBASE_PROJECT_ID before enabling Firebase.");
        }
    }

    private static void validateJdbc(String value) {
        URI uri = value.startsWith("jdbc:mysql://") ? uri(value.substring(5)) : null;
        require(uri != null && uri.getHost() != null && uri.getUserInfo() == null && uri.getFragment() == null
            && uri.getPath().matches("/[a-zA-Z0-9_-]+"), "Set DB_URL to a single MySQL host and schema.");
        String host = uri.getHost();
        var settings = new HashMap<String, String>();
        for (String pair : (uri.getQuery() == null ? "" : uri.getQuery()).split("&")) {
            String[] entry = pair.split("=", 2);
            require(entry.length == 2 && settings.putIfAbsent(entry[0].toLowerCase(Locale.ROOT), entry[1]) == null,
                "Set explicit, unambiguous DB_URL options.");
        }
        require(settings.keySet().stream().noneMatch(
            key -> key.matches("user|password|usessl|requiressl|verifyservercertificate")),
            "Supply database credentials separately and use sslMode for JDBC TLS.");
        String ssl = settings.get("sslmode");
        boolean isolated = ("db".equals(host) && uri.getPort() == 3306 && "/cartiq".equals(uri.getPath()))
            || "127.0.0.1".equals(host) || "localhost".equals(host) || "[::1]".equals(host);
        require("VERIFY_IDENTITY".equals(ssl) || (isolated && "REQUIRED".equals(ssl)),
            "Require MySQL TLS and verify the identity of remote database hosts.");
    }

    private static URI uri(String value) {
        try { return URI.create(value); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    private static boolean httpsOrigin(String value) {
        URI uri = uri(value);
        return uri != null && "https".equals(uri.getScheme()) && uri.getHost() != null
            && uri.getRawPath().isEmpty() && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
    }

    private static int keyLength(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String property(Environment env, String key) {
        try { return env.getProperty(key, ""); }
        catch (IllegalArgumentException unresolved) { return ""; }
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException(message);
    }
}
