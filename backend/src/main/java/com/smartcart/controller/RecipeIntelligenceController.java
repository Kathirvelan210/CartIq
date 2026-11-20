package com.smartcart.controller;

import com.smartcart.dto.Models.CartItem;
import com.smartcart.exception.ApiException;
import com.smartcart.recipe.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!mysql-local")
@RequestMapping("/api/me/recipe-intelligence")
public class RecipeIntelligenceController {
    private final RecipeLibrary library;
    private final MealPlanner planner;

    public RecipeIntelligenceController(RecipeLibrary library, MealPlanner planner) { this.library=library; this.planner=planner; }
    public record Generate(@NotBlank @Size(min=3,max=600) String prompt,boolean usePantry) {}

    @GetMapping
    public ResponseEntity<List<RecipeLibrary.Entry>> browse(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="") String q,
        @RequestParam(defaultValue="") String cuisine,@RequestParam(required=false) RecipeCard.Meal meal,
        @RequestParam(required=false) RecipeCard.Diet diet,@RequestParam(required=false) RecipeCard.Difficulty difficulty,
        @RequestParam(required=false) Integer maxMinutes,@RequestParam(required=false) Long budgetMinor,@RequestParam(required=false) String locationId) {
        if (q.length()>150 || cuisine.length()>100 || maxMinutes!=null&&(maxMinutes<1||maxMinutes>480)
            || budgetMinor!=null&&(budgetMinor<0||budgetMinor>100000000||locationId==null||locationId.isBlank())) {
            throw ApiException.bad("Check the recipe filters and choose a location for budget estimates.");
        }
        var matches=library.browse(q,cuisine,meal,diet,difficulty,maxMinutes);
        if (budgetMinor!=null) matches=matches.stream().filter(entry->{
            var result=planner.plan(jwt.getSubject(),new MealPlanner.Request(locationId,
                List.of(new MealPlanner.Meal(entry.id(),1,entry.recipe().servings())),budgetMinor,null,null));
            return "WITHIN_ESTIMATED_SUBTOTAL".equals(result.budgetStatus());
        }).toList();
        return noStore(matches);
    }

    @PostMapping("/generate")
    public ResponseEntity<RecipeLibrary.Entry> generate(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Generate request) {
        if (request.prompt().strip().length()<3) throw ApiException.bad("Enter a recipe request.");
        var receipt=library.generate(jwt.getSubject(),request.prompt(),request.usePantry());
        return ResponseEntity.ok().headers(receipt.evidence()).cacheControl(CacheControl.noStore()).body(receipt.entry());
    }

    public record Week(@NotBlank @Size(min=3,max=600) String prompt,boolean usePantry,RecipeCard.Diet diet) {}
    @PostMapping("/week")
    public ResponseEntity<List<RecipeLibrary.Entry>> week(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Week request){
        return noStore(library.week(jwt.getSubject(),request.prompt().strip(),request.usePantry(),request.diet()));
    }

    @PostMapping("/plan")
    public ResponseEntity<MealPlanner.Plan> plan(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody MealPlanner.Request input) {
        return noStore(planner.plan(jwt.getSubject(),input));
    }

    @PostMapping("/cart")
    public ResponseEntity<List<CartItem>> cart(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody MealPlanner.CartRequest input) {
        return noStore(planner.add(jwt.getSubject(),input));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
