package com.smartcart.controller;

import com.smartcart.grocery.HistoricalPriceIntake;
import com.smartcart.grocery.PriceAcquisitionCoverage;
import com.smartcart.grocery.PricePrediction;
import com.smartcart.exception.ApiException;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile("!mysql-local")
@RequestMapping("/api/admin/price-acquisition")
public class PriceAcquisitionController {
    private final HistoricalPriceIntake intake;
    private final PriceAcquisitionCoverage coverage;

    public PriceAcquisitionController(HistoricalPriceIntake intake,PriceAcquisitionCoverage coverage) {
        this.intake=intake;this.coverage=coverage;
    }

    @GetMapping("/sources") public ResponseEntity<List<Map<String,Object>>> sources() { return result(intake.registry()); }
    @PutMapping("/sources/{grantId}") public ResponseEntity<Void> approve(@AuthenticationPrincipal Jwt jwt,@PathVariable String grantId,
        @Valid @RequestBody HistoricalPriceIntake.Authorization authorization) {
        intake.authorize(jwt.getSubject(),grantId,authorization);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @DeleteMapping("/sources/{grantId}") public ResponseEntity<Void> revoke(@PathVariable String grantId) {
        intake.revoke(grantId);return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/batches") public ResponseEntity<List<Map<String,Object>>> batches() { return result(intake.batches()); }
    @GetMapping("/batches/{batch}") public ResponseEntity<List<HistoricalPriceIntake.Decision>> decisions(@PathVariable String batch) {
        return result(intake.decisions(batch));
    }
    @PostMapping(value="/batches",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<HistoricalPriceIntake.Report> upload(@AuthenticationPrincipal Jwt jwt,@RequestParam String grantId,
        @RequestParam String format,@RequestParam(defaultValue="false") boolean commit,@RequestParam(defaultValue="false") boolean reviewed,
        @RequestParam(required=false) String evidenceReference,@RequestPart MultipartFile file) throws IOException {
        if(file.getSize()>524288) throw ApiException.bad("Submit a file of at most 512 KiB.");
        return result(intake.upload(jwt.getSubject(),grantId,file.getBytes(),format.toUpperCase(Locale.ROOT),commit,evidenceReference,reviewed));
    }
    @GetMapping("/coverage") public ResponseEntity<PriceAcquisitionCoverage.Overview> overview() { return result(coverage.overview()); }
    @GetMapping("/coverage/series") public ResponseEntity<PriceAcquisitionCoverage.ExactSeries> series(@RequestParam String variantId,
        @RequestParam String retailer,@RequestParam String locationId) {
        return result(coverage.series(new PricePrediction.Scope(variantId,retailer,locationId)));
    }
    private static <T> ResponseEntity<T> result(T data) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(data); }
}
