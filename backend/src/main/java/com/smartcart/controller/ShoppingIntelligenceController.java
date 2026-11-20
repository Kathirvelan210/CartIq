package com.smartcart.controller;

import com.smartcart.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!mysql-local")
@RequestMapping("/api/me/shopping-intelligence")
public class ShoppingIntelligenceController {
    public record Preferences(BulkBuying.Household household) {}
    public record PackLimits(BulkBuying.Limits limits) {}
    private final BulkBuying bulk;
    private final PersonalShopping personal;
    private final CatalogService catalogue;

    public ShoppingIntelligenceController(BulkBuying bulk,PersonalShopping personal,CatalogService catalogue) {
        this.bulk=bulk;this.personal=personal;this.catalogue=catalogue;
    }

    @GetMapping("/preferences") public ResponseEntity<Preferences> preferences(@AuthenticationPrincipal Jwt jwt) {
        return result(new Preferences(bulk.household(jwt.getSubject())));
    }
    @PutMapping("/preferences") public ResponseEntity<Preferences> preferences(@AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody BulkBuying.Household input) {
        return result(new Preferences(bulk.household(jwt.getSubject(),input)));
    }
    @GetMapping("/bulk/{variant}/limits") public ResponseEntity<PackLimits> limits(@AuthenticationPrincipal Jwt jwt,
        @PathVariable String variant,@RequestParam String locationId) {
        return result(new PackLimits(bulk.limits(jwt.getSubject(),catalogue.product(variant,locationId))));
    }
    @PutMapping("/bulk/{variant}/limits") public ResponseEntity<PackLimits> limits(@AuthenticationPrincipal Jwt jwt,
        @PathVariable String variant,@RequestParam String locationId,@Valid @RequestBody BulkBuying.Limits input) {
        return result(new PackLimits(bulk.limits(jwt.getSubject(),catalogue.product(variant,locationId),input)));
    }
    @GetMapping("/bulk/{variant}") public ResponseEntity<BulkBuying.Advice> bulk(@AuthenticationPrincipal Jwt jwt,
        @PathVariable String variant,@RequestParam String locationId,@RequestParam(required=false) @Min(1) @Max(365) Integer daysBeforeExpiry) {
        return result(bulk.advise(jwt.getSubject(),catalogue.product(variant,locationId),locationId,daysBeforeExpiry));
    }
    @GetMapping("/recommendations") public ResponseEntity<PersonalShopping.Recommendations> recommendations(@AuthenticationPrincipal Jwt jwt,
        @RequestParam String locationId,@RequestParam(required=false) @PositiveOrZero @Max(1_000_000_000L) Long budgetMinor) {
        return result(personal.recommend(jwt.getSubject(),locationId,budgetMinor));
    }
    @GetMapping("/basket") public ResponseEntity<PersonalShopping.Basket> basket(@AuthenticationPrincipal Jwt jwt,
        @RequestParam String locationId,@RequestParam(required=false) @PositiveOrZero @Max(1_000_000_000L) Long budgetMinor) {
        return result(personal.basket(jwt.getSubject(),locationId,budgetMinor));
    }
    private static <T> ResponseEntity<T> result(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }
}
