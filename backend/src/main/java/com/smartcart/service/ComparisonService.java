package com.smartcart.service;

import com.smartcart.dto.Models.*;
import com.smartcart.exception.ApiException;
import com.smartcart.integration.QuickCommerceProvider;
import com.smartcart.integration.QuickCommerceProvider.Outcome;
import com.smartcart.repository.Db;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import static com.smartcart.repository.Db.*;

@Service
public class ComparisonService {
    private final CatalogService catalog;
    private final LocalStoreService stores;
    private final Db db;
    private final Optional<QuickCommerceProvider> quickCommerce;
    public ComparisonService(CatalogService catalog,LocalStoreService stores,Db db){this(catalog,stores,db,Optional.empty());}

    @Autowired
    public ComparisonService(CatalogService catalog, LocalStoreService stores, Db db,
                             Optional<QuickCommerceProvider> quickCommerce) {
        this.catalog=catalog;
        this.stores=stores;
        this.db=db;
        this.quickCommerce=quickCommerce;
    }

    public record RetailerCandidates(String retailer, String status, Outcome outcome,
                                     QuickCommerceProvider.Result result, String message,String providerRequestId,Integer upstreamStatus) {
        public RetailerCandidates(String retailer,String status,Outcome outcome,QuickCommerceProvider.Result result,String message){
            this(retailer,status,outcome,result,message,null,null);
        }
    }
    public record RetailerSearch(String query, List<RetailerCandidates> retailers, boolean paused) {}

    /** Manual development comparison of retailer candidates, independent of verified catalogue offers. */
    public RetailerSearch compareRetailerSearch(String query, double lat, double lon, String pincode) {
        return compareRetailerSearch(query, lat, lon, pincode, null);
    }

    public RetailerSearch compareRetailerSearch(String query, double lat, double lon, String pincode,
                                               List<String> requestedRetailers) {
        List<String> retailers = QuickCommerceProvider.RETAILERS;
        if (requestedRetailers != null) {
            var selected = new HashSet<>(requestedRetailers);
            if (selected.isEmpty() || selected.size() != requestedRetailers.size() ||
                selected.stream().anyMatch(retailer -> retailer == null || !QuickCommerceProvider.RETAILERS.contains(retailer))) {
                throw ApiException.bad("Choose one or more distinct supported retailers.");
            }
            retailers = retailers.stream().filter(selected::contains).toList();
        }
        var provider=quickCommerce.orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
            "QuickCommerce development access is unavailable."));
        var rows=new ArrayList<RetailerCandidates>();
        boolean stopped=false;
        for (var retailer : retailers) {
            if (stopped) {
                rows.add(new RetailerCandidates(retailer,"not_attempted",Outcome.NOT_ATTEMPTED,null,
                    "An earlier request stopped this comparison. No request was sent to this retailer."));
                continue;
            }
            try {
                var result=provider.search(retailer,query,lat,lon,pincode);
                rows.add(new RetailerCandidates(retailer,"success",
                    result.products().isEmpty() ? Outcome.NO_RESULTS : Outcome.SUCCESS,result,null));
            } catch (QuickCommerceProvider.DuplicateLookup duplicate) {
                rows.add(new RetailerCandidates(retailer,"duplicate",Outcome.NOT_ATTEMPTED,null,duplicate.getMessage()));
            } catch (ApiException error) {
                if (error.status == HttpStatus.BAD_REQUEST) throw error;
                var failure = error instanceof QuickCommerceProvider.LookupFailure known ? known : null;
                rows.add(new RetailerCandidates(retailer,"error",
                    failure == null ? Outcome.PROVIDER_ERROR : failure.outcome,null,error.getMessage(),
                    failure==null?null:failure.providerRequestId,failure==null?null:failure.upstreamStatus));
                stopped = failure == null || !failure.confirmedNoCharge ||
                    failure.upstreamStatus!=null&&Set.of(401,402,403,429).contains(failure.upstreamStatus);
            }
        }
        return new RetailerSearch(query.strip(),List.copyOf(rows),stopped);
    }

    public record Option(String source,String name,String variantId,Long priceMinor,Long unitPriceMinor,String unitLabel,
        String availability,Integer stockQuantity,Double distanceKm,Long deliveryMinor,Long handlingMinor,Long minimumMinor,
        Long totalMinor,String dataSource,Instant observedAt,String verificationStatus,boolean eligible,String reason,String sourceId) {}
    public record Result(Product product,int quantity,String fulfillment,List<Option> options,Option lowestEligible,Long savingsMinor,String explanation) {}

    public Result compare(String variantId,String locationId,double latitude,double longitude,double radiusKm,int quantity,String fulfillment) {
        if(quantity<1||quantity>99)throw ApiException.bad("Quantity must be between 1 and 99.");
        if(!Set.of("pickup","delivery").contains(fulfillment))throw ApiException.bad("Choose pickup or delivery.");
        Product product=catalog.product(variantId,locationId);
        var rows=new ArrayList<Option>();
        var platforms=new HashMap<String,Platform>();
        for(var platform:catalog.platforms()) platforms.put(platform.id(),platform);
        for(String source:List.of("blinkit","instamart","zepto")){
            var platform=platforms.get(source);
            var offer=product.offers().stream().filter(o->source.equals(o.platform())).findFirst().orElse(null);
            String name=platform==null?source:platform.name();
            var evidence=db.maybe("SELECT e.* FROM online_comparison_evidence e JOIN location_prices l ON l.id=e.price_id JOIN platform_products pp ON pp.id=l.platform_product_id JOIN provider_mappings m ON m.platform_product_id=pp.id JOIN product_variants v ON v.id=pp.variant_id JOIN products p ON p.id=v.product_id WHERE pp.platform_id=? AND v.id=? AND l.location_id=? AND l.source='REWALTO' AND l.verification='VERIFIED' AND e.retailer_sku=pp.retailer_sku AND e.product_name=p.name AND e.brand=p.brand AND e.variant_name=p.variant_name AND e.amount_base=v.amount_base AND e.base_unit=v.base_unit AND e.price_observed_at=l.observed_at",source,variantId,locationId);
            Integer stock=evidence.map(row->row.get("stock_quantity")==null?null:((Number)row.get("stock_quantity")).intValue()).orElse(null);
            Long delivery=evidence.map(row->number(row,"delivery_charge_minor")).orElse(null);
            Long handling=evidence.map(row->number(row,"handling_charge_minor")).orElse(null);
            Long minimum=evidence.map(row->number(row,"minimum_minor")).orElse(null);
            Instant evidenceTime=evidence.map(row->time(row,"observed_at")).orElse(null);
            boolean evidenceFresh=catalog.freshness().fresh(evidenceTime,Instant.now());
            Long subtotal=offer==null||offer.priceMinor()==null?null:Math.multiplyExact(offer.priceMinor(),(long)quantity);
            String reason=offer==null?"No observation available.":!fulfillment.equals("delivery")?"Online pickup is not confirmed.":platform==null||!platform.enabled()?"Source disabled.":evidence.isEmpty()?"Exact identity evidence is unavailable.":!catalog.eligible(offer)?"Price or availability is not verified and fresh.":!evidenceFresh?"Stock and terms evidence is stale.":stock==null?"Stock quantity is unknown.":stock<quantity?"Insufficient stock.":delivery==null||handling==null?"Fulfillment charges are unknown.":minimum==null?"Minimum order requirement is unknown.":subtotal<minimum?"Minimum order is not met.":"Eligible.";
            boolean eligible="Eligible.".equals(reason);
            Long total=eligible?Math.addExact(Math.addExact(subtotal,delivery),handling):null;
            rows.add(new Option(source,name,variantId,offer==null?null:offer.priceMinor(),offer==null?null:offer.unitPriceMinor(),
                offer==null?CatalogService.unitLabel(product.baseUnit()):offer.unitLabel(),offer==null?"no_data":offer.status(),stock,null,
                delivery,handling,minimum,total,offer==null?null:offer.source(),offer==null?null:offer.updatedAt(),offer==null?null:offer.verificationStatus(),eligible,reason,source));
        }
        for(var store:stores.nearby(latitude,longitude,radiusKm)){
            String storeId=text(store,"id");
            var listing=db.maybe("SELECT price_minor,stock_quantity,observed_at FROM local_store_listings WHERE store_id=? AND variant_id=?",storeId,variantId);
            Long price=listing.map(row->number(row,"price_minor")).orElse(null);
            Integer stock=listing.map(row->((Number)row.get("stock_quantity")).intValue()).orElse(null);
            Instant observed=listing.map(row->time(row,"observed_at")).orElse(null);
            boolean enabled=bool(store,fulfillment.equals("pickup")?"pickup_enabled":"delivery_enabled");
            boolean fresh=catalog.freshness().fresh(observed,Instant.now());
            var terms=db.maybe("SELECT * FROM local_store_comparison_terms WHERE store_id=?",storeId);
            Long charge=terms.map(row->number(row,fulfillment+"_charge_minor")).orElse(null);
            Long minimum=terms.map(row->number(row,fulfillment+"_minimum_minor")).orElse(null);
            Instant termsObserved=terms.map(row->time(row,"observed_at")).orElse(null);
            boolean termsFresh=catalog.freshness().fresh(termsObserved,Instant.now());
            Long subtotal=price==null?null:Math.multiplyExact(price,(long)quantity);
            String reason=listing.isEmpty()?"No listing available.":!enabled?"Fulfillment unavailable.":stock<quantity?"Insufficient stock.":!fresh?"Listing is stale.":charge==null?"Fulfillment charge is unknown.":minimum==null?"Minimum order requirement is unknown.":!termsFresh?"Fulfillment terms are stale.":subtotal<minimum?"Minimum order is not met.":"Eligible.";
            boolean eligible="Eligible.".equals(reason);
            Long total=eligible?Math.addExact(subtotal,charge):null;
            rows.add(new Option("offline",text(store,"name"),variantId,price,price==null?null:CatalogService.unitPrice(price,product.amountBase(),product.baseUnit()),
                CatalogService.unitLabel(product.baseUnit()),stock==null?"no_data":stock>0?"available":"out_of_stock",stock,
                ((Number)store.get("distanceKm")).doubleValue(),charge,
                null,minimum,total,"LOCAL_STORE",observed,"ADMIN_RECORDED",eligible,reason,storeId));
        }
        var eligible=rows.stream().filter(Option::eligible).sorted(Comparator.comparingLong(Option::totalMinor).thenComparing(Option::source).thenComparing(Option::sourceId)).toList();
        Option lowest=eligible.isEmpty()?null:eligible.get(0);
        Long savings=eligible.size()<2?null:eligible.get(eligible.size()-1).totalMinor()-lowest.totalMinor();
        return new Result(product,quantity,fulfillment,List.copyOf(rows),lowest,savings,
            lowest==null?"No option has confirmed identity, availability and total cost.":"Lowest eligible total among confirmed options.");
    }
}

