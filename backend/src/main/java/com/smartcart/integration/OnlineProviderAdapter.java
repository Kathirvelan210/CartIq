package com.smartcart.integration;

import java.util.List;
import java.util.Set;

/** Provider boundary for the three supported online grocery sources. */
public interface OnlineProviderAdapter {
    Set<String> SUPPORTED_RETAILERS = Set.of("blinkit", "instamart", "zepto");

    String retailer();
    List<GroceryDataProvider.Observation> observations(String sku, String pincode);
    default boolean supports(String value) { return retailer().equals(value) && SUPPORTED_RETAILERS.contains(value); }
}
