package com.smartcart.controller;

import com.smartcart.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!mysql-local")
@RequestMapping("/api")
class WeeklyShoppingController {
    private static final String PLAN="/me/shopping-intelligence/weekly";
    private static final String FEEDBACK="/me/shopping-intelligence/feedback";
    record Revision(int revision) {}
    record Preview(WeeklyShopping.Preview plan,UUID planFeedbackId,UUID basketFeedbackId) {}
    private final WeeklyShopping planner;
    private final UnifiedShoppingAssistant assistant;
    private final RecommendationFeedback feedback;

    private final com.smartcart.recipe.RecipeLibrary recipes;
    WeeklyShoppingController(WeeklyShopping planner,UnifiedShoppingAssistant assistant,RecommendationFeedback feedback,com.smartcart.recipe.RecipeLibrary recipes) {
        this.recipes=recipes;
        this.planner=planner;this.assistant=assistant;this.feedback=feedback;
    }

    @GetMapping(PLAN) ResponseEntity<WeeklyShopping.Workspace> workspace(@AuthenticationPrincipal Jwt jwt,@RequestParam @Size(max=60) String locationId) {
        return result(planner.workspace(jwt.getSubject(),locationId));
    }
    @PostMapping(PLAN+"/preview") ResponseEntity<Preview> preview(@AuthenticationPrincipal Jwt jwt,@RequestParam @Size(max=60) String locationId,
        @Valid @RequestBody WeeklyShopping.Draft draft) {
        return result(new Preview(planner.preview(jwt.getSubject(),locationId,draft),UUID.randomUUID(),UUID.randomUUID()));
    }
    @PutMapping(PLAN) ResponseEntity<Revision> save(@AuthenticationPrincipal Jwt jwt,@RequestParam @Size(max=60) String locationId,
        @Valid @RequestBody WeeklyShopping.Save save) {
        return result(new Revision(planner.save(jwt.getSubject(),locationId,save)));
    }
    @DeleteMapping(PLAN) ResponseEntity<Void> clear(@AuthenticationPrincipal Jwt jwt,@RequestParam @Size(max=60) String locationId) {
        planner.clear(jwt.getSubject(),locationId);return empty();
    }
    @PostMapping(PLAN+"/assistant") ResponseEntity<UnifiedShoppingAssistant.Reply> answer(@AuthenticationPrincipal Jwt jwt,
        @RequestParam @Size(max=60) String locationId,@Valid @RequestBody UnifiedShoppingAssistant.Message input) {
        return result(assistant.answer(jwt.getSubject(),locationId,input));
    }
    @PostMapping(PLAN+"/assistant/gemini") ResponseEntity<UnifiedShoppingAssistant.Reply> gemini(@AuthenticationPrincipal Jwt jwt,
        @RequestParam @Size(max=60) String locationId,@Valid @RequestBody UnifiedShoppingAssistant.Message input) {
        String owner=jwt.getSubject();
        return result(assistant.answer(owner,locationId,input,recipes.route(owner,input.message())));
    }
    @PutMapping(FEEDBACK+"/{reference}") ResponseEntity<RecommendationFeedback.Rating> record(@AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID reference,@Valid @RequestBody RecommendationFeedback.Rating rating) {
        return result(feedback.record(jwt.getSubject(),reference,rating));
    }
    @DeleteMapping(FEEDBACK) ResponseEntity<Void> clearFeedback(@AuthenticationPrincipal Jwt jwt) {
        feedback.clear(jwt.getSubject());return empty();
    }
    @GetMapping("/admin/shopping-intelligence/feedback") ResponseEntity<RecommendationFeedback.Analytics> analytics(){
        return result(feedback.aggregate());
    }
    private static <T> ResponseEntity<T> result(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    private static ResponseEntity<Void> empty(){return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
}
