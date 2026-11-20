package com.smartcart.controller;

import com.smartcart.service.*;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!mysql-local")
@RequestMapping("/api/admin/retailer-operations")
public class RetailerOperationsController {
    private final RetailerCoverage coverage;
    public RetailerOperationsController(RetailerCoverage coverage) { this.coverage=coverage; }

    @GetMapping("/filters") public ResponseEntity<RetailerCoverage.Filters> filters(){
        return result(coverage.filters());
    }
    @GetMapping("/coverage") public ResponseEntity<RetailerCoverage.Coverage> coverage(
        @AuthenticationPrincipal Jwt jwt,@RequestParam @NotBlank @Size(max=60) String locationId,
        @RequestParam(required=false) String retailer,@RequestParam(required=false) @Size(max=50) String category,
        @RequestParam(required=false) RetailerCoverage.Status status,@RequestParam(defaultValue="0") @Min(0) @Max(10000) int page,
        @RequestHeader(name=LiveOffers.POSITION_HEADER,required=false) String position) {
        return result(coverage.report(locationId,retailer,category,status,page,jwt.getSubject(),position));
    }
    private static <T> ResponseEntity<T> result(T values) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(values);
    }
}
