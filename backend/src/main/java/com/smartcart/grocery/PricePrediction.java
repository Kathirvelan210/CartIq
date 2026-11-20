package com.smartcart.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartcart.dto.Models.Product;
import com.smartcart.exception.ApiException;
import com.smartcart.repository.Db;
import com.smartcart.service.AuthService;
import com.smartcart.service.CartService;
import com.smartcart.service.CatalogService;
import jakarta.validation.constraints.*;
import java.net.http.HttpClient;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import static com.smartcart.repository.Db.*;

@Service
@Profile("!mysql-local")
public class PricePrediction {
    private static final String JOIN="""
        FROM approved_price_observations o JOIN price_retention_grants g ON g.id=o.grant_id
        JOIN platform_products pp ON pp.id=o.listing_id AND pp.retailer_sku=o.retailer_sku
        JOIN product_variants v ON v.id=o.variant_id
        JOIN products p ON p.id=v.product_id AND p.active=TRUE
        JOIN provider_mapping_decisions d ON d.decision_id=g.mapping_decision_id AND d.disposition='APPROVED'
            AND d.variant_id=o.variant_id AND d.platform_id=pp.platform_id AND d.retailer_sku=o.retailer_sku
            AND d.canonical_pack=v.pack_label AND d.canonical_title=p.name AND d.canonical_brand=p.brand
        LEFT JOIN price_forecast_permissions f ON f.grant_id=g.id
        LEFT JOIN price_source_authorizations a ON a.grant_id=g.id
        WHERE g.revoked_at IS NULL AND g.retain_until>? AND o.observed_at<=?
          AND NOT EXISTS (SELECT 1 FROM provider_mapping_decisions n WHERE n.variant_id=d.variant_id
                          AND n.platform_id=d.platform_id AND n.revision_no>d.revision_no)
        """;
    private static final String TRAIN=" AND f.revoked_at IS NULL AND f.expires_at>? AND g.dataset_allowed=TRUE"
        +" AND (a.grant_id IS NULL OR (a.revoked_at IS NULL AND a.training_allowed=TRUE))";
    private static final String SERIES=" AND o.variant_id=? AND pp.platform_id=? AND o.location_id=?";
    private static final String TARGET=" AND o.price_minor BETWEEN 1 AND 1000000000 AND o.stock_status='AVAILABLE'";
    private final Db db;
    private final CatalogService catalogue;
    private final CartService cart;
    private final String secret;
    private final boolean enabled;
    private final RestClient ai;
    private final Map<Scope,Model> models=new LinkedHashMap<>();

    public record Approval(@NotBlank @Size(max=200) String permissionReference,@NotBlank @Pattern(regexp="[a-f0-9]{64}") String evidenceSha256,
        @NotNull Instant expiresAt,@AssertTrue boolean genuineReviewed,boolean serveAllowed) {}
    public record Scope(String variant,String retailer,String location) {}
    public record Coverage(long observations,long products,long variants,long retailers,long locations,Instant first,Instant last,
        long observationDays,long missingPrice,long missingMrp,long unknownStock,long duplicates,long reviewedIdentity,
        long trainingAllowed,long inferenceAllowed,List<Map<String,Object>> groups,String limitation) {}
    public record Reading(Instant observedAt, Long priceMinor, String stock) {}
    public record Advice(String action,String reason) {}
    public record Prediction(String status,String productId,String variantId,String pack,String retailer,String locationId,Reading latestObservation,Long observedChangeMinor,
        String freshness,int horizonDays,LocalDate forecastDate,Long predictedMinor,Long lowMinor,Long highMinor,
        Double empiricalCoverage,String modelVersion,Instant trainedAt,String warning,Advice advice) {}
    public record Experiment(Scope scope,String productId,String category,int approvedRows,int lateRows,String evidenceVersion,JsonNode evaluation) {}
    private record Model(String version,Instant expires,Set<String> grants,JsonNode result) {}

    public PricePrediction(Db db,CatalogService catalogue,CartService cart,@Value("${smartcart.ai-url}") String url,
        @Value("${smartcart.ai-secret}") String secret,@Value("${CARTIQ_APPROVED_FORECAST_ENABLED:false}") boolean enabled) {
        this.db=db;this.catalogue=catalogue;this.cart=cart;this.secret=secret;this.enabled=enabled;
        var transport=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        transport.setReadTimeout(Duration.ofSeconds(9));
        ai=RestClient.builder().baseUrl(url).requestFactory(transport).build();
    }

    @Transactional
    public void approve(String reviewer,String grantId,Approval approval) {
        if (catalogue.sample()) throw ApiException.bad("Forecast permissions are unavailable in a sample catalogue.");
        var grant=db.one("SELECT * FROM price_retention_grants WHERE id=? FOR UPDATE",grantId);
        var source=db.maybe("SELECT training_allowed,serve_allowed FROM price_source_authorizations WHERE grant_id=?",grantId);
        if (source.isPresent()&&(!bool(source.get(),"training_allowed")||approval.serveAllowed()&&!bool(source.get(),"serve_allowed"))) {
            throw ApiException.bad("The registered source rights do not allow this forecast approval.");
        }
        Instant now=Instant.now().truncatedTo(ChronoUnit.SECONDS), expires=approval.expiresAt();
        if (grant.get("revoked_at")!=null || !bool(grant,"dataset_allowed") || !expires.isAfter(now)
            || !expires.equals(expires.truncatedTo(ChronoUnit.SECONDS)) || expires.isAfter(time(grant,"retain_until"))
            || approval.serveAllowed()&&!bool(grant,"history_allowed")) {
            throw ApiException.bad("The training and inference permission must be within the active retention and use grant.");
        }
        db.insert("price_forecast_permissions",fields("grant_id",grantId,"permission_reference",approval.permissionReference(),
            "evidence_sha256",approval.evidenceSha256(),"approved_by",reviewer,"approved_at",now,"expires_at",expires,
            "serve_allowed",approval.serveAllowed()));
    }

    public void revoke(String grantId) {
        db.run("UPDATE price_forecast_permissions SET revoked_at=? WHERE grant_id=? AND revoked_at IS NULL",Instant.now(),grantId);
        discardGrant(grantId);
    }

    @TransactionalEventListener
    void onRetentionRevoked(PriceObservations.RetentionRevoked event) { discardGrant(event.grantId()); }

    private void discardGrant(String id) { synchronized(models){models.values().removeIf(m->m.grants().contains(id));} }

    @Transactional(readOnly=true)
    public Coverage coverage() {
        if (catalogue.sample()) return new Coverage(0,0,0,0,0,null,null,0,0,0,0,0,0,0,0,List.of(),"Sample catalogue excluded.");
        Instant now=Instant.now();
        var summary=db.one("""
            SELECT COUNT(*) AS observations,COUNT(DISTINCT p.id) AS products,COUNT(DISTINCT o.variant_id) AS variants,
            COUNT(DISTINCT pp.platform_id) AS retailers,COUNT(DISTINCT o.location_id) AS locations,
            MIN(o.observed_at) AS first_at,MAX(o.observed_at) AS last_at,COUNT(DISTINCT CAST(o.observed_at AS DATE)) AS days,
            SUM(CASE WHEN o.price_minor IS NULL THEN 1 ELSE 0 END) AS missing_price,
            SUM(CASE WHEN o.mrp_minor IS NULL THEN 1 ELSE 0 END) AS missing_mrp,
            SUM(CASE WHEN o.stock_status='UNKNOWN' THEN 1 ELSE 0 END) AS unknown_stock,
            SUM(CASE WHEN f.revoked_at IS NULL AND f.expires_at>? AND g.dataset_allowed=TRUE
                AND (a.grant_id IS NULL OR (a.revoked_at IS NULL AND a.training_allowed=TRUE)) THEN 1 ELSE 0 END) AS train_count,
            SUM(CASE WHEN f.revoked_at IS NULL AND f.expires_at>? AND g.dataset_allowed=TRUE AND g.history_allowed=TRUE AND f.serve_allowed=TRUE
                AND (a.grant_id IS NULL OR (a.revoked_at IS NULL AND a.serve_allowed=TRUE)) THEN 1 ELSE 0 END) AS serve_count
            """+JOIN,now,now,now,now);
        long n=num(summary,"observations");
        var groups=db.rows("""
            SELECT p.category_id,pp.platform_id,o.location_id,COUNT(*) AS observations,COUNT(DISTINCT CAST(o.observed_at AS DATE)) AS observed_days
            """+JOIN+" GROUP BY p.category_id,pp.platform_id,o.location_id ORDER BY observations DESC,p.category_id,pp.platform_id,o.location_id LIMIT 201",now,now);
        // Unique constraints enforce both natural keys. Conflicting/replayed attempts cannot enter the store.
        return new Coverage(n,num(summary,"products"),num(summary,"variants"),num(summary,"retailers"),num(summary,"locations"),
            nullableTime(summary,"first_at"),nullableTime(summary,"last_at"),num(summary,"days"),num(summary,"missing_price"),num(summary,"missing_mrp"),
            num(summary,"unknown_stock"),0,n,num(summary,"train_count"),num(summary,"serve_count"),groups.stream().limit(200).toList(),
            groups.size()>200?"Top 200 coverage groups displayed. Scope totals are complete.":"Only retained observations with a current reviewed identity are counted.");
    }

    public Experiment train(Scope scope) {
        Product product=product(scope);
        if (!enabled||secret.isBlank()||catalogue.sample()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Approved forecast training is disabled.");
        Instant now=Instant.now();
        var rows=points(scope,now);
        String version=version(scope,rows);
        long days=rows.stream().map(r->time(r,"observed_at").atOffset(ZoneOffset.UTC).toLocalDate()).distinct().count();
        JsonNode result;
        if (days<60) {
            result=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("status","INSUFFICIENT_DATA")
                .put("warning","At least 60 eligible price days are required.");
        } else {
            try {
                result=ai.post().uri("/price-intelligence/experiment").header("X-Service-Token",secret)
                    .body(Map.of("datasetVersion",version,"points",rows.stream().map(r->Map.of("priceMinor",num(r,"price_minor"),
                        "observedAt",time(r,"observed_at").toString(),"recordedAt",time(r,"available_at").toString())).toList()))
                    .retrieve().body(JsonNode.class);
                if (result==null || !version.equals(result.path("datasetVersion").asText()) ||
                    "TRAINED".equals(result.path("status").asText()) && !validForecast(result,now)) throw new IllegalStateException();
            } catch (RestClientException | IllegalStateException | DateTimeException error) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,"The forecasting service did not return a matching evaluation.");
            }
        }
        if ("TRAINED".equals(result.path("status").asText()) && result.path("intervalEligible").asBoolean()
            && rows.stream().allMatch(r->bool(r,"serve_allowed"))) {
            Instant expiry=rows.stream().map(r->time(r,"permission_end")).min(Comparator.naturalOrder()).orElseThrow();
            synchronized(models) {
                if (models.size()>=128) models.remove(models.keySet().iterator().next());
                models.put(scope,new Model(version,expiry,rows.stream().map(r->text(r,"grant_id")).collect(Collectors.toUnmodifiableSet()),result));
            }
        } else synchronized(models){models.remove(scope);}
        int late=(int)rows.stream().filter(r->!time(r,"observed_at").atOffset(ZoneOffset.UTC).toLocalDate()
            .equals(time(r,"available_at").atOffset(ZoneOffset.UTC).toLocalDate())).count();
        return new Experiment(scope,product.canonicalProductId(),product.category(),rows.size(),late,version,result);
    }

    public Prediction prediction(String user,Scope scope) {
        var product=product(scope);
        Instant now=Instant.now();
        var latest=catalogue.sample()?List.<Map<String,Object>>of():db.rows("SELECT o.observed_at,o.price_minor,o.stock_status "+JOIN+SERIES+
            " AND g.history_allowed=TRUE ORDER BY o.observed_at DESC LIMIT 2",now,now,scope.variant(),scope.retailer(),scope.location());
        var observed=latest.isEmpty()?null:new Reading(time(latest.get(0),"observed_at"),number(latest.get(0),"price_minor"),text(latest.get(0),"stock_status"));
        Long previous=latest.size()<2?null:number(latest.get(1),"price_minor");
        Long change=observed==null||observed.priceMinor()==null||previous==null?null:observed.priceMinor()-previous;
        String freshness=observed==null?"UNKNOWN":observed.observedAt().isAfter(now.minusSeconds(900))?"FRESH":"STALE";
        Model model;
        synchronized(models){model=models.get(scope);}
        JsonNode forecast=null;
        String warning="A validated model and enough permission-approved observations are required for this pack, retailer and location.";
        if (model!=null && enabled && now.isBefore(model.expires())) {
            var rows=points(scope,now);
            if (model.version().equals(version(scope,rows)) && rows.stream().allMatch(r->bool(r,"serve_allowed"))
                && now.atOffset(ZoneOffset.UTC).toLocalDate().toString().equals(model.result().path("forecastDate").asText())) {
                forecast=model.result(); warning=forecast.path("warning").asText();
            }
        }
        var prefs=cart.list(user).stream().filter(c->scope.variant().equals(c.productId())&&scope.location().equals(c.locationId()))
            .map(c->c.preferences()).findFirst().orElse(null);
        Long low=forecast==null?null:forecast.path("lowMinor").longValue(), high=forecast==null?null:forecast.path("highMinor").longValue();
        return new Prediction(forecast==null?"INSUFFICIENT_DATA":"FORECAST",product.canonicalProductId(),scope.variant(),product.quantity(),scope.retailer(),scope.location(),
            observed,change,freshness,1,forecast==null?null:LocalDate.parse(forecast.path("forecastDate").asText()),forecast==null?null:forecast.path("estimateMinor").longValue(),
            low,high,forecast==null?null:forecast.path("empiricalTestCoverage").doubleValue(),forecast==null?null:forecast.path("modelVersion").asText(),
            forecast==null?null:Instant.parse(forecast.path("trainedAt").asText()),warning,advice(observed,freshness,low,high,change,prefs));
    }

    /** Reads a previously approved forecast. This path never trains or calls the model service. */
    public Optional<Prediction> approvedTiming(String user,Scope scope) {
        if(!enabled||catalogue.sample())return Optional.empty();
        synchronized(models){if(!models.containsKey(scope))return Optional.empty();}
        var result=prediction(user,scope);
        return "FORECAST".equals(result.status())?Optional.of(result):Optional.empty();
    }

    static Advice advice(Reading current,String freshness,Long low,Long high,Long change,com.smartcart.dto.Models.Preferences prefs) {
        if (current==null || current.priceMinor()==null || !"FRESH".equals(freshness) || !"AVAILABLE".equals(current.stock())) {
            return new Advice("CHECK_AVAILABILITY","A fresh, available price is required before comparing purchase timing.");
        }
        if (prefs!=null && prefs.maxPriceMinor()!=null && current.priceMinor()>prefs.maxPriceMinor()) {
            return new Advice("COMPARE_OPTIONS","The observed price exceeds your saved maximum. Compare alternatives.");
        }
        if (prefs!=null && prefs.targetPriceMinor()!=null && current.priceMinor()<=prefs.targetPriceMinor()) {
            return new Advice("BUY_NOW","The available price meets your saved price target.");
        }
        if (low!=null && current.priceMinor()<=low) return new Advice("BUY_NOW","The available price is at or below the forecast range.");
        if (high!=null && prefs!=null && "NON_ESSENTIAL".equals(prefs.intent()) && high<current.priceMinor()*.95) {
            return new Advice("CONSIDER_WAITING","The whole validated forecast range is lower and this purchase is marked non-essential.");
        }
        String trend=change==null||change==0?"":change>0?"The latest price rose. ":"The latest price fell. ";
        return new Advice("NO_TIMING_SIGNAL",trend+"There is no clear timing advantage. Buy essentials when needed.");
    }

    @Scheduled(fixedDelay=60000)
    void expire() { synchronized(models){models.values().removeIf(m->!m.expires().isAfter(Instant.now()));} }

    private Product product(Scope scope) {
        if (!Set.of("blinkit","zepto","instamart").contains(scope.retailer())) throw ApiException.bad("Choose a known retailer.");
        return catalogue.product(scope.variant(),scope.location());
    }
    List<Map<String,Object>> points(Scope scope,Instant now) {
        var rows=db.rows("""
            SELECT o.id,o.grant_id,o.price_minor,o.observed_at,COALESCE(o.source_available_at,o.recorded_at) AS available_at,
            o.source_as_of_evidence,f.serve_allowed,f.expires_at AS permission_end,g.retain_until,
            f.evidence_sha256,g.evidence_sha256 AS retention_hash
            """+JOIN+TRAIN+SERIES+TARGET+" AND o.observed_at>=? AND o.observed_at<? ORDER BY o.observed_at,o.id LIMIT 5001",now,now,now,
            scope.variant(),scope.retailer(),scope.location(),now.minus(180,ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS),now.truncatedTo(ChronoUnit.DAYS));
        if (rows.size()>5000) throw ApiException.bad("The scoped series exceeds the experiment limit.");
        return rows;
    }
    private static String version(Scope scope,List<Map<String,Object>> rows) {
        return AuthService.hash("forecast-v1|"+scope+"|"+ rows.stream().map(r->text(r,"id")+":"+time(r,"available_at")+":"+text(r,"evidence_sha256")+":"+
            text(r,"retention_hash")).collect(Collectors.joining(",")));
    }
    private static boolean validForecast(JsonNode result,Instant now) {
        var low=result.path("lowMinor"); var point=result.path("estimateMinor"); var high=result.path("highMinor");
        var coverage=result.path("empiricalTestCoverage");
        return result.path("intervalEligible").asBoolean() && low.isIntegralNumber() && point.isIntegralNumber() && high.isIntegralNumber()
            && low.longValue()>0 && low.longValue()<=point.longValue() && point.longValue()<=high.longValue() && high.longValue()<=2_000_000_000L
            && coverage.isNumber() && coverage.doubleValue()>=.8 && coverage.doubleValue()<=1
            && result.path("modelVersion").asText().matches("rolling-price-v1-(?:naive|moving-7|exponential-0\\.3)")
            && LocalDate.parse(result.path("forecastDate").asText()).equals(now.atOffset(ZoneOffset.UTC).toLocalDate())
            && !Instant.parse(result.path("trainedAt").asText()).isAfter(now.plusSeconds(30));
    }
    private static Instant nullableTime(Map<String,Object> row,String field) { return row.get(field)==null?null:time(row,field); }
}
