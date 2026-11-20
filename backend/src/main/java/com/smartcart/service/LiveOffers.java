package com.smartcart.service;

import com.smartcart.catalogimport.ProductMatching;
import com.smartcart.catalogimport.ImportNormalizer;
import com.smartcart.catalogimport.ProductMatching.Identity;
import com.smartcart.dto.Models.Product;
import com.smartcart.exception.ApiException;
import com.smartcart.integration.QuickCommerceProvider;
import com.smartcart.repository.Db;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import static com.smartcart.repository.Db.*;

/** Local, account-scoped normalized snapshots. The existing mapping journal remains the approval source. */
@Service
@Profile("local")
public class LiveOffers {
    public static final String POSITION_HEADER="X-CartIQ-Offer-Position";
    public record Offer(String retailer,String nativeId,String pack,Long priceMinor,Long mrpMinor,Long unitPriceMinor,
        String unit,Boolean available,Integer stockPacks,String variantId,String productId,String locationId,
        Instant observedAt,Instant fetchedAt,Instant freshUntil,String requestId,String source,String match,int score,String confidence,
        String title,String brand,String gtin,String flavour,String formulation) {}
    private record Key(String owner,String location,String variant,String retailer) {}
    private record Candidate(Offer offer,Identity identity,String decisionId) {}
    private record Snapshot(Candidate candidate,Instant expiresAt,String discoveryAuthorization) {}
    private record DiscoveryKey(String owner,String area,String query,String retailer) {}
    public record MarketplaceListing(QuickCommerceProvider.Product listing,Offer evidence) {}
    public record Discovery(String retailer,Instant fetchedAt,Instant freshUntil,String requestId,List<MarketplaceListing> listings,
        @com.fasterxml.jackson.annotation.JsonIgnore String authorizationId) {}
    private final Map<Key,Snapshot> current=new ConcurrentHashMap<>();
    private final Map<DiscoveryKey,Discovery> discoveries=new ConcurrentHashMap<>();
    private final Set<List<String>> refreshing=ConcurrentHashMap.newKeySet();
    private final Db db;
    private final CatalogService catalogue;
    private final String location,permission;
    private final double lat,lon;

    public LiveOffers(Db db,CatalogService catalogue,@Value("${QUICKCOMMERCE_OFFER_PERMISSION_REFERENCE:}") String permission,
        @Value("${QUICKCOMMERCE_OFFER_LOCATION_ID:}") String location,
        @Value("${QUICKCOMMERCE_OFFER_LAT:NaN}") double lat,@Value("${QUICKCOMMERCE_OFFER_LON:NaN}") double lon) {
        this.db=db;this.catalogue=catalogue;this.permission=permission;this.location=location;this.lat=lat;this.lon=lon;
    }
    public String areaFor(ShoppingPositions.Fix fix) {
        return areaFor(fix.latitude(),fix.longitude(),fix.postcode());
    }
    public String areaFor(double latitude,double longitude,String postcode) {
        return matchesPosition(location,latitude+","+longitude+","+(postcode==null?"":postcode))?location:null;
    }
    public boolean configured(String locationId) {
        return !catalogue.sample()&&!permission.isBlank()&&permission.length()<=200&&!location.isBlank()&&location.equals(locationId)
            && Double.isFinite(lat)&&Double.isFinite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180;
    }
    public String requireScope(String locationId,Double latitude,Double longitude,String pin) {
        if(!configured(locationId))throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
            "The transient offer pilot has no approved catalogue location or written permission reference.");
        String expected=text(db.one("SELECT postcode FROM delivery_locations WHERE id=?",locationId),"postcode");
        if(latitude==null||longitude==null||Double.compare(latitude,lat)!=0||Double.compare(longitude,lon)!=0
            ||pin!=null&&!pin.equals(expected))
            throw ApiException.bad("Use the approved confirmed coordinates for this catalogue area.");
        return expected;
    }
    public boolean matchesPosition(String locationId,String position) {
        if(!configured(locationId)||position==null||position.length()>100)return false;
        var values=position.split(",",-1);
        if(values.length!=3)return false;
        try {
            if(Double.compare(Double.parseDouble(values[0]),lat)!=0||Double.compare(Double.parseDouble(values[1]),lon)!=0)return false;
        }catch(NumberFormatException invalid){return false;}
        return values[2].isEmpty()||db.maybe("SELECT postcode FROM delivery_locations WHERE id=?",locationId)
            .map(row->values[2].equals(text(row,"postcode"))).orElse(false);
    }
    public List<Offer> update(String owner,String locationId,Product pack,String retailer,QuickCommerceProvider.Result response) {
        return update(owner,locationId,pack,retailer,response,null,response.freshUntil());
    }
    public List<Offer> update(String owner,String locationId,Product pack,String retailer,QuickCommerceProvider.Result response,String requiredMapping,Instant endsAt) {
        return update(owner,locationId,pack,retailer,response,requiredMapping,endsAt,null);
    }
    public List<Offer> update(String owner,String locationId,Product pack,String retailer,QuickCommerceProvider.Result response,
                              String requiredMapping,Instant endsAt,String discoveryAuthorization) {
        if(!configured(locationId)||!retailer.equals(response.retailer()))return List.of();
        Instant now=Instant.now();
        Instant fetchedUntil=catalogue.freshness().expires(response.fetchedAt());
        Instant cutoff=fetchedUntil.isBefore(endsAt)?fetchedUntil:endsAt;
        var results=response.products().stream().limit(100).map(row->normalize(pack,locationId,response,row,now,requiredMapping,cutoff)).toList();
        var evidence=results.stream().map(Candidate::offer).toList();
        // An unrelated discovery query cannot withdraw an existing exact-pack quote.
        if(discoveryAuthorization!=null&&results.stream().noneMatch(row->Objects.equals(requiredMapping,row.decisionId())))
            return evidence;
        var approved=results.stream().filter(row->"VERIFIED".equals(row.offer().match())).toList();
        if(approved.size()>1)approved=List.of();
        Candidate selected=approved.isEmpty()?null:approved.get(0);
        synchronized(current) {
            current.entrySet().removeIf(row->!row.getValue().expiresAt().isAfter(now));
            var key=new Key(owner,locationId,pack.id(),retailer);
            if(current.size()<256||current.containsKey(key))
                current.put(key,new Snapshot(selected,cutoff,discoveryAuthorization));
        }
        return evidence;
    }
    public <T> T refresh(String owner,String locationId,String variant,Supplier<T> request) {
        var key=List.of(owner,locationId,variant);
        if(!refreshing.add(key))throw new ApiException(HttpStatus.CONFLICT,"An offer refresh for this pack is already in progress.");
        try{return request.get();}finally{refreshing.remove(key);}
    }
    public synchronized void rememberDiscovery(String owner,String area,String query,QuickCommerceProvider.Result result,
                                               List<Offer> evidence,Instant pilotEnd,String authorizationId) {
        var cutoff=result.freshUntil().isBefore(pilotEnd)?result.freshUntil():pilotEnd;
        var bySku=new LinkedHashMap<String,MarketplaceListing>();
        for(int i=0;i<Math.min(result.products().size(),evidence.size());i++){
            var row=result.products().get(i);
            bySku.putIfAbsent(row.nativeId(),new MarketplaceListing(row,evidence.get(i)));
        }
        var key=new DiscoveryKey(owner,area,query,result.retailer());
        if(discoveries.size()>=48&&!discoveries.containsKey(key))
            discoveries.entrySet().stream().min(Comparator.comparing(e->e.getValue().fetchedAt()))
                .ifPresent(e->discoveries.remove(e.getKey(),e.getValue()));
        discoveries.put(key,new Discovery(result.retailer(),result.fetchedAt(),cutoff,result.requestId(),List.copyOf(bySku.values()),authorizationId));
    }
    public List<Discovery> discovery(String owner,String area,String query){
        Instant now=Instant.now();
        return discoveries.entrySet().stream().filter(e->e.getKey().owner().equals(owner)&&e.getKey().area().equals(area)
            &&e.getKey().query().equals(query)&&e.getValue().freshUntil().isAfter(now))
            .map(Map.Entry::getValue).sorted(Comparator.comparing(Discovery::retailer)).toList();
    }
    public record Selection(Offer offer,boolean previouslyRetrieved,String decisionId) {}
    public boolean discoveryDisplayActive(String ticket){
        var now=Instant.now();
        return db.count("""
            SELECT COUNT(*) FROM retailer_pilot_attempts a JOIN retailer_pilots p ON p.id=a.pilot_id
            WHERE a.id=? AND a.discovery_query IS NOT NULL AND a.confirmed_debit=1
              AND p.enabled=TRUE AND p.permission_reference=? AND p.starts_at<=? AND p.ends_at>?
            """,ticket,permission,now,now)>0;
    }
    public boolean approvedMapping(Product product,String retailer,String decision,String sku) {
        return mapping(product,retailer,sku).filter(row->decision.equals(text(row,"decision_id"))).isPresent();
    }
    public Map<String,Long> coverage(String owner,String locationId,String category) {
        var byRetailer=new HashMap<String,Set<String>>();
        for(var key:current.keySet()) {
            if(!key.owner().equals(owner)||!key.location().equals(locationId))continue;
            if(db.maybe("""
                SELECT v.id FROM product_variants v JOIN products p ON p.id=v.product_id AND p.active=TRUE
                WHERE v.id=? AND (COALESCE(?,'')='' OR p.category_id=?)
                AND NOT EXISTS(SELECT 1 FROM platform_locations l WHERE l.platform_id=? AND l.location_id=? AND l.available=FALSE)
                """,key.variant(),category,category,key.retailer(),locationId).isEmpty())continue;
            try {
                var quote=selected(owner,locationId,catalogue.product(key.variant(),locationId),key.retailer()).map(Selection::offer).orElse(null);
                if(quote!=null&&quote.priceMinor()!=null&&quote.unitPriceMinor()!=null&&Boolean.TRUE.equals(quote.available())
                    &&(quote.stockPacks()==null||quote.stockPacks()>0))
                    byRetailer.computeIfAbsent(key.retailer(),unused->new HashSet<>()).add(quote.productId());
            }catch(ApiException changed) { if(changed.status!=HttpStatus.NOT_FOUND)throw changed; }
        }
        var counts=new HashMap<String,Long>();
        byRetailer.forEach((retailer,products)->counts.put(retailer,(long)products.size()));
        return counts;
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay=30_000)
    void discardExpired() {
        Instant now=Instant.now();
        current.entrySet().removeIf(row->!row.getValue().expiresAt().isAfter(now));
        discoveries.entrySet().removeIf(row->!row.getValue().freshUntil().isAfter(now));
    }
    public Optional<Selection> selected(String owner,String locationId,Product pack,String retailer) {
        if(owner==null||!configured(locationId))return Optional.empty();
        var key=new Key(owner,locationId,pack.id(),retailer);
        var row=current.get(key);
        if(row==null)return Optional.empty();
        var candidate=row.candidate();
        var offer=candidate==null?null:candidate.offer();
        if(!row.expiresAt().isAfter(Instant.now())||offer!=null&&!offer.freshUntil().isAfter(Instant.now())
            ||row.discoveryAuthorization()!=null&&!discoveryDisplayActive(row.discoveryAuthorization())){
            current.remove(key,row);return Optional.empty();
        }
        if(offer!=null&&mapping(pack,retailer,offer.nativeId()).filter(approval->candidate.decisionId().equals(text(approval,"decision_id"))
            &&compatible(text(approval,"current_flavour"),candidate.identity().variant())
            &&compatible(text(approval,"current_formulation"),candidate.identity().formulation())
            &&compatibleBarcode(pack.id(),candidate.identity().gtin())).isEmpty())
            return Optional.of(new Selection(null,row.discoveryAuthorization()!=null,null));
        return Optional.of(new Selection(offer,row.discoveryAuthorization()!=null,candidate==null?null:candidate.decisionId()));
    }

    private Candidate normalize(Product pack,String locationId,QuickCommerceProvider.Result response,QuickCommerceProvider.Product row,Instant now,
                                String requiredMapping,Instant cutoff) {
        String retailer=response.retailer();
        var approval=mapping(pack,retailer,row.nativeId());
        if(requiredMapping!=null)approval=approval.filter(a->requiredMapping.equals(text(a,"decision_id")));
        var identity=new Identity(barcode(row),row.brand(),row.title(),row.pack(),row.flavour(),row.formulation());
        var evidence=approval.map(a->new Identity(text(a,"listing_gtin"),text(a,"listing_brand"),text(a,"listing_title"),
            text(a,"listing_pack"),text(a,"listing_variant"),text(a,"listing_formulation")))
            .orElseGet(()->new Identity(null,pack.brand(),pack.name(),pack.quantity(),null,null));
        var result=ProductMatching.compareHybrid(evidence,identity);
        boolean sameApprovedFields=approval.isPresent()&&same(evidence.brand(),row.brand())&&same(evidence.name(),row.title())
            &&same(evidence.variant(),row.flavour())&&same(evidence.formulation(),row.formulation())
            &&(evidence.gtin()==null||sameBarcode(evidence.gtin(),identity.gtin()));
        var canonicalPack=ProductMatching.pack(pack.quantity());
        boolean canonicalFields=approval.map(a->compatible(text(a,"current_flavour"),row.flavour())
            &&compatible(text(a,"current_formulation"),row.formulation())).orElse(false);
        boolean verified=response.requestId()!=null&&sameApprovedFields&&canonicalFields&&compatibleBarcode(pack.id(),identity.gtin())
            &&canonicalPack!=null&&row.normalizedPack()!=null
            &&canonicalPack.count()==row.normalizedPack().count()&&canonicalPack.each().unit().equals(row.normalizedPack().each().unit())
            &&canonicalPack.each().amount().compareTo(row.normalizedPack().each().amount())==0&&result.reasons().contains("PACK_SAME")
            &&result.reasons().stream().noneMatch(code->Set.of("GTIN_CONFLICT","INVALID_GTIN","RESTRICTED_GTIN","VARIANT_DIFFERENT","FORMULATION_DIFFERENT").contains(code));
        Instant observed=parseTime(row.providerFetchedAt());
        Instant freshnessStart=observed==null?response.fetchedAt():observed;
        Instant expiry=freshnessStart.isAfter(now)?Instant.EPOCH:catalogue.freshness().expires(freshnessStart);
        if(expiry.isAfter(cutoff))expiry=cutoff;
        if(row.providerFetchedAt()!=null&&(observed==null||observed.isAfter(now)))expiry=Instant.EPOCH;
        var normalized=row.normalizedPack();
        Long totalPrice=row.priceMinor()==null||row.priceMinor()>100_000_000L?null:row.priceMinor();
        Long unitPrice=totalPrice==null||normalized==null?null:CatalogService.unitPrice(totalPrice,
            normalized.each().amount().multiply(java.math.BigDecimal.valueOf(normalized.count())),normalized.each().unit());
        var offer=new Offer(retailer,row.nativeId(),row.pack(),row.priceMinor(),row.mrpMinor(),unitPrice,
            normalized==null?null:CatalogService.unitLabel(normalized.each().unit()),row.available(),row.inventory(),
            verified?pack.id():null,verified?pack.canonicalProductId():null,locationId,observed,response.fetchedAt(),expiry,
            response.requestId(),"QUICKCOMMERCEAPI",verified?"VERIFIED":result.decision()==ProductMatching.Decision.DISTINCT?"DISTINCT":"UNRESOLVED",
            result.score(),result.confidence().name(),row.title(),row.brand(),"invalid".equals(identity.gtin())?null:identity.gtin(),
            row.flavour(),row.formulation());
        return new Candidate(offer,identity,approval.map(a->text(a,"decision_id")).orElse(null));
    }
    private Optional<Map<String,Object>> mapping(Product pack,String retailer,String sku){
        return db.maybe("""
            SELECT d.*,a.flavour AS current_flavour,a.formulation AS current_formulation FROM provider_mapping_decisions d
            JOIN platform_products p ON p.variant_id=d.variant_id AND p.platform_id=d.platform_id AND p.retailer_sku=d.retailer_sku
            JOIN provider_mappings m ON m.platform_product_id=p.id
            JOIN grocery_platforms g ON g.id=p.platform_id AND g.enabled=TRUE
            JOIN product_variants v ON v.id=p.variant_id
            JOIN products c ON c.id=v.product_id AND c.variant_name=d.canonical_variant
            LEFT JOIN grocery_variant_details a ON a.variant_id=v.id
            WHERE d.variant_id=? AND d.platform_id=? AND d.retailer_sku=? AND d.disposition='APPROVED'
              AND d.source_provider='QUICKCOMMERCEAPI' AND d.permission_reference=? AND d.canonical_brand=?
              AND d.canonical_title=? AND d.canonical_pack=?
              AND NOT EXISTS(SELECT 1 FROM provider_mapping_decisions n WHERE n.variant_id=d.variant_id
                 AND n.platform_id=d.platform_id AND n.revision_no>d.revision_no)
            """,pack.id(),retailer,sku,permission,pack.brand(),pack.name(),pack.quantity());
    }
    private static boolean same(String a,String b){return ProductMatching.normalized(a).equals(ProductMatching.normalized(b));}
    private static boolean compatible(String known,String supplied){return known==null||known.isBlank()||same(known,supplied);}
    private boolean compatibleBarcode(String variant,String barcode) {
        if(barcode==null)return true;
        final String normalized;
        try {normalized=ImportNormalizer.gtin(barcode);}
        catch(ApiException invalid){return false;}
        var assigned=db.maybe("SELECT variant_id FROM variant_barcodes WHERE gtin=?",normalized);
        return assigned.map(row->variant.equals(text(row,"variant_id")))
            .orElseGet(()->db.count("SELECT COUNT(*) FROM variant_barcodes WHERE variant_id=?",variant)==0);
    }
    private static String barcode(QuickCommerceProvider.Product row) {
        if(row.gtin()!=null&&row.barcode()!=null&&!sameBarcode(row.gtin(),row.barcode()))return "invalid";
        return row.gtin()==null?row.barcode():row.gtin();
    }
    private static boolean sameBarcode(String a,String b) {
        try{return ImportNormalizer.gtin(a).equals(ImportNormalizer.gtin(b));}
        catch(ApiException invalid){return false;}
    }
    private static Instant parseTime(String value){
        if(value==null)return null;
        try{return Instant.parse(value);}catch(java.time.format.DateTimeParseException invalid){return null;}
    }
}
