package com.smartcart.controller;

import com.smartcart.exception.ApiException;
import com.smartcart.integration.QuickCommerceProvider;
import com.smartcart.service.ControlledPilot;
import com.smartcart.service.ShoppingPositions;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Private transient lookup. Preparing a request never reserves credits or searches a retailer. */
@RestController
@Profile("local")
@RequestMapping("/api/shopping/retailers/private-search")
class ShopperSearchController {
    record Selection(@NotBlank @Size(max=150) String query,@Pattern(regexp="blinkit|zepto|instamart") @NotNull String retailer,
                     @NotNull Double latitude,@NotNull Double longitude,@Pattern(regexp="[1-9][0-9]{5}") String postcode) {}
    record Preview(UUID id,Selection selection,int ledgerBalance,int maximumCredits,Instant expiresAt) {}
    record Receipt(String retailer,String outcome,QuickCommerceProvider.Result result,Integer upstreamStatus,String providerRequestId,
                   String billing, String message,long durationMs) {}
    record Confirmation(@NotNull UUID id,boolean approved) {}
    private final Map<String,Preview> pending=new HashMap<>();
    private record SavedReceipt(String owner, Selection place, Receipt value, Instant expires) {}
    private final Map<UUID,SavedReceipt> receipts = new LinkedHashMap<>();
    private final java.util.concurrent.ScheduledExecutorService cleanup = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    @jakarta.annotation.PreDestroy void close() { cleanup.shutdownNow(); }

    @GetMapping("/receipts/{id}")
    ResponseEntity<Receipt> receipt(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        SavedReceipt saved;
        synchronized (receipts) { saved = receipts.get(id); }
        if (saved == null || !saved.owner().equals(jwt.getSubject()) || !saved.expires().isAfter(Instant.now()))
            throw new ApiException(HttpStatus.NOT_FOUND, "Previous retailer search expired.");
        var s = saved.place();
        positions.require(jwt.getSubject(),s.latitude(),s.longitude(),s.postcode());
        return response(saved.value());
    }

    private final ShoppingPositions positions;
    private final QuickCommerceProvider provider;

    ShopperSearchController(ShoppingPositions positions,QuickCommerceProvider provider){
        this.positions=positions;this.provider=provider;
        cleanup.scheduleAtFixedRate(()->{ synchronized(receipts) {
            receipts.values().removeIf(r -> !r.expires().isAfter(Instant.now()));
        }},1,1,java.util.concurrent.TimeUnit.MINUTES);
    }
    @GetMapping("/platforms")
    ResponseEntity<List<String>> platforms(){return response(provider.shoppingPlatforms());}

    @PostMapping("/prepare")
    ResponseEntity<Preview> prepare(@AuthenticationPrincipal Jwt jwt,@RequestBody @Valid Selection request){
        var owner=jwt.getSubject();
        var term=ControlledPilot.discoveryQuery(request.query());
        positions.require(owner,request.latitude(),request.longitude(),request.postcode());
        int balance=provider.previewSearch(request.retailer(),term,request.latitude(),request.longitude(),request.postcode());
        var now=Instant.now();
        var preview=new Preview(UUID.randomUUID(),new Selection(term,request.retailer(),request.latitude(),request.longitude(),request.postcode()),balance,1,now.plusSeconds(300));
        synchronized(pending){
            pending.values().removeIf(value->!value.expiresAt().isAfter(now));
            if(!pending.containsKey(owner)&&pending.size()>=4096)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Search preview capacity reached.");
            pending.put(owner,preview);
        }
        return response(preview);
    }
    @PostMapping("/confirm")
    ResponseEntity<Receipt> confirm(@AuthenticationPrincipal Jwt jwt,@RequestBody @Valid Confirmation input){
        String owner=jwt.getSubject();
        Preview preview;
        synchronized(pending){
            preview=pending.get(owner);
            if(!input.approved()||preview==null||!preview.id().equals(input.id())||!preview.expiresAt().isAfter(Instant.now()))
                throw new ApiException(HttpStatus.CONFLICT,"Prepare this exact search again before confirming.");
            pending.remove(owner);
        }
        var s=preview.selection();
        positions.require(owner,s.latitude(),s.longitude(),s.postcode());
        long start=System.nanoTime();
        try {
            var result=QuickCommerceProvider.withPilotPermit(()->provider.search(s.retailer(),s.query(),s.latitude(),s.longitude(),s.postcode()));
            var receipt = new Receipt(s.retailer(),result.products().isEmpty()?"NO_RESULTS":"SUCCESS",result,result.upstreamStatus(),
                result.requestId(),"CHARGED_SETTLED",null,(System.nanoTime()-start)/1_000_000);
            synchronized(receipts) {
                if(receipts.size()>=256) receipts.remove(receipts.keySet().iterator().next());
                receipts.put(preview.id(),new SavedReceipt(owner,s,receipt,Instant.now().plusSeconds(300)));
            }
            return response(receipt);
        } catch(QuickCommerceProvider.LookupFailure failure) {
            return response(new Receipt(s.retailer(),failure.outcome.name(),null,failure.upstreamStatus,failure.providerRequestId,
                failure.confirmedNoCharge?"NO_CHARGE_SETTLED":"UNCERTAIN",failure.getMessage(),(System.nanoTime()-start)/1_000_000));
        }
    }
    private static <T> ResponseEntity<T> response(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
