package com.smartcart;

import static com.smartcart.repository.Db.*;
import static org.junit.jupiter.api.Assertions.*;

import com.smartcart.config.ReferenceData;
import com.smartcart.catalogimport.ProductMatching.Identity;
import com.smartcart.exception.ApiException;
import com.smartcart.integration.GroceryDataProvider;
import com.smartcart.integration.ProviderSyncService;
import com.smartcart.integration.ProviderSyncService.MappingEvidence;
import com.smartcart.integration.ProviderSyncService.ReviewDisposition;
import com.smartcart.repository.Db;
import com.smartcart.service.CatalogService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PriceObservationIntegrationTest {
    @Test
    void recordsDistinctObservationTimestampsAndKeepsFixtureLocationsSeparate() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:observations-" + id()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).load().migrate();
        var db = new Db(new JdbcTemplate(dataSource));
        new ReferenceData(db,false).run(new DefaultApplicationArguments());
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.executeWithoutResult(status -> {
            exercise(db);
            status.setRollbackOnly();
        });
    }

    static void exercise(Db db) {
        String variant = id(), reviewer = id(), secondReviewer = id(), area = id(), otherArea = id();
        String sku = "fixture-" + variant;
        String listing = variant + "-blinkit";
        var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        for (String actor : List.of(reviewer, secondReviewer)) {
            db.insert("users", fields("id", actor, "name", "Fixture reviewer", "email", actor + "@example.invalid",
                "password_hash", "unused", "role", "admin"));
        }
        for(String location : List.of(area,otherArea)) {
            db.insert("delivery_locations",fields("id",location,"name","Fixture area","city","Fixture city",
                "postcode",location.equals(area)?"234567":"234568"));
        }
        db.insert("products",fields("id",variant,"category_id","pantry","name","Fixture grain","brand","Fixture brand",
            "variant_name","Plain","description","Test fixture","color","#123456","art","bag","active",true));
        db.insert("product_variants",fields("id",variant,"product_id",variant,"pack_label","1 kg","amount_base",1000,"base_unit","g"));

        var feed = new FixtureFeed(sku);
        var service = new ProviderSyncService(db,feed);
        var catalogue = new CatalogService(db,false);
        String fixtureGtin = "09999000000015";
        db.insert("variant_barcodes",fields("gtin",fixtureGtin,"variant_id",variant));
        var wrongPack = new Identity(fixtureGtin,"Fixture brand","Fixture grain","2 x 500 g","Plain",null);
        assertTrue(service.reviewMapping(variant,wrongPack).blockingReasons().contains("PACK_DIFFERENT"));
        var wrongBarcode = new Identity("8901719134852","Fixture brand","Fixture grain","1 kg","Plain",null);
        assertTrue(service.reviewMapping(variant,wrongBarcode).blockingReasons().contains("CANONICAL_GTIN_CONFLICT"));
        var checked = new Identity(null,"Fixture brand","Fixture grain","1000 g","Plain",null);
        assertTrue(service.reviewMapping(variant,checked).blockingReasons().isEmpty());
        db.run("UPDATE products SET variant_name='Vanilla' WHERE id=?", variant);
        var unnamed = new Identity(null,checked.brand(),checked.name(),checked.pack(),null,null);
        assertTrue(service.reviewMapping(variant,unnamed).blockingReasons().contains("VARIANT_UNCONFIRMED"));
        assertBlocked(() -> service.map(reviewer, variant, "blinkit", sku, unnamed, fixtureEvidence("a", null)));
        db.run("UPDATE products SET variant_name='Standard' WHERE id=?", variant);
        var named = new Identity(fixtureGtin,checked.brand(),checked.name(),checked.pack(),"Vanilla",null);
        assertTrue(service.reviewMapping(variant,named).blockingReasons().contains("VARIANT_UNCONFIRMED"));
        db.run("UPDATE products SET variant_name='Plain' WHERE id=?", variant);
        var formulation = new Identity(fixtureGtin,checked.brand(),checked.name(),checked.pack(),"Plain","Sugar-free");
        assertTrue(service.reviewMapping(variant,formulation).blockingReasons().contains("FORMULATION_UNCONFIRMED"));
        assertBlocked(() -> service.map(reviewer, variant, "blinkit", sku, formulation, fixtureEvidence("a", null)));
        assertBlocked(() -> service.map(reviewer, variant, "blinkit", sku));
        assertBlocked(() -> service.map(reviewer, variant, "blinkit", sku, checked));
        assertBlocked(() -> service.map(reviewer, variant, "blinkit", sku, wrongPack,
            fixtureEvidence("a", null)));
        assertTrue(decisionRows(db, variant).isEmpty());

        String rejectedSku = sku + "-rejected";
        var rejected = fixtureEvidence("b", null);
        service.recordReview(reviewer, variant, "blinkit", rejectedSku, wrongPack, rejected,
            ReviewDisposition.REJECTED);
        assertBlocked(() -> service.map(reviewer, variant, "blinkit", rejectedSku, checked, rejected));
        assertBlocked(() -> service.map(reviewer, variant, "blinkit", rejectedSku, checked,
            fixtureEvidence("c", null)));
        assertBlocked(() -> service.map(reviewer, variant, "blinkit", rejectedSku, checked,
            fixtureEvidence("b", rejected.decisionId())));

        String initialSku = sku + "-initial";
        var unresolved = fixtureEvidence("d", null);
        service.recordReview(reviewer, variant, "blinkit", initialSku, checked, unresolved,
            ReviewDisposition.UNRESOLVED);
        assertBlocked(() -> service.map(reviewer, variant, "blinkit", initialSku, checked, unresolved));
        assertBlocked(() -> service.map(reviewer, variant, "blinkit", initialSku, checked,
            fixtureEvidence("e", rejected.decisionId())));
        assertBlocked(() -> service.map(reviewer, variant, "blinkit", initialSku, checked,
            fixtureEvidence("d", unresolved.decisionId())));
        assertEquals(0, db.count("SELECT COUNT(*) FROM platform_products WHERE id=?", listing));
        assertEquals(0, db.count("SELECT COUNT(*) FROM provider_mappings WHERE platform_product_id=?", listing));
        assertEquals(List.of("REJECTED", "UNRESOLVED"), decisions(db, variant));

        var approved = fixtureEvidence("e", unresolved.decisionId());
        service.map(reviewer, variant, "blinkit", initialSku, checked, approved);
        var initialAudit = mappingAudit(db, listing);
        assertEquals(reviewer, text(initialAudit, "matched_by"));
        var firstApprovalRows = decisionRows(db, variant);
        assertEquals(List.of("REJECTED", "UNRESOLVED", "APPROVED"), decisions(db, variant));
        var storedApproval = firstApprovalRows.get(2);
        assertEquals(3, num(storedApproval, "revision_no"));
        assertEquals(unresolved.decisionId(), text(storedApproval, "supersedes_decision_id"));
        assertEquals(reviewer, text(storedApproval, "reviewed_by"));
        assertNotNull(time(storedApproval, "reviewed_at"));
        assertEquals(approved.sourceProvider(), text(storedApproval, "source_provider"));
        assertEquals(approved.sourceRecordId(), text(storedApproval, "source_record_id"));
        assertEquals(approved.evidenceReference(), text(storedApproval, "evidence_reference"));
        assertEquals(approved.evidenceSha256(), text(storedApproval, "evidence_sha256"));
        assertEquals(approved.permissionReference(), text(storedApproval, "permission_reference"));
        assertEquals(approved.rationale(), text(storedApproval, "rationale"));
        assertNull(text(storedApproval, "listing_gtin"));
        assertEquals(checked.pack(), text(storedApproval, "listing_pack"));
        assertEquals("1 kg", text(storedApproval, "canonical_pack"));
        assertEquals(1, db.count("SELECT COUNT(*) FROM provider_mapping_decisions WHERE decision_id=? AND canonical_gtins LIKE ?",
            approved.decisionId(), "%" + fixtureGtin + "%"));

        service.map(secondReviewer, variant, "blinkit", initialSku, checked, approved);
        var changedPayload = new MappingEvidence(approved.decisionId(), approved.sourceProvider(), approved.sourceRecordId(),
            approved.evidenceReference(), "f".repeat(64), approved.permissionReference(), approved.rationale(),
            approved.supersedesDecisionId());
        assertBlocked(() -> service.map(secondReviewer, variant, "blinkit", initialSku, checked, changedPayload));
        assertBlocked(() -> service.map(secondReviewer, variant, "blinkit", initialSku,
            new Identity(fixtureGtin, checked.brand(), checked.name(), checked.pack(), checked.variant(), checked.formulation()),
            approved));
        assertEquals(firstApprovalRows, decisionRows(db, variant));
        assertEquals(initialAudit, mappingAudit(db, listing));

        var replacement = fixtureEvidence("f", null);
        service.map(secondReviewer, variant, "blinkit", sku, checked, replacement);
        var applied = mappingAudit(db, listing);
        assertEquals(secondReviewer, text(applied, "matched_by"));
        var reviewedRows = decisionRows(db, variant);
        var storedReplacement = reviewedRows.get(3);
        assertEquals(firstApprovalRows, reviewedRows.subList(0, 3));
        assertEquals(4, num(storedReplacement, "revision_no"));
        assertEquals(initialSku, text(storedReplacement, "previous_sku"));
        assertEquals(reviewer, text(storedReplacement, "previous_reviewed_by"));
        assertEquals(time(initialAudit, "matched_at"), time(storedReplacement, "previous_reviewed_at"));
        assertBlocked(() -> service.map(reviewer, variant, "blinkit", initialSku, checked, approved));
        assertEquals(sku, text(db.one("SELECT retailer_sku FROM platform_products WHERE id=?", listing), "retailer_sku"));
        assertEquals(reviewedRows, decisionRows(db, variant));

        String source = "https://example.invalid/fixture";
        feed.next = feed.row("234567",now.minusSeconds(1200),1250L,1400L,true,source);
        service.sync(area);
        var stale = catalogue.product(variant,area).offers().get(0);
        assertFalse(catalogue.eligible(stale));
        assertEquals(now.minusSeconds(1200),stale.updatedAt());
        assertEquals("TEST_FIXTURE",stale.source());
        assertEquals(1,history(db,listing,area));

        feed.next = feed.row("234567",now.minusSeconds(8).plusMillis(725),1100L,1400L,true,source);
        service.sync(area);
        String current = text(current(db,listing,area),"id");
        assertTrue(catalogue.eligible(catalogue.product(variant,area).offers().get(0)));
        db.insert("online_comparison_evidence",fields("price_id",current,"retailer_sku",sku,"product_name","Fixture grain",
            "brand","Fixture brand","variant_name","Plain","amount_base",1000,"base_unit","g",
            "price_observed_at",now.minusSeconds(8),"evidence_source","TEST_FIXTURE","observed_at",now,"verified_by",reviewer));
        assertEquals(0,service.sync(area).get("imported"));
        assertEquals(2,history(db,listing,area));
        assertEquals(1,db.count("SELECT COUNT(*) FROM online_comparison_evidence WHERE price_id=?",current));
        assertEquals(now.minusSeconds(8),time(current(db,listing,area),"observed_at"));

        feed.next = feed.row("234568",now.minusSeconds(8),1900L,2000L,true,source);
        service.sync(otherArea);
        assertEquals(1900,num(current(db,listing,otherArea),"price_minor"));
        assertEquals(1100,num(current(db,listing,area),"price_minor"));
        assertEquals(1,history(db,listing,otherArea));

        feed.next = feed.row("234567",now.minusSeconds(4),null,null,false,null);
        service.sync(area);
        assertEquals("out_of_stock",text(current(db,listing,area),"availability"));
        assertNull(number(current(db,listing,area),"price_minor"));
        assertEquals(2,history(db,listing,area));
        assertEquals(0,db.count("SELECT COUNT(*) FROM online_comparison_evidence WHERE price_id=?",current));
        assertFalse(catalogue.eligible(catalogue.product(variant,area).offers().get(0)));

        feed.next = feed.row("234567",now.minusSeconds(5),1130L,1400L,true,"https://example.invalid/older");
        service.sync(area);
        assertEquals(3,history(db,listing,area));
        assertEquals("out_of_stock",text(current(db,listing,area),"availability"));
        assertEquals(source,text(db.one("SELECT source_url FROM platform_products WHERE id=?",listing),"source_url"));
        assertEquals("TEST_FIXTURE",text(db.one("""
            SELECT source FROM price_history WHERE platform_product_id=? AND location_id=? AND observed_at=?
            """,listing,area,now.minusSeconds(5)),"source"));

        feed.next = feed.row("234567",now.minusSeconds(2),null,null,null,null);
        service.sync(area);
        assertEquals("no_data",text(current(db,listing,area),"availability"));
        assertEquals(3,history(db,listing,area));

        feed.next = feed.row("234568",now,950L,1400L,true,source);
        assertEquals(1,service.sync(area).get("failedMappings"));
        assertEquals("no_data",text(current(db,listing,area),"availability"));
        var priceHistory = db.rows("SELECT * FROM price_history WHERE platform_product_id=? ORDER BY id", listing);
        var observations = db.rows("SELECT * FROM location_prices WHERE platform_product_id=? ORDER BY id", listing);
        var reassignment = fixtureEvidence("1", null);
        assertBlocked(() -> service.map(reviewer, variant, "blinkit", sku + "-reassigned", checked, reassignment));
        assertEquals(observations, db.rows("SELECT * FROM location_prices WHERE platform_product_id=? ORDER BY id", listing));
        assertEquals(sku,text(db.one("SELECT retailer_sku FROM platform_products WHERE id=?",listing),"retailer_sku"));

        // Even if current observations are archived, the SKU still owns the existing price history.
        db.run("DELETE FROM location_prices WHERE platform_product_id=?",listing);
        assertEquals(0,db.count("SELECT COUNT(*) FROM location_prices WHERE platform_product_id=?",listing));
        assertBlocked(() -> service.map(reviewer, variant, "blinkit", sku + "-reassigned", checked, reassignment));
        assertEquals(sku,text(db.one("SELECT retailer_sku FROM platform_products WHERE id=?",listing),"retailer_sku"));
        assertEquals(3,history(db,listing,area));
        assertEquals(1,history(db,listing,otherArea));
        assertEquals(priceHistory, db.rows("SELECT * FROM price_history WHERE platform_product_id=? ORDER BY id", listing));
        assertEquals(applied, mappingAudit(db, listing));
        assertEquals(reviewedRows, decisionRows(db, variant));

        var rereview = fixtureEvidence("2", rejected.decisionId());
        service.recordReview(secondReviewer, variant, "blinkit", rejectedSku, checked, rereview,
            ReviewDisposition.UNRESOLVED);
        var recycledEvidence = fixtureEvidence("b", rereview.decisionId());
        assertBlocked(() -> service.map(secondReviewer, variant, "blinkit", rejectedSku, checked, recycledEvidence));
        assertEquals(List.of("REJECTED", "UNRESOLVED", "APPROVED", "APPROVED", "UNRESOLVED"), decisions(db, variant));
        assertEquals(applied, mappingAudit(db, listing));
    }

    private static MappingEvidence fixtureEvidence(String fingerprint, String supersedes) {
        return new MappingEvidence(id(), "TEST_FIXTURE", "record-" + fingerprint,
            "https://example.invalid/evidence/" + fingerprint, fingerprint.repeat(64), "fixture-permission",
            "Fixture reviewer checked the product evidence.", supersedes);
    }

    private static void assertBlocked(Executable attempt) {
        assertEquals(409, assertThrows(ApiException.class, attempt).status.value());
    }

    private static List<Map<String, Object>> decisionRows(Db db, String variant) {
        return db.rows("""
            SELECT decision_id, revision_no, disposition, retailer_sku, reviewed_by, reviewed_at,
                source_provider, source_record_id, evidence_reference, evidence_sha256, permission_reference, rationale,
                supersedes_decision_id, listing_gtin, listing_brand, listing_title, listing_pack, listing_variant,
                listing_formulation, canonical_pack, canonical_brand, canonical_title, canonical_variant,
                blocking_reasons, previous_sku, previous_reviewed_by, previous_reviewed_at
            FROM provider_mapping_decisions WHERE variant_id=? AND platform_id='blinkit' ORDER BY revision_no
            """, variant);
    }

    private static List<String> decisions(Db db, String variant) {
        return decisionRows(db, variant).stream().map(row -> text(row, "disposition")).toList();
    }

    private static Map<String, Object> mappingAudit(Db db, String listing) {
        return db.one("SELECT matched_by, matched_at FROM provider_mappings WHERE platform_product_id=?", listing);
    }

    private static long history(Db db,String listing,String location) {
        return db.count("SELECT COUNT(*) FROM price_history WHERE platform_product_id=? AND location_id=?",listing,location);
    }

    private static java.util.Map<String,Object> current(Db db,String listing,String location) {
        return db.one("SELECT * FROM location_prices WHERE platform_product_id=? AND location_id=?",listing,location);
    }

    private static class FixtureFeed implements GroceryDataProvider {
        private final String sku;
        private Observation next;

        FixtureFeed(String sku) { this.sku=sku; }
        public String name() { return "TEST_FIXTURE"; }
        public List<Observation> observations(String retailer,String requestedSku,String pin) {
            return sku.equals(requestedSku)?List.of(next):List.of();
        }

        Observation row(String pin,Instant time,Long price,Long mrp,Boolean stock,String url) {
            return new Observation("blinkit",sku,pin,time,"Fixture grain","Fixture brand","1 kg",price,mrp,stock,url);
        }
    }
}
