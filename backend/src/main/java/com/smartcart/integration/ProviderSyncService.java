package com.smartcart.integration;

import com.smartcart.catalogimport.ImportNormalizer;
import com.smartcart.catalogimport.ProductMatching;
import com.smartcart.repository.Db;
import com.smartcart.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import static com.smartcart.repository.Db.*;

@Service
public class ProviderSyncService {
    private static final Set<String> PLATFORMS = Set.of("blinkit", "instamart", "zepto");
    private static final Set<String> GENERIC_VARIANTS = Set.of("", "standard", "regular", "original", "plain", "classic", "default");
    private static final Pattern DECISION_ID = Pattern.compile("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final String APPROVED = "APPROVED";
    private final Db db;private final GroceryDataProvider provider;
    private final ProviderMappingJournal journal;
    @org.springframework.beans.factory.annotation.Autowired
    public ProviderSyncService(Db db){this(db,null);}
    public ProviderSyncService(Db db,GroceryDataProvider provider){
        this.db=db;
        this.provider=provider;
        this.journal=new ProviderMappingJournal(db);
    }

    public record MappingReview(String variantId, String canonicalPack, List<String> canonicalGtins,
                                ProductMatching.Result evidence, List<String> blockingReasons,ProductMatching.Identity canonicalIdentity) {}
    public record MappingEvidence(String decisionId, String sourceProvider, String sourceRecordId,
                                  String evidenceReference, String evidenceSha256, String permissionReference,
                                  String rationale, String supersedesDecisionId) {}
    public enum ReviewDisposition { REJECTED, UNRESOLVED }
    private record MappingAssessment(ProductMatching.Identity canonical, MappingReview review) {}

    @Transactional(readOnly=true)
    public MappingReview reviewMapping(String variant, ProductMatching.Identity listing) {
        return assess(variant,listing).review();
    }

    private MappingAssessment assess(String variant, ProductMatching.Identity listing) {
        var canonical=db.one("""
            SELECT v.pack_label,p.name,p.brand,p.variant_name,d.flavour,d.formulation
            FROM product_variants v JOIN products p ON p.id=v.product_id
            LEFT JOIN grocery_variant_details d ON d.variant_id=v.id WHERE v.id=?
            """,variant);
        List<String> gtins=db.rows("SELECT gtin FROM variant_barcodes WHERE variant_id=? ORDER BY gtin",variant)
            .stream().map(row->text(row,"gtin")).toList();
        String submitted=listing.gtin();
        String usableGtin=null;
        List<String> blockers=new ArrayList<>();
        if(submitted!=null&&!submitted.isBlank()) {
            try {
                usableGtin=ImportNormalizer.gtin(submitted.strip());
                if(usableGtin.startsWith("0200")) blockers.add("UNUSABLE_LISTING_GTIN");
                if(!gtins.isEmpty()&&!gtins.contains(usableGtin)) blockers.add("CANONICAL_GTIN_CONFLICT");
                if(db.count("SELECT COUNT(*) FROM variant_barcodes WHERE gtin=? AND variant_id<>?",usableGtin,variant)>0)
                    blockers.add("GTIN_REGISTERED_TO_ANOTHER_VARIANT");
            } catch(ApiException invalid) { blockers.add("INVALID_LISTING_GTIN"); }
        }
        String canonicalGtin=usableGtin!=null&&gtins.contains(usableGtin) ? usableGtin : gtins.stream().findFirst().orElse(null);
        String variantName=Objects.requireNonNullElse(text(canonical,"flavour"),text(canonical,"variant_name"));
        String formulation=text(canonical,"formulation");
        String comparableVariant="standard".equalsIgnoreCase(variantName) ? null : variantName;
        String pack=text(canonical,"pack_label");
        var identity=new ProductMatching.Identity(canonicalGtin,text(canonical,"brand"),text(canonical,"name"),
            pack,comparableVariant,formulation);
        var evidence=ProductMatching.compareHybrid(identity,listing);
        for(String reason:List.of("BRAND_DIFFERENT","PACK_DIFFERENT","VARIANT_DIFFERENT","FORMULATION_DIFFERENT"))
            if(evidence.reasons().contains(reason)) blockers.add(reason);
        if(evidence.reasons().contains("PACK_MISSING")) blockers.add("PACK_COMPATIBILITY_UNCONFIRMED");
        if(evidence.reasons().contains("BRAND_MISSING")) blockers.add("BRAND_UNCONFIRMED");
        if(evidence.reasons().contains("TITLE_MISSING")||evidence.reasons().contains("TITLE_DIFFERENT"))
            blockers.add("TITLE_UNCONFIRMED");
        if(evidence.reasons().contains("VARIANT_MISSING")
            && (namedVariant(variantName)||namedVariant(listing.variant())))
            blockers.add("VARIANT_UNCONFIRMED");
        if(evidence.reasons().contains("FORMULATION_MISSING") && (provided(formulation,150)||provided(listing.formulation(),150)))
            blockers.add("FORMULATION_UNCONFIRMED");
        var snapshot=new ProductMatching.Identity(canonicalGtin,identity.brand(),identity.name(),pack,variantName,formulation);
        return new MappingAssessment(snapshot,new MappingReview(variant,pack,gtins,evidence,List.copyOf(blockers),snapshot));
    }

    @Transactional public void map(String admin,String variant,String platform,String sku) {
        map(admin,variant,platform,sku,null,null);
    }

    @Transactional public void map(String admin,String variant,String platform,String sku,ProductMatching.Identity identity) {
        map(admin,variant,platform,sku,identity,null);
    }

    @Transactional
    public void map(String admin,String variant,String platform,String sku,ProductMatching.Identity identity,
                    MappingEvidence evidence) {
        lockCandidate(variant,platform,sku);
        var previous=lockCurrentMapping(variant,platform);
        String currentSku=text(previous,"retailer_sku");
        boolean currentApproval=sku.equals(currentSku)&&text(previous,"matched_by")!=null;
        if(evidence==null) {
            if(!currentApproval) throw conflict("A new mapping approval requires a source evidence and permission record.");
            if(identity!=null) requireCompatible(assess(variant,identity).review());
            return;
        }
        validateEvidence(identity,evidence);
        if(journal.replay(variant,platform,sku,identity,evidence,APPROVED)!=null) {
            if(!currentApproval) throw conflict("This approval is historical and cannot change the current mapping.");
            return;
        }
        journal.requirePredecessor(variant,platform,sku,evidence,true);
        var assessment=assess(variant,identity);
        requireCompatible(assessment.review());
        if(db.count("SELECT COUNT(*) FROM platform_products WHERE platform_id=? AND retailer_sku=? AND variant_id<>?",
            platform,sku,variant)>0)
            throw conflict("This retailer SKU is already assigned to a different canonical variant.");

        Instant now=Instant.now();
        String listing=text(previous,"id");
        if(listing==null) {
            listing=variant+"-"+platform;
            db.insert("platform_products",fields("id",listing,"platform_id",platform,"variant_id",variant,"retailer_sku",sku));
        } else if(!sku.equals(currentSku)) {
            if(db.count("SELECT COUNT(*) FROM location_prices WHERE platform_product_id=?",listing)>0
                ||db.count("SELECT COUNT(*) FROM price_history WHERE platform_product_id=?",listing)>0
                ||db.count("SELECT COUNT(*) FROM price_retention_grants WHERE listing_id=?",listing)>0)
                throw conflict(
                    "This listing has observations for its existing SKU. Create a separately reviewed listing before remapping.");
            db.run("UPDATE platform_products SET source_url=NULL,retailer_sku=?,updated_at=? WHERE id=?",sku,now,listing);
        }
        if(text(previous,"matched_by")==null)
            db.insert("provider_mappings",fields("platform_product_id",listing,"matched_by",admin,"matched_at",now));
        else if(!sku.equals(currentSku))
            db.run("UPDATE provider_mappings SET matched_by=?,matched_at=?,updated_at=? WHERE platform_product_id=?",admin,now,now,listing);
        journal.append(admin,now,variant,platform,sku,identity,evidence,APPROVED,assessment.canonical(),assessment.review(),previous);
    }

    @Transactional
    public Map<String,Object> recordReview(String admin,String variant,String platform,String sku,
                                           ProductMatching.Identity identity,MappingEvidence evidence,
                                           ReviewDisposition disposition) {
        if(disposition==null) throw ApiException.bad("Choose REJECTED or UNRESOLVED for a review decision.");
        validateEvidence(identity,evidence);
        lockCandidate(variant,platform,sku);
        var replay=journal.replay(variant,platform,sku,identity,evidence,disposition.name());
        if(replay!=null) return replay;
        journal.requirePredecessor(variant,platform,sku,evidence,false);
        var assessment=assess(variant,identity);
        return journal.append(admin,Instant.now(),variant,platform,sku,identity,evidence,disposition.name(),
            assessment.canonical(),assessment.review(),lockCurrentMapping(variant,platform));
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> mappingDecisions(String variant,String platform,int afterRevision) {
        validateTarget(variant,platform);
        if(afterRevision<0) throw ApiException.bad("The revision cursor must be nonnegative.");
        return journal.list(variant,platform,afterRevision);
    }

    private static void validateTarget(String variant,String platform) {
        if(!provided(variant,60)||platform==null||!PLATFORMS.contains(platform))
            throw ApiException.bad("Provide the canonical variant and a supported retailer platform.");
    }

    private void lockCandidate(String variant,String platform,String sku) {
        validateTarget(variant,platform);
        if(!provided(sku,150)||!sku.equals(sku.strip())||sku.toLowerCase(Locale.ROOT).startsWith("sample-"))
            throw ApiException.bad("Use a real retailer SKU after checking the exact brand, variant and pack size.");
        db.one("SELECT id FROM product_variants WHERE id=? FOR UPDATE",variant);
    }

    private Map<String,Object> lockCurrentMapping(String variant,String platform) {
        var listing=db.maybe("SELECT id,retailer_sku FROM platform_products WHERE variant_id=? AND platform_id=? FOR UPDATE",
            variant,platform);
        if(listing.isEmpty()) return Map.of();
        var previous=new LinkedHashMap<>(listing.get());
        db.maybe("SELECT matched_by,matched_at FROM provider_mappings WHERE platform_product_id=?",text(previous,"id"))
            .ifPresent(previous::putAll);
        return previous;
    }

    private static void validateEvidence(ProductMatching.Identity identity,MappingEvidence evidence) {
        if(identity==null||evidence==null||!provided(evidence.decisionId(),36)
            ||!DECISION_ID.matcher(evidence.decisionId()).matches()
            ||!provided(evidence.evidenceSha256(),64)||!SHA256.matcher(evidence.evidenceSha256()).matches()
            ||!provided(evidence.sourceProvider(),80)||!provided(evidence.sourceRecordId(),150)
            ||!provided(evidence.evidenceReference(),500)||!provided(evidence.permissionReference(),200)
            ||!provided(evidence.rationale(),1000)
            ||(evidence.supersedesDecisionId()!=null&&!DECISION_ID.matcher(evidence.supersedesDecisionId()).matches()))
            throw ApiException.bad("Provide a listing identity, decision UUID, source, record and evidence references, "
                + "lowercase SHA-256, permission reference and reason for this decision.");
    }

    private static boolean provided(String value,int maxLength) {
        return value!=null&&!value.isBlank()&&value.length()<=maxLength;
    }

    private static boolean namedVariant(String value) {
        return value!=null&&!GENERIC_VARIANTS.contains(value.strip().toLowerCase(Locale.ROOT));
    }

    private static void requireCompatible(MappingReview review) {
        if(!review.blockingReasons().isEmpty())
            throw conflict("Resolve the listing evidence before mapping. " + String.join(", ",review.blockingReasons()));
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT,message);
    }

    @Transactional public Map<String,Integer> sync(String location) {
        if (provider == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Retailer observation imports are disabled.");
        }
        var area=db.one("SELECT * FROM delivery_locations WHERE id=?",location);
        String pin=text(area,"postcode");
        int imported=0,unverified=0,failed=0;
        var mappings=db.rows("""
            SELECT pp.id AS listing_id,pp.platform_id,pp.retailer_sku,v.amount_base,v.base_unit,p.name,p.brand
            FROM provider_mappings m JOIN platform_products pp ON pp.id=m.platform_product_id
            JOIN product_variants v ON v.id=pp.variant_id JOIN products p ON p.id=v.product_id
            ORDER BY pp.id LIMIT 100
            """);
        for(var mapping:mappings) {
            String platform=text(mapping,"platform_id"), sku=text(mapping,"retailer_sku");
            try {
                for(var observation:provider.observations(platform,sku,pin)) {
                    if(!platform.equals(observation.retailer())||!sku.equals(observation.retailerSku())
                        ||!pin.equals(observation.pincode())||observation.observedAt()==null
                        ||observation.observedAt().isAfter(Instant.now().plusSeconds(60))
                        ||(observation.priceMinor()!=null&&observation.priceMinor()<0)
                        ||(observation.mrpMinor()!=null&&observation.mrpMinor()<0))
                        throw ApiException.bad("Observation does not match its listing, location or price contract.");
                    boolean verified=identityMatches(observation,text(mapping,"name"),text(mapping,"brand"),
                        decimal(mapping,"amount_base"),text(mapping,"base_unit"));
                    if(persist(text(mapping,"listing_id"),location,observation,verified)) {
                        imported++;
                        if(!verified)unverified++;
                    }
                }
            } catch(ApiException e) {failed++;}
        }
        return Map.of("imported",imported,"unverified",unverified,"failedMappings",failed);
    }
    static String normalize(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
    public static boolean identityMatches(GroceryDataProvider.Observation o,String name,String brand,BigDecimal amount,String unit){if(o.brand()==null||!normalize(o.brand()).equals(normalize(brand))||o.title()==null||!normalize(o.title()).contains(normalize(name))||o.packSizeRaw()==null)return false;var matcher=java.util.regex.Pattern.compile("(?i)^\\s*(\\d+(?:\\.\\d+)?)\\s*(kg|g|ml|l|litres?|liters?|pieces?|pcs)\\s*$").matcher(o.packSizeRaw());if(!matcher.matches())return false;BigDecimal size=new BigDecimal(matcher.group(1));String raw=matcher.group(2).toLowerCase(Locale.ROOT);String kind=raw.equals("kg")||raw.equals("g")?"g":raw.startsWith("piece")||raw.equals("pcs")?"piece":"ml";if(raw.equals("kg")||raw.equals("l")||raw.startsWith("lit"))size=size.multiply(BigDecimal.valueOf(1000));return kind.equals(unit)&&size.compareTo(amount)==0;}
    private boolean persist(String listing,String location,GroceryDataProvider.Observation o,boolean verified){
        var mapped=db.one("SELECT retailer_sku FROM platform_products WHERE id=? FOR UPDATE",listing);
        if(!o.retailerSku().equals(text(mapped,"retailer_sku")))
            throw new ApiException(org.springframework.http.HttpStatus.CONFLICT,
                "The listing changed while fetching observations. Retry the sync against the current SKU.");
        String status=o.inStock()==null?"no_data":o.inStock()?"available":"out_of_stock";
        String verification=verified?"VERIFIED":"UNVERIFIED";
        // V1 timestamps have whole-second precision on MySQL. Use the same key before writing.
        Instant observedAt=o.observedAt().truncatedTo(ChronoUnit.SECONDS);
        var prior=db.maybe("SELECT * FROM location_prices WHERE platform_product_id=? AND location_id=? FOR UPDATE",listing,location);
        if(prior.isPresent()&&time(prior.get(),"observed_at").equals(observedAt))return false;
        boolean latest=prior.isEmpty()||time(prior.get(),"observed_at").isBefore(observedAt);
        if(prior.isEmpty())
            db.insert("location_prices",fields("id",id(),"platform_product_id",listing,"location_id",location,"price_minor",o.priceMinor(),"mrp_minor",o.mrpMinor(),"availability",status,"observed_at",observedAt,"source",provider.name(),"verification",verification));
        else if(latest) {
            db.run("DELETE FROM online_comparison_evidence WHERE price_id=?",text(prior.get(),"id"));
            db.run("UPDATE location_prices SET price_minor=?,mrp_minor=?,availability=?,observed_at=?,source=?,verification=?,updated_at=? WHERE id=?",o.priceMinor(),o.mrpMinor(),status,observedAt,provider.name(),verification,Instant.now(),text(prior.get(),"id"));
        }
        if(latest&&verified&&o.sourceUrl()!=null&&o.sourceUrl().length()<=1000)
            db.run("UPDATE platform_products SET source_url=?,updated_at=? WHERE id=? AND retailer_sku=?",o.sourceUrl(),Instant.now(),listing,o.retailerSku());
        boolean history=o.priceMinor()!=null&&db.count("SELECT COUNT(*) FROM price_history WHERE platform_product_id=? AND location_id=? AND observed_at=?",listing,location,observedAt)==0;
        if(history)
            db.insert("price_history",fields("id",id(),"platform_product_id",listing,"location_id",location,"price_minor",o.priceMinor(),"observed_at",observedAt,"source",provider.name(),"verification",verification));
        return latest||history;
    }
}
