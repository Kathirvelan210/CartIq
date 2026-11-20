package com.smartcart.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.dto.Models.*;
import com.smartcart.exception.ApiException;
import com.smartcart.recipe.*;
import com.smartcart.recipe.MealPlanner.Meal;
import com.smartcart.repository.Db;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.smartcart.repository.Db.*;

@Service
@Profile("!mysql-local")
public class WeeklyShopping {
    public record Draft(@NotNull @Size(max=14) List<@NotNull @Valid Meal> meals,
        @NotNull @Size(max=30) List<@NotNull @Valid BasketLine> items,
        @PositiveOrZero @Max(100_000_000) Long budgetMinor,@NotNull BulkBuying.Diet diet,
        @NotNull @Size(max=100) Map<@NotBlank @Size(max=120) String,@NotNull @PositiveOrZero @Max(200_000) BigDecimal> onHand) {}
    public record Save(@NotNull @Valid Draft draft,@Min(0) int revision) {}
    public record Workspace(Draft draft,int revision,List<RecipeLibrary.Entry> recipes,List<BasketLine> cart,
                            Integer householdSize,boolean sample) {}
    public record Preview(List<MealPlanner.Ingredient> ingredients,BasketOptimizer.PlanComparison comparison,Long budgetMinor,
                          String budgetStatus,String method,Instant checkedAt,List<String> notes,boolean sample) {}
    private final Db db;
    private final ObjectMapper json;
    private final CatalogService catalogue;
    private final RecipeLibrary library;
    private final MealPlanner meals;
    private final CartService carts;
    private final BulkBuying bulk;
    private final CurrentOffers offers;
    private final BasketOptimizer optimizer;

    public WeeklyShopping(Db db,ObjectMapper json,CatalogService catalogue,RecipeLibrary library,MealPlanner meals,
                          CartService carts,BulkBuying bulk,CurrentOffers offers,BasketOptimizer optimizer) {
        this.db=db;this.json=json;this.catalogue=catalogue;this.library=library;this.meals=meals;this.carts=carts;
        this.bulk=bulk;this.offers=offers;this.optimizer=optimizer;
    }

    public Workspace workspace(String owner,String location) {
        catalogue.requireLocation(location);
        var home=bulk.household(owner);
        var saved=db.maybe("SELECT revision,plan_input FROM weekly_shopping_plans WHERE user_id=? AND location_id=?",owner,location);
        var draft=saved.map(r->decode(text(r,"plan_input"))).orElseGet(()->new Draft(List.of(),List.of(),null,home==null?BulkBuying.Diet.ANY:home.diet(),Map.of()));
        var recipes=new ArrayList<>(library.browse("",home==null?null:home.preferredCuisine(),null,null,null,null));
        for(var recipe:library.browse("",null,null,null,null,null))if(!recipes.contains(recipe))recipes.add(recipe);
        var cart=carts.list(owner).stream().filter(line->location.equals(line.locationId())).map(line->new BasketLine(line.productId(),line.quantity())).toList();
        return new Workspace(draft,saved.map(r->(int)num(r,"revision")).orElse(0),recipes,cart,home==null?null:home.householdSize(),catalogue.sample());
    }

    @Transactional
    public int save(String owner,String location,Save save) {
        validate(save.draft(),location,true);
        carts.lockOwner(owner);
        var prior=db.maybe("SELECT revision FROM weekly_shopping_plans WHERE user_id=? AND location_id=?",owner,location);
        if(prior.map(r->(int)num(r,"revision")).orElse(0)!=save.revision())
            throw new ApiException(HttpStatus.CONFLICT,"This plan changed in another session. Reload before saving.");
        int next=save.revision()+1;
        String payload=encode(save.draft());
        if(prior.isPresent()) db.run("UPDATE weekly_shopping_plans SET plan_input=?,revision=?,updated_at=? WHERE user_id=? AND location_id=?",
            payload,next,Instant.now(),owner,location);
        else db.insert("weekly_shopping_plans",fields("user_id",owner,"location_id",location,"plan_input",payload,"revision",next,"updated_at",Instant.now()));
        return next;
    }

    @Transactional
    public void clear(String owner,String location) {
        catalogue.requireLocation(location);
        carts.lockOwner(owner);
        db.run("DELETE FROM weekly_shopping_plans WHERE user_id=? AND location_id=?",owner,location);
    }

    @Transactional(readOnly=true)
    public Preview preview(String owner,String location,Draft draft) {
        validate(draft,location,false);
        Long budget=bulk.budget(owner,bulk.household(owner),draft.budgetMinor());
        var menu=draft.meals().isEmpty()?List.<MealPlanner.Ingredient>of():meals.plan(owner,
            new MealPlanner.Request(location,draft.meals(),null,draft.onHand(),Map.of()),offers).ingredients();
        var demands=new ArrayList<BasketOptimizer.Demand>();
        var catalogueRows=new HashMap<String,Product>();
        var quotes=new HashMap<String,List<CurrentOffers.Quote>>();
        for(var row:menu) {
            if(row.missing().signum()==0)continue;
            var choices=new ArrayList<List<BasketOptimizer.PlanLine>>();
            for(var choice:row.choices()) {
                var lines=new ArrayList<BasketOptimizer.PlanLine>();
                for(var pack:choice.packs()) {
                    var p=catalogueRows.computeIfAbsent(pack.variantId(),id->catalogue.product(id,location));
                    quotes.computeIfAbsent(p.id(),id->offers.forPack(p,location,owner)).stream()
                        .filter(q->q.retailer().equals(choice.retailer())&&q.priceMinor()==pack.eachMinor()&&q.observedAt().equals(pack.observedAt())
                            &&q.packLimit()>=pack.count()).findFirst().ifPresent(q->lines.add(new BasketOptimizer.PlanLine(p,pack.count(),q)));
                }
                if(!lines.isEmpty()&&lines.size()==choice.packs().size())choices.add(lines);
            }
            String gap=row.cataloguePacks().isEmpty()?"no compatible catalogue pack":"fresh prices or sufficient stock not confirmed";
            demands.add(new BasketOptimizer.Demand(row.name()+" ("+row.missing().stripTrailingZeros().toPlainString()+" "+row.unit()+")"
                +(choices.isEmpty()?" — "+gap:""),choices));
        }
        for(var line:draft.items()){
            if(db.maybe("SELECT v.id FROM product_variants v JOIN products p ON p.id=v.product_id WHERE v.id=? AND p.active=TRUE",line.productId()).isEmpty()) {
                demands.add(new BasketOptimizer.Demand("An exact pack in the editable list is no longer in the active catalogue.",List.of()));
                continue;
            }
            var p=catalogueRows.computeIfAbsent(line.productId(),id->catalogue.product(id,location));
            var choices=quotes.computeIfAbsent(p.id(),id->offers.forPack(p,location,owner)).stream().filter(q->q.packLimit()>=line.quantity())
                .map(q->List.of(new BasketOptimizer.PlanLine(p,line.quantity(),q))).toList();
            demands.add(new BasketOptimizer.Demand(line.quantity()+" × "+p.name()+" ("+p.quantity()+")",choices));
        }
        var comparison=optimizer.comparePlan(demands);
        var best=comparison.recommended();
        String budgetStatus=budget==null?"NO_BUDGET":best.totalMinor()==null?
            (best.pricedItemsMinor()>budget?"PARTIAL_OVER_BUDGET":"BUDGET_UNKNOWN"):
            best.totalMinor()<=budget?"WITHIN_BUDGET":comparison.searchComplete()?"OVER_BUDGET":"SEARCH_LIMIT";
        return new Preview(menu,comparison,budget,budgetStatus,"RULE_BASED",Instant.now(),List.of(
            "Ingredients use reviewed links and dimension-compatible packs. Extra items keep their selected pack.",
            "Prices come from location-scoped recorded offers or authorized transient live responses. An available offer without a stock count can cover one pack.",
            "Compare the evaluated bundles. Each ingredient uses a single retailer and up to two pack sizes. Retailer terms and stock can change."
        ),catalogue.sample());
    }

    private void validate(Draft draft,String location,boolean checkItems) {
        catalogue.requireLocation(location);
        var recipes=new HashMap<String,RecipeLibrary.Entry>();
        library.browse("",null,null,null,null,null).forEach(r->recipes.put(r.id(),r));
        for(var item:draft.meals()) {
            var recipe=recipes.get(item.recipeId());
            if(recipe==null)throw ApiException.bad("Select a curated recipe for the weekly plan.");
            if(draft.diet()!=BulkBuying.Diet.ANY&&!draft.diet().name().equals(recipe.recipe().diet().name()))
                throw ApiException.bad("A selected meal conflicts with the diet you chose for this plan.");
        }
        var items=new HashSet<String>();
        for(var row:draft.items()) {
            if(row.productId().length()>60||!items.add(row.productId()))throw ApiException.bad("Select each exact extra pack at most once.");
            if(checkItems)catalogue.product(row.productId(),location);
        }
    }

    private String encode(Draft draft) {
        try {
            String data=json.writeValueAsString(draft);
            if(data.getBytes(StandardCharsets.UTF_8).length>60_000)throw ApiException.bad("The plan exceeds its storage limit.");
            return data;
        } catch(JsonProcessingException e) {throw new IllegalStateException("Could not serialize plan input");}
    }
    private Draft decode(String value) {
        try {return json.readValue(value,Draft.class);}
        catch(JsonProcessingException e) {throw new IllegalStateException("Could not read saved plan input");}
    }
}
