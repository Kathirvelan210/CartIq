package com.smartcart.controller;

import com.smartcart.repository.Db;
import com.smartcart.service.CatalogService;
import com.smartcart.exception.ApiException;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static com.smartcart.repository.Db.*;

@RestController @RequestMapping("/api")
public class TrainingController {
    private final Db db;private final CatalogService catalog;
    public TrainingController(Db db,CatalogService catalog){this.db=db;this.catalog=catalog;}
    @GetMapping("/training/sample") public Map<String,Object> sample(){if(!catalog.sample())throw ApiException.missing();return dataset("SAMPLE",true);}
    @GetMapping("/admin/training/data") public Map<String,Object> verified(){return dataset("VERIFIED",false);}
    private Map<String,Object> dataset(String verification,boolean sample){
        var prices=db.rows("SELECT pp.variant_id,pp.platform_id,h.location_id,h.price_minor,h.observed_at FROM price_history h JOIN platform_products pp ON pp.id=h.platform_product_id WHERE h.verification=? ORDER BY h.observed_at DESC LIMIT 200000",verification).stream().map(r->Map.of("productId",text(r,"variant_id"),"platform",text(r,"platform_id"),"locationId",text(r,"location_id"),"priceMinor",num(r,"price_minor"),"observedAt",time(r,"observed_at").toString())).toList();
        var products=db.rows("SELECT id,name,brand FROM products ORDER BY id").stream().map(r->Map.of("id",text(r,"id"),"name",text(r,"name"),"brand",text(r,"brand"))).toList();
        var recipes=db.rows("SELECT * FROM recipes ORDER BY id").stream().map(r->{var ingredients=db.rows("SELECT i.product_id,p.name FROM recipe_ingredients i JOIN products p ON p.id=i.product_id WHERE i.recipe_id=?",text(r,"id")).stream().map(i->Map.of("productId",text(i,"product_id"),"name",text(i,"name"))).toList();return Map.of("id",text(r,"id"),"name",text(r,"name"),"ingredients",ingredients);}).toList();
        return Map.of("schemaVersion",1,"sample",sample,"source",sample?"SIMULATED_APPLICATION_FIXTURES":"VERIFIED_APPLICATION_OBSERVATIONS","exportedAt",java.time.Instant.now().toString(),"prices",prices,"products",products,"recipes",recipes);
    }
}
