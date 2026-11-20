package com.smartcart.integration;

import com.smartcart.exception.ApiException;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RetailerDiagnostics {
    public enum Category { SUCCESS, LOCAL_GUARD, AUTH_OR_ACCOUNT, UNAVAILABLE_AREA_OR_ITEM, UNSUPPORTED,
        RATE_LIMIT, PROVIDER_FAILURE, RETAILER_UPSTREAM, PLATFORM_FAILED_UNSPECIFIED, TIMEOUT, ADAPTER_RESPONSE }
    public enum Billing { CHARGED_SETTLED, NO_CHARGE_SETTLED, UNCERTAIN, NOT_DISPATCHED }
    public record Event(Instant at,String requestId,String providerRequestId,String retailer,String operation,
        Integer upstreamStatus,String outcome,boolean failedPlatform,Category category,Billing billing) {}
    private final Deque<Event> recent=new ArrayDeque<>();
    private final Map<String,Instant> lastSuccess=new HashMap<>();

    synchronized void add(Event event) {
        recent.addFirst(event);
        if(recent.size()>120)recent.removeLast();
        if(event.category()==Category.SUCCESS)lastSuccess.put(event.retailer(),event.at());
    }
    public synchronized List<Event> events(String retailer) {
        return recent.stream().filter(e->retailer==null||retailer.equals(e.retailer())).toList();
    }
    public synchronized Map<String,Instant> lastSuccess(){return Map.copyOf(lastSuccess);}
    public synchronized Optional<Event> lookup(String requestId,String retailer) {
        if(requestId==null)return Optional.empty();
        return recent.stream().filter(event->requestId.equals(event.requestId())&&retailer.equals(event.retailer())).findFirst();
    }

    static Event outcome(String requestId,String retailer,String operation,QuickCommerceProvider.Result result,ApiException error,boolean dispatched) {
        if(result!=null)return new Event(Instant.now(),requestId,result.requestId(),retailer,operation,result.upstreamStatus(),
            result.products().isEmpty()?"NO_RESULTS":"SUCCESS",false,Category.SUCCESS,Billing.CHARGED_SETTLED);
        var failure=error instanceof QuickCommerceProvider.LookupFailure lookup?lookup:null;
        var category=failure==null?(dispatched?Category.ADAPTER_RESPONSE:Category.LOCAL_GUARD):failure.category;
        return new Event(Instant.now(),requestId,failure==null?null:failure.providerRequestId,retailer,operation,
            failure==null?null:failure.upstreamStatus,failure==null?error.status.name():failure.outcome.name(),
            category==Category.PLATFORM_FAILED_UNSPECIFIED,category, !dispatched?Billing.NOT_DISPATCHED:
            failure!=null&&failure.confirmedNoCharge?Billing.NO_CHARGE_SETTLED:Billing.UNCERTAIN);
    }
}
