package com.smartcart.controller;

import com.smartcart.service.ComparisonService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ComparisonController {
    private final ComparisonService comparison;
    public ComparisonController(ComparisonService comparison){this.comparison=comparison;}
    @GetMapping("/{id}/comparison")
    public ComparisonService.Result compare(@PathVariable String id,@RequestParam String locationId,
        @RequestParam double latitude,@RequestParam double longitude,@RequestParam(defaultValue="5") double radiusKm,
        @RequestParam(defaultValue="1") int quantity,@RequestParam(defaultValue="pickup") String fulfillment) {
        return comparison.compare(id,locationId,latitude,longitude,radiusKm,quantity,fulfillment);
    }
}
