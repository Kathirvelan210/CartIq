package com.smartcart.service;

import com.smartcart.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Short-lived device selections. Never persist provider addresses or precise coordinates. */
@Service
public class ShoppingPositions {
    public record Fix(Double latitude, Double longitude, Double accuracy, Long observedAt, String source, String postcode) {}
    private final Map<String,Fix> active = new HashMap<>();
    private static final long TTL = 900_000;

    public synchronized void select(String owner, Fix fix) {
        long now = Instant.now().toEpochMilli();
        if (fix == null || !coordinate(fix.latitude(),90) || !coordinate(fix.longitude(),180)
            || fix.observedAt()==null || fix.observedAt()<now-TTL || fix.observedAt()>now+5000
            || fix.postcode()!=null && !fix.postcode().matches("[1-9][0-9]{5}")
            || !("gps".equals(fix.source()) || "pin".equals(fix.source()) || "manual".equals(fix.source()))
            || "manual".equals(fix.source()) && fix.accuracy()!=null
            || "pin".equals(fix.source()) && fix.postcode()==null
            || "gps".equals(fix.source()) && fix.accuracy()==null
            || fix.accuracy()!=null && (!Double.isFinite(fix.accuracy()) || fix.accuracy()<0))
            throw ApiException.bad("Select a recent device fix or a resolved Indian PIN.");
        active.values().removeIf(point -> point.observedAt()<now-TTL);
        if (!active.containsKey(owner) && active.size()>=4096)
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Position selection is temporarily busy.");
        active.put(owner,fix);
    }
    private static boolean coordinate(Double value, double limit) {
        return value!=null && Double.isFinite(value) && Math.abs(value)<=limit;
    }
    public synchronized void clear(String owner) {active.remove(owner);}
    public synchronized void require(String owner, Double latitude, Double longitude, String pin) {
        Fix fix=active.get(owner);
        if(fix==null || fix.observedAt()<System.currentTimeMillis()-TTL ||
           !Objects.equals(fix.latitude(),latitude) || !Objects.equals(fix.longitude(),longitude) ||
           !Objects.equals(fix.postcode(),pin))
            throw new ApiException(HttpStatus.CONFLICT,"Confirm your current shopping location again.");
    }
}
