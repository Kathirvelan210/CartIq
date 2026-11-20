package com.smartcart.controller;

import com.smartcart.exception.ApiException;
import com.smartcart.integration.QuickCommerceProvider;
import com.smartcart.service.ControlledPilot;
import com.smartcart.service.ShoppingPositions;
import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("local")
@RequestMapping("/api/shopping/retailers/private-search/compare-all")
class ShopperComparisonController {
    record Selection(@NotBlank @Size(max=150) String query, @NotNull Double latitude, @NotNull Double longitude,
                     @Pattern(regexp="[1-9][0-9]{5}") String postcode,
                     @Size(min=1,max=3) List<@NotNull @Pattern(regexp="blinkit|zepto|instamart") String> retailers) {}
    record Preview(UUID id, Selection selection, QuickCommerceProvider.ComparisonQuote quote, int maximumCredits, Instant expiresAt) {}
    record Confirmation(@NotNull UUID id, boolean approved) {}
    record Progress(UUID id, List<QuickCommerceProvider.ComparisonRow> rows, boolean done,
                    QuickCommerceProvider.ComparisonBilling billing, String error) {}
    static final class Job {
        final String owner;
        final Preview preview;
        final Map<String,QuickCommerceProvider.ComparisonRow> rows = new LinkedHashMap<>();
        private boolean started, done;
        private QuickCommerceProvider.ComparisonBilling billing;
        private String error;
        Job(String owner, Preview preview) { this.owner=owner; this.preview=preview; }
        synchronized void publish(QuickCommerceProvider.ComparisonRow row) { rows.put(row.retailer(),row); }
        synchronized void finish(QuickCommerceProvider.ComparisonBilling result,String message) { billing=result; error=message; done=true; }
        synchronized Progress progress() { return new Progress(preview.id(), List.copyOf(rows.values()),done,billing,error); }
    }
    private final ShoppingPositions positions;
    private final QuickCommerceProvider provider;
    private final Map<UUID,Job> jobs = new ConcurrentHashMap<>();
    private final ScheduledExecutorService expiry = Executors.newSingleThreadScheduledExecutor();
    // Never queue paid dispatches behind another comparison.
    private final ExecutorService runner = new ThreadPoolExecutor(0,1,30,TimeUnit.SECONDS,new SynchronousQueue<>());
    ShopperComparisonController(ShoppingPositions positions, QuickCommerceProvider provider) {
        this.positions=positions; this.provider=provider;
        expiry.scheduleAtFixedRate(()->{synchronized(baskets){pruneBaskets();}},1,1,TimeUnit.MINUTES);
    }
    @PreDestroy void close() { runner.shutdown(); expiry.shutdownNow(); }
    record CachedRow(UUID jobId, Instant expiresAt, QuickCommerceProvider.ComparisonRow row) {}

    @PostMapping("/cached")
    ResponseEntity<List<CachedRow>> cached(@AuthenticationPrincipal Jwt jwt,@RequestBody @Valid Selection input) {
        return response(cachedRows(jwt.getSubject(),input));
    }

    private List<CachedRow> cachedRows(String owner,Selection input){
        var query=ControlledPilot.discoveryQuery(input.query());
        var wanted=input.retailers()==null?QuickCommerceProvider.RETAILERS:input.retailers();
        var matches=new LinkedHashMap<String,CachedRow>();
        var now=Instant.now();
        for(var job:jobs.values()){
            var place=job.preview.selection();
            if(!job.owner.equals(owner)||!place.query().equals(query)||!place.latitude().equals(input.latitude())
                ||!place.longitude().equals(input.longitude())||!Objects.equals(place.postcode(),input.postcode()))continue;
            for(var row:job.progress().rows()){
                var result=row.result();
                if(!wanted.contains(row.retailer())||result==null||result.products().isEmpty())continue;
                var deadline=job.preview.expiresAt().plusSeconds(180);
                if(result.freshUntil().isBefore(deadline))deadline=result.freshUntil();
                if(!deadline.isAfter(now))continue;
                matches.merge(row.retailer(),new CachedRow(job.preview.id(),deadline,row),
                    (a,b)->a.row().result().fetchedAt().isAfter(b.row().result().fetchedAt())?a:b);
            }
        }
        return List.copyOf(matches.values());
    }
    @PostMapping("/prepare")
    ResponseEntity<Preview> prepare(@AuthenticationPrincipal Jwt jwt,@RequestBody @Valid Selection input) {
        String owner=jwt.getSubject();
        String query=ControlledPilot.discoveryQuery(input.query());
        positions.require(owner,input.latitude(),input.longitude(),input.postcode());
        // Older clients omitted retailers when requesting the full comparison.
        var retailers=input.retailers()==null?QuickCommerceProvider.RETAILERS:List.copyOf(input.retailers());
        var selection=new Selection(query,input.latitude(),input.longitude(),input.postcode(),retailers);
        if(!cachedRows(owner,selection).isEmpty())throw new ApiException(HttpStatus.CONFLICT,"Products are already available. Reopen the search to load them without another charge.");
        var quote=provider.quoteComparison(query,input.latitude(),input.longitude(),input.postcode(),retailers);
        var preview=new Preview(quote.blocker()==null?UUID.randomUUID():null, selection,quote,retailers.size(),Instant.now().plusSeconds(120));
        if (preview.id()!=null) {
            synchronized (jobs) {
                if (jobs.size()>=1024) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Comparison preview capacity reached.");
                jobs.put(preview.id(),new Job(owner,preview));
            }
            expiry.schedule(()->jobs.remove(preview.id()),5,TimeUnit.MINUTES);
        }
        return response(preview);
    }
    @PostMapping("/confirm")
    ResponseEntity<Progress> confirm(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Confirmation confirmation) {
        var job=owned(jwt,confirmation.id());
        synchronized(job) {
            if (!confirmation.approved() || job.started || !Instant.now().isBefore(job.preview.expiresAt()))
                throw new ApiException(HttpStatus.CONFLICT,"This comparison needs a new preview and confirmation.");
            var s=job.preview.selection();
            positions.require(job.owner,s.latitude(),s.longitude(),s.postcode());
            job.started=true;
            try {
                runner.execute(()->{
                    try {
                        positions.require(job.owner,s.latitude(),s.longitude(),s.postcode());
                        job.finish(provider.compareConfirmed(s.query(),s.latitude(),s.longitude(),s.postcode(),
                            s.retailers(),job.preview.quote(),job::publish),null);
                    } catch (ApiException failure) { job.finish(null,failure.getMessage()); }
                    catch (RuntimeException failure) { job.finish(null,"Comparison outcome unavailable. Check billing before searching again."); }
                });
            } catch (RejectedExecutionException busy) { job.finish(null,"Another comparison is running. No request was sent."); }
            return response(job.progress());
        }
    }
    @GetMapping("/{id}")
    ResponseEntity<Progress> progress(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) { return response(owned(jwt,id).progress()); }
    record Place(@NotNull Double latitude,@NotNull Double longitude,@Pattern(regexp="[1-9][0-9]{5}") String postcode) {}
    record Pick(@NotBlank @Pattern(regexp="blinkit|zepto|instamart") String retailer,@NotBlank @Size(max=150) String nativeId) {}
    record Change(@NotNull @Valid Place place,@NotNull UUID lineId,@Min(0) @Max(99) int quantity) {}
    record CartLine(UUID id,String retailer,String nativeId,String title,String brand,String pack,long priceMinor,
                    String imageUrl,Integer stock,int quantity,Instant fetchedAt,Instant freshUntil) {
        CartLine quantity(int value) {return new CartLine(id,retailer,nativeId,title,brand,pack,priceMinor,imageUrl,stock,value,fetchedAt,freshUntil);}
    }
    private record BasketKey(String owner,Place place) {}
    private static final class Basket {
        final Map<UUID,CartLine> lines=new LinkedHashMap<>();
        Instant until=Instant.now().plusSeconds(1800);
    }
    // Private planning selections expire in memory. No catalogue join, checkout or price-history write.
    private final Map<BasketKey,Basket> baskets=new HashMap<>();
    private void pruneBaskets(){baskets.values().removeIf(b->!b.until.isAfter(Instant.now()));}
    private BasketKey scope(Jwt jwt,Place place){
        positions.require(jwt.getSubject(),place.latitude(),place.longitude(),place.postcode());
        return new BasketKey(jwt.getSubject(),place);
    }
    @PostMapping("/basket")
    ResponseEntity<List<CartLine>> basket(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Place place){
        var key=scope(jwt,place);
        synchronized(baskets){pruneBaskets();var b=baskets.get(key);return response(b==null?List.of():List.copyOf(b.lines.values()));}
    }
    @PostMapping("/{id}/basket/add")
    ResponseEntity<List<CartLine>> add(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Pick pick){
        var job=owned(jwt,id);
        var s=job.preview.selection();
        var key=scope(jwt,new Place(s.latitude(),s.longitude(),s.postcode()));
        var receipt=job.progress().rows().stream().filter(r->r.retailer().equals(pick.retailer())).findFirst().orElse(null);
        if(receipt==null||receipt.result()==null||!receipt.result().freshUntil().isAfter(Instant.now()))
            throw new ApiException(HttpStatus.CONFLICT,"This listing has expired or is unavailable.");
        var result=receipt.result();
        var item=result.products().stream().filter(p->p.nativeId().equals(pick.nativeId())).findFirst().orElse(null);
        if(item==null||!Boolean.TRUE.equals(item.available())||item.priceMinor()==null||!"INR".equals(item.currency()))
            throw new ApiException(HttpStatus.CONFLICT,"A current available listing with a confirmed price is required.");
        synchronized(baskets){
            pruneBaskets();
            if(!baskets.containsKey(key)&&baskets.size()>=1024)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Cart capacity reached.");
            var b=baskets.computeIfAbsent(key,k->new Basket());
            var prior=b.lines.values().stream().filter(l->l.nativeId().equals(pick.nativeId())&&l.retailer().equals(pick.retailer())).findFirst().orElse(null);
            if(prior!=null&&(!Objects.equals(prior.title(),item.title())||!Objects.equals(prior.pack(),item.pack())||!Objects.equals(prior.brand(),item.brand())))
                throw new ApiException(HttpStatus.CONFLICT,"Listing identity changed. Remove the previous pack before adding this listing.");
            int qty=prior==null?1:prior.quantity()+1;
            checkQuantity(qty,item.inventory());
            if(prior==null&&b.lines.size()>=100)throw ApiException.bad("This basket is full.");
            var line=new CartLine(prior==null?UUID.randomUUID():prior.id(),pick.retailer(),pick.nativeId(),item.title(),item.brand(),item.pack(),
                item.priceMinor(),item.imageUrl(),item.inventory(),qty,result.fetchedAt(),result.freshUntil());
            b.lines.put(line.id(),line);b.until=Instant.now().plusSeconds(1800);
            return response(List.copyOf(b.lines.values()));
        }
    }
    private static void checkQuantity(int quantity,Integer stock){
        if(quantity>Math.min(99,stock==null?1:stock))throw ApiException.bad("Quantity exceeds confirmed stock.");
    }
    @PostMapping("/basket/quantity")
    ResponseEntity<List<CartLine>> quantity(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Change input){
        var key=scope(jwt,input.place());
        synchronized(baskets){
            pruneBaskets();var b=baskets.get(key);
            if(b==null||!b.lines.containsKey(input.lineId()))throw new ApiException(HttpStatus.NOT_FOUND,"Cart selection expired.");
            var line=b.lines.get(input.lineId());
            if(input.quantity()>line.quantity()){
                if(!line.freshUntil().isAfter(Instant.now()))throw ApiException.bad("Refresh this listing before increasing quantity.");
                checkQuantity(input.quantity(),line.stock());
            }
            if(input.quantity()==0)b.lines.remove(line.id());else b.lines.put(line.id(),line.quantity(input.quantity()));
            b.until=Instant.now().plusSeconds(1800);
            return response(List.copyOf(b.lines.values()));
        }
    }
    private Job owned(Jwt jwt,UUID id) {
        var job=jobs.get(id);
        if (job==null || !job.owner.equals(jwt.getSubject())) throw new ApiException(HttpStatus.NOT_FOUND,"Comparison not found or expired.");
        return job;
    }
    private static <T> ResponseEntity<T> response(T value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
}
