package com.smartcart.grocery;

import com.smartcart.exception.ApiException;
import com.smartcart.repository.Db;
import com.smartcart.service.CatalogService;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import static com.smartcart.repository.Db.*;

@Service
@Profile("!mysql-local")
public class PriceAcquisitionCoverage {
    public record ExactSeries(PricePrediction.Scope scope,long eligibleRows,int eligibleDays,LocalDate first,LocalDate last,
        int validationTargets,int testTargets,List<LocalDate> missingDates,String readiness) {}
    public record Overview(PricePrediction.Coverage coverage,long legacySimulated,long withoutTrainingApproval,
       List<Map<String,Object>> intakeCounts,List<Map<String,Object>> series,boolean sample) {}
    private final Db db;
    private final CatalogService catalogue;
    private final PricePrediction predictions;

    public PriceAcquisitionCoverage(Db db,CatalogService catalogue,PricePrediction predictions) {
        this.db=db;this.catalogue=catalogue;this.predictions=predictions;
    }

    public Overview overview() {
        var coverage=predictions.coverage();
        long simulated=db.count("SELECT COUNT(*) FROM price_history WHERE verification='SAMPLE'");
        var decisions=db.rows("SELECT disposition,COUNT(*) AS total FROM price_intake_decisions GROUP BY disposition ORDER BY disposition");
        var series=catalogue.sample()?List.<Map<String,Object>>of():db.rows("""
            SELECT o.variant_id,p.platform_id AS retailer,o.location_id,COUNT(*) AS retained_observations
            FROM approved_price_observations o JOIN price_retention_grants g ON g.id=o.grant_id
            JOIN platform_products p ON p.id=o.listing_id
            WHERE g.revoked_at IS NULL AND g.retain_until>?
            GROUP BY o.variant_id,p.platform_id,o.location_id ORDER BY retained_observations DESC,o.variant_id,retailer,o.location_id LIMIT 200
            """,Instant.now());
        return new Overview(coverage,simulated,coverage.observations()-coverage.trainingAllowed(),decisions,series,catalogue.sample());
    }

    public ExactSeries series(PricePrediction.Scope scope) {
        if(!Set.of("blinkit","zepto","instamart").contains(scope.retailer())) throw ApiException.bad("Choose a known retailer.");
        catalogue.product(scope.variant(),scope.location());
        Instant now=Instant.now();
        var rows=catalogue.sample()?List.<Map<String,Object>>of():predictions.points(scope,now);
        var days=rows.stream().map(row->{
            LocalDate observed=time(row,"observed_at").atOffset(ZoneOffset.UTC).toLocalDate();
            return observed.equals(time(row,"available_at").atOffset(ZoneOffset.UTC).toLocalDate())?observed:null;
        }).filter(Objects::nonNull).distinct().sorted().toList();
        int n=days.size(), validation=0, test=0;
        for(int i=Math.max(1,n*6/10);i<n;i++) if(days.get(i-1).plusDays(1).equals(days.get(i))) {
            if (i>=n*8/10) test++; else validation++;
        }
        var first=n==0?null:days.get(0);
        var last=n==0?null:days.get(n-1);
        var yesterday=now.atOffset(ZoneOffset.UTC).toLocalDate().minusDays(1);
        var known=new HashSet<>(days);
        List<LocalDate> missing=first==null?List.of():first.datesUntil(yesterday.plusDays(1)).filter(d->!known.contains(d)).toList();
        String readiness=n<60?"INSUFFICIENT_DAYS":validation<10||test<10?"INSUFFICIENT_CONSECUTIVE_TARGETS":
            !yesterday.equals(last)?"STALE":rows.stream().anyMatch(row->!bool(row,"serve_allowed"))?"TRAINING_ONLY":"READY_FOR_EXPERIMENT";
        return new ExactSeries(scope,rows.size(),n,first,last,validation,test,missing,readiness);
    }
}
