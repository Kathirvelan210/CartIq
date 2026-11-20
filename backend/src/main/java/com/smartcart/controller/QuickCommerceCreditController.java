package com.smartcart.controller;

import com.smartcart.integration.QuickCommerceProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
public class QuickCommerceCreditController {
    private final QuickCommerceProvider provider;

    public QuickCommerceCreditController(QuickCommerceProvider provider) {
        this.provider = provider;
    }

    @GetMapping("/api/admin/providers/quickcommerce/credits")
    public ResponseEntity<QuickCommerceProvider.CreditBalance> credits() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(provider.creditBalance());
    }
}
