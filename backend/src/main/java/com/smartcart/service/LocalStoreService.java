package com.smartcart.service;

import com.smartcart.repository.Db;
import com.smartcart.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.smartcart.repository.Db.*;

@Service
public class LocalStoreService {
    private final Db db;
    public LocalStoreService(Db db) { this.db=db; }
    public record Store(String name,String address,String postcode,Double latitude,Double longitude,boolean pickupEnabled,boolean deliveryEnabled,Long deliveryMinor,boolean active) {}
    public record Listing(String variantId,Long priceMinor,Long mrpMinor,Integer stockQuantity) {}
    @Transactional public String create(Store store) {
        validate(store);
        String id=id();
        db.insert("local_stores",fields("id",id,"name",store.name(),"address",store.address(),"postcode",store.postcode(),"latitude",store.latitude(),"longitude",store.longitude(),"pickup_enabled",store.pickupEnabled(),"delivery_enabled",store.deliveryEnabled(),"delivery_minor",store.deliveryMinor(),"active",store.active()));
        return id;
    }
    @Transactional public void update(String id,Store store) {
        validate(store); db.one("SELECT id FROM local_stores WHERE id=?",id);
        db.run("UPDATE local_stores SET name=?,address=?,postcode=?,latitude=?,longitude=?,pickup_enabled=?,delivery_enabled=?,delivery_minor=?,active=?,updated_at=? WHERE id=?",store.name(),store.address(),store.postcode(),store.latitude(),store.longitude(),store.pickupEnabled(),store.deliveryEnabled(),store.deliveryMinor(),store.active(),Instant.now(),id);
    }
    private void validate(Store s) {
        if(s.name()==null||s.name().isBlank()||s.name().length()>120||s.address()==null||s.address().isBlank()||s.address().length()>250||s.postcode()==null||!s.postcode().matches("[1-9][0-9]{5}")||s.latitude()==null||!Double.isFinite(s.latitude())||Math.abs(s.latitude())>90||s.longitude()==null||!Double.isFinite(s.longitude())||Math.abs(s.longitude())>180||s.deliveryMinor()!=null&&s.deliveryMinor()<0) throw ApiException.bad("Check store details.");
    }
    @Transactional public void saveListing(String storeId,Listing listing) {
        if(listing.variantId()==null||listing.variantId().isBlank()||listing.priceMinor()==null||listing.priceMinor()<0||listing.stockQuantity()==null||listing.stockQuantity()<0||listing.mrpMinor()!=null&&listing.mrpMinor()<0)throw ApiException.bad("Check price and stock.");
        db.one("SELECT id FROM local_stores WHERE id=? FOR UPDATE",storeId); db.one("SELECT id FROM product_variants WHERE id=?",listing.variantId());
        Instant now=Instant.now();
        if(db.count("SELECT COUNT(*) FROM local_store_listings WHERE store_id=? AND variant_id=?",storeId,listing.variantId())==0) db.insert("local_store_listings",fields("id",id(),"store_id",storeId,"variant_id",listing.variantId(),"price_minor",listing.priceMinor(),"mrp_minor",listing.mrpMinor(),"stock_quantity",listing.stockQuantity(),"observed_at",now));
        else db.run("UPDATE local_store_listings SET price_minor=?,mrp_minor=?,stock_quantity=?,observed_at=?,updated_at=? WHERE store_id=? AND variant_id=?",listing.priceMinor(),listing.mrpMinor(),listing.stockQuantity(),now,now,storeId,listing.variantId());
    }
    public List<Map<String,Object>> nearby(String postcode) {
        if(postcode==null||!postcode.matches("[1-9][0-9]{5}"))throw ApiException.bad("Enter a valid PIN code.");
        return db.rows("SELECT id,name,address,postcode,latitude,longitude,pickup_enabled,delivery_enabled,delivery_minor FROM local_stores WHERE postcode=? AND active=TRUE ORDER BY name,id",postcode);
    }
    public List<Map<String,Object>> nearby(double latitude,double longitude,double radiusKm) {
        if(!Double.isFinite(latitude)||Math.abs(latitude)>90||!Double.isFinite(longitude)||Math.abs(longitude)>180||!Double.isFinite(radiusKm)||radiusKm<=0||radiusKm>100)throw ApiException.bad("Check location and distance.");
        List<Map<String,Object>> result=new ArrayList<>();
        for(var row:db.rows("SELECT id,name,address,postcode,latitude,longitude,pickup_enabled,delivery_enabled,delivery_minor FROM local_stores WHERE active=TRUE")) {
            double lat=decimal(row,"latitude").doubleValue(),lon=decimal(row,"longitude").doubleValue();
            double a=Math.pow(Math.sin(Math.toRadians(lat-latitude)/2),2)+Math.cos(Math.toRadians(latitude))*Math.cos(Math.toRadians(lat))*Math.pow(Math.sin(Math.toRadians(lon-longitude)/2),2);
            double distance=6371.0088*2*Math.asin(Math.sqrt(Math.min(1,a)));
            if(distance<=radiusKm){var store=new LinkedHashMap<>(row);store.put("distanceKm",Math.round(distance*100.0)/100.0);result.add(store);}
        }
        result.sort(Comparator.comparingDouble(row->((Number)row.get("distanceKm")).doubleValue()));
        return result;
    }
    public Map<String,Object> details(String storeId) {
        return db.one("SELECT id,name,address,postcode,latitude,longitude,pickup_enabled,delivery_enabled,delivery_minor FROM local_stores WHERE id=? AND active=TRUE",storeId);
    }
    public List<Map<String,Object>> adminStores() {
        return db.rows("SELECT id,name,address,postcode,latitude,longitude,pickup_enabled,delivery_enabled,delivery_minor,active FROM local_stores ORDER BY name,id");
    }
    public List<Map<String,Object>> listings(String storeId) {
        db.one("SELECT id FROM local_stores WHERE id=? AND active=TRUE",storeId);
        return db.rows("SELECT l.variant_id,l.price_minor,l.mrp_minor,l.stock_quantity,l.observed_at,p.name,p.brand,v.pack_label FROM local_store_listings l JOIN product_variants v ON v.id=l.variant_id JOIN products p ON p.id=v.product_id WHERE l.store_id=? AND p.active=TRUE ORDER BY p.name,v.amount_base",storeId);
    }
}
