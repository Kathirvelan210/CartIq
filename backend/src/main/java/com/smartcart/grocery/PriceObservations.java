package com.smartcart.grocery;

import com.smartcart.catalogimport.ProductMatching;
import com.smartcart.exception.ApiException;
import com.smartcart.repository.Db;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.smartcart.repository.Db.*;

@Service
@Profile("!mysql-local")
public class PriceObservations {
    public record Grant(@NotBlank @Size(max=80) String listingId, @NotBlank @Size(max=60) String locationId,
        @NotBlank @Size(max=36) String mappingDecisionId, @NotBlank @Size(max=80) String provider,
        @NotBlank @Size(max=200) String permissionReference, @NotBlank @Size(max=500) String evidenceReference,
        @NotBlank @Pattern(regexp="[a-f0-9]{64}") String evidenceSha256, @NotNull Instant firstObservationAt,
        @NotNull Instant lastObservationAt, @NotNull Instant retainUntil, boolean historyAllowed, boolean datasetAllowed) {}

    public enum Stock { AVAILABLE, OUT_OF_STOCK, UNKNOWN }

    public record Input(@NotBlank @Size(max=36) String grantId, @NotBlank @Size(max=150) String sourceRecordId,
        @NotBlank @Size(max=500) String sourceEvidence, @NotNull Instant observedAt,
        @NotBlank @Size(max=150) String retailerSku, @NotBlank @Size(max=60) String pack,
        @Pattern(regexp="INR") @NotBlank String currency, @PositiveOrZero @Max(9_007_199_254_740_991L) Long priceMinor,
        @PositiveOrZero @Max(9_007_199_254_740_991L) Long mrpMinor,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal discountPercent, @NotNull Stock stock) {}

    public record Recorded(String id, boolean created) {}
    public record RetentionRevoked(String grantId) {}
    private final Db db;
    private final ApplicationEventPublisher events;

    public PriceObservations(Db db,ApplicationEventPublisher events) { this.db = db; this.events=events; }

    @Transactional
    public String approve(String admin, Grant request) {
        Instant now = now();
        Instant first = second(request.firstObservationAt());
        Instant last = second(request.lastObservationAt());
        Instant expiry = second(request.retainUntil());
        if (first.isAfter(last) || !expiry.isAfter(now) || !expiry.isAfter(last)
            || !first.equals(request.firstObservationAt()) || !last.equals(request.lastObservationAt()) || !expiry.equals(request.retainUntil())
            || first.isBefore(Instant.parse("2000-01-01T00:00:00Z")) || expiry.isAfter(Instant.parse("2038-01-01T00:00:00Z"))) {
            throw ApiException.bad("Use whole-second timestamps for the observation window and a later retention deadline.");
        }
        var listing = lockListing(request.listingId());
        db.one("SELECT id FROM delivery_locations WHERE id=?",request.locationId());
        var decision = mapping(listing, request.mappingDecisionId());
        if (!request.provider().equals(text(decision,"source_provider"))) throw conflict("The grant provider differs from the reviewed listing source.");
        String id = id();
        db.insert("price_retention_grants", fields("id",id,"listing_id",request.listingId(),"location_id",request.locationId(),
            "mapping_decision_id",request.mappingDecisionId(),"provider",request.provider(),"permission_reference",request.permissionReference(),
            "evidence_reference",request.evidenceReference(),"evidence_sha256",request.evidenceSha256(),"first_observation_at",first,
            "last_observation_at",last,"retain_until",expiry,"history_allowed",request.historyAllowed(),"dataset_allowed",request.datasetAllowed(),
            "approved_by",admin,"approved_at",now));
        return id;
    }

    @Transactional
    public Recorded record(Input request) {
        return evaluate(request,null,null,true);
    }

    @Transactional
    public Recorded evaluate(Input request,Instant sourceAvailableAt,String asOfEvidence,boolean write) {
        if (sourceAvailableAt!=null && (sourceAvailableAt.isBefore(request.observedAt())||sourceAvailableAt.isAfter(Instant.now())
            ||!sourceAvailableAt.equals(second(sourceAvailableAt)))) throw ApiException.bad("Historical availability time is invalid.");
        var scope = db.one("SELECT listing_id FROM price_retention_grants WHERE id=?",request.grantId());
        var listing = lockListing(text(scope,"listing_id"));
        var grant = db.one("SELECT * FROM price_retention_grants WHERE id=? FOR UPDATE",request.grantId());
        Instant now = now(), at = second(request.observedAt());
        if (grant.get("revoked_at") != null || !now.isBefore(time(grant,"retain_until"))) {
            throw conflict("This retention grant is inactive.");
        }
        if (request.observedAt().isAfter(Instant.now()) || at.isBefore(time(grant,"first_observation_at")) || at.isAfter(time(grant,"last_observation_at"))) {
            throw ApiException.bad("The observation time is outside the approved window.");
        }
        var decision = mapping(listing,text(grant,"mapping_decision_id"));
        var actual = ProductMatching.pack(request.pack());
        var approved = ProductMatching.pack(text(decision,"canonical_pack"));
        var reviewed = ProductMatching.pack(text(decision,"listing_pack"));
        var identity = db.one("""
            SELECT v.pack_label,p.brand,p.name FROM product_variants v JOIN products p ON p.id=v.product_id WHERE v.id=?
            """,text(listing,"variant_id"));
        var current = ProductMatching.pack(text(identity,"pack_label"));
        if (!request.retailerSku().equals(text(listing,"retailer_sku")) || !samePack(actual,approved) || !samePack(actual,reviewed)
            || !samePack(actual,current) || !Objects.equals(text(identity,"brand"),text(decision,"canonical_brand"))
            || !Objects.equals(text(identity,"name"),text(decision,"canonical_title"))) {
            throw conflict("The listing identifier or exact pack differs from the reviewed identity.");
        }
        if (request.priceMinor() == null && request.mrpMinor() == null && request.discountPercent() == null && request.stock() == Stock.UNKNOWN) {
            throw ApiException.bad("At least one observed value is required.");
        }
        String listingId = text(listing,"id"), locationId = text(grant,"location_id");
        String provider = text(grant,"provider");
        var prior = db.maybe("""
            SELECT * FROM approved_price_observations WHERE listing_id=? AND location_id=?
            AND (observed_at=? OR (source_provider=? AND source_record_id=?)) LIMIT 1
            """,listingId,locationId,at,provider,request.sourceRecordId());
        if (prior.isPresent()) {
            var row = prior.get();
            if (request.grantId().equals(text(row,"grant_id")) && at.equals(time(row,"observed_at"))
                && request.sourceRecordId().equals(text(row,"source_record_id")) && request.sourceEvidence().equals(text(row,"source_evidence"))
                && Objects.equals(sourceAvailableAt,row.get("source_available_at")==null?null:time(row,"source_available_at"))
                && Objects.equals(asOfEvidence,text(row,"source_as_of_evidence"))
                && Objects.equals(request.priceMinor(),number(row,"price_minor")) && Objects.equals(request.mrpMinor(),number(row,"mrp_minor"))
                && equivalent(request.discountPercent(),nullableDecimal(row,"discount_percent")) && request.stock().name().equals(text(row,"stock_status"))) {
                return new Recorded(text(row,"id"),false);
            }
            throw conflict("An existing observation conflicts with this timestamp or source record.");
        }
        if (!write) return new Recorded(null,true);
        String id = id();
        db.insert("approved_price_observations", fields("id",id,"grant_id",request.grantId(),"listing_id",listingId,"location_id",locationId,
            "variant_id",text(listing,"variant_id"),"retailer_sku",request.retailerSku(),"observed_at",at,"recorded_at",now,
            "source_provider",provider,"source_record_id",request.sourceRecordId(),"source_evidence",request.sourceEvidence(),
            "source_available_at",sourceAvailableAt,"source_as_of_evidence",asOfEvidence,"price_minor",request.priceMinor(),
            "mrp_minor",request.mrpMinor(),"discount_percent",request.discountPercent(),"stock_status",request.stock().name(),
            "pack_label",text(decision,"canonical_pack"),"pack_count",actual.count(),"each_amount",actual.each().amount(),"base_unit",actual.each().unit()));
        return new Recorded(id,true);
    }

    @Transactional
    public void revoke(String grantId) {
        db.one("SELECT id FROM price_retention_grants WHERE id=? FOR UPDATE",grantId);
        db.run("UPDATE price_retention_grants SET revoked_at=? WHERE id=? AND revoked_at IS NULL",now(),grantId);
        db.run("DELETE FROM approved_price_observations WHERE grant_id=?",grantId);
        db.run("UPDATE price_intake_batches SET original_evidence_reference=NULL WHERE grant_id=? AND original_evidence_reference IS NOT NULL",grantId);
        events.publishEvent(new RetentionRevoked(grantId));
    }

    @Scheduled(fixedDelay=900000)
    public void purgeExpired() {
        Instant cutoff=now();
        db.run("DELETE FROM approved_price_observations WHERE grant_id IN (SELECT id FROM price_retention_grants WHERE retain_until<=? OR revoked_at IS NOT NULL)",cutoff);
        db.run("UPDATE price_intake_batches SET original_evidence_reference=NULL WHERE original_evidence_reference IS NOT NULL AND grant_id IN (SELECT id FROM price_retention_grants WHERE retain_until<=? OR revoked_at IS NOT NULL)",cutoff);
    }

    private Map<String,Object> lockListing(String listingId) {
        return db.one("SELECT id,retailer_sku,variant_id,platform_id FROM platform_products WHERE id=? FOR UPDATE",listingId);
    }

    private Map<String,Object> mapping(Map<String,Object> listing, String decisionId) {
        var decision = db.maybe("""
            SELECT * FROM provider_mapping_decisions WHERE variant_id=? AND platform_id=? ORDER BY revision_no DESC LIMIT 1
            """,text(listing,"variant_id"),text(listing,"platform_id")).orElseThrow(()->conflict("The listing needs a current approved mapping."));
        if (!decisionId.equals(text(decision,"decision_id")) || !"APPROVED".equals(text(decision,"disposition"))
            || !text(listing,"retailer_sku").equals(text(decision,"retailer_sku"))) {
            throw conflict("The listing needs a current approved mapping.");
        }
        return decision;
    }

    private static boolean samePack(ProductMatching.Pack a, ProductMatching.Pack b) {
        return a != null && b != null && a.count()==b.count() && a.each().unit().equals(b.each().unit())
            && equivalent(a.each().amount(),b.each().amount());
    }

    private static boolean equivalent(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? a == b : a.compareTo(b) == 0;
    }

    static BigDecimal nullableDecimal(Map<String,Object> row, String key) {
        return row.get(key)==null ? null : decimal(row,key);
    }

    private static Instant second(Instant value) { return value.truncatedTo(ChronoUnit.SECONDS); }
    private static Instant now() { return second(Instant.now()); }
    private static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT,message); }
}
