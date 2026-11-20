package com.smartcart.integration;

import com.smartcart.catalogimport.ProductMatching.Identity;
import com.smartcart.exception.ApiException;
import com.smartcart.integration.ProviderSyncService.MappingEvidence;
import com.smartcart.integration.ProviderSyncService.MappingReview;
import com.smartcart.repository.Db;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.smartcart.repository.Db.*;

/** Append-only decision records. Writers hold the canonical variant lock before calling this journal. */
final class ProviderMappingJournal {
    private final Db db;

    ProviderMappingJournal(Db db) {
        this.db = db;
    }

    private Map<String, Object> submission(String variant, String platform, String sku, Identity identity,
                                            MappingEvidence evidence, String disposition) {
        return fields("decision_id", evidence.decisionId(), "variant_id", variant, "platform_id", platform,
            "retailer_sku", sku, "disposition", disposition, "source_provider", evidence.sourceProvider(),
            "source_record_id", evidence.sourceRecordId(), "evidence_reference", evidence.evidenceReference(),
            "evidence_sha256", evidence.evidenceSha256(), "permission_reference", evidence.permissionReference(),
            "rationale", evidence.rationale(), "supersedes_decision_id", evidence.supersedesDecisionId(),
            "listing_gtin", identity.gtin(), "listing_brand", identity.brand(), "listing_title", identity.name(),
            "listing_pack", identity.pack(), "listing_variant", identity.variant(),
            "listing_formulation", identity.formulation());
    }

    Map<String, Object> replay(String variant, String platform, String sku, Identity identity,
                               MappingEvidence evidence, String disposition) {
        var existing = db.maybe("SELECT * FROM provider_mapping_decisions WHERE decision_id=?", evidence.decisionId());
        if (existing.isEmpty()) return null;
        var row = existing.get();
        boolean same = submission(variant, platform, sku, identity, evidence, disposition).entrySet().stream()
            .allMatch(field -> Objects.equals(field.getValue(), text(row, field.getKey())));
        if (!same) throw conflict("The decision ID already belongs to a different submission.");
        return row;
    }

    void requirePredecessor(String variant, String platform, String sku, MappingEvidence evidence,
                            boolean approval) {
        var latest = db.maybe("""
            SELECT decision_id
            FROM provider_mapping_decisions WHERE variant_id=? AND platform_id=? AND retailer_sku=?
            ORDER BY revision_no DESC LIMIT 1
            """, variant, platform, sku);
        String latestId = latest.map(row -> text(row, "decision_id")).orElse(null);
        if (!Objects.equals(latestId, evidence.supersedesDecisionId())) {
            throw conflict("A new decision must explicitly supersede the latest decision for this candidate.");
        }
        if (approval && latest.isPresent() && db.count("""
            SELECT COUNT(*) FROM provider_mapping_decisions WHERE variant_id=? AND platform_id=? AND retailer_sku=?
                AND disposition IN ('REJECTED','UNRESOLVED') AND evidence_sha256=?
            """, variant, platform, sku, evidence.evidenceSha256())>0) {
            throw conflict("Approval after a rejection or unresolved decision requires new source evidence.");
        }
    }

    Map<String, Object> append(String reviewer, Instant reviewedAt, String variant, String platform, String sku,
                               Identity identity, MappingEvidence evidence, String disposition,
                               Identity canonical, MappingReview review, Map<String, Object> previous) {
        long revision = 1 + db.count("""
            SELECT COALESCE(MAX(revision_no), 0) FROM provider_mapping_decisions WHERE variant_id=? AND platform_id=?
            """, variant, platform);
        var row = submission(variant, platform, sku, identity, evidence, disposition);
        row.putAll(fields("revision_no", revision, "reviewed_by", reviewer, "reviewed_at", reviewedAt,
            "canonical_gtins", String.join(",", review.canonicalGtins()), "canonical_pack", canonical.pack(),
            "canonical_brand", canonical.brand(), "canonical_title", canonical.name(),
            "canonical_variant", canonical.variant(), "blocking_reasons", String.join(",", review.blockingReasons()),
            "previous_sku", text(previous, "retailer_sku"),
            "previous_reviewed_by", text(previous, "matched_by"), "previous_reviewed_at", time(previous, "matched_at")));
        db.insert("provider_mapping_decisions", row);
        return db.one("SELECT * FROM provider_mapping_decisions WHERE decision_id=?", evidence.decisionId());
    }

    List<Map<String, Object>> list(String variant, String platform, int afterRevision) {
        return db.rows("""
            SELECT * FROM provider_mapping_decisions WHERE variant_id=? AND platform_id=? AND revision_no>?
            ORDER BY revision_no LIMIT 100
            """, variant, platform, afterRevision);
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
