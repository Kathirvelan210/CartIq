package com.smartcart.controller;

import com.smartcart.exception.ApiException;
import com.smartcart.repository.Db;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import static com.smartcart.repository.Db.*;

@RestController
@RequestMapping("/api/admin/provider")
public class OnlineComparisonEvidenceController {
    private final Db db;
    public OnlineComparisonEvidenceController(Db db){this.db=db;}
    public record Evidence(String sku,String name,String brand,String variantName,BigDecimal amountBase,String baseUnit,
        Integer stockQuantity,Long deliveryChargeMinor,Long handlingChargeMinor,Long minimumMinor,String evidenceSource) {}
    private static String normalize(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");}
    @PutMapping("/observations/{priceId}/comparison-evidence")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void put(@PathVariable String priceId,@RequestBody Evidence e,@AuthenticationPrincipal Jwt admin){
        var row=db.one("SELECT l.observed_at,l.verification,l.source,pp.retailer_sku,p.name,p.brand,p.variant_name,v.amount_base,v.base_unit FROM location_prices l JOIN platform_products pp ON pp.id=l.platform_product_id JOIN provider_mappings m ON m.platform_product_id=pp.id JOIN product_variants v ON v.id=pp.variant_id JOIN products p ON p.id=v.product_id WHERE l.id=? FOR UPDATE",priceId);
        if(!"VERIFIED".equals(text(row,"verification"))||!"REWALTO".equals(text(row,"source"))||
            e.sku()==null||!e.sku().equals(text(row,"retailer_sku"))||
            !normalize(e.name()).equals(normalize(text(row,"name")))||!normalize(e.brand()).equals(normalize(text(row,"brand")))||
            !normalize(e.variantName()).equals(normalize(text(row,"variant_name")))||
            normalize(e.variantName()).isBlank()||"standard".equals(normalize(e.variantName()))||
            e.amountBase()==null||e.amountBase().compareTo(decimal(row,"amount_base"))!=0||
            !normalize(e.baseUnit()).equals(normalize(text(row,"base_unit")))||
            e.evidenceSource()==null||e.evidenceSource().isBlank()||e.evidenceSource().length()>150)
            throw ApiException.bad("Verified source, exact SKU, brand, product, flavour and pack are required.");
        if(e.stockQuantity()!=null&&e.stockQuantity()<0)throw ApiException.bad("Invalid stock quantity.");
        for(Long value:new Long[]{e.deliveryChargeMinor(),e.handlingChargeMinor(),e.minimumMinor()})
            if(value!=null&&value<0)throw ApiException.bad("Charges and minimum cannot be negative.");
        db.run("DELETE FROM online_comparison_evidence WHERE price_id=?",priceId);
        db.insert("online_comparison_evidence",fields("price_id",priceId,"retailer_sku",e.sku(),"product_name",text(row,"name"),
            "brand",text(row,"brand"),"variant_name",text(row,"variant_name"),"amount_base",decimal(row,"amount_base"),
            "base_unit",text(row,"base_unit"),"price_observed_at",time(row,"observed_at"),"stock_quantity",e.stockQuantity(),
            "delivery_charge_minor",e.deliveryChargeMinor(),"handling_charge_minor",e.handlingChargeMinor(),"minimum_minor",e.minimumMinor(),
            "evidence_source",e.evidenceSource(),"observed_at",Instant.now(),"verified_by",admin.getSubject()));
    }
}
