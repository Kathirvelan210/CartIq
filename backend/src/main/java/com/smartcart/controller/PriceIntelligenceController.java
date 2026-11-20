package com.smartcart.controller;

import com.smartcart.grocery.PricePrediction;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!mysql-local")
@RequestMapping("/api")
public class PriceIntelligenceController {
    private final PricePrediction engine;

    public PriceIntelligenceController(PricePrediction engine) { this.engine=engine; }

    @GetMapping("/me/price-intelligence/{variant}")
    public ResponseEntity<PricePrediction.Prediction> prediction(@AuthenticationPrincipal Jwt jwt,@PathVariable String variant,
        @RequestParam String retailer,@RequestParam String locationId) {
        return privateResult(engine.prediction(jwt.getSubject(),new PricePrediction.Scope(variant,retailer,locationId)));
    }

    @GetMapping("/admin/price-intelligence/coverage")
    public ResponseEntity<PricePrediction.Coverage> coverage() { return privateResult(engine.coverage()); }

    @PostMapping("/admin/price-intelligence/experiment")
    public ResponseEntity<PricePrediction.Experiment> train(@RequestParam String variantId,@RequestParam String retailer,@RequestParam String locationId) {
        return privateResult(engine.train(new PricePrediction.Scope(variantId,retailer,locationId)));
    }

    @PostMapping("/admin/price-intelligence/forecast-permissions/{grantId}")
    public ResponseEntity<Void> approval(@AuthenticationPrincipal Jwt reviewer,@PathVariable String grantId,@Valid @RequestBody PricePrediction.Approval approval) {
        engine.approve(reviewer.getSubject(),grantId,approval);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping("/admin/price-intelligence/forecast-permissions/{grantId}")
    public ResponseEntity<Void> revoke(@PathVariable String grantId) {
        engine.revoke(grantId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static <T> ResponseEntity<T> privateResult(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
