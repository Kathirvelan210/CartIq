package com.smartcart.service;

import com.smartcart.dto.Models.Product;
import com.smartcart.repository.Db;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import static com.smartcart.repository.Db.*;

@Service
@Profile("!mysql-local")
public class CurrentOffers {
    public record Quote(String retailer,long priceMinor,long unitPriceMinor,String unit,Long stockPacks,Instant observedAt,String source,
                        String origin,String nativeId,String requestId,Instant freshUntil,Instant providerObservedAt) {
        public Quote(String retailer,long priceMinor,long unitPriceMinor,String unit,Long stockPacks,Instant observedAt,String source){
            this(retailer,priceMinor,unitPriceMinor,unit,stockPacks,observedAt,source,"RECORDED",null,null,observedAt.plusSeconds(900),null);
        }
        public int packLimit(){return stockPacks==null?1:(int)Math.min(99,stockPacks);}
    }
    private final Db db;
    private final CatalogService catalogue;
    private final Optional<LiveOffers> live;
    public CurrentOffers(Db db,CatalogService catalogue,Optional<LiveOffers> live){this.db=db;this.catalogue=catalogue;this.live=live;}

    public List<Quote> forPack(Product product,String location) {
        return forPack(product,location,null);
    }
    public List<Quote> forPack(Product product,String location,String owner) {
        var request=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        String position=request instanceof org.springframework.web.context.request.ServletRequestAttributes servlet?
            servlet.getRequest().getHeader(LiveOffers.POSITION_HEADER):null;
        return forPackAtPosition(product,location,owner,position);
    }
    public List<Quote> forPackAtPosition(Product product,String location,String owner,String position) {
        Instant now=Instant.now();
        var byRetailer=new TreeMap<String,Quote>();
        var latestCatalogue=new HashMap<String,Instant>();
        var stock=new HashMap<String,Long>();
        for(var row:db.rows("""
            SELECT pp.platform_id,e.stock_quantity FROM online_comparison_evidence e
            JOIN location_prices l ON l.id=e.price_id AND l.verification='VERIFIED' AND l.observed_at=e.price_observed_at
            JOIN platform_products pp ON pp.id=l.platform_product_id AND pp.retailer_sku=e.retailer_sku
            JOIN provider_mappings m ON m.platform_product_id=pp.id
            JOIN product_variants v ON v.id=pp.variant_id AND v.amount_base=e.amount_base AND v.base_unit=e.base_unit
            JOIN products p ON p.id=v.product_id AND p.name=e.product_name AND p.brand=e.brand AND p.variant_name=e.variant_name
            WHERE pp.variant_id=? AND l.location_id=? AND e.observed_at>? AND e.observed_at<=? AND e.stock_quantity IS NOT NULL
            """,product.id(),location,catalogue.freshness().cutoff(now),now)) stock.put(text(row,"platform_id"),num(row,"stock_quantity"));
        for(var offer:product.offers()) {
            if (catalogue.freshness().fresh(offer.updatedAt(),now)
                && ("VERIFIED".equals(offer.verificationStatus())||catalogue.sample()&&"SAMPLE".equals(offer.verificationStatus())))
                latestCatalogue.put(offer.platform(),offer.updatedAt());
            if(!catalogue.eligible(offer)||offer.priceMinor()>100_000_000L||offer.updatedAt().isAfter(now))continue;
            Long quantity=stock.get(offer.platform());
            if(quantity==null||quantity>0) byRetailer.put(offer.platform(),new Quote(offer.platform(),offer.priceMinor(),offer.unitPriceMinor(),
                offer.unitLabel(),quantity,offer.updatedAt(),offer.source(),"RECORDED",null,null,catalogue.freshness().expires(offer.updatedAt()),null));
        }
        // A newer licensed observation may remove an older available offer. It carries no numerical stock count.
        var newest=new HashSet<String>();
        for(var row:db.rows("""
            SELECT o.price_minor,o.stock_status,o.observed_at,o.source_provider,pp.platform_id
            FROM approved_price_observations o JOIN price_retention_grants g ON g.id=o.grant_id
              AND g.listing_id=o.listing_id AND g.location_id=o.location_id AND g.provider=o.source_provider
            JOIN price_source_authorizations a ON a.grant_id=g.id AND a.revoked_at IS NULL AND a.display_allowed=TRUE
            JOIN platform_products pp ON pp.id=o.listing_id AND pp.retailer_sku=o.retailer_sku AND pp.variant_id=o.variant_id
            JOIN grocery_platforms gp ON gp.id=pp.platform_id AND gp.enabled=TRUE
            JOIN provider_mapping_decisions d ON d.decision_id=g.mapping_decision_id AND d.disposition='APPROVED'
              AND d.variant_id=o.variant_id AND d.platform_id=pp.platform_id AND d.retailer_sku=o.retailer_sku
            JOIN product_variants v ON v.id=o.variant_id AND v.pack_label=o.pack_label AND v.pack_label=d.canonical_pack
            JOIN products p ON p.id=v.product_id AND p.name=d.canonical_title AND p.brand=d.canonical_brand
            WHERE o.variant_id=? AND o.location_id=? AND o.observed_at<=? AND o.observed_at>?
              AND g.history_allowed=TRUE AND g.revoked_at IS NULL AND g.retain_until>?
              AND NOT EXISTS (SELECT 1 FROM provider_mapping_decisions n WHERE n.variant_id=d.variant_id AND n.platform_id=d.platform_id AND n.revision_no>d.revision_no)
            ORDER BY o.observed_at DESC,o.id
            """,product.id(),location,now,catalogue.freshness().cutoff(now),now)) {
            String retailer=text(row,"platform_id"); if(!newest.add(retailer))continue;
            Instant at=time(row,"observed_at"); var old=latestCatalogue.get(retailer);
            if(old!=null && !at.isAfter(old)) continue;
            Long price=number(row,"price_minor");
            byRetailer.remove(retailer);
            if(price!=null&&price<=100_000_000L&&"AVAILABLE".equals(text(row,"stock_status"))) {
                byRetailer.put(retailer,new Quote(retailer,price,CatalogService.unitPrice(price,product.amountBase(),product.baseUnit()),
                    CatalogService.unitLabel(product.baseUnit()),null,at,text(row,"source_provider"),"RECORDED",null,null,catalogue.freshness().expires(at),null));
            }
        }
        if(live.isPresent()&&owner!=null&&live.get().matchesPosition(location,position))for(String retailer:com.smartcart.integration.QuickCommerceProvider.RETAILERS){
            var snapshot=live.get().selected(owner,location,product,retailer);
            if(snapshot.isEmpty())continue;
            byRetailer.remove(retailer);
            var offer=snapshot.get().offer();
            if(offer!=null&&Boolean.TRUE.equals(offer.available())&&offer.priceMinor()!=null&&offer.unitPriceMinor()!=null
                &&(offer.stockPacks()==null||offer.stockPacks()>0)) {
                byRetailer.put(retailer,new Quote(retailer,offer.priceMinor(),offer.unitPriceMinor(),offer.unit(),
                    offer.stockPacks()==null?null:offer.stockPacks().longValue(),offer.fetchedAt(),offer.source(),
                    snapshot.get().previouslyRetrieved()?"RETRIEVED_OFFER":"LIVE_RESPONSE",
                    offer.nativeId(),offer.requestId(),offer.freshUntil(),offer.observedAt()));
            }
        }
        var blocked=db.rows("SELECT platform_id FROM platform_locations WHERE location_id=? AND available=FALSE",location);
        blocked.forEach(row->byRetailer.remove(text(row,"platform_id")));
        db.rows("""
            SELECT d.platform_id FROM provider_mapping_decisions d JOIN platform_products p
              ON p.variant_id=d.variant_id AND p.platform_id=d.platform_id AND p.retailer_sku=d.retailer_sku
            WHERE d.variant_id=? AND d.disposition IN ('REJECTED','UNRESOLVED')
              AND NOT EXISTS(SELECT 1 FROM provider_mapping_decisions n WHERE n.variant_id=d.variant_id AND n.platform_id=d.platform_id
                AND n.retailer_sku=d.retailer_sku AND n.revision_no>d.revision_no)
            """,product.id()).forEach(row->byRetailer.remove(text(row,"platform_id")));
        return byRetailer.values().stream().sorted(Comparator.comparingLong(Quote::priceMinor).thenComparing(Quote::retailer)).toList();
    }
}
