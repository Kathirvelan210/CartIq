package com.smartcart.controller;

import com.smartcart.service.LocalStoreService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;

@RestController
@RequestMapping("/api")
public class LocalStoreController {
    private final LocalStoreService stores;
    public LocalStoreController(LocalStoreService stores){this.stores=stores;}
    @GetMapping("/admin/local-stores") public List<Map<String,Object>> adminStores(){return stores.adminStores();}
    @PostMapping("/admin/local-stores") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,String> create(@RequestBody LocalStoreService.Store store){return Map.of("id",stores.create(store));}
    @PutMapping("/admin/local-stores/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable String id,@RequestBody LocalStoreService.Store store){stores.update(id,store);}
    @PutMapping("/admin/local-stores/{id}/listings") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void listing(@PathVariable String id,@RequestBody LocalStoreService.Listing listing){stores.saveListing(id,listing);}
    @GetMapping("/local-stores") public List<Map<String,Object>> nearby(@RequestParam(required=false) String postcode,@RequestParam(required=false) Double latitude,@RequestParam(required=false) Double longitude,@RequestParam(defaultValue="5") double radiusKm){
        if(latitude!=null&&longitude!=null)return stores.nearby(latitude,longitude,radiusKm);
        if(latitude!=null||longitude!=null)throw com.smartcart.exception.ApiException.bad("Enter both latitude and longitude.");
        return stores.nearby(postcode);
    }
    @GetMapping("/local-stores/{id}") public Map<String,Object> details(@PathVariable String id){return stores.details(id);}
    @GetMapping("/local-stores/{id}/listings") public List<Map<String,Object>> listings(@PathVariable String id){return stores.listings(id);}
}
