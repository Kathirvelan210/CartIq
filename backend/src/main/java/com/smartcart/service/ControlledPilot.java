package com.smartcart.service;

import com.smartcart.dto.Models.Product;
import com.smartcart.exception.ApiException;
import com.smartcart.integration.QuickCommerceProvider;
import com.smartcart.repository.Db;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.smartcart.repository.Db.*;

@Service @Profile("local")
public class ControlledPilot {
    public enum Group { MILK,RICE,DAL,COOKING_OIL,SNACKS }
    public enum Operation { SEARCH,ITEM }
    public record Pack(@NotBlank @Size(max=60) String variant,@NotNull UUID mappingDecision,@NotNull Group group) {}
    public record Setup(@NotBlank @Size(max=60) String location,@NotBlank @Pattern(regexp="blinkit|zepto|instamart") String retailer,
        @NotNull Instant startsAt,@NotNull Instant endsAt,@Min(1) @Max(12) int maxAttempts,@Min(1) @Max(12) int maxCredits,
        @NotBlank @Size(max=200) String permissionReference,@AssertTrue boolean displayAndTemporaryJoinApproved,
        @AssertTrue boolean operationalMetadataApproved,@NotNull @Size(min=1,max=10) List<@NotNull @Valid Pack> packs) {}
    public record Authorization(@NotBlank @Size(max=36) String accountId,@NotBlank @Size(max=60) String variant,
        @NotNull Operation operation,@NotNull Instant expiresAt,@NotBlank @Size(max=200) String authorizationReference,
        @AssertTrue boolean separatelyAuthorized) {}
    public record Ticket(String id,String retailer,Operation operation,Instant expiresAt,String nativeId) {}
    public record DiscoveryTicket(String id,String retailer,String query,String variant,Instant expiresAt) {}
    public record Claim(String id,String mappingDecision,Instant pilotEndsAt) {}
    private final Db db;
    private final CatalogService catalogue;
    private final LiveOffers offers;
    private final boolean enabled;
    private final String permission;

    public ControlledPilot(Db db,CatalogService catalogue,LiveOffers offers,@Value("${QUICKCOMMERCE_PILOT_ENABLED:false}") boolean enabled,
                           @Value("${QUICKCOMMERCE_OFFER_PERMISSION_REFERENCE:}") String permission){
        this.db=db;this.catalogue=catalogue;this.offers=offers;this.enabled=enabled;this.permission=permission;
    }
    public boolean enabled(){return enabled;}
    public List<Map<String,Object>> candidates(String area,String retailer){
        catalogue.requireLocation(area);
        if(!QuickCommerceProvider.RETAILERS.contains(retailer))throw ApiException.bad("Choose a known retailer.");
        if(!offers.configured(area))return List.of();
        return db.rows("""
            SELECT d.decision_id,d.variant_id,d.retailer_sku,p.name,p.brand,v.pack_label,c.name AS category
            FROM provider_mapping_decisions d
            JOIN platform_products pp ON pp.variant_id=d.variant_id AND pp.platform_id=d.platform_id AND pp.retailer_sku=d.retailer_sku
            JOIN provider_mappings m ON m.platform_product_id=pp.id
            JOIN product_variants v ON v.id=d.variant_id
            JOIN products p ON p.id=v.product_id AND p.active=TRUE
            JOIN product_categories c ON c.id=p.category_id
            WHERE d.platform_id=? AND d.disposition='APPROVED' AND d.source_provider='QUICKCOMMERCEAPI' AND d.permission_reference=?
              AND NOT EXISTS(SELECT 1 FROM provider_mapping_decisions n WHERE n.variant_id=d.variant_id
                AND n.platform_id=d.platform_id AND n.revision_no>d.revision_no)
            ORDER BY c.name,p.name,v.pack_label LIMIT 150
            """,retailer,permission).stream().filter(row->offers.approvedMapping(catalogue.product(text(row,"variant_id"),area),retailer,
                text(row,"decision_id"),text(row,"retailer_sku"))).toList();
    }
    public Map<String,Object> overview(){
        return fields("dispatchEnabled",enabled,"permissionConfigured",!permission.isBlank(),
            "pilots",db.rows("SELECT * FROM retailer_pilots ORDER BY created_at DESC LIMIT 50"));
    }
    public Map<String,Object> report(String id){
        return fields("pilot",db.one("SELECT * FROM retailer_pilots WHERE id=?",id),
            "packs",db.rows("""
                SELECT pv.*,p.name,p.brand,v.pack_label FROM retailer_pilot_variants pv JOIN product_variants v ON v.id=pv.variant_id
                JOIN products p ON p.id=v.product_id WHERE pv.pilot_id=? ORDER BY pv.grocery_group,p.name,v.pack_label
                """,id),
            "attempts",db.rows("""
                SELECT id,variant_id,authorization_reference,operation,discovery_query,expires_at,authorized_at,claimed_at,completed_at,result_code,
                billing,provider_request_id,upstream_status,confirmed_debit,credits_remaining,candidate_count,verified_count
                FROM retailer_pilot_attempts WHERE pilot_id=? ORDER BY authorized_at DESC LIMIT 100
                """,id));
    }

    @Transactional public String create(String admin,Setup setup){
        if(!offers.configured(setup.location())||!permission.equals(setup.permissionReference()))
            throw ApiException.bad("Configure the reviewed permission reference and approved catalogue location first.");
        Instant now=Instant.now();
        if(!setup.startsAt().isBefore(setup.endsAt())||!setup.endsAt().isAfter(now)
            ||Duration.between(setup.startsAt(),setup.endsAt()).compareTo(Duration.ofDays(30))>0||setup.maxCredits()>setup.maxAttempts())
            throw ApiException.bad("Choose a future end, a window of up to 30 days and a credit limit no greater than attempts.");
        if(setup.packs().stream().map(Pack::variant).distinct().count()!=setup.packs().size())
            throw ApiException.bad("Each exact pack can appear only once.");
        String id=id();
        db.insert("retailer_pilots",fields("id",id,"location_id",setup.location(),"retailer",setup.retailer(),"starts_at",setup.startsAt(),
            "ends_at",setup.endsAt(),"enabled",false,"attempt_limit",setup.maxAttempts(),"credit_limit",setup.maxCredits(),
            "permission_reference",setup.permissionReference(),"created_by",admin,"created_at",now));
        for(var selected:setup.packs()){
            var product=catalogue.product(selected.variant(),setup.location());
            requireMapping(product,setup.retailer(),selected.mappingDecision().toString());
            db.insert("retailer_pilot_variants",fields("pilot_id",id,"variant_id",selected.variant(),"mapping_decision_id",
                selected.mappingDecision().toString(),"grocery_group",selected.group().name()));
        }
        return id;
    }
    @Transactional public void enable(String pilot,boolean on){
        var row=lock(pilot);
        if(on&&(!enabled||!scope(row)||!time(row,"ends_at").isAfter(Instant.now())))
            throw ApiException.bad("Local dispatch and current permission scope are required before enabling this pilot.");
        db.run("UPDATE retailer_pilots SET enabled=? WHERE id=?",on,pilot);
    }
    @Transactional public String authorize(String admin,String pilot,Authorization request){
        return authorize(admin,pilot,request,null);
    }
    @Transactional public String authorizeDiscovery(String admin,String pilot,Authorization request,String query,boolean discoveryDisplayApproved){
        if(!discoveryDisplayApproved||request.operation()!=Operation.SEARCH)
            throw ApiException.bad("Confirm written private display rights for all listings returned by this search.");
        return authorize(admin,pilot,request,discoveryQuery(query));
    }
    private String authorize(String admin,String pilot,Authorization request,String query){
        var row=lock(pilot);
        var now=Instant.now();
        requireWindow(row,now);
        requireBudget(row);
        db.one("SELECT id FROM users WHERE id=?",request.accountId());
        if(!request.expiresAt().isAfter(now)||request.expiresAt().isAfter(now.plusSeconds(3600))||request.expiresAt().isAfter(time(row,"ends_at")))
            throw ApiException.bad("Set a one-use authorization expiry within the active pilot and the next hour.");
        requireMapping(catalogue.product(request.variant(),text(row,"location_id")),text(row,"retailer"),decision(pilot,request.variant()));
        if(db.count("SELECT COUNT(*) FROM retailer_pilot_attempts WHERE pilot_id=? AND authorization_reference=?",pilot,request.authorizationReference())>0)
            throw new ApiException(HttpStatus.CONFLICT,"This explicit authorization reference was already recorded.");
        if(db.count("SELECT COUNT(*) FROM retailer_pilot_attempts WHERE pilot_id=?",pilot)>=num(row,"attempt_limit"))
            throw new ApiException(HttpStatus.CONFLICT,"The pilot's authorization limit has been reached.");
        String id=id();
        db.insert("retailer_pilot_attempts",fields("id",id,"pilot_id",pilot,"variant_id",request.variant(),"account_id",request.accountId(),
            "authorized_by",admin,"authorization_reference",request.authorizationReference(),"operation",request.operation().name(),
            "expires_at",request.expiresAt(),"authorized_at",now,"discovery_query",query));
        return id;
    }
    public List<Ticket> tickets(String account,String area,String variant) {
        if(!enabled||!offers.configured(area))return List.of();
        Instant now=Instant.now();
        return db.rows("""
            SELECT a.id,p.retailer,a.operation,a.expires_at,d.retailer_sku FROM retailer_pilot_attempts a JOIN retailer_pilots p ON p.id=a.pilot_id
            JOIN retailer_pilot_variants v ON v.pilot_id=a.pilot_id AND v.variant_id=a.variant_id
            JOIN provider_mapping_decisions d ON d.decision_id=v.mapping_decision_id AND d.disposition='APPROVED'
            WHERE a.account_id=? AND a.variant_id=? AND p.location_id=? AND p.permission_reference=?
              AND a.discovery_query IS NULL AND a.claimed_at IS NULL AND a.expires_at>? AND p.enabled=TRUE AND p.starts_at<=? AND p.ends_at>?
            ORDER BY a.expires_at,a.id LIMIT 12
            """,account,variant,area,permission,now,now,now).stream().map(row->new Ticket(text(row,"id"),text(row,"retailer"),
                Operation.valueOf(text(row,"operation")),time(row,"expires_at"),text(row,"retailer_sku"))).toList();
    }
    @Transactional public Claim claim(String ticket,String account,String area,Product pack,String retailer,Operation operation,String requestedSku){
        return claim(ticket,account,area,pack,retailer,operation,requestedSku,null);
    }
    @Transactional public Claim claimDiscovery(String ticket,String account,String area,Product pack,String retailer,String query){
        return claim(ticket,account,area,pack,retailer,Operation.SEARCH,null,discoveryQuery(query));
    }
    public List<DiscoveryTicket> discoveryTickets(String account,String area,String query){
        if(!enabled||!offers.configured(area))return List.of();
        var now=Instant.now();
        return db.rows("""
            SELECT a.id,p.retailer,a.discovery_query,a.variant_id,a.expires_at
            FROM retailer_pilot_attempts a JOIN retailer_pilots p ON p.id=a.pilot_id
            WHERE a.account_id=? AND p.location_id=? AND p.permission_reference=? AND a.discovery_query=?
              AND a.claimed_at IS NULL AND a.expires_at>? AND p.enabled=TRUE AND p.starts_at<=? AND p.ends_at>?
            ORDER BY a.expires_at,a.id LIMIT 12
            """,account,area,permission,discoveryQuery(query),now,now,now).stream()
            .map(row->new DiscoveryTicket(text(row,"id"),text(row,"retailer"),text(row,"discovery_query"),text(row,"variant_id"),
                time(row,"expires_at"))).toList();
    }
    public boolean discoveryDisplayActive(String ticket){
        return enabled&&offers.discoveryDisplayActive(ticket);
    }
    public static String discoveryQuery(String query){
        if(query==null)throw ApiException.bad("Enter a marketplace search term.");
        String normalized=java.text.Normalizer.normalize(query,java.text.Normalizer.Form.NFKC)
            .strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
        if(normalized.isBlank()||normalized.length()>150||normalized.chars().anyMatch(Character::isISOControl))
            throw ApiException.bad("Enter a marketplace search term of up to 150 characters.");
        return normalized;
    }
    private Claim claim(String ticket,String account,String area,Product pack,String retailer,Operation operation,String requestedSku,String query){
        if(!enabled||ticket==null)throw denied("A one-use pilot authorization is required for this refresh.");
        var found=db.maybe("SELECT pilot_id FROM retailer_pilot_attempts WHERE id=? AND account_id=?",ticket,account)
            .orElseThrow(()->denied("No matching pilot authorization was found."));
        String pilot=text(found,"pilot_id");
        var lock=lock(pilot);
        Instant now=Instant.now();
        requireWindow(lock,now);
        var row=db.one("SELECT * FROM retailer_pilot_attempts WHERE id=? FOR UPDATE",ticket);
        if(!area.equals(text(lock,"location_id"))||!retailer.equals(text(lock,"retailer"))||!pack.id().equals(text(row,"variant_id"))
            ||!operation.name().equals(text(row,"operation"))||!Objects.equals(query,row.get("discovery_query"))
            ||row.get("claimed_at")!=null||!time(row,"expires_at").isAfter(now))
            throw denied("This authorization is spent, expired or outside the approved scope.");
        requireBudget(lock);
        String mappingDecision=decision(pilot,pack.id());
        String sku=requireMapping(pack,retailer,mappingDecision);
        if(requestedSku!=null&&!requestedSku.equals(sku))throw denied("The item does not match the approved listing.");
        db.run("UPDATE retailer_pilot_attempts SET claimed_at=?,discovery_claimed=? WHERE id=?",now,query!=null,ticket);
        return new Claim(ticket,mappingDecision,time(lock,"ends_at"));
    }
    @Transactional public void finish(Claim claim,String result,String billing,String providerId,Integer upstreamStatus,Integer debit,
                                     Integer balance,Integer candidates,Integer verified){
        db.run("""
            UPDATE retailer_pilot_attempts SET completed_at=?,result_code=?,billing=?,provider_request_id=?,upstream_status=?,
            confirmed_debit=?,credits_remaining=?,candidate_count=?,verified_count=?
            WHERE id=? AND claimed_at IS NOT NULL AND completed_at IS NULL
            """,Instant.now(),result,billing,providerId,upstreamStatus,debit,balance,candidates,verified,claim.id());
    }
    private Map<String,Object> lock(String pilot){return db.one("SELECT * FROM retailer_pilots WHERE id=? FOR UPDATE",pilot);}
    private boolean scope(Map<String,Object> row){return permission.equals(text(row,"permission_reference"))&&offers.configured(text(row,"location_id"));}
    private void requireWindow(Map<String,Object> row,Instant now){
        if(!enabled||!scope(row)||!bool(row,"enabled")||time(row,"starts_at").isAfter(now)||!time(row,"ends_at").isAfter(now))
            throw denied("The retailer pilot is disabled, outside its approved window or missing permission.");
    }
    private void requireBudget(Map<String,Object> row){
        String pilot=text(row,"id");
        if(db.count("SELECT COUNT(*) FROM retailer_pilot_attempts WHERE pilot_id=? AND claimed_at IS NOT NULL AND confirmed_debit IS NULL",pilot)>0)
            throw new ApiException(HttpStatus.CONFLICT,"An earlier pilot request has unresolved billing.");
        if(db.count("SELECT COUNT(*) FROM retailer_pilot_attempts WHERE pilot_id=? AND claimed_at IS NOT NULL",pilot)>=num(row,"attempt_limit")
            ||db.count("SELECT COALESCE(SUM(confirmed_debit),0) FROM retailer_pilot_attempts WHERE pilot_id=?",pilot)>=num(row,"credit_limit"))
            throw new ApiException(HttpStatus.CONFLICT,"The approved pilot limit has been reached.");
    }
    private String decision(String pilot,String variant){
        return text(db.maybe("SELECT mapping_decision_id FROM retailer_pilot_variants WHERE pilot_id=? AND variant_id=?",pilot,variant)
            .orElseThrow(()->denied("This exact pack is outside the pilot.")),"mapping_decision_id");
    }
    private String requireMapping(Product product,String retailer,String decision){
        String sku=text(db.one("SELECT retailer_sku FROM provider_mapping_decisions WHERE decision_id=?",decision),"retailer_sku");
        if(!offers.approvedMapping(product,retailer,decision,sku))throw denied("The exact-pack mapping is missing, changed or outside the permission scope.");
        return sku;
    }
    private static ApiException denied(String message){return new ApiException(HttpStatus.FORBIDDEN,message);}
}
