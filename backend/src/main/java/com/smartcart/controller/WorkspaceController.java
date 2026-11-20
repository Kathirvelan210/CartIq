package com.smartcart.controller;

import com.smartcart.dto.Models.*;
import com.smartcart.service.*;
import com.smartcart.notification.NotificationService;
import com.smartcart.repository.Db;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;
import static com.smartcart.repository.Db.*;

@RestController @RequestMapping("/api")
public class WorkspaceController {
    private final WatchService watches;private final NotificationService notices;private final BudgetService budgets;private final RecipeService recipes;private final AnalyticsService analytics;private final AssistantService assistant;private final Db db;
    public WorkspaceController(WatchService w,NotificationService n,BudgetService b,RecipeService r,AnalyticsService a,AssistantService s,Db db){watches=w;notices=n;budgets=b;recipes=r;analytics=a;assistant=s;this.db=db;}
    @GetMapping("/me/overview") public Overview overview(@AuthenticationPrincipal Jwt j,@RequestParam String locationId){return analytics.overview(j.getSubject(),locationId);}
    @GetMapping("/me/watchlist") public List<Watch> watches(@AuthenticationPrincipal Jwt j){return watches.list(j.getSubject());}
    @PutMapping("/me/watchlist") public Watch watch(@AuthenticationPrincipal Jwt j,@Valid @RequestBody WatchInput r){return watches.save(j.getSubject(),r);}
    @DeleteMapping("/me/watchlist/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void unwatch(@AuthenticationPrincipal Jwt j,@PathVariable String id){watches.remove(j.getSubject(),id);}
    @PostMapping("/me/watchlist/check") @ResponseStatus(HttpStatus.NO_CONTENT) public void check(@AuthenticationPrincipal Jwt j){watches.scanUser(j.getSubject());}
    @GetMapping("/me/notifications") public List<Notice> notifications(@AuthenticationPrincipal Jwt j){return notices.list(j.getSubject());}
    @PatchMapping("/me/notifications/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT) public void read(@AuthenticationPrincipal Jwt j,@PathVariable String id){notices.read(j.getSubject(),id);}
    @GetMapping("/me/preferences") public NotificationSettings settings(@AuthenticationPrincipal Jwt j){return notices.settings(j.getSubject());}
    @PutMapping("/me/preferences") public NotificationSettings settings(@AuthenticationPrincipal Jwt j,@Valid @RequestBody NotificationSettings r){notices.settings(j.getSubject(),r);return notices.settings(j.getSubject());}
    @PostMapping("/me/device-tokens") @ResponseStatus(HttpStatus.NO_CONTENT) public void device(@AuthenticationPrincipal Jwt j,@Valid @RequestBody DeviceInput r){notices.register(j.getSubject(),r.token(),r.previousToken());}
    @DeleteMapping("/me/device-tokens") @ResponseStatus(HttpStatus.NO_CONTENT) public void deviceDelete(@AuthenticationPrincipal Jwt j,@Valid @RequestBody(required=false) DeviceInput r){if(r==null)notices.unregister(j.getSubject());else notices.unregister(j.getSubject(),r.token());}
    @GetMapping("/me/budget") public Budget budget(@AuthenticationPrincipal Jwt j,@RequestParam(required=false) String month){return budgets.budget(j.getSubject(),month==null?BudgetService.month():month);}
    @PutMapping("/me/budget") public Budget budget(@AuthenticationPrincipal Jwt j,@Valid @RequestBody BudgetInput r){return budgets.save(j.getSubject(),r);}
    @PostMapping("/me/expenses") @ResponseStatus(HttpStatus.CREATED) public void expense(@AuthenticationPrincipal Jwt j,@Valid @RequestBody ExpenseInput r){budgets.expense(j.getSubject(),r);}
    @GetMapping("/me/history") public List<Purchase> history(@AuthenticationPrincipal Jwt j){return budgets.history(j.getSubject());}
    @PostMapping("/me/history/sample") @ResponseStatus(HttpStatus.CREATED) public Purchase purchase(@AuthenticationPrincipal Jwt j,@Valid @RequestBody PurchaseInput r){return budgets.recordSample(j.getSubject(),r);}
    @GetMapping("/me/spending-trend") public List<MonthSpend> trend(@AuthenticationPrincipal Jwt j){return budgets.trend(j.getSubject());}
    @GetMapping("/me/replenishment") public List<Replenishment> replenishment(@AuthenticationPrincipal Jwt j){return budgets.replenishment(j.getSubject());}
    @PutMapping("/me/replenishment") @ResponseStatus(HttpStatus.NO_CONTENT) public void cycle(@AuthenticationPrincipal Jwt j,@Valid @RequestBody ReplenishmentInput r){budgets.cycle(j.getSubject(),r);}
    @PostMapping("/me/replenishment/{id}/restocked") @ResponseStatus(HttpStatus.NO_CONTENT) public void restock(@AuthenticationPrincipal Jwt j,@PathVariable String id){budgets.replenish(j.getSubject(),id);}
    @DeleteMapping("/me/replenishment/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void removeCycle(@AuthenticationPrincipal Jwt j,@PathVariable String id){budgets.removeCycle(j.getSubject(),id);}
    @GetMapping("/recipes") public List<Recipe> recipes(@AuthenticationPrincipal Jwt j,@RequestParam(defaultValue="") String q){return recipes.recipes(j==null?null:j.getSubject(),q);}
    @PostMapping("/recipes/{id}/plan") public RecipePlan plan(@AuthenticationPrincipal Jwt j,@PathVariable String id,@Valid @RequestBody RecipePlanInput r){return recipes.plan(j.getSubject(),id,r);}
    @PostMapping("/recipes/{id}/cart") public List<CartItem> addRecipe(@AuthenticationPrincipal Jwt j,@PathVariable String id,@Valid @RequestBody RecipeAddInput r){return recipes.add(j.getSubject(),id,r);}
    @GetMapping("/me/pantry") public List<Pantry> pantry(@AuthenticationPrincipal Jwt j){return recipes.pantry(j.getSubject());}
    @PutMapping("/me/pantry") @ResponseStatus(HttpStatus.NO_CONTENT) public void pantry(@AuthenticationPrincipal Jwt j,@Valid @RequestBody PantryInput r){recipes.pantry(j.getSubject(),r);}
    @GetMapping("/products/{id}/analytics") public PriceAnalytics prices(@PathVariable String id,@RequestParam String locationId){return analytics.prices(id,locationId);}
    @GetMapping("/me/recommendations") public List<Recommendation> recommendations(@AuthenticationPrincipal Jwt j,@RequestParam String locationId){return analytics.recommendations(j.getSubject(),locationId);}
    @PostMapping("/me/assistant") public AssistantAnswer assistant(@AuthenticationPrincipal Jwt j,@Valid @RequestBody AssistantInput r){return assistant.answer(j.getSubject(),r);}
    @GetMapping("/me/wishlist") public List<String> saved(@AuthenticationPrincipal Jwt j){return db.rows("SELECT variant_id FROM saved_products WHERE user_id=?",j.getSubject()).stream().map(r->text(r,"variant_id")).toList();}
    @PutMapping("/me/wishlist/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void save(@AuthenticationPrincipal Jwt j,@PathVariable String id){db.one("SELECT id FROM product_variants WHERE id=?",id);if(db.count("SELECT COUNT(*) FROM saved_products WHERE user_id=? AND variant_id=?",j.getSubject(),id)==0)db.insert("saved_products",fields("user_id",j.getSubject(),"variant_id",id));}
    @DeleteMapping("/me/wishlist/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void unsave(@AuthenticationPrincipal Jwt j,@PathVariable String id){db.run("DELETE FROM saved_products WHERE user_id=? AND variant_id=?",j.getSubject(),id);}
}
