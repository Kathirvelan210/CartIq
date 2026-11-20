package com.smartcart.notification;

import com.smartcart.repository.Db;
import com.smartcart.service.AuthService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static com.smartcart.repository.Db.*;
import static org.junit.jupiter.api.Assertions.*;

class NotificationReceiptMigrationTest {
    @TempDir Path previousMigrations;

    @Test void receiptMigrationPreservesV11DataAndPriorFlywayValidationOnH2() throws Exception {
        exercise(new DriverManagerDataSource("jdbc:h2:mem:notification-receipts-upgrade-" + id()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", ""));
    }

    private void exercise(DriverManagerDataSource dataSource) throws Exception {
        var db = new Db(new JdbcTemplate(dataSource));
        var prior = Flyway.configure().dataSource(dataSource).target("11").cleanDisabled(true).load();
        assertEquals(11, prior.migrate().migrationsExecuted);
        String user = id(), device = id(), delivered = id(), legacyPending = id();
        db.insert("users", fields("id", user, "name", "Receipt fixture", "email", user + "@example.invalid",
            "password_hash", "unused"));
        db.insert("device_tokens", fields("id", device, "user_id", user, "token", "fixture:push-device",
            "token_hash", AuthService.hash("fixture:push-device")));
        db.insert("notifications", fields("id", delivered, "user_id", user, "title", "Fixture delivery",
            "body", "Private in-app content", "kind", "TARGET", "dedupe_key", "fixture-delivered",
            "sample", false, "delivery_status", "SENT"));
        db.insert("notifications", fields("id", legacyPending, "user_id", user, "title", "Fixture pending",
            "body", "Preserve this in-app message", "kind", "TARGET", "dedupe_key", "fixture-pending",
            "sample", false, "delivery_status", "PUSH_PENDING"));
        var priorDevice = db.one("SELECT * FROM device_tokens WHERE id=?", device);
        var priorNotice = db.one("SELECT * FROM notifications WHERE id=?", delivered);

        var next = Flyway.configure().dataSource(dataSource).target("12").cleanDisabled(true).load();
        assertEquals(1, next.migrate().migrationsExecuted);
        assertTrue(next.validateWithResult().validationSuccessful);
        assertEquals("12", next.info().current().getVersion().getVersion());
        assertEquals(priorDevice, db.one("SELECT * FROM device_tokens WHERE id=?", device));
        assertEquals(priorNotice, db.one("SELECT * FROM notifications WHERE id=?", delivered));
        var preserved = db.one("SELECT * FROM notifications WHERE id=?", legacyPending);
        assertEquals("IN_APP", text(preserved, "delivery_status"));
        assertEquals("Preserve this in-app message", text(preserved, "body"));
        assertEquals(0, db.count("SELECT COUNT(*) FROM notification_deliveries"));

        // The previous JAR sees only its own migrations. A Flyway target alone does not model that.
        var migrationDir = Path.of(getClass().getResource("/db/migration").toURI());
        int copied = 0;
        try (var migrations = Files.list(migrationDir)) {
            for (Path file : migrations.filter(path -> path.getFileName().toString().matches("V(?:[1-9]|1[01])__.*\\.sql")).toList()) {
                Files.copy(file, previousMigrations.resolve(file.getFileName()));
                copied++;
            }
        }
        assertEquals(11, copied);
        var previous = Flyway.configure().dataSource(dataSource).cleanDisabled(true)
            .locations("filesystem:" + previousMigrations.toString().replace('\\', '/')).load();
        assertTrue(previous.validateWithResult().validationSuccessful);
        assertEquals(0, previous.info().pending().length);
    }

}
