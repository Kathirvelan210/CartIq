package com.smartcart.controller;

import com.smartcart.dto.Models.CartItem;
import com.smartcart.exception.ApiException;
import com.smartcart.integration.QuickCommerceProvider;
import com.smartcart.integration.RetailerDiagnostics;
import com.smartcart.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("local")
@RequestMapping("/api/shopping/retailers/marketplace")
class MarketplaceController {
    record View(boolean configured,List<ControlledPilot.DiscoveryTicket> authorizations,List<LiveOffers.Discovery> recorded,String locationId) {}
    record Position(@NotBlank @Size(max=60) String locationId,@NotNull Double lat,@NotNull Double lon,
        @Pattern(regexp="[1-9][0-9]{5}") String pincode) {}
    record Search(@NotNull @Valid Position location,@NotBlank @Size(max=150) String query,@NotBlank @Size(max=60) String variant,
        @NotNull @Pattern(regexp="blinkit|zepto|instamart") String retailer,@NotNull UUID authorizationId) {}
    record Add(@NotNull @Valid Position location,@NotBlank @Size(max=150) String query,@NotBlank @Size(max=60) String variant,
        @NotNull @Pattern(regexp="blinkit|zepto|instamart") String retailer,@NotBlank @Size(max=150) String nativeId,boolean replace) {}
    record Result(View marketplace,String retailer,String outcome,String billing,String requestId,Integer upstreamStatus,Integer debit,String message) {}
    record CartResult(CartItem item,LiveOffers.Offer offer) {}

    private final ControlledPilot pilot;
    private final LiveOffers offers;
    private final CatalogService catalogue;
    private final QuickCommerceProvider provider;
    private final RetailerDiagnostics diagnostics;
    private final CartService carts;
    private final ShoppingPositions positions;

    MarketplaceController(ControlledPilot pilot,LiveOffers offers,CatalogService catalogue,QuickCommerceProvider provider,
                          RetailerDiagnostics diagnostics,CartService carts,ShoppingPositions positions){
        this.positions=positions;
        this.pilot=pilot;this.offers=offers;this.catalogue=catalogue;this.provider=provider;this.diagnostics=diagnostics;this.carts=carts;
    }
    @GetMapping
    ResponseEntity<View> read(@AuthenticationPrincipal Jwt jwt,@RequestParam String locationId,@RequestParam double lat,
        @RequestParam double lon,@RequestParam(required=false) String pincode,@RequestParam String query){
        String term=ControlledPilot.discoveryQuery(query);
        String matchedArea=offers.areaFor(lat,lon,pincode);
        if(matchedArea!=null)locationId=matchedArea;
        if(!configured(locationId))return response(new View(false,List.of(),List.of(),null));
        offers.requireScope(locationId,lat,lon,pincode);
        return response(view(jwt.getSubject(),locationId,term));
    }
    @PostMapping("/search")
    ResponseEntity<Result> search(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Search search){
        String owner=jwt.getSubject(),area=search.location().locationId(),term=ControlledPilot.discoveryQuery(search.query());
        positions.require(owner,search.location().lat(),search.location().lon(),search.location().pincode());
        String pin=requirePosition(search.location());
        return response(offers.refresh(owner,area,"marketplace:"+term,()->dispatch(owner,area,term,pin,search)));
    }
    private Result dispatch(String owner,String area,String term,String pin,Search search){
        String retailer=search.retailer();
        View previous=view(owner,area,term);
        if(previous.recorded().stream().anyMatch(row->retailer.equals(row.retailer())))
            return new Result(previous,retailer,"CACHED","NOT_DISPATCHED",null,null,0,null);
        var product=catalogue.product(search.variant(),area);
        var claim=pilot.claimDiscovery(search.authorizationId().toString(),owner,area,product,retailer,term);
        QuickCommerceProvider.Result lookup;
        try{
            lookup=QuickCommerceProvider.withPilotPermit(()->provider.search(retailer,term,search.location().lat(),search.location().lon(),pin));
        }catch(ApiException error){
            var failure=error instanceof QuickCommerceProvider.LookupFailure known?known:null;
            var event=diagnostics.lookup(org.slf4j.MDC.get("requestId"),retailer).orElse(null);
            String billing=error instanceof QuickCommerceProvider.DuplicateLookup?"NOT_DISPATCHED":event==null?"UNCERTAIN":event.billing().name();
            String outcome=failure==null?"PROVIDER_ERROR":failure.outcome.name();
            String id=failure==null?null:failure.providerRequestId;
            Integer status=failure==null?null:failure.upstreamStatus,debit="UNCERTAIN".equals(billing)?null:0;
            pilot.finish(claim,outcome,billing,id,status,debit,null,0,0);
            return new Result(view(owner,area,term),retailer,outcome,billing,id,status,debit,error.getMessage());
        }
        try{
            var evidence=offers.update(owner,area,product,retailer,lookup,claim.mappingDecision(),claim.pilotEndsAt(),claim.id());
            var outcome=evidence.isEmpty()?"NO_RESULTS":"SUCCESS";
            int verified=(int)evidence.stream().filter(row->"VERIFIED".equals(row.match())).count();
            pilot.finish(claim,outcome,"CHARGED_SETTLED",lookup.requestId(),lookup.upstreamStatus(),1,lookup.creditsRemaining(),evidence.size(),verified);
            offers.rememberDiscovery(owner,area,term,lookup,evidence,claim.pilotEndsAt(),claim.id());
            return new Result(view(owner,area,term),retailer,outcome,"CHARGED_SETTLED",lookup.requestId(),lookup.upstreamStatus(),1,null);
        }catch(RuntimeException invalid){
            pilot.finish(claim,"NORMALIZATION_ERROR","CHARGED_SETTLED",lookup.requestId(),lookup.upstreamStatus(),1,lookup.creditsRemaining(),null,null);
            throw new ApiException(HttpStatus.BAD_GATEWAY,"The lookup was charged, but offer normalization failed. Check the pilot report.");
        }
    }
    public record CartQuote(String itemId,long priceMinor,java.time.Instant fetchedAt,java.time.Instant freshUntil){}
    @PostMapping("/cart-quotes")
    ResponseEntity<List<CartQuote>> cartQuotes(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Position position){
        var owner=jwt.getSubject();
        positions.require(owner,position.lat(),position.lon(),position.pincode());
        if(!configured(position.locationId()))return response(List.of());
        requirePosition(position);
        var result=new java.util.ArrayList<CartQuote>();
        for(var item:carts.list(owner)){
            var choice=item.listing();
            if(choice==null||!item.locationId().equals(position.locationId()))continue;
            var pack=catalogue.product(item.productId(),item.locationId());
            offers.selected(owner,item.locationId(),pack,choice.retailer())
                .filter(s->choice.decisionId().equals(s.decisionId())).map(LiveOffers.Selection::offer)
                .filter(o->choice.nativeId().equals(o.nativeId())&&Boolean.TRUE.equals(o.available())&&o.priceMinor()!=null&&
                    (o.stockPacks()==null?item.quantity()==1:o.stockPacks()>=item.quantity()))
                .ifPresent(o->result.add(new CartQuote(item.id(),o.priceMinor(),o.fetchedAt(),o.freshUntil())));
        }
        return response(result);
    }
    @PostMapping("/cart")
    ResponseEntity<CartResult> add(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Add input){
        positions.require(jwt.getSubject(),input.location().lat(),input.location().lon(),input.location().pincode());
        requirePosition(input.location());
        String owner=jwt.getSubject(),area=input.location().locationId();
        var cached=view(owner,area,ControlledPilot.discoveryQuery(input.query())).recorded().stream()
            .filter(row->row.retailer().equals(input.retailer())).flatMap(row->row.listings().stream())
            .anyMatch(row->input.variant().equals(row.evidence().variantId())&&input.nativeId().equals(row.listing().nativeId()));
        var pack=catalogue.product(input.variant(),area);
        var selection=offers.selected(owner,area,pack,input.retailer()).orElseThrow(()->new ApiException(HttpStatus.CONFLICT,"Refresh the exact listing first."));
        var offer=java.util.Optional.ofNullable(selection.offer())
            .filter(row->cached&&row.nativeId().equals(input.nativeId())&&row.priceMinor()!=null&&Boolean.TRUE.equals(row.available())
                &&(row.stockPacks()==null||row.stockPacks()>0))
            .orElseThrow(()->new ApiException(HttpStatus.CONFLICT,"Choose a fresh priced listing with an approved exact-pack mapping."));
        return response(new CartResult(carts.addMarketplacePack(owner,pack.id(),area,offer.stockPacks(),selection.decisionId(),input.replace()),offer));
    }
    private boolean configured(String area){return pilot.enabled()&&provider.configured()&&offers.configured(area);}
    private String requirePosition(Position position){
        if(!configured(position.locationId()))throw new ApiException(HttpStatus.FORBIDDEN,"The marketplace pilot is unavailable for this area.");
        return offers.requireScope(position.locationId(),position.lat(),position.lon(),position.pincode());
    }
    private View view(String owner,String area,String term){
        var rows=offers.discovery(owner,area,term).stream().filter(row->pilot.discoveryDisplayActive(row.authorizationId())).toList();
        return new View(true,pilot.discoveryTickets(owner,area,term),rows,area);
    }
    private static <T> ResponseEntity<T> response(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
