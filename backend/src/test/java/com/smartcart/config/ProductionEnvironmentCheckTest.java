package com.smartcart.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class ProductionEnvironmentCheckTest {
    private MockEnvironment release() {
        MockEnvironment env = new MockEnvironment()
            .withProperty("smartcart.jwt-secret", "isolated-test-key".repeat(4))
            .withProperty("smartcart.allowed-origins", "https://localhost:8443")
            .withProperty("spring.datasource.url", "jdbc:mysql://db:3306/cartiq?connectionTimeZone=UTC&sslMode=REQUIRED")
            .withProperty("spring.datasource.username", "fixture-api")
            .withProperty("spring.datasource.password", "fixture-password")
            .withProperty("spring.flyway.enabled", "false")
            .withProperty("spring.flyway.clean-disabled", "true")
            .withProperty("spring.jpa.hibernate.ddl-auto", "validate")
            .withProperty("smartcart.secure-cookies", "true")
            .withProperty("smartcart.sample-data", "false")
            .withProperty("smartcart.import-starter-catalogue", "false");
        env.setActiveProfiles("prod");
        return env;
    }

    @Test void permitsIsolatedComposeAndCertificateVerifiedRemoteMysql() {
        assertDoesNotThrow(() -> ProductionEnvironmentCheck.validate(release()));
        assertDoesNotThrow(() -> ProductionEnvironmentCheck.validate(release().withProperty(
            "spring.datasource.url", "jdbc:mysql://db.example.invalid/cartiq?sslMode=VERIFY_IDENTITY")));
    }

    @Test void rejectsUnsafeEndpointsWithoutEchoingValues() {
        var env = release().withProperty("spring.datasource.url", "jdbc:mysql://db.example.invalid/cartiq?sslMode=REQUIRED");
        var error = assertThrows(IllegalStateException.class, () -> ProductionEnvironmentCheck.validate(env));
        assertFalse(error.getMessage().contains("db.example.invalid"));
        assertThrows(IllegalStateException.class, () -> ProductionEnvironmentCheck.validate(release().withProperty(
            "smartcart.allowed-origins", "http://localhost:8443")));
    }

    @Test void rejectsStartupSchemaMutationsAndMixedProfiles() {
        assertThrows(IllegalStateException.class, () -> ProductionEnvironmentCheck.validate(release()
            .withProperty("spring.flyway.enabled", "true")));
        var env = release();
        env.setActiveProfiles("prod", "local");
        assertThrows(IllegalStateException.class, () -> ProductionEnvironmentCheck.validate(env));
    }

    @Test void requiresIndependentOptionalServiceSecretsAndFirebaseProject() {
        assertThrows(IllegalStateException.class, () -> ProductionEnvironmentCheck.validate(release()
            .withProperty("smartcart.ai-secret", "isolated-test-key".repeat(4))));
        assertThrows(IllegalStateException.class, () -> ProductionEnvironmentCheck.validate(release()
            .withProperty("smartcart.firebase-enabled", "true")));
        assertDoesNotThrow(() -> ProductionEnvironmentCheck.validate(release()
            .withProperty("smartcart.firebase-enabled", "true").withProperty("smartcart.firebase-project-id", "cartiq-test")));
    }
}
