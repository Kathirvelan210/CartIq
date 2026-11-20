package com.smartcart.integration;
import java.time.Instant;
import java.util.List;
public interface GroceryDataProvider {
    record Observation(String retailer,String retailerSku,String pincode,Instant observedAt,String title,String brand,String packSizeRaw,Long priceMinor,Long mrpMinor,Boolean inStock,String sourceUrl) {}
    String name();
    List<Observation> observations(String retailer,String sku,String pin);
}
