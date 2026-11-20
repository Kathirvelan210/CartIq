package com.smartcart.controller;

import com.smartcart.grocery.PriceObservations;
import com.smartcart.grocery.PriceHistory;
import com.smartcart.grocery.GroceryCatalogue;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!mysql-local")
@RequestMapping("/api")
public class GroceryDataController {
    private final PriceObservations observations;
    private final PriceHistory history;
    private final GroceryCatalogue catalogue;

    public GroceryDataController(PriceObservations observations, PriceHistory history, GroceryCatalogue catalogue) {
        this.observations=observations; this.history=history; this.catalogue=catalogue;
    }

    @GetMapping("/grocery/variants/{variant}/identity")
    public GroceryCatalogue.Identity identity(@PathVariable String variant,@RequestParam String locationId) {
        return catalogue.identity(variant,locationId);
    }

    @PutMapping("/admin/grocery/variants/{variant}/details")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void details(@PathVariable String variant,@Valid @RequestBody GroceryCatalogue.Details input) { catalogue.details(variant,input); }

    @GetMapping("/grocery/recipes/{recipe}/components")
    public GroceryCatalogue.RecipeComponents components(@PathVariable String recipe) { return catalogue.recipe(recipe); }

    @PostMapping("/admin/grocery/ingredients")
    public Map<String,String> ingredient(@Valid @RequestBody GroceryCatalogue.Ingredient input) {
        return Map.of("id",catalogue.addIngredient(input));
    }

    @PostMapping("/admin/grocery/ingredients/{ingredient}/products")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void link(@PathVariable String ingredient,@Valid @RequestBody GroceryCatalogue.IngredientLink input) { catalogue.addLink(ingredient,input); }

    @PostMapping("/admin/grocery/ingredients/{ingredient}/substitutions")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void substitute(@PathVariable String ingredient,@Valid @RequestBody GroceryCatalogue.Substitute input) { catalogue.addSubstitute(ingredient,input); }

    @PutMapping("/admin/grocery/recipe-components/{component}/ingredient/{ingredient}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void attach(@PathVariable String component,@PathVariable String ingredient) { catalogue.attach(component,ingredient); }

    @PostMapping("/admin/grocery/retention-grants")
    public ResponseEntity<Map<String,String>> approve(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PriceObservations.Grant input) {
        return noStore(Map.of("id", observations.approve(jwt.getSubject(),input)));
    }

    @DeleteMapping("/admin/grocery/retention-grants/{id}")
    public ResponseEntity<Void> revoke(@PathVariable String id) {
        observations.revoke(id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/admin/grocery/observations")
    public ResponseEntity<PriceObservations.Recorded> record(@Valid @RequestBody PriceObservations.Input input) {
        return noStore(observations.record(input));
    }

    @GetMapping("/grocery/variants/{variant}/price-history")
    public ResponseEntity<PriceHistory.History> history(@PathVariable String variant,@RequestParam String locationId,
                                                       @RequestParam(required=false) Instant from,@RequestParam(required=false) Instant until) {
        Instant end=until==null ? Instant.now() : until;
        return noStore(history.history(variant,locationId,from==null ? end.minus(90,ChronoUnit.DAYS) : from,end));
    }

    @GetMapping("/admin/grocery/dataset")
    public ResponseEntity<PriceHistory.Dataset> dataset(@RequestParam String variantId,@RequestParam String locationId,
        @RequestParam Instant from,@RequestParam Instant validationStart,@RequestParam Instant testStart,@RequestParam Instant until) {
        return noStore(history.dataset(variantId,locationId,from,validationStart,testStart,until));
    }

    private static <T> ResponseEntity<T> noStore(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }
}
