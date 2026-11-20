package com.smartcart;

import com.smartcart.config.StarterCatalogue;
import com.smartcart.repository.Db;
import org.junit.jupiter.api.Test;

import org.springframework.boot.DefaultApplicationArguments;


import static org.junit.jupiter.api.Assertions.*;

class StarterCatalogueTest {
    private final org.springframework.jdbc.datasource.DriverManagerDataSource source =
        new org.springframework.jdbc.datasource.DriverManagerDataSource(
            "jdbc:h2:mem:startertest;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    private final Db db = new Db(new org.springframework.jdbc.core.JdbcTemplate(source));
    private final StarterCatalogue importer = new StarterCatalogue(db, new com.fasterxml.jackson.databind.ObjectMapper());

    @org.junit.jupiter.api.BeforeEach void prepare() throws Exception {
        org.flywaydb.core.Flyway.configure().dataSource(source).load().migrate();
        new com.smartcart.config.ReferenceData(db, false).run(new DefaultApplicationArguments());
        importer.run(new DefaultApplicationArguments());
    }
    @Test void importsCatalogueWithoutPricesAndPreservesExistingChangesOnRetry() throws Exception {
        assertCounts();
        db.run("UPDATE products SET description=? WHERE id='milk'", "Locally reviewed description");
        importer.run(new DefaultApplicationArguments());
        assertCounts();
        assertEquals("Locally reviewed description", Db.text(db.one("SELECT description FROM products WHERE id='milk'"), "description"));
        assertEquals(0, db.count("SELECT COUNT(*) FROM grocery_platforms WHERE id NOT IN ('blinkit','instamart','zepto')"));
        assertEquals(0, db.count("SELECT COUNT(*) FROM product_variants v LEFT JOIN products p ON p.id=v.product_id WHERE p.id IS NULL"));
    }

    private void assertCounts() {
        assertEquals(24, db.count("SELECT COUNT(*) FROM products"));
        assertEquals(27, db.count("SELECT COUNT(*) FROM product_variants"));
        assertEquals(4, db.count("SELECT COUNT(*) FROM platform_products"));
        assertEquals(3, db.count("SELECT COUNT(*) FROM grocery_platforms"));
        assertEquals(0, db.count("SELECT COUNT(*) FROM location_prices"));
        assertEquals(0, db.count("SELECT COUNT(*) FROM price_history"));
    }
}

