package com.smartcart.controller;
import com.smartcart.catalogimport.ProductMatching;
import com.smartcart.integration.ProviderSyncService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/api/admin/provider")
public class ProviderController {
    private final ProviderSyncService sync;
    public ProviderController(ProviderSyncService sync){this.sync=sync;}
    public record Mapping(@NotBlank String variantId,@NotBlank @Pattern(regexp="blinkit|instamart|zepto") String platform,
                          @NotBlank @Size(max=150) String retailerSku, @Valid ProductMatching.Identity listingIdentity,
                          ProviderSyncService.MappingEvidence mappingEvidence) {}
    public record MappingReview(@NotBlank String variantId, @NotNull @Valid ProductMatching.Identity listingIdentity) {}
    public record ReviewPreview(String decisionId,ProviderSyncService.MappingReview assessment) {}
    public record MappingDecision(@NotBlank String variantId,
                                  @NotBlank @Pattern(regexp="blinkit|instamart|zepto") String platform,
                                  @NotBlank @Size(max=150) String retailerSku,
                                  @NotNull @Valid ProductMatching.Identity listingIdentity,
                                  @NotNull ProviderSyncService.MappingEvidence mappingEvidence,
                                  @NotNull ProviderSyncService.ReviewDisposition disposition) {}
    @PostMapping("/mapping/review") public ProviderSyncService.MappingReview review(@Valid @RequestBody MappingReview request) {
        return sync.reviewMapping(request.variantId(),request.listingIdentity());
    }
    @PostMapping("/mapping/preview") public ReviewPreview preview(@Valid @RequestBody MappingReview request) {
        return new ReviewPreview(java.util.UUID.randomUUID().toString(),sync.reviewMapping(request.variantId(),request.listingIdentity()));
    }
    @PutMapping("/mapping") public Map<String,String> map(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Mapping mapping){
        sync.map(jwt.getSubject(),mapping.variantId(),mapping.platform(),mapping.retailerSku(),mapping.listingIdentity(),
            mapping.mappingEvidence());
        return Map.of("status","mapped");
    }
    @PostMapping("/mapping/decisions")
    public Map<String,Object> recordDecision(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody MappingDecision request) {
        return sync.recordReview(jwt.getSubject(),request.variantId(),request.platform(),request.retailerSku(),
            request.listingIdentity(),request.mappingEvidence(),request.disposition());
    }
    @GetMapping("/mapping/decisions")
    public List<Map<String,Object>> decisions(@RequestParam String variantId,@RequestParam String platform,
                                              @RequestParam(defaultValue="0") int afterRevision) {
        return sync.mappingDecisions(variantId,platform,afterRevision);
    }
    @PostMapping("/sync") public Map<String,Integer> sync(@RequestParam String locationId){return sync.sync(locationId);}
}
