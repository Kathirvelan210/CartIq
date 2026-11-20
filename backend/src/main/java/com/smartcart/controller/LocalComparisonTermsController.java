package com.smartcart.controller;

import com.smartcart.exception.ApiException;
import com.smartcart.repository.Db;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import static com.smartcart.repository.Db.*;

@RestController
@RequestMapping("/api/admin/local-stores")
public class LocalComparisonTermsController {
    private final Db db;
    public LocalComparisonTermsController(Db db){this.db=db;}
    public record Terms(Long pickupChargeMinor,Long deliveryChargeMinor,Long pickupMinimumMinor,Long deliveryMinimumMinor) {}
    @PutMapping("/{id}/comparison-terms")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void update(@PathVariable String id,@RequestBody Terms t) {
        for(Long value:new Long[]{t.pickupChargeMinor(),t.deliveryChargeMinor(),t.pickupMinimumMinor(),t.deliveryMinimumMinor()})
            if(value!=null&&value<0)throw ApiException.bad("Charges and minimums cannot be negative.");
        db.one("SELECT id FROM local_stores WHERE id=? FOR UPDATE",id);
        Instant now=Instant.now();
        if(db.count("SELECT COUNT(*) FROM local_store_comparison_terms WHERE store_id=?",id)==0)
            db.insert("local_store_comparison_terms",fields("store_id",id,"pickup_charge_minor",t.pickupChargeMinor(),"delivery_charge_minor",t.deliveryChargeMinor(),"pickup_minimum_minor",t.pickupMinimumMinor(),"delivery_minimum_minor",t.deliveryMinimumMinor(),"observed_at",now));
        else db.run("UPDATE local_store_comparison_terms SET pickup_charge_minor=?,delivery_charge_minor=?,pickup_minimum_minor=?,delivery_minimum_minor=?,observed_at=? WHERE store_id=?",t.pickupChargeMinor(),t.deliveryChargeMinor(),t.pickupMinimumMinor(),t.deliveryMinimumMinor(),now,id);
    }
}
