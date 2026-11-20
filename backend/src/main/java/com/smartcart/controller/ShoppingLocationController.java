package com.smartcart.controller;

import com.smartcart.integration.ShoppingGeocoder;
import com.smartcart.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/location-lookup")
public class ShoppingLocationController {
    public static final String CONSENT_HEADER = "X-CartIQ-Geocoding-Consent";
    private static final String CONSENT_VERSION = "delivery-geocoding-v1";
    private final ShoppingGeocoder geocoder;

    public ShoppingLocationController(ShoppingGeocoder geocoder) {
        this.geocoder = geocoder;
    }

    public record PinRequest(String postcode, String locality) {}
    public record ReverseRequest(@NotNull Double latitude, @NotNull Double longitude) {}
    public record Options(boolean available, boolean deviceGpsAllowed, boolean googleDemo) {}

    @GetMapping("/options")
    public ResponseEntity<Options> options() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new Options(geocoder.available(), geocoder.deviceGpsAllowed(), geocoder.googleDemo()));
    }

    @PostMapping("/pin")
    public ResponseEntity<List<ShoppingGeocoder.Place>> pin(@RequestBody PinRequest request,
        @RequestHeader(value = CONSENT_HEADER, required = false) String consent) {
        requireConsent(consent);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(geocoder.pin(request.postcode(), request.locality()));
    }

    @PostMapping("/reverse")
    public ResponseEntity<ShoppingGeocoder.Place> reverse(@Valid @RequestBody ReverseRequest request,
        @RequestHeader(value = CONSENT_HEADER, required = false) String consent) {
        requireConsent(consent);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(geocoder.reverse(request.latitude(), request.longitude()));
    }

    @PostMapping("/pin/verify")
    public ResponseEntity<ShoppingGeocoder.Place> verifyPin(@RequestBody ShoppingGeocoder.Place candidate,
        @RequestHeader(value = CONSENT_HEADER, required = false) String consent) {
        requireConsent(consent);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(geocoder.reversePinPlace(candidate));
    }

    static void requireConsent(String consent) {
        if (!CONSENT_VERSION.equals(consent)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Allow delivery geocoding before looking up a location.");
        }
    }

}
