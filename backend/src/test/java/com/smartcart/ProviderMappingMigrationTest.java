package com.smartcart;

import com.smartcart.config.ReferenceData;
import com.smartcart.repository.Db;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static com.smartcart.repository.Db.fields;
import static com.smartcart.repository.Db.id;
import static com.smartcart.repository.Db.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderMappingMigrationTest {
    @Test
    void preservesLegacyMappingOnH2Upgrade() {
        exerciseUpgrade(new DriverManagerDataSource("jdbc:h2:mem:mapping-upgrade-" + id()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CARTIQ_MAPPING_UPGRADE_MYSQL", matches = "true")
    void preservesLegacyMappingOnMysqlUpgrade() {
        var dataSource = new DriverManagerDataSource(
            "jdbc:mysql://127.0.0.1:13316/cartiq_mapping_upgrade?connectionTimeZone=UTC",
            System.getenv("CARTIQ_CATALOGUE_MYSQL_USERNAME"), System.getenv("CARTIQ_CATALOGUE_MYSQL_PASSWORD"));
        var db = new Db(new JdbcTemplate(dataSource));
        assertEquals("cartiq_mapping_upgrade", text(db.one("SELECT DATABASE() AS schema_name"), "schema_name"));
        exerciseUpgrade(dataSource);
    }

    private void exerciseUpgrade(DriverManagerDataSource dataSource) {
        var db = new Db(new JdbcTemplate(dataSource));
        var beforeUpgrade = Flyway.configure().dataSource(dataSource).target("10").load();
        assertEquals(10, beforeUpgrade.migrate().migrationsExecuted);
        new ReferenceData(db, false).run(new DefaultApplicationArguments());

        String variant = id(), reviewer = id(), area = id(), price = id(), history = id();
        String listing = variant + "-blinkit";
        var observed = Instant.parse("2025-01-01T00:00:00Z");
        db.insert("users", fields("id", reviewer, "name", "Migration fixture reviewer",
            "email", reviewer + "@example.invalid", "password_hash", "unused", "role", "admin"));
        db.insert("delivery_locations", fields("id", area, "name", "Migration fixture area",
            "city", "Migration fixture city", "postcode", "234567"));
        db.insert("products", fields("id", variant, "category_id", "pantry", "name", "Migration fixture",
            "brand", "Fixture", "variant_name", "Plain", "description", "Migration test",
            "color", "#123456", "art", "bag"));
        db.insert("product_variants", fields("id", variant, "product_id", variant, "pack_label", "1 kg",
            "amount_base", 1000, "base_unit", "g"));
        db.insert("platform_products", fields("id", listing, "platform_id", "blinkit", "variant_id", variant,
            "retailer_sku", "fixture-upgrade", "source_url", "https://example.invalid/legacy"));
        db.insert("provider_mappings", fields("platform_product_id", listing, "matched_by", reviewer,
            "matched_at", observed));
        db.insert("location_prices", fields("id", price, "platform_product_id", listing, "location_id", area,
            "price_minor", 1200L, "availability", "available", "observed_at", observed,
            "source", "TEST_FIXTURE", "verification", "VERIFIED"));
        db.insert("price_history", fields("id", history, "platform_product_id", listing, "location_id", area,
            "price_minor", 1200L, "observed_at", observed, "source", "TEST_FIXTURE", "verification", "VERIFIED"));

        String mappingQuery = "SELECT * FROM provider_mappings WHERE platform_product_id=?";
        String listingQuery = "SELECT * FROM platform_products WHERE id=?";
        String currentQuery = "SELECT * FROM location_prices WHERE id=?";
        String historyQuery = "SELECT * FROM price_history WHERE id=?";
        var legacyMapping = db.one(mappingQuery, listing);
        var legacyListing = db.one(listingQuery, listing);
        var legacyPrice = db.one(currentQuery, price);
        var legacyHistory = db.one(historyQuery, history);

        var afterUpgrade = Flyway.configure().dataSource(dataSource).target("11").load();
        assertEquals(1, afterUpgrade.migrate().migrationsExecuted);
        assertTrue(afterUpgrade.validateWithResult().validationSuccessful);
        assertEquals(legacyMapping, db.one(mappingQuery, listing));
        assertEquals(legacyListing, db.one(listingQuery, listing));
        assertEquals(legacyPrice, db.one(currentQuery, price));
        assertEquals(legacyHistory, db.one(historyQuery, history));
        assertEquals(0, db.count("SELECT COUNT(*) FROM provider_mapping_decisions"));
    }
}
