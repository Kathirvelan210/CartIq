package com.smartcart.service;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OfferFreshness {
    private final int seconds;
    public OfferFreshness(@Value("${CARTIQ_OFFER_FRESHNESS_SECONDS:900}") int seconds) {
        if(seconds<60||seconds>900)throw new IllegalArgumentException("CARTIQ_OFFER_FRESHNESS_SECONDS must be between 60 and 900.");
        this.seconds=seconds;
    }
    public int seconds(){return seconds;}
    public Instant cutoff(Instant now){return now.minusSeconds(seconds);}
    public Instant expires(Instant at){return at.plusSeconds(seconds);}
    public boolean fresh(Instant at,Instant now){return at!=null&&!at.isAfter(now)&&at.isAfter(cutoff(now));}
}
