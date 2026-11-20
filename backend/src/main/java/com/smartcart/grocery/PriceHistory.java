package com.smartcart.grocery;

import com.smartcart.exception.ApiException;
import com.smartcart.repository.Db;
import com.smartcart.service.CatalogService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.smartcart.repository.Db.*;

@Service
public class PriceHistory {
    private static final int LIMIT = 5000;
    private static final Duration FRESH_FOR = Duration.ofHours(24);
    private final Db db;
    private final CatalogService catalogue;

    public PriceHistory(Db db, CatalogService catalogue) { this.db=db; this.catalogue=catalogue; }

    public record Reading(String id, String variantId, String locationId, String listingId, String retailer,
        String sku, Instant observedAt, Instant recordedAt, String provider, String permissionId, Instant retainUntil,
        String sourceRecordId, Long priceMinor, Long mrpMinor, BigDecimal discountPercent, String stock, String pack,
        int packCount, BigDecimal eachAmount, String unit) {}
    public record Stats(int observations, int priced, Long minimumMinor, Long maximumMinor, Long averageMinor) {}
    public record Day(LocalDate date, Stats stats) {}
    public record HistoryReading(Instant observedAt,String provider,Long priceMinor,Long mrpMinor,BigDecimal discountPercent,String stock) {}
    public record Series(String retailer, String listingId, String sku, Stats stats, List<Day> days, List<HistoryReading> readings,
        Instant lastObservedAt, String freshness, Long priceChangeMinor) {}
    public record History(String variantId, String locationId, Instant from, Instant until, Stats stats, List<Series> retailers) {}
    public record Feature(String split, Reading observation, Long previousPriceMinor, Long secondsSincePreviousPrice,
        BigDecimal pricePerBaseUnitMinor) {}
    private static final class Chronology {
        final PriorityQueue<Reading> pending=new PriorityQueue<>(Comparator.comparing(Reading::recordedAt));
        Reading latest;

        Reading prior(Reading row) {
            while (!pending.isEmpty() && !pending.peek().recordedAt().isAfter(row.observedAt())) {
                var eligible=pending.remove();
                if (latest==null || eligible.observedAt().isAfter(latest.observedAt())) latest=eligible;
            }
            if (row.priceMinor()!=null) pending.add(row);
            return latest;
        }
    }
    public record Dataset(String schemaVersion, String version, Instant generatedAt, Instant from, Instant validationStart,
        Instant testStart, Instant until, Map<String,Long> splitCounts, List<Feature> rows) {}

    @Transactional(readOnly=true)
    public History history(String variant, String location, Instant from, Instant until) {
        Instant now=Instant.now();
        checkRange(from,until,now);
        catalogue.product(variant,location);
        List<Reading> rows=read(variant,location,from,until,now,false);
        var series=rows.stream().collect(Collectors.groupingBy(Reading::listingId,TreeMap::new,Collectors.toList()));
        return new History(variant,location,from,until,stats(rows),series.values().stream().map(group->series(group,now)).toList());
    }

    @Transactional(readOnly=true)
    public Dataset dataset(String variant, String location, Instant from, Instant validation, Instant test, Instant until) {
        Instant now=Instant.now();
        checkRange(from,until,now);
        if (!from.isBefore(validation) || !validation.isBefore(test) || !test.isBefore(until)) {
            throw ApiException.bad("Choose strictly ordered training, validation and test windows.");
        }
        catalogue.product(variant,location);
        List<Reading> rows=read(variant,location,from,until,now,true);
        var chronology=new HashMap<String,Chronology>();
        var counts=new LinkedHashMap<String,Long>(Map.of("TRAIN",0L,"VALIDATION",0L,"TEST",0L));
        var features=new ArrayList<Feature>();
        for (Reading row:rows) {
            String split=row.observedAt().isBefore(validation)?"TRAIN":row.observedAt().isBefore(test)?"VALIDATION":"TEST";
            // Walk forward within one listing/location. Evidence recorded after the row cannot become its feature.
            var prior=chronology.computeIfAbsent(row.listingId(), key->new Chronology()).prior(row);
            Long previous=prior==null ? null : prior.priceMinor();
            var unitPrice=row.priceMinor()==null ? null : BigDecimal.valueOf(row.priceMinor())
                .divide(row.eachAmount().multiply(BigDecimal.valueOf(row.packCount())),6,RoundingMode.HALF_UP);
            features.add(new Feature(split,row,previous,previous==null?null:Duration.between(prior.observedAt(),row.observedAt()).toSeconds(),unitPrice));
            counts.merge(split,1L,Long::sum);
        }
        String manifest="price-v1|"+variant+"|"+location+"|"+from+"|"+validation+"|"+test+"|"+until+"|"
            +rows.stream().map(Reading::id).collect(Collectors.joining(","));
        return new Dataset("price-v1",com.smartcart.service.AuthService.hash(manifest),now,from,validation,test,until,counts,List.copyOf(features));
    }

    private List<Reading> read(String variant,String location,Instant from,Instant until,Instant now,boolean dataset) {
        // Only the permission bit is chosen by code. All request values are bound parameters.
        String allowed=dataset?"g.dataset_allowed":"g.history_allowed";
        var rows=db.rows("""
            SELECT o.*,p.platform_id,g.provider,g.retain_until
            FROM approved_price_observations o JOIN price_retention_grants g ON g.id=o.grant_id
            JOIN platform_products p ON p.id=o.listing_id
            WHERE o.variant_id=? AND o.location_id=? AND o.observed_at>=? AND o.observed_at<?
              AND g.revoked_at IS NULL AND g.retain_until>? AND
            """+allowed+"=TRUE ORDER BY o.observed_at,o.listing_id,o.id LIMIT 5001",variant,location,from,until,now);
        if (rows.size()>LIMIT) throw ApiException.bad("Narrow the observation window to at most 5000 readings.");
        return rows.stream().map(r->new Reading(text(r,"id"),text(r,"variant_id"),text(r,"location_id"),text(r,"listing_id"),
            text(r,"platform_id"),text(r,"retailer_sku"),time(r,"observed_at"),time(r,"recorded_at"),text(r,"provider"),
            text(r,"grant_id"),time(r,"retain_until"),text(r,"source_record_id"),number(r,"price_minor"),number(r,"mrp_minor"),
            PriceObservations.nullableDecimal(r,"discount_percent"),text(r,"stock_status"),text(r,"pack_label"),(int)num(r,"pack_count"),decimal(r,"each_amount"),text(r,"base_unit"))).toList();
    }

    private static Series series(List<Reading> rows,Instant now) {
        Reading latest=rows.get(rows.size()-1);
        var days=rows.stream().collect(Collectors.groupingBy(r->r.observedAt().atOffset(ZoneOffset.UTC).toLocalDate(),TreeMap::new,Collectors.toList()));
        Long change=null;
        if (latest.priceMinor()!=null) {
            for (int i=rows.size()-2;i>=0;i--) if (rows.get(i).priceMinor()!=null) {
                change=latest.priceMinor()-rows.get(i).priceMinor();
                break;
            }
        }
        return new Series(latest.retailer(),latest.listingId(),latest.sku(),stats(rows),days.entrySet().stream()
            .map(e->new Day(e.getKey(),stats(e.getValue()))).toList(),rows.stream().map(r->new HistoryReading(
            r.observedAt(),r.provider(),r.priceMinor(),r.mrpMinor(),r.discountPercent(),r.stock())).toList(),latest.observedAt(),
            latest.observedAt().plus(FRESH_FOR).isAfter(now)?"FRESH":"STALE",change);
    }

    private static Stats stats(List<Reading> rows) {
        List<Long> prices=rows.stream().map(Reading::priceMinor).filter(Objects::nonNull).toList();
        if (prices.isEmpty()) return new Stats(rows.size(),0,null,null,null);
        long average=prices.stream().map(BigDecimal::valueOf).reduce(BigDecimal.ZERO,BigDecimal::add)
            .divide(BigDecimal.valueOf(prices.size()),0,RoundingMode.HALF_UP).longValueExact();
        return new Stats(rows.size(),prices.size(),Collections.min(prices),Collections.max(prices),average);
    }

    private static void checkRange(Instant from,Instant until,Instant now) {
        if (!from.isBefore(until) || until.isAfter(now) || Duration.between(from,until).compareTo(Duration.ofDays(366))>0
            || from.isBefore(Instant.parse("2000-01-01T00:00:00Z"))) throw ApiException.bad("Choose a past observation window of at most 366 days.");
    }
}
