package com.smartcart.controller;

import com.smartcart.exception.ApiException;
import com.smartcart.integration.QuickCommerceProvider;
import com.smartcart.integration.RetailerDiagnostics;
import com.smartcart.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local")
@RequestMapping("/api/shopping/retailers/offers")
class LiveOffersController {
    record Refresh(@NotBlank @Size(max=60) String locationId,@NotNull Double lat,@NotNull Double lon,
        @Pattern(regexp="[1-9][0-9]{5}") String pincode,@NotNull @Size(min=1,max=3) List<@NotBlank String> retailers,UUID authorizationId) {}
    record Item(@NotNull @Valid Refresh scope,@NotBlank @Size(max=150) String nativeId) {}
    record Retailer(String retailer,String status,QuickCommerceProvider.Outcome outcome,String message,
        String providerRequestId,Integer upstreamStatus,java.time.Instant fetchedAt,List<LiveOffers.Offer> offers) {}
    record Receipt(String authorizationId,String result,String billing,String providerRequestId,Integer status,Integer confirmedDebit,Integer balance) {}
    record View(boolean configured,List<CurrentOffers.Quote> quotes,List<Retailer> retailers,boolean paused,
                boolean controlledPilot,List<ControlledPilot.Ticket> authorizations,Receipt receipt) {}

    private final LiveOffers live;
    private final CatalogService catalogue;
    private final CurrentOffers current;
    private final ComparisonService comparison;
    private final QuickCommerceProvider provider;
    private final ControlledPilot pilot;
    private final RetailerDiagnostics diagnostics;
    private final boolean legacy;
    private final ShoppingPositions positions;

    LiveOffersController(LiveOffers live,CatalogService catalogue,CurrentOffers current,ComparisonService comparison,QuickCommerceProvider provider,
       ControlledPilot pilot,RetailerDiagnostics diagnostics,ShoppingPositions positions,
       @org.springframework.beans.factory.annotation.Value("${QUICKCOMMERCE_LEGACY_LOOKUPS_ENABLED:false}") boolean legacy){
        this.live=live;this.catalogue=catalogue;this.current=current;this.comparison=comparison;this.provider=provider;
        this.positions=positions;this.pilot=pilot;this.diagnostics=diagnostics;this.legacy=legacy;
    }
    @GetMapping("/{variant}")
    ResponseEntity<View> offers(@AuthenticationPrincipal Jwt jwt,@PathVariable String variant,@RequestParam String locationId) {
        var product=catalogue.product(variant,locationId);
        boolean configured=provider.configured()&&live.configured(locationId)&&(pilot.enabled()||legacy);
        return result(new View(configured,current.forPack(product,locationId,jwt.getSubject()),List.of(),false,pilot.enabled(),
            configured?pilot.tickets(jwt.getSubject(),locationId,variant):List.of(),null));
    }
    @PostMapping("/{variant}/refresh")
    ResponseEntity<View> refresh(@AuthenticationPrincipal Jwt jwt,@PathVariable String variant,@Valid @RequestBody Refresh scope) {
        String pin=live.requireScope(scope.locationId(),scope.lat(),scope.lon(),scope.pincode());
        if(pilot.enabled())positions.require(jwt.getSubject(),scope.lat(),scope.lon(),scope.pincode());
        var product=catalogue.product(variant,scope.locationId());
        String query=String.join(" ",product.brand(),product.name(),product.quantity()).strip().replaceAll("\\s+"," ");
        if(query.length()>150)throw ApiException.bad("The canonical product query is too long for the retailer.");
        String owner=jwt.getSubject();
        if(pilot.enabled())return result(live.refresh(owner,scope.locationId(),variant,()->controlled(owner,scope,pin,product,query,null)));
        requireLegacy();
        return result(live.refresh(owner,scope.locationId(),variant,()->{
            var lookedUp=comparison.compareRetailerSearch(query,scope.lat(),scope.lon(),pin,scope.retailers());
            var rows=lookedUp.retailers().stream().map(row->new Retailer(row.retailer(),row.status(),row.outcome(),row.message(),
                row.result()==null?row.providerRequestId():row.result().requestId(),
                row.result()==null?row.upstreamStatus():row.result().upstreamStatus(),
                row.result()==null?null:row.result().fetchedAt(),
                row.result()==null?List.of():live.update(owner,scope.locationId(),product,row.retailer(),row.result()))).toList();
            return new View(true,current.forPackAtPosition(product,scope.locationId(),owner,position(scope,pin)),rows,lookedUp.paused(),false,List.of(),null);
        }));
    }
    @PostMapping("/{variant}/item")
    ResponseEntity<View> item(@AuthenticationPrincipal Jwt jwt,@PathVariable String variant,@Valid @RequestBody Item input) {
        var scope=input.scope();
        if(scope.retailers().size()!=1)throw ApiException.bad("Choose one retailer for item details.");
        String pin=live.requireScope(scope.locationId(),scope.lat(),scope.lon(),scope.pincode());
        if(pilot.enabled())positions.require(jwt.getSubject(),scope.lat(),scope.lon(),scope.pincode());
        var product=catalogue.product(variant,scope.locationId());
        String retailer=scope.retailers().get(0),owner=jwt.getSubject();
        if(pilot.enabled())return result(live.refresh(owner,scope.locationId(),variant,()->controlled(owner,scope,pin,product,null,input.nativeId())));
        requireLegacy();
        return result(live.refresh(owner,scope.locationId(),variant,()->{
            var prior=live.selected(owner,scope.locationId(),product,retailer).map(LiveOffers.Selection::offer).orElse(null);
            if(prior==null||!prior.nativeId().equals(input.nativeId()))throw ApiException.bad("Select a fresh verified listing from this account and location.");
            Retailer row;
            boolean paused=false;
            try {
                var response=provider.item(retailer,input.nativeId(),scope.lat(),scope.lon(),pin);
                row=new Retailer(retailer,"success",response.products().isEmpty()?QuickCommerceProvider.Outcome.NO_RESULTS:
                    QuickCommerceProvider.Outcome.SUCCESS,null,response.requestId(),response.upstreamStatus(),response.fetchedAt(),
                    live.update(owner,scope.locationId(),product,retailer,response));
            } catch(QuickCommerceProvider.DuplicateLookup duplicate) {
                row=new Retailer(retailer,"duplicate",QuickCommerceProvider.Outcome.NOT_ATTEMPTED,duplicate.getMessage(),null,null,null,List.of());
            } catch(ApiException failure) {
                if(failure.status==HttpStatus.BAD_REQUEST)throw failure;
                var known=failure instanceof QuickCommerceProvider.LookupFailure e?e:null;
                row=new Retailer(retailer,"error",known==null?QuickCommerceProvider.Outcome.PROVIDER_ERROR:known.outcome,
                    failure.getMessage(),known==null?null:known.providerRequestId,known==null?null:known.upstreamStatus,null,List.of());
                paused=known==null||!known.confirmedNoCharge||known.upstreamStatus!=null&&Set.of(401,402,403,429).contains(known.upstreamStatus);
            }
            return new View(true,current.forPackAtPosition(product,scope.locationId(),owner,position(scope,pin)),List.of(row),paused,false,List.of(),null);
        }));
    }
    private void requireLegacy(){
        if(!legacy)throw new ApiException(HttpStatus.FORBIDDEN,"A one-use controlled pilot authorization is required.");
    }
    private View controlled(String owner,Refresh scope,String pin,com.smartcart.dto.Models.Product product,String query,String item){
        if(scope.retailers().size()!=1)throw ApiException.bad("A pilot action authorizes exactly one retailer request.");
        if(!provider.configured())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"QuickCommerce provider is unavailable.");
        String retailer=scope.retailers().get(0),area=scope.locationId();
        var claim=pilot.claim(scope.authorizationId()==null?null:scope.authorizationId().toString(),owner,area,product,retailer,
            item==null?ControlledPilot.Operation.SEARCH:ControlledPilot.Operation.ITEM,item);
        QuickCommerceProvider.Result response;
        try {
            response=QuickCommerceProvider.withPilotPermit(()->item==null?provider.search(retailer,query,scope.lat(),scope.lon(),pin):
                provider.item(retailer,item,scope.lat(),scope.lon(),pin));
        }catch(ApiException error){
            var event=diagnostics.lookup(org.slf4j.MDC.get("requestId"),retailer).orElse(null);
            var failure=error instanceof QuickCommerceProvider.LookupFailure known?known:null;
            String billing=error instanceof QuickCommerceProvider.DuplicateLookup?"NOT_DISPATCHED":event==null?"UNCERTAIN":event.billing().name();
            String requestId=failure==null?null:failure.providerRequestId;
            Integer status=failure==null?null:failure.upstreamStatus;
            var outcome=failure==null?QuickCommerceProvider.Outcome.PROVIDER_ERROR:failure.outcome;
            Integer debit="UNCERTAIN".equals(billing)?null:0;
            pilot.finish(claim,outcome.name(),billing,requestId,status,debit,null,0,0);
            var row=new Retailer(retailer,"error",outcome,error.getMessage(),requestId,status,null,List.of());
            return pilotResult(owner,scope,pin,product,row,new Receipt(claim.id(),outcome.name(),billing,requestId,status,debit,null));
        }
        List<LiveOffers.Offer> normalized;
        try {normalized=live.update(owner,area,product,retailer,response,claim.mappingDecision(),claim.pilotEndsAt());}
        catch(RuntimeException invalid){
            // The adapter returned only after settlement. Preserve billing evidence even if publication failed.
            pilot.finish(claim,"NORMALIZATION_ERROR","CHARGED_SETTLED",response.requestId(),response.upstreamStatus(),1,response.creditsRemaining(),null,null);
            throw new ApiException(HttpStatus.BAD_GATEWAY,"The lookup was charged, but offer normalization failed. Check the pilot report.");
        }
        var outcome=normalized.isEmpty()?QuickCommerceProvider.Outcome.NO_RESULTS:QuickCommerceProvider.Outcome.SUCCESS;
        int verified=(int)normalized.stream().filter(o->"VERIFIED".equals(o.match())).count();
        pilot.finish(claim,outcome.name(),"CHARGED_SETTLED",response.requestId(),response.upstreamStatus(),1,response.creditsRemaining(),normalized.size(),verified);
        var row=new Retailer(retailer,"success",outcome,null,response.requestId(),response.upstreamStatus(),response.fetchedAt(),normalized);
        return pilotResult(owner,scope,pin,product,row,new Receipt(claim.id(),outcome.name(),"CHARGED_SETTLED",response.requestId(),
            response.upstreamStatus(),1,response.creditsRemaining()));
    }
    private View pilotResult(String owner,Refresh scope,String pin,com.smartcart.dto.Models.Product product,Retailer row,Receipt receipt){
        return new View(true,current.forPackAtPosition(product,scope.locationId(),owner,position(scope,pin)),List.of(row),
            "UNCERTAIN".equals(receipt.billing()),true,pilot.tickets(owner,scope.locationId(),product.id()),receipt);
    }
    private static String position(Refresh scope,String pin){return scope.lat()+","+scope.lon()+","+pin;}
    private static <T> ResponseEntity<T> result(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
