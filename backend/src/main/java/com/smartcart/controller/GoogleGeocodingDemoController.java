package com.smartcart.controller;

import com.smartcart.exception.ApiException;
import com.smartcart.integration.ShoppingGeocoder;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("(local | test) & !prod")
@RequestMapping("/api/admin/geocoder-demo")
class GoogleGeocodingDemoController {
    private final ShoppingGeocoder geocoder;

    GoogleGeocodingDemoController(ShoppingGeocoder geocoder) {
        this.geocoder = geocoder;
    }

    @PostMapping("/pin")
    ResponseEntity<List<ShoppingGeocoder.Place>> pin(@RequestBody ShoppingLocationController.PinRequest request,
        @RequestHeader(value = ShoppingLocationController.CONSENT_HEADER, required = false) String consent) {
        requireDemo(consent);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(geocoder.pin(request.postcode(), request.locality()));
    }

    @PostMapping("/reverse")
    ResponseEntity<ShoppingGeocoder.Place> reverse(@Valid @RequestBody ShoppingLocationController.ReverseRequest request,
        @RequestHeader(value = ShoppingLocationController.CONSENT_HEADER, required = false) String consent) {
        requireDemo(consent);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(geocoder.reverse(request.latitude(), request.longitude()));
    }

    private void requireDemo(String consent) {
        if (!geocoder.googleDemo()) throw ApiException.missing();
        ShoppingLocationController.requireConsent(consent);
    }
}
