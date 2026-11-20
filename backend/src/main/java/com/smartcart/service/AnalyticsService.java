package com.smartcart.service;

import com.smartcart.dto.Models.*;
import com.smartcart.repository.Db;
import com.smartcart.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import static com.smartcart.repository.Db.*;

@Service
public class AnalyticsService {
    private final Db db;private final CatalogService catalog;private final BudgetService budgets;private final WatchService watches;private final NotificationService notices;private final RestClient ai;private final String aiSecret;private final boolean forecastEnabled;
    public AnalyticsService(Db db,CatalogService catalog,BudgetService budgets,WatchService watches,NotificationService notices,@Value("${smartcart.ai-url}") String url,@Value("${smartcart.ai-secret}") String secret,@Value("${smartcart.price-forecast-enabled:false}") boolean forecastEnabled){this.db=db;this.catalog=catalog;this.budgets=budgets;this.watches=watches;this.notices=notices;this.aiSecret=secret;this.forecastEnabled=forecastEnabled;var f=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(600)).build());f.setReadTimeout(Duration.ofSeconds(3));this.ai=RestClient.builder().baseUrl(url).requestFactory(f).build();}

    public record PriceIncrease(String name,String pack,String platform,long before,long after,Instant previousAt,Instant observedAt) {}

    public List<PriceIncrease> recentIncreases(String location) {
        catalog.requireLocation(location);
        return db.rows("""
            WITH ranked AS (
                SELECT p.name,v.pack_label,pp.platform_id,h.platform_product_id,h.price_minor,h.observed_at,
                    ROW_NUMBER() OVER (PARTITION BY h.platform_product_id ORDER BY h.observed_at DESC) AS observation_rank
                FROM price_history h JOIN platform_products pp ON pp.id=h.platform_product_id
                JOIN product_variants v ON v.id=pp.variant_id JOIN products p ON p.id=v.product_id
                WHERE h.location_id=? AND h.verification=? AND p.active=TRUE AND h.observed_at<=?
            )
            SELECT current_reading.*,previous_reading.price_minor AS previous_price,
                previous_reading.observed_at AS previous_at
            FROM ranked current_reading JOIN ranked previous_reading
                ON current_reading.platform_product_id=previous_reading.platform_product_id
            WHERE current_reading.observation_rank=1 AND previous_reading.observation_rank=2
                AND current_reading.price_minor>previous_reading.price_minor
            ORDER BY current_reading.observed_at DESC,current_reading.platform_product_id LIMIT 5
            """,location,catalog.sample()?"SAMPLE":"VERIFIED",Instant.now()).stream()
            .map(r->new PriceIncrease(text(r,"name"),text(r,"pack_label"),text(r,"platform_id"),
                num(r,"previous_price"),num(r,"price_minor"),time(r,"previous_at"),time(r,"observed_at"))).toList();
    }

    public PriceAnalytics prices(String product,String location){
        catalog.product(product,location);
        var rows=db.rows("SELECT h.*,pp.platform_id FROM price_history h JOIN platform_products pp ON pp.id=h.platform_product_id WHERE pp.variant_id=? AND h.location_id=? AND h.verification=? AND h.observed_at<=? ORDER BY h.observed_at DESC LIMIT 540",
            product,location,catalog.sample()?"SAMPLE":"VERIFIED",Instant.now());
        List<Point> points=rows.stream()
            .map(r->new Point(time(r,"observed_at"),text(r,"platform_id"),num(r,"price_minor")))
            .sorted(Comparator.comparing(Point::observedAt)).toList();
        Long min=points.stream().map(Point::priceMinor).min(Long::compare).orElse(null);
        Long max=points.stream().map(Point::priceMinor).max(Long::compare).orElse(null);
        Long average=points.isEmpty()?null:Math.round(points.stream().mapToLong(Point::priceMinor).average().orElse(0));
        Forecast forecast=new Forecast(null,null,null,0,"Forecasts disabled","Historical observations are available when recorded.");
        if(forecastEnabled&&catalog.sample())forecast=new Forecast(null,null,null,0,"Insufficient comparable history","A forecast requires distinct observation days for one listing.");
        boolean oneListing=rows.stream().map(row->text(row,"platform_product_id")).distinct().count()==1;
        long distinctDays=points.stream().map(point->point.observedAt().atOffset(ZoneOffset.UTC).toLocalDate()).distinct().count();
        if(forecastEnabled&&catalog.sample()&&oneListing&&distinctDays>=8){
            forecast=new Forecast(null,null,null,0,"Model service unavailable","Historical observations are available.");
            try{
                Forecast result=ai.post().uri("/forecast").header("X-Service-Token",aiSecret)
                    .body(Map.of("productId",product,"platform",points.get(0).platform(),"locationId",location,"sample",catalog.sample(),
                        "points",points.stream().map(p->Map.of("observedAt",p.observedAt().toString(),"priceMinor",p.priceMinor())).toList()))
                    .retrieve().body(Forecast.class);
                if(result!=null)forecast=result;
            }catch(Exception ignored){
                // Price history is still available when the model cannot return an estimate.
            }
        }
        return new PriceAnalytics(product,points,min,max,average,forecast,catalog.sample());
    }
    public List<Recommendation> recommendations(String user,String location){var counts=db.rows("SELECT i.variant_id,SUM(i.quantity) AS purchases FROM shopping_history_items i JOIN shopping_history h ON h.id=i.history_id WHERE h.user_id=? GROUP BY i.variant_id",user);Map<String,Long> freq=new HashMap<>();for(var c:counts)freq.put(text(c,"variant_id"),num(c,"purchases"));String brands=notices.settings(user).preferredBrands().toLowerCase(Locale.ROOT);return catalog.products(location,"",null,100,0).stream().filter(p->catalog.best(p).isPresent()).sorted(Comparator.<Product>comparingLong(p->freq.getOrDefault(p.id(),0L)+(Arrays.stream(brands.split(",")).map(String::trim).anyMatch(b->!b.isEmpty()&&b.equals(p.brand().toLowerCase(Locale.ROOT)))?10:0)).reversed()).limit(6).map(p->new Recommendation(p,freq.containsKey(p.id())?"Frequently recorded in your shopping history":Arrays.stream(brands.split(",")).map(String::trim).anyMatch(b->!b.isEmpty()&&b.equals(p.brand().toLowerCase(Locale.ROOT)))?"Matches a preferred brand":"Available catalogue suggestion; more history will improve personalization")).toList();}
    public Overview overview(String user,String location){return new Overview(budgets.budget(user,BudgetService.month()),budgets.history(user).stream().limit(5).toList(),recommendations(user,location),budgets.trend(user),watches.list(user).size(),(int)notices.list(user).stream().filter(n->!n.read()).count(),(int)budgets.replenishment(user).stream().filter(Replenishment::due).count());}
}
