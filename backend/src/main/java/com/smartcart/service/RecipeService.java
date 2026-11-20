package com.smartcart.service;

import com.smartcart.dto.Models.*;
import com.smartcart.repository.Db;
import com.smartcart.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.Instant;
import java.util.*;
import static com.smartcart.repository.Db.*;

@Service
public class RecipeService {
    private final Db db;private final CatalogService catalog;private final CartService carts;private final com.smartcart.integration.ModelService models;
    public RecipeService(Db db,CatalogService catalog,CartService carts,com.smartcart.integration.ModelService models){this.db=db;this.catalog=catalog;this.carts=carts;this.models=models;}
    public List<Recipe> recipes(String user,String query){String q=query==null?"":query.toLowerCase(Locale.ROOT);Map<String,Double> learnedRanks=models.recipeRanks(user==null?List.of():pantry(user).stream().filter(p->p.amountBase().signum()>0).map(Pantry::name).toList());return db.rows("SELECT r.*,(SELECT COUNT(*) FROM recipe_ingredients i WHERE i.recipe_id=r.id) AS ingredient_count FROM recipes r ORDER BY r.name").stream().filter(r->text(r,"name").toLowerCase(Locale.ROOT).contains(q)).map(r->new Recipe(text(r,"id"),text(r,"name"),(int)num(r,"servings"),text(r,"instructions"),text(r,"source"),bool(r,"sample"),text(r,"color"),(int)num(r,"ingredient_count"),user==null?0:(int)db.count("SELECT COUNT(*) FROM recipe_ingredients i JOIN user_pantry p ON p.product_id=i.product_id AND p.base_unit=i.base_unit WHERE i.recipe_id=? AND p.user_id=? AND p.amount_base>=i.amount_base",text(r,"id"),user))).sorted(Comparator.<Recipe>comparingDouble(r->r.pantryMatches()+learnedRanks.getOrDefault(r.id(),0.0)).reversed()).toList();}
    public RecipePlan plan(String user,String recipeId,RecipePlanInput input){return plan(user,recipeId,input,Map.of());}
    private RecipePlan plan(String user,String recipeId,RecipePlanInput input,Map<String,String> selections){var recipe=db.one("SELECT * FROM recipes WHERE id=?",recipeId);catalog.requireLocation(input.locationId());List<Ingredient> ingredients=new ArrayList<>();long total=0;boolean complete=true;for(var i:db.rows("SELECT i.*,p.name FROM recipe_ingredients i JOIN products p ON p.id=i.product_id WHERE i.recipe_id=? ORDER BY p.name",recipeId)){String pid=text(i,"product_id"),unit=text(i,"base_unit");BigDecimal required=decimal(i,"amount_base").multiply(BigDecimal.valueOf(input.servings())).divide(BigDecimal.valueOf(num(recipe,"servings")),3,RoundingMode.UP);BigDecimal owned=user==null?BigDecimal.ZERO:db.maybe("SELECT amount_base FROM user_pantry WHERE user_id=? AND product_id=? AND base_unit=?",user,pid,unit).map(p->decimal(p,"amount_base")).orElse(BigDecimal.ZERO);if(input.ownedProductIds()!=null&&input.ownedProductIds().contains(pid))owned=required;BigDecimal missing=required.subtract(owned).max(BigDecimal.ZERO);Product chosen=null;long bestCost=Long.MAX_VALUE;int packs=0;if(missing.signum()>0){for(Product p:catalog.packsForProduct(pid,unit,input.locationId())){if(selections.containsKey(pid)&&!p.id().equals(selections.get(pid)))continue;var offer=catalog.best(p);if(offer.isEmpty())continue;int count=missing.divide(p.amountBase(),0,RoundingMode.CEILING).intValueExact();long cost=offer.get().priceMinor()*count;if(cost<bestCost){chosen=p;bestCost=cost;packs=count;}}if(chosen==null)complete=false;else total+=bestCost;}ingredients.add(new Ingredient(pid,text(i,"name"),required,owned,missing,unit,chosen==null?null:chosen.id(),packs,chosen==null?null:bestCost));}return new RecipePlan(recipeId,input.servings(),ingredients,total,complete,catalog.sample());}
    @Transactional public List<CartItem> add(String user,String recipeId,RecipeAddInput input){
        carts.lockOwner(user);
        RecipePlan plan=plan(user,recipeId,input.plan(),input.selectedVariantIds()==null?Map.of():input.selectedVariantIds());
        if(!plan.complete())throw ApiException.bad("A missing ingredient has no available selected pack. Review your selections first.");
        for(var ingredient:plan.ingredients()){
            if(ingredient.missingBase().signum()==0)continue;
            var existing=carts.list(user).stream()
                .filter(c->c.locationId().equals(input.plan().locationId())&&c.productId().equals(ingredient.suggestedVariantId())).findFirst();
            int quantity=ingredient.packs()+existing.map(CartItem::quantity).orElse(0);
            if(quantity>99)throw ApiException.bad("Recipe quantities exceed the supported pack count.");
            Preferences prefs=existing.map(CartItem::preferences).orElse(new Preferences("ESSENTIAL",null,null,null,null,null,null,false));
            carts.save(user,new CartInput(ingredient.suggestedVariantId(),input.plan().locationId(),quantity,prefs));
        }
        return carts.list(user);
    }
    public List<Pantry> pantry(String user){return db.rows("SELECT u.*,p.name FROM user_pantry u JOIN products p ON p.id=u.product_id WHERE u.user_id=? ORDER BY p.name",user).stream().map(r->new Pantry(text(r,"product_id"),text(r,"name"),decimal(r,"amount_base"),text(r,"base_unit"))).toList();}
    @Transactional public void pantry(String user,PantryInput input){if(db.count("SELECT COUNT(*) FROM product_variants WHERE product_id=? AND base_unit=?",input.productId(),input.baseUnit())==0)throw ApiException.bad("The pantry unit does not match this product.");var row=db.maybe("SELECT id FROM user_pantry WHERE user_id=? AND product_id=?",user,input.productId());if(input.amountBase().signum()==0){db.run("DELETE FROM user_pantry WHERE user_id=? AND product_id=?",user,input.productId());return;}if(row.isPresent())db.run("UPDATE user_pantry SET amount_base=?,base_unit=?,updated_at=? WHERE user_id=? AND product_id=?",input.amountBase(),input.baseUnit(),Instant.now(),user,input.productId());else db.insert("user_pantry",fields("id",id(),"user_id",user,"product_id",input.productId(),"amount_base",input.amountBase(),"base_unit",input.baseUnit()));}
}
