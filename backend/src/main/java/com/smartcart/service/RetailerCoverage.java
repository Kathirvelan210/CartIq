package com.smartcart.service;

import com.smartcart.exception.ApiException;
import com.smartcart.integration.RetailerDiagnostics;
import com.smartcart.repository.Db;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import static com.smartcart.repository.Db.*;

@Service
@Profile("!mysql-local")
public class RetailerCoverage {
    public enum Status { USABLE, NO_MAPPING, UNRESOLVED, PACK_CONFLICT, NO_OBSERVATION, STALE, MISSING_PRICE, UNKNOWN_STOCK, UNAVAILABLE, MISSING_FEES, DISABLED }
    public record Area(String id,String name) {}
    public record Filters(List<Area> locations,List<Area> categories) {}
    public record Counts(long totalProducts,long approvedProducts,long productsWithoutMapping,long unresolvedProducts,
        long staleProducts,long missingPriceProducts,long uncertainStockProducts,long missingFeeProducts,long packConflictProducts) {}
    public record Retailer(String id,long recordedUsableProducts,long usableLiveProductsForCurrentAccount,Instant lastSuccessfulRefresh) {}
    public record Row(String productId,String variantId,String categoryId,String name,String brand,String pack,String retailer,String nativeId,
        boolean approved,Status status,boolean missingFees,boolean missingPrice,boolean uncertainStock,boolean packConflict,
        Long priceMinor,Long stock,Instant observedAt,String observationSource,String origin,String verification) {}
    public record Coverage(String locationId,int freshnessSeconds,Instant calculatedAt,Counts counts,List<Retailer> retailers,
        List<Row> rows,boolean hasNext,List<RetailerDiagnostics.Event> events,boolean sample) {}
    private final Db db;
    private final CatalogService catalogue;
    private final RetailerDiagnostics diagnostics;
    private final Optional<LiveOffers> live;

    public RetailerCoverage(Db db,CatalogService catalogue,RetailerDiagnostics diagnostics,Optional<LiveOffers> live) {
        this.db=db;this.catalogue=catalogue;this.diagnostics=diagnostics;this.live=live;
    }
    public Filters filters(){
        return new Filters(catalogue.locations().stream().map(a->new Area(a.id(),a.name())).toList(),
            catalogue.categories().stream().map(c->new Area(c.id(),c.name())).toList());
    }

    // The observation ranking excludes historical specimens whose retention or display scope lost approval.
    private static final String COVERAGE="""
        WITH decisions AS (
          SELECT d.*, ROW_NUMBER() OVER(PARTITION BY variant_id,platform_id,retailer_sku ORDER BY revision_no DESC) AS sku_rank
          FROM provider_mapping_decisions d
        ), conflicts AS (
          SELECT variant_id,platform_id,
            MAX(CASE WHEN disposition='UNRESOLVED' THEN 1 ELSE 0 END) AS unresolved,
            MAX(CASE WHEN disposition<>'APPROVED' AND (blocking_reasons LIKE '%PACK_DIFFERENT%' OR blocking_reasons LIKE '%PACK_COMPATIBILITY_UNCONFIRMED%') THEN 1 ELSE 0 END) AS pack_conflict
          FROM decisions WHERE sku_rank=1 GROUP BY variant_id,platform_id
        ), licensed AS (
          SELECT o.*,ROW_NUMBER() OVER(PARTITION BY o.listing_id ORDER BY o.observed_at DESC,o.id) AS obs_rank
          FROM approved_price_observations o
          JOIN price_retention_grants g ON g.id=o.grant_id AND g.listing_id=o.listing_id AND g.location_id=o.location_id AND g.provider=o.source_provider
          JOIN price_source_authorizations a ON a.grant_id=g.id AND a.display_allowed=TRUE AND a.revoked_at IS NULL
          JOIN provider_mapping_decisions d ON d.decision_id=g.mapping_decision_id AND d.disposition='APPROVED'
          JOIN platform_products pp ON pp.id=o.listing_id AND pp.retailer_sku=o.retailer_sku AND pp.variant_id=o.variant_id
          JOIN product_variants v ON v.id=o.variant_id AND v.pack_label=o.pack_label AND v.pack_label=d.canonical_pack
          JOIN products p ON p.id=v.product_id AND p.name=d.canonical_title AND p.brand=d.canonical_brand
          WHERE o.location_id=? AND g.history_allowed=TRUE AND g.revoked_at IS NULL AND g.retain_until>?
            AND d.variant_id=o.variant_id AND d.platform_id=pp.platform_id AND d.retailer_sku=o.retailer_sku
            AND NOT EXISTS(SELECT 1 FROM provider_mapping_decisions n WHERE n.variant_id=d.variant_id
                AND n.platform_id=d.platform_id AND n.revision_no>d.revision_no)
        ), inputs AS (
          SELECT p.id AS product_id,p.name,p.brand,p.category_id,v.id AS variant_id,v.pack_label,
            gp.id AS retailer, pp.retailer_sku, CASE WHEN d.disposition='APPROVED' AND m.platform_product_id IS NOT NULL
                AND d.canonical_title=p.name AND d.canonical_brand=p.brand AND d.canonical_pack=v.pack_label
                AND d.canonical_variant=p.variant_name THEN 1 ELSE 0 END AS mapped,
            COALESCE(c.unresolved,0) AS unresolved,COALESCE(c.pack_conflict,0) AS pack_conflict,
            CASE WHEN gp.enabled=TRUE AND (pl.available IS NULL OR pl.available=TRUE) THEN 1 ELSE 0 END AS enabled,
            CASE WHEN gp.fee_source='VERIFIED' AND gp.delivery_minor IS NOT NULL AND gp.handling_minor IS NOT NULL
                AND gp.minimum_minor IS NOT NULL THEN 0 ELSE 1 END AS missing_fees,
            l.observed_at AS recorded_at,l.price_minor AS recorded_price,UPPER(l.availability) AS recorded_status,l.source AS recorded_source,
            l.verification AS recorded_verification,e.stock_quantity AS recorded_stock,
            h.observed_at AS licensed_at,h.price_minor AS licensed_price,h.stock_status AS licensed_status,h.source_provider AS licensed_source
          FROM products p CROSS JOIN grocery_platforms gp LEFT JOIN product_variants v ON v.product_id=p.id
          LEFT JOIN platform_products pp ON pp.variant_id=v.id AND pp.platform_id=gp.id
          LEFT JOIN provider_mappings m ON m.platform_product_id=pp.id
          LEFT JOIN decisions d ON d.variant_id=v.id AND d.platform_id=gp.id AND d.retailer_sku=pp.retailer_sku AND d.sku_rank=1
          LEFT JOIN conflicts c ON c.variant_id=v.id AND c.platform_id=gp.id
          LEFT JOIN platform_locations pl ON pl.platform_id=gp.id AND pl.location_id=?
          LEFT JOIN location_prices l ON l.platform_product_id=pp.id AND l.location_id=? AND l.verification='VERIFIED'
          LEFT JOIN online_comparison_evidence e ON e.price_id=l.id AND e.price_observed_at=l.observed_at AND e.retailer_sku=pp.retailer_sku
            AND e.product_name=p.name AND e.brand=p.brand AND e.variant_name=p.variant_name AND e.amount_base=v.amount_base AND e.base_unit=v.base_unit
            AND e.observed_at>? AND e.observed_at<=?
          LEFT JOIN licensed h ON h.listing_id=pp.id AND h.obs_rank=1
          WHERE p.active=TRUE AND gp.id IN ('blinkit','zepto','instamart')
            AND (COALESCE(?,'')='' OR gp.id=?) AND (COALESCE(?,'')='' OR p.category_id=?)
        ), chosen AS (
          SELECT i.*,CASE WHEN licensed_at IS NOT NULL AND (recorded_at IS NULL OR licensed_at>recorded_at)
            THEN 1 ELSE 0 END AS use_licensed FROM inputs i
        ), evidence AS (
          SELECT c.*,
            CASE WHEN use_licensed=1 THEN licensed_at ELSE recorded_at END AS offer_at,
            CASE WHEN use_licensed=1 THEN licensed_price ELSE recorded_price END AS price_minor,
            CASE WHEN use_licensed=1 THEN licensed_status ELSE recorded_status END AS stock_status,
            CASE WHEN use_licensed=1 THEN NULL ELSE recorded_stock END AS stock_count,
            CASE WHEN use_licensed=1 THEN licensed_source ELSE recorded_source END AS observation_source
          FROM chosen c
        ), classified AS (
          SELECT e.*,
            CASE WHEN offer_at IS NOT NULL AND price_minor IS NULL THEN 1 ELSE 0 END AS missing_price,
            CASE WHEN offer_at IS NOT NULL AND (stock_status IS NULL OR stock_status IN ('UNKNOWN','NO_DATA') OR
                  (stock_status='AVAILABLE' AND stock_count IS NULL)) THEN 1 ELSE 0 END AS uncertain_stock,
            CASE WHEN offer_at IS NOT NULL AND (offer_at<=? OR offer_at>?) THEN 1 ELSE 0 END AS stale
          FROM evidence e
        ), coverage AS (
          SELECT k.*, CASE
            WHEN enabled=0 THEN 'DISABLED' WHEN pack_conflict=1 AND mapped=0 THEN 'PACK_CONFLICT'
            WHEN unresolved=1 AND mapped=0 THEN 'UNRESOLVED' WHEN mapped=0 THEN 'NO_MAPPING'
            WHEN offer_at IS NULL THEN 'NO_OBSERVATION' WHEN stale=1 THEN 'STALE'
            WHEN stock_count=0 OR stock_status IN ('OUT_OF_STOCK','UNAVAILABLE') THEN 'UNAVAILABLE'
            WHEN missing_price=1 THEN 'MISSING_PRICE'
            WHEN stock_status IS NULL OR stock_status<>'AVAILABLE' THEN 'UNKNOWN_STOCK'
            WHEN price_minor>100000000 THEN 'MISSING_PRICE'
            ELSE 'USABLE' END AS offer_status
          FROM classified k
        )
        """;

    public Coverage report(String area,String retailer,String category,Status status,int page,String owner,String position) {
        catalogue.requireLocation(area);
        if(retailer!=null&&!Set.of("blinkit","zepto","instamart").contains(retailer))throw ApiException.bad("Choose a known retailer.");
        var now=Instant.now();var cutoff=catalogue.freshness().cutoff(now);
        Object[] scope={area,now,area,area,cutoff,now,retailer,retailer,category,category,cutoff,now};
        String eligible="COUNT(DISTINCT CASE WHEN %s THEN product_id END)";
        var totals=db.one(COVERAGE+"""
            SELECT COUNT(DISTINCT product_id) AS total,%s AS approved,%s AS unresolved_count,
              %s AS stale_count,%s AS missing_price_count,%s AS stock_count_total,%s AS fee_count,%s AS conflict_count
            FROM coverage
            """.formatted(eligible.formatted("mapped=1"),eligible.formatted("unresolved=1"),eligible.formatted("stale=1"),
            eligible.formatted("missing_price=1"),eligible.formatted("uncertain_stock=1"),eligible.formatted("missing_fees=1"),eligible.formatted("pack_conflict=1")),scope);
        long total=num(totals,"total"),mapped=num(totals,"approved");
        var counts=new Counts(total,mapped,total-mapped,num(totals,"unresolved_count"),num(totals,"stale_count"),
            num(totals,"missing_price_count"),num(totals,"stock_count_total"),num(totals,"fee_count"),num(totals,"conflict_count"));
        var last=diagnostics.lastSuccess();
        var ownLive=live.filter(s->s.matchesPosition(area,position)).map(s->s.coverage(owner,area,category)).orElse(Map.of());
        var retailers=db.rows(COVERAGE+"SELECT retailer,COUNT(DISTINCT CASE WHEN offer_status='USABLE' THEN product_id END) AS usable FROM coverage GROUP BY retailer ORDER BY retailer",scope)
            .stream().map(row->{String id=text(row,"retailer");return new Retailer(id,num(row,"usable"),ownLive.getOrDefault(id,0L),last.get(id));}).toList();
        String where=status==null?"":status==Status.MISSING_FEES?" WHERE missing_fees=1":status==Status.MISSING_PRICE?" WHERE missing_price=1 OR offer_status='MISSING_PRICE'":
            status==Status.UNKNOWN_STOCK?" WHERE uncertain_stock=1":status==Status.PACK_CONFLICT?" WHERE pack_conflict=1":
            status==Status.UNRESOLVED?" WHERE unresolved=1":status==Status.STALE?" WHERE stale=1":" WHERE offer_status='"+status.name()+"'";
        var params=new ArrayList<>(Arrays.asList(scope));params.add(page*30);
        var rows=db.rows(COVERAGE+"SELECT * FROM coverage"+where+" ORDER BY name,product_id,variant_id,retailer LIMIT 31 OFFSET ?",params.toArray());
        return new Coverage(area,catalogue.freshness().seconds(),now,counts,retailers,rows.stream().limit(30).map(RetailerCoverage::row).toList(),
            rows.size()>30,diagnostics.events(retailer),catalogue.sample());
    }
    private static Row row(Map<String,Object> r){
        boolean licensed=bool(r,"use_licensed");
        return new Row(text(r,"product_id"),text(r,"variant_id"),text(r,"category_id"),text(r,"name"),text(r,"brand"),text(r,"pack_label"),
            text(r,"retailer"),text(r,"retailer_sku"),bool(r,"mapped"),Status.valueOf(text(r,"offer_status")),bool(r,"missing_fees"),
            bool(r,"missing_price"),bool(r,"uncertain_stock"),bool(r,"pack_conflict"),number(r,"price_minor"),number(r,"stock_count"),
            time(r,"offer_at"),text(r,"observation_source"),licensed?"APPROVED_OBSERVATION":"RECORDED",licensed?"APPROVED":text(r,"recorded_verification"));
    }
}
