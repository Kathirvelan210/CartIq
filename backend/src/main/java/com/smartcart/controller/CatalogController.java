package com.smartcart.controller;
import com.smartcart.dto.Models.*;
import com.smartcart.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api")
public class CatalogController {
    private final CatalogService catalog;
    public CatalogController(CatalogService catalog){this.catalog=catalog;}
    @GetMapping("/products") public List<Product> products(@RequestParam String locationId,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String category,@RequestParam(defaultValue="100") int limit,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="") String brand,@RequestParam(defaultValue="") String pack){return catalog.products(locationId,q,category,limit,offset,brand,pack);}
    @GetMapping("/products/page") public ProductPage productPage(@RequestParam String locationId,@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String category,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="24") int pageSize,@RequestParam(defaultValue="name_asc") String sort,@RequestParam(defaultValue="") String brand,@RequestParam(defaultValue="") String pack){return catalog.productPage(locationId,q,category,page,pageSize,sort,brand,pack);}
    @GetMapping("/products/lookup/barcode") public Product productByBarcode(@RequestParam String gtin,@RequestParam String locationId){return catalog.productByBarcode(gtin,locationId);}
    @GetMapping("/products/{id}") public Product product(@PathVariable String id,@RequestParam String locationId){return catalog.product(id,locationId);}
    @GetMapping("/products/{id}/metadata") public CatalogService.Metadata metadata(@PathVariable String id,@RequestParam String locationId){return catalog.metadata(id,locationId);}
    @GetMapping("/products/{id}/variants") public List<Product> variants(@PathVariable String id,@RequestParam String locationId){return catalog.variantsFor(id,locationId);}
    @GetMapping("/locations") public List<Location> locations(){return catalog.locations();}
    @GetMapping("/categories") public List<Category> categories(){return catalog.categories();}
    @GetMapping("/platforms") public List<Platform> platforms(){return catalog.platforms();}
    @GetMapping("/meta") public Map<String,Object> meta(){return Map.of("sample",catalog.sample(),"groceryProvider",catalog.sample()?"SAMPLE_ONLY":"QUICKCOMMERCEAPI","orderingEnabled",false,"version","0.2.0","message","Retailer ordering and payments are outside this shopping-list application. Provider access needs verified credentials and permission.");}
}
