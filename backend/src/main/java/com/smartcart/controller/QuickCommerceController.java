package com.smartcart.controller;

import com.smartcart.dto.Models.ErrorBody;
import com.smartcart.integration.QuickCommerceProvider;
import com.smartcart.service.ComparisonService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@RequestMapping({"/api/admin/providers/quickcommerce", "/api/shopping/retailers"})
public class QuickCommerceController {
    private final QuickCommerceProvider provider;
    private final ComparisonService comparison;

    public QuickCommerceController(QuickCommerceProvider provider, ComparisonService comparison) {
        this.provider = provider;
        this.comparison = comparison;
    }

    @GetMapping("/search")
    public ResponseEntity<QuickCommerceProvider.Result> search(@RequestParam String retailer, @RequestParam String q,
             @RequestParam double lat, @RequestParam double lon, @RequestParam(required = false) String pincode) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(provider.search(retailer, q, lat, lon, pincode));
    }

    @GetMapping("/item")
    public ResponseEntity<QuickCommerceProvider.Result> item(@RequestParam String retailer, @RequestParam String itemId,
             @RequestParam double lat, @RequestParam double lon, @RequestParam(required = false) String pincode) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(provider.item(retailer, itemId, lat, lon, pincode));
    }

    public record CompareRequest(@NotBlank String q, @NotNull Double lat, @NotNull Double lon, String pincode,
                                 List<String> retailers) {}

    @PostMapping("/compare")
    public ResponseEntity<ComparisonService.RetailerSearch> compare(@Valid @RequestBody CompareRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(comparison.compareRetailerSearch(request.q(), request.lat(), request.lon(), request.pincode(),
                request.retailers()));
    }

    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<ErrorBody> serializationFailure() {
        // A converter exception can contain provider data. Do not forward or log its message or cause.
        return ResponseEntity.internalServerError().cacheControl(CacheControl.noStore())
            .body(new ErrorBody("INTERNAL", "QuickCommerce result could not be serialized."));
    }
}
