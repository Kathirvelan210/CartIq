package com.smartcart.integration;

import java.time.Instant;
import java.util.List;

/** Explicitly labelled fixture adapter used by tests and local development. */
public final class MockOnlineProviderAdapter implements OnlineProviderAdapter {
    private final String source;
    public MockOnlineProviderAdapter(String source) {
        if (!SUPPORTED_RETAILERS.contains(source)) throw new IllegalArgumentException("Unsupported retailer");
        this.source = source;
    }
    @Override public String retailer() { return source; }
    @Override public List<GroceryDataProvider.Observation> observations(String sku, String pincode) {
        return List.of(new GroceryDataProvider.Observation(source, sku, pincode, Instant.now(),
            "DEVELOPMENT FIXTURE", "DEVELOPMENT FIXTURE", "1 piece", null, null, null, null));
    }
}
