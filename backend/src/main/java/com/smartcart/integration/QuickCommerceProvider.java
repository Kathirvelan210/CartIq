package com.smartcart.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.smartcart.catalogimport.ProductMatching;
import com.smartcart.exception.ApiException;
import com.smartcart.service.OfferFreshness;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
@Profile("local")
public class QuickCommerceProvider {
    private static final Logger LOG = LoggerFactory.getLogger(QuickCommerceProvider.class);
    private static final JsonMapper JSON = JsonMapper.builder()
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Map<String, String> PLATFORMS = Map.of(
        "blinkit", "BlinkIt", "zepto", "Zepto", "instamart", "Swiggy");
    private static final Map<String, Set<String>> RETAILER_HOSTS = Map.of(
        "BlinkIt", Set.of("blinkit.com", "www.blinkit.com"),
        "Zepto", Set.of("zepto.com", "www.zepto.com", "zeptonow.com", "www.zeptonow.com"),
        "Swiggy", Set.of("swiggy.com", "www.swiggy.com"));
    public static final List<String> RETAILERS = List.of("blinkit", "zepto", "instamart");
    private final RestClient client;
    private final CloseableHttpClient transport;
    private final String key;
    private final boolean enabled;
    private final QuickCommerceBudget budget;
    private final RetailerDiagnostics diagnostics;
    private final OfferFreshness freshness;
    private final boolean controlledMode;
    private List<String> searchPlatforms = List.of();
    private Instant platformsExpire = Instant.EPOCH;

    public synchronized List<String> shoppingPlatforms() {
        requireEnabled();
        if (Instant.now().isBefore(platformsExpire)) return searchPlatforms;
        try {
            var body = client.get().uri("/v1/supported-platforms").retrieve().body(JsonNode.class);
            if (body == null || !body.path("search").isArray()) throw invalidResponse("supported platforms", null);
            var supported = new HashSet<String>();
            body.path("search").forEach(node -> { if (node.isTextual()) supported.add(node.textValue()); });
            searchPlatforms = RETAILERS.stream().filter(id -> supported.contains(PLATFORMS.get(id))).toList();
            platformsExpire = Instant.now().plusSeconds(3600);
            return searchPlatforms;
        } catch (RestClientException failure) { throw invalidResponse("supported platforms unavailable", null); }
    }

    public int previewSearch(String retailer,String query,double lat,double lon,String pin) {
        if (!shoppingPlatforms().contains(retailer)) throw ApiException.bad("Search is unavailable for this retailer.");
        var state = budget.inspect();
        if (state.allowance() <= 0) throw new ApiException(HttpStatus.CONFLICT,"Credit budget is exhausted or a previous charge is pending.");
        if (state.attempts().contains(fingerprint(new Request(false,retailer,query,lat,lon,pin)))) throw new DuplicateLookup();
        return state.balance();
    }
    private static final ThreadLocal<Boolean> PILOT_PERMIT=new ThreadLocal<>();

    @Autowired
    public QuickCommerceProvider(@Value("${QUICKCOMMERCE_API_KEY:}") String key,
                                 @Value("${smartcart.quickcommerce.enabled:false}") boolean enabled,
                                 @Value("${smartcart.quickcommerce.verified-start-balance:0}") int verifiedBalance,
                                 @Value("${smartcart.quickcommerce.report-key-presence:false}") boolean reportKeyPresence,
                                 RetailerDiagnostics diagnostics,OfferFreshness freshness,
                                 @Value("${QUICKCOMMERCE_PILOT_ENABLED:false}") boolean controlledMode,
                                 @Value("${QUICKCOMMERCE_LEGACY_LOOKUPS_ENABLED:false}") boolean legacyMode) {
        this.key = key;
        this.enabled = enabled;
        this.diagnostics=diagnostics;
        this.freshness=freshness;
        this.controlledMode=controlledMode||!legacyMode;
        this.budget = new QuickCommerceBudget(Path.of(System.getProperty("user.home"), ".cartiq", "quickcommerce-budget.txt"), verifiedBalance);
        this.transport = transport();
        this.client = RestClient.builder().baseUrl("https://api.quickcommerceapi.com")
            .requestFactory(new HttpComponentsClientHttpRequestFactory(transport)).build();
        if (reportKeyPresence) System.out.println("QUICKCOMMERCE_API_KEY: " + (key.isBlank() ? "MISSING" : "PRESENT"));
    }

    QuickCommerceProvider(RestClient client, String key, boolean enabled, QuickCommerceBudget budget) {
        this.client = client;
        this.transport = null;
        this.key = key;
        this.enabled = enabled;
        this.budget = budget;
        this.diagnostics=new RetailerDiagnostics();
        this.freshness=new OfferFreshness(900);
        this.controlledMode=false;
    }

    private static CloseableHttpClient transport() {
        var manager = PoolingHttpClientConnectionManagerBuilder.create()
            .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(Timeout.ofSeconds(3))
                .setSocketTimeout(Timeout.ofSeconds(10)).build()).build();
        return HttpClients.custom().setConnectionManager(manager)
            .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.ofSeconds(10)).build())
            .disableAutomaticRetries().disableRedirectHandling().disableCookieManagement().build();
    }

    @PreDestroy
    void closeTransport() throws IOException {
        comparisonWorkers.shutdown();
        if (transport != null) transport.close();
    }

    public record Product(String nativeId, String title, String brand, String pack, String currency,
                          Long priceMinor, Long mrpMinor, Boolean available, Integer inventory,
                          String storeId, String providerFetchedAt, ProductMatching.Pack normalizedPack,
                          BigDecimal discountPercent, String imageUrl, String productUrl,
                          String gtin, String barcode, String flavour, String formulation, String category, String deliveryEstimate,String variant) {
        public Product(String nativeId,String title,String brand,String pack,String currency,Long priceMinor,Long mrpMinor,
                       Boolean available,Integer inventory,String storeId,String providerFetchedAt,ProductMatching.Pack normalizedPack,
                       BigDecimal discountPercent,String imageUrl,String productUrl,String gtin,String barcode,String flavour,String formulation,String category) {
            this(nativeId,title,brand,pack,currency,priceMinor,mrpMinor,available,inventory,storeId,providerFetchedAt,normalizedPack,
                discountPercent,imageUrl,productUrl,gtin,barcode,flavour,formulation,category,null,null);
        }
        @JsonProperty public String normalizedTitle() { return ProductMatching.normalized(title); }
        @JsonProperty public String normalizedBrand() { return brand==null ? null : ProductMatching.normalized(brand); }
    }
    public record Location(double latitude, double longitude, String pincode, String responsePincode) {}
    public record Result(String retailer, Integer creditsRemaining, int upstreamStatus, Location location, List<Product> products,
                         Instant fetchedAt, String requestId,int freshnessSeconds) {
        public Result(String retailer,int creditsRemaining,int upstreamStatus,Location location,List<Product> products,Instant fetchedAt,String requestId){
            this(retailer,creditsRemaining,upstreamStatus,location,products,fetchedAt,requestId,900);
        }
        @JsonProperty public String source() { return "QUICKCOMMERCEAPI"; }
        @JsonProperty public String retention() { return "TRANSIENT"; }
        @JsonProperty public Instant freshUntil() { return fetchedAt.plusSeconds(freshnessSeconds); }
    }
    public record CreditBalance(int available, int upstreamStatus) {}
    public enum Outcome { SUCCESS, NO_RESULTS, UNAVAILABLE, PROVIDER_ERROR, TIMEOUT, NOT_SUPPORTED, NOT_ATTEMPTED }
    public static final class LookupFailure extends ApiException {
        public final Outcome outcome;
        public final boolean confirmedNoCharge;
        public final String providerRequestId;
        public final Integer upstreamStatus;
        public final RetailerDiagnostics.Category category;

        LookupFailure(HttpStatus status, String message, Outcome outcome, boolean confirmedNoCharge) {
            this(status,message,outcome,confirmedNoCharge,null,null);
        }
        LookupFailure(HttpStatus status,String message,Outcome outcome,boolean confirmedNoCharge,String upstreamId,Integer upstreamStatus){
            this(status,message,outcome,confirmedNoCharge,upstreamId,upstreamStatus,classify(outcome,upstreamStatus));
        }
        LookupFailure(HttpStatus status,String message,Outcome outcome,boolean confirmedNoCharge,String upstreamId,Integer upstreamStatus,
                      RetailerDiagnostics.Category category){
            super(status, message);
            this.outcome = outcome;
            this.confirmedNoCharge = confirmedNoCharge;
            this.providerRequestId=upstreamId;
            this.upstreamStatus=upstreamStatus;
            this.category=category;
        }
    }
    private static RetailerDiagnostics.Category classify(Outcome outcome,Integer status) {
        if(status!=null)return switch(status){
            case 401,402,403->RetailerDiagnostics.Category.AUTH_OR_ACCOUNT;
            case 404->RetailerDiagnostics.Category.UNAVAILABLE_AREA_OR_ITEM;
            case 422->RetailerDiagnostics.Category.UNSUPPORTED;
            case 429->RetailerDiagnostics.Category.RATE_LIMIT;
            case 502->RetailerDiagnostics.Category.RETAILER_UPSTREAM;
            case 504->RetailerDiagnostics.Category.TIMEOUT;
            default->RetailerDiagnostics.Category.PROVIDER_FAILURE;
        };
        return outcome==Outcome.TIMEOUT?RetailerDiagnostics.Category.TIMEOUT:RetailerDiagnostics.Category.ADAPTER_RESPONSE;
    }
    private record Request(boolean item, String retailer, String value, double lat, double lon, String pin) {}

    public static final class DuplicateLookup extends ApiException {
        DuplicateLookup() { super(HttpStatus.CONFLICT, "This lookup was already attempted in this experiment."); }
    }

    public Result search(String retailer, String query, double lat, double lon, String pin) {
        return request(false, retailer, query, lat, lon, pin);
    }

    public Result item(String retailer, String itemId, double lat, double lon, String pin) {
        return request(true, retailer, itemId, lat, lon, pin);
    }

    private Result request(boolean item, String retailer, String value, double lat, double lon, String pin) {
        if (retailer == null || !PLATFORMS.containsKey(retailer) || value == null || value.isBlank()
            || value.length() > 150 || value.chars().anyMatch(Character::isISOControl)
            || !Double.isFinite(lat) || Math.abs(lat) > 90 || !Double.isFinite(lon) || Math.abs(lon) > 180
            || (pin != null && !pin.matches("[1-9][0-9]{5}"))) {
            throw ApiException.bad("Provide a supported retailer, valid lookup and coordinates, and an optional Indian PIN.");
        }
        return execute(new Request(item, retailer, value.strip(), lat == 0 ? 0 : lat, lon == 0 ? 0 : lon, pin));
    }

    private Result execute(Request input) {
        long start=System.nanoTime();
        String operation=input.item()?"item":"search";
        boolean[] dispatched={false};
        try {
            requireEnabled();
            if(controlledMode) {
                if(!Boolean.TRUE.equals(PILOT_PERMIT.get()))
                    throw new ApiException(HttpStatus.FORBIDDEN,"This lookup requires a one-use controlled pilot authorization.");
                PILOT_PERMIT.set(false);
            }
            var result=budget.spend(fingerprint(input),expectedBalance->{dispatched[0]=true;return fetch(input,expectedBalance);});
            logLookup(RetailerDiagnostics.outcome(MDC.get("requestId"),input.retailer(),operation,result,null,true),start);
            return result;
        } catch(ApiException failure) {
            logLookup(RetailerDiagnostics.outcome(MDC.get("requestId"),input.retailer(),operation,null,failure,dispatched[0]),start);
            throw failure;
        }
    }
    public static <T> T withPilotPermit(java.util.function.Supplier<T> lookup) {
        if(PILOT_PERMIT.get()!=null)throw new IllegalStateException("Pilot authorizations cannot be nested.");
        PILOT_PERMIT.set(true);
        try{return lookup.get();}finally{PILOT_PERMIT.remove();}
    }

    private void logLookup(RetailerDiagnostics.Event event,long start){
        diagnostics.add(event);
        LOG.info("retailer_lookup requestId={} retailer={} operation={} outcome={} durationMs={} providerRequestId={} upstreamStatus={} failedPlatform={} category={} billing={}",
            event.requestId(),event.retailer(),event.operation(),event.outcome(),TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start),
            event.providerRequestId(),event.upstreamStatus(),event.failedPlatform(),event.category(),event.billing());
    }

    private void requireEnabled() {
        if (!configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "QuickCommerce development access is disabled or missing its API key.");
        }
    }

    public boolean configured(){return enabled&&!key.isBlank();}

    /** The documented account preflight is free of retailer charges and makes no ledger writes. */
    public CreditBalance creditBalance() {
        requireEnabled();

        try {
            var response = client.get().uri("/v1/credits").header("X-API-Key", key).retrieve().toEntity(JsonNode.class);
            var body = response.getBody();
            var amount = body == null ? null : body.path("summary").path("total_available");
            if (!response.getStatusCode().is2xxSuccessful() || amount == null || !amount.isIntegralNumber()
                || !amount.canConvertToInt() || amount.intValue() < 0) {
                throw invalidResponse("account balance", response.getStatusCode().value());
            }
            var header = response.getHeaders().get("X-Credits-Remaining");
            if (header != null && !List.of(Integer.toString(amount.intValue())).equals(header)) {
                throw invalidResponse("account balance header verification", response.getStatusCode().value());
            }
            return new CreditBalance(amount.intValue(), response.getStatusCode().value());
        } catch (RestClientResponseException failure) {
            throw invalidResponse("account balance", failure.getStatusCode().value());
        } catch (RestClientException failure) {
            throw invalidResponse("account balance transport", null);
        }
    }

    public record ComparisonLeg(String retailer,String platform,boolean duplicate,
                                @com.fasterxml.jackson.annotation.JsonIgnore String reservationKey) {}
    public record ComparisonQuote(int balance,int allowance,List<ComparisonLeg> legs,String blocker,Instant checkedAt) {}
    public record ComparisonRow(String retailer,Result result,Outcome outcome,String message,String providerRequestId,Integer upstreamStatus) {}
    public record ComparisonBilling(boolean settled,Integer balance) {}

    public ComparisonQuote quoteComparison(String term,double lat,double lon,String pin,List<String> retailers) {
        if(retailers==null || retailers.isEmpty() || retailers.size()>RETAILERS.size() ||
            retailers.stream().anyMatch(id->id==null||!RETAILERS.contains(id)) ||
            retailers.stream().distinct().count()!=retailers.size()) throw ApiException.bad("Select one to three distinct supported retailers.");
        var supported = shoppingPlatforms();
        var state = budget.inspect();
        int verified = creditBalance().available();
        var legs = retailers.stream().map(id -> {
            String base=fingerprint(new Request(false,id,term,lat,lon,pin));
            String next=base;
            // Every refresh has exactly one successor to the last settled reservation.
            // Concurrent or replayed previews therefore contend for the same ledger entry.
            while(state.attempts().contains(next)){
                String prefix="evidence\t"+next+"\t";
                var receipt=state.evidence().stream().filter(line->line.startsWith(prefix)
                    && !line.endsWith("\tbatch-awaiting-reconciliation")).findFirst();
                if(receipt.isEmpty()){ next=null; break; }
                next=digest("comparison-refresh\n"+receipt.get());
            }
            return new ComparisonLeg(id,PLATFORMS.get(id),state.attempts().contains(base),next);
        }).toList();
        String blocker = null;
        if (!supported.containsAll(retailers)) blocker = "A selected retailer is absent from the provider search platform list.";
        else if (state.pending() || state.balance() != verified) blocker = "The credit ledger needs reconciliation.";
        else if (state.allowance() < legs.size()) blocker = state.allowance() == 0 ? "No requests remain in the current allowance." : "Select fewer retailers to fit the remaining allowance.";
        else if (legs.stream().anyMatch(leg->leg.reservationKey()==null)) blocker = "The previous search has no individual settlement receipt. Check its billing before requesting it again.";
        return new ComparisonQuote(verified,state.allowance(),legs,blocker,Instant.now());
    }

    private final java.util.concurrent.ExecutorService comparisonWorkers = java.util.concurrent.Executors.newFixedThreadPool(3);

    public ComparisonBilling compareConfirmed(String term,double lat,double lon,String pin,List<String> retailers,ComparisonQuote approved,
                                               java.util.function.Consumer<ComparisonRow> publish) {
        var quote = quoteComparison(term,lat,lon,pin,retailers);
        if (quote.blocker()!=null || quote.balance()!=approved.balance() || !quote.legs().equals(approved.legs()))
            throw new ApiException(HttpStatus.CONFLICT,"Comparison eligibility or balance changed. Prepare a new preview.");
        var finalBalance = new java.util.concurrent.atomic.AtomicReference<Integer>();
        var legs=quote.legs();
        boolean settled = budget.spendComparison(legs.stream().map(ComparisonLeg::reservationKey).toList(),
            approved.balance(), start -> {
                var tasks = legs.stream().map(leg -> java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                    var input=new Request(false,leg.retailer(),term,lat,lon,pin);
                    try {
                        var result = fetch(input,start-legs.size(),start);
                        publish.accept(new ComparisonRow(input.retailer(),result,result.products().isEmpty()?Outcome.NO_RESULTS:Outcome.SUCCESS,
                            null,result.requestId(),result.upstreamStatus()));
                        return new QuickCommerceBudget.Debit(leg.reservationKey(),result.requestId(),result.creditsRemaining()==null?-1:1);
                    } catch (LookupFailure failure) {
                        publish.accept(new ComparisonRow(input.retailer(),null,failure.outcome,failure.getMessage(),
                            failure.providerRequestId,failure.upstreamStatus));
                        return new QuickCommerceBudget.Debit(leg.reservationKey(),failure.providerRequestId,failure.confirmedNoCharge?0:-1);
                    } catch (RuntimeException failure) {
                        publish.accept(new ComparisonRow(input.retailer(),null,Outcome.PROVIDER_ERROR,
                            "Retailer response could not be verified.",null,null));
                        return new QuickCommerceBudget.Debit(leg.reservationKey(),null,-1);
                    }
                },comparisonWorkers)).toList();
                var receipts = tasks.stream().map(java.util.concurrent.CompletableFuture::join).toList();
                try { finalBalance.set(creditBalance().available()); }
                catch (ApiException unavailable) { return new QuickCommerceBudget.Settlement(-1,receipts); }
                return new QuickCommerceBudget.Settlement(finalBalance.get(),receipts);
            });
        return new ComparisonBilling(settled,finalBalance.get());
    }

    private static String fingerprint(Request input) {
        String lookup = input.item() ? input.value() : normalizeQuery(input.value());
        String scope = String.join("\n", input.item() ? "item" : "search", input.retailer(), lookup,
            Double.toString(input.lat()), Double.toString(input.lon()), input.pin() == null ? "" : input.pin());
        return digest(scope);
    }
    private static String digest(String scope){
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Missing SHA-256 implementation", impossible);
        }
    }

    private static String normalizeQuery(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT).replaceAll("(?U)\\s+", " ");
    }

    private Result fetch(Request input, int expectedBalance) {
        return fetch(input, expectedBalance, null);
    }

    private Result fetch(Request input, int expectedBalance, Integer batchStart) {
        String platform = PLATFORMS.get(input.retailer());
        ResponseEntity<byte[]> response;
        try {
            response = client.get().uri(builder -> {
                var uri = builder.path(input.item() ? "/v1/item" : "/v1/search")
                    .queryParam(input.item() ? "item_id" : "q", "{lookup}")
                    .queryParam("platform", platform).queryParam("lat", input.lat()).queryParam("lon", input.lon());
                if (input.pin() != null) uri.queryParam("pincode", input.pin());
                return uri.build(input.value());
            }).header("X-API-Key", key).retrieve().toEntity(byte[].class);
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            var headers = failure.getResponseHeaders();
            boolean noCharge = batchStart == null && headers != null &&
                List.of(Integer.toString(expectedBalance + 1)).equals(headers.get("X-Credits-Remaining"));
            String upstreamId=safeRequestId(headers==null?null:headers.getFirst("X-Request-Id"));
            if (status == 404) throw new LookupFailure(HttpStatus.NOT_FOUND,
                "QuickCommerce returned HTTP 404. Item or retailer location was unavailable.", Outcome.UNAVAILABLE, noCharge,upstreamId,status);
            if (status == 422) throw new LookupFailure(HttpStatus.BAD_GATEWAY,
                "QuickCommerce returned HTTP 422. The provider did not support this lookup.", Outcome.NOT_SUPPORTED, noCharge,upstreamId,status);
            if (status == 504) throw new LookupFailure(HttpStatus.GATEWAY_TIMEOUT, "QuickCommerce returned HTTP 504.",
                Outcome.TIMEOUT, noCharge,upstreamId,status);
            if (status == 502 || status == 500) throw new LookupFailure(HttpStatus.BAD_GATEWAY,
                "QuickCommerce returned HTTP " + status + ".", Outcome.PROVIDER_ERROR, noCharge,upstreamId,status);
            if (status == 401 || status == 402 || status == 403 || status == 429) {
                throw new LookupFailure(HttpStatus.SERVICE_UNAVAILABLE, "QuickCommerce returned HTTP " + status + ". Credentials, credits or rate limit require review.",
                    Outcome.PROVIDER_ERROR,noCharge,upstreamId,status);
            }
            throw new LookupFailure(HttpStatus.BAD_GATEWAY, "QuickCommerce returned HTTP " + status + ".",Outcome.PROVIDER_ERROR,noCharge,upstreamId,status);
        } catch (ResourceAccessException failure) {
            if (failure.contains(java.net.SocketTimeoutException.class)) {
                throw new LookupFailure(HttpStatus.GATEWAY_TIMEOUT, "QuickCommerce lookup timed out.", Outcome.TIMEOUT, false);
            }
            throw invalidResponse("transport", null);
        } catch (RestClientException failure) {
            throw invalidResponse("response transfer", null);
        }
        int upstreamStatus = response.getStatusCode().value();
        if (!response.getStatusCode().is2xxSuccessful() || !response.hasBody()) {
            throw invalidResponse("HTTP response", upstreamStatus);
        }
        JsonNode body;
        try { body = JSON.readTree(response.getBody()); }
        catch (IOException invalid) { throw invalidResponse("JSON decoding", upstreamStatus,safeRequestId(response.getHeaders().getFirst("X-Request-Id"))); }
        if (body == null || !body.isObject() || !"success".equals(body.path("status").textValue())) {
            String id=body!=null&&body.isObject()?requestId(body,response):safeRequestId(response.getHeaders().getFirst("X-Request-Id"));
            throw invalidResponse("success envelope", upstreamStatus,id);
        }
        String correlationId = requestId(body, response);
        var data = body.path("data");
        String pin = identifier(data.path("pincode"));
        String query = text(data, "query");
        var failures = data.path("failed_platforms");
        if (!platformMatches(platform,data.path("platform").asText()) || !coordinate(data, "lat", input.lat())
            || !coordinate(data, "lon", input.lon())
            || (data.hasNonNull("pincode") && pin == null)
            || (data.hasNonNull("query") && query == null)
            || (pin != null && (!pin.matches("[1-9][0-9]{5}") || (input.pin() != null && !input.pin().equals(pin))))
            || (!input.item() && query != null && !normalizeQuery(input.value()).equals(normalizeQuery(query)))
            || (input.item() && !input.value().equals(identifier(data.path("item_id"))))) {
            throw invalidResponse("retailer or lookup scope verification", upstreamStatus,correlationId);
        }
        var records = data.path(input.item() ? "items" : "products");
        var creditField = body.path("credits_remaining");
        var creditHeaders = response.getHeaders().get("X-Credits-Remaining");
        Integer snapshot = expectedBalance;
        if (batchStart != null) {
            // Credit snapshots can arrive out of order. Keep a valid listing visible even
            // when billing evidence is absent; only the ledger can declare settlement.
            snapshot = null;
            for (int value=expectedBalance; value<=batchStart; value++) {
                boolean headerPresent=creditHeaders!=null, bodyPresent=!creditField.isMissingNode()&&!creditField.isNull();
                if ((headerPresent||bodyPresent) && (!headerPresent||List.of(Integer.toString(value)).equals(creditHeaders))
                    && (!bodyPresent||creditBodyMatches(creditField,value))) { snapshot=value; break; }
            }
            if (snapshot!=null && snapshot.equals(batchStart) && (failures.isMissingNode()||failures.isEmpty())) snapshot=null;
        }
        if (!failures.isMissingNode() && (!failures.isArray() || !failures.isEmpty())) {
            if (failures.isArray() && failures.size() == 1 && platformMatches(platform,failures.get(0).textValue())
                && records.isArray() && records.isEmpty()
                && (batchStart==null?balanceMatches(creditField, creditHeaders, expectedBalance + 1):snapshot!=null)) {
                LOG.info("QuickCommerce failed-platform response: requestId={}, upstreamStatus={}, platform={}",
                    correlationId, upstreamStatus, platform);
                throw new LookupFailure(HttpStatus.BAD_GATEWAY,
                    batchStart==null?"QuickCommerce reported a retailer failure without a charge (upstream HTTP " + upstreamStatus + ").":"The provider reported a failed retailer.",
                    Outcome.PROVIDER_ERROR, true,correlationId,upstreamStatus,RetailerDiagnostics.Category.PLATFORM_FAILED_UNSPECIFIED);
            }
            LOG.warn("QuickCommerce retailer failure could not be reconciled: requestId={}, upstreamStatus={}, "
                + "creditBodyKind={}, creditBodyMatches={}, creditHeaderPresent={}, creditHeaderMatches={}", correlationId,
                upstreamStatus, creditField.getNodeType(), creditBodyMatches(creditField, expectedBalance + 1),
                creditHeaders != null, List.of(Integer.toString(expectedBalance + 1)).equals(creditHeaders));
            boolean reportedFailure=failures.isArray()&&failures.size()==1&&platformMatches(platform,failures.get(0).textValue());
            throw new LookupFailure(HttpStatus.BAD_GATEWAY,"QuickCommerce retailer success verification failed (upstream HTTP "+upstreamStatus+").",
                Outcome.PROVIDER_ERROR,false,correlationId,upstreamStatus,reportedFailure?
                    RetailerDiagnostics.Category.PLATFORM_FAILED_UNSPECIFIED:RetailerDiagnostics.Category.ADAPTER_RESPONSE);
        }
        if (batchStart==null) verifyChargedBalance(creditField, creditHeaders, expectedBalance, upstreamStatus, correlationId);
        if (!records.isArray()) throw invalidResponse("listing collection", upstreamStatus,correlationId);
        var products = new ArrayList<Product>();
        var ids = new HashSet<String>();
        for (var record : records) {
            try {
                var product = product(record, input.item(), platform, upstreamStatus);
                if (!ids.add(product.nativeId())) throw invalidResponse("duplicate listing identity", upstreamStatus);
                products.add(product);
            }catch(LookupFailure invalid){
                throw new LookupFailure(invalid.status,invalid.getMessage(),invalid.outcome,false,correlationId,upstreamStatus,
                    RetailerDiagnostics.Category.ADAPTER_RESPONSE);
            }
        }
        return new Result(input.retailer(), snapshot, upstreamStatus,
            new Location(input.lat(), input.lon(), input.pin(), pin), List.copyOf(products), Instant.now(),
            correlationId,freshness.seconds());
    }

    private static void verifyChargedBalance(JsonNode body, List<String> header, int expected, int upstreamStatus,
                                              String requestId) {
        boolean headerMatches = List.of(Integer.toString(expected)).equals(header);
        boolean absent = body.isMissingNode() || body.isNull();
        boolean bodyMatches = creditBodyMatches(body, expected);
        if ((!absent && !bodyMatches) || !headerMatches) {
            LOG.warn("QuickCommerce credit evidence rejected: requestId={}, upstreamStatus={}, creditBodyKind={}, "
                + "creditBodyMatches={}, creditHeaderPresent={}, creditHeaderMatches={}",
                requestId, upstreamStatus, body.getNodeType(), bodyMatches, header != null, headerMatches);
            throw invalidResponse(!absent && !bodyMatches ? "balance verification" : "balance header verification",
                upstreamStatus,requestId);
        }
    }

    private static boolean balanceMatches(JsonNode body, List<String> header, int expected) {
        return List.of(Integer.toString(expected)).equals(header) && creditBodyMatches(body, expected);
    }

    private static boolean creditBodyMatches(JsonNode body, int expected) {
        return (body.isIntegralNumber() && body.canConvertToInt() && body.intValue() == expected)
            || (body.isTextual() && Integer.toString(expected).equals(body.textValue()));
    }

    private static String requestId(JsonNode body, ResponseEntity<byte[]> response) {
        String fromBody = text(body, "request_id");
        String fromHeader = response.getHeaders().getFirst("X-Request-Id");
        if (fromBody != null && fromHeader != null && !fromBody.equalsIgnoreCase(fromHeader)) return null;
        return safeRequestId(fromBody == null ? fromHeader : fromBody);
    }
    private static String safeRequestId(String value){
        return value != null && value.matches("(?i)[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}") ? value.toLowerCase(Locale.ROOT) : null;
    }
    private static boolean platformMatches(String expected,String actual) {
        return expected.equalsIgnoreCase(actual)||"Swiggy".equals(expected)&&"Swiggy Instamart".equalsIgnoreCase(actual);
    }

    private static boolean coordinate(JsonNode data, String field, double expected) {
        var node = data.path(field);
        return node.isNumber() && Math.abs(node.doubleValue() - expected) < 0.000001;
    }

    private static Product product(JsonNode row, boolean item, String platform, int upstreamStatus) {
        String id = identifier(row.path(item ? "item_id" : "id"));
        String title = text(row, "name");
        var source = row.path("platform");
        var platformField = source.isObject() ? source.path("name") : source;
        String reportedPlatform = text(platformField);
        if (id == null || id.length() > 150 || id.chars().anyMatch(Character::isISOControl) || title == null
            || (!platformField.isNull() && !platformField.isMissingNode() && reportedPlatform == null)
            || (reportedPlatform != null && !platformMatches(platform,reportedPlatform))) {
            throw invalidResponse("listing identity verification", upstreamStatus);
        }
        String currency = text(row, "currency");
        // The requested platforms use INR. Do not reinterpret a listing with an explicit currency conflict.
        if (row.hasNonNull("currency") && !"INR".equalsIgnoreCase(currency)) {
            throw invalidResponse("listing currency verification", upstreamStatus);
        }
        var availability = row.path("available");
        var inventory = row.path("inventory");
        String pack = text(row, "quantity");
        String store = identifier(row.path("store_id"));
        if (store == null) store = identifier(source.path("storeId"));
        return new Product(id, title, text(row, "brand"), pack, "INR",
            money(row, item ? "price" : "offer_price", upstreamStatus), money(row, "mrp", upstreamStatus),
            availability.isBoolean() ? availability.booleanValue() : null,
            inventory.isIntegralNumber() && inventory.canConvertToInt() && inventory.intValue() >= 0 ? inventory.intValue() : null,
            store, text(row, "fetched_at"), ProductMatching.pack(pack),
            discountPercent(row.path("discount_percent")), image(row, item),
            safeUrl(text(row, "deeplink"), RETAILER_HOSTS.get(platform)),
            barcodeField(row.path("gtin")), barcodeField(row.path("barcode")),
            text(row, "flavour"), text(row, "formulation"), text(row, "category"), text(source, "sla"), text(row, "variant"));
    }

    private static String barcodeField(JsonNode node) {
        String value = identifier(node);
        return value != null && value.length() <= 20 && value.chars().noneMatch(Character::isISOControl) ? value : null;
    }

    private static BigDecimal discountPercent(JsonNode value) {
        if (!value.isNumber()) return null;
        var percent = value.decimalValue();
        return percent.signum() >= 0 && percent.compareTo(BigDecimal.valueOf(100)) <= 0 ? percent : null;
    }

    private static String image(JsonNode row, boolean item) {
        for (var candidate : row.path(item ? "gallery" : "images")) {
            var url = item && "image".equals(candidate.path("type").asText()) ? candidate.path("url") : candidate;
            if (url.isTextual()) {
                String valid = safeUrl(url.textValue(), null);
                if (valid != null) return valid;
            }
        }
        return null;
    }

    private static String safeUrl(String value, Set<String> allowedHosts) {
        if (value == null || value.length() > 1500) return null;
        try {
            var uri = URI.create(value);
            if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443)
                && (allowedHosts == null || allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT)))) return value;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
        return null;
    }

    private static String text(JsonNode row, String field) {
        return text(row.path(field));
    }

    private static String text(JsonNode value) {
        return value.isTextual() && !value.textValue().isBlank() ? value.textValue() : null;
    }

    private static String identifier(JsonNode value) {
        if (value.isIntegralNumber() && value.bigIntegerValue().signum() >= 0) return value.asText();
        return text(value);
    }

    private static Long money(JsonNode row, String field, int upstreamStatus) {
        var value = row.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isNumber()) throw invalidResponse("listing price verification", upstreamStatus);
        BigDecimal amount = value.decimalValue();
        if (amount.signum() < 0) throw invalidResponse("listing price verification", upstreamStatus);
        try { return amount.movePointRight(2).longValueExact(); }
        catch (ArithmeticException invalid) { throw invalidResponse("listing price verification", upstreamStatus); }
    }

    private static ApiException invalidResponse(String stage, Integer upstreamStatus) {
        return invalidResponse(stage,upstreamStatus,null);
    }
    private static LookupFailure invalidResponse(String stage,Integer upstreamStatus,String requestId) {
        String status = upstreamStatus == null ? "" : " (upstream HTTP " + upstreamStatus + ")";
        return new LookupFailure(HttpStatus.BAD_GATEWAY,"QuickCommerce " + stage + " failed" + status + ".",Outcome.PROVIDER_ERROR,
            false,requestId,upstreamStatus,RetailerDiagnostics.Category.ADAPTER_RESPONSE);
    }
}
