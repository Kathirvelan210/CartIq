package com.smartcart.controller;
import com.smartcart.integration.ModelService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/me")
public class ModelController {
    private final ModelService models;
    public ModelController(ModelService models){this.models=models;}
    @GetMapping("/models") public JsonNode status(){return models.status();}
    @GetMapping("/ingredients/match") public JsonNode match(@RequestParam String query){return models.match(query);}
}
