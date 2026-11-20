package com.smartcart.service;

import com.smartcart.dto.Models.Product;
import com.smartcart.exception.ApiException;
import com.smartcart.repository.Db;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.smartcart.repository.Db.*;

@Service
@Profile("!mysql-local")
public class BulkBuying {
    public record Household(@NotNull @Min(1) @Max(30) Integer householdSize,@NotNull Diet diet,
        @Size(max=100) String preferredCuisine,@PositiveOrZero @Max(1_000_000_000L) Long shoppingBudgetMinor) {}
    public enum Diet { ANY, VEGETARIAN, NON_VEGETARIAN }
    public record Limits(@NotNull @Positive @Digits(integer=7,fraction=3) BigDecimal perPersonDaily,
        @NotNull @PositiveOrZero @Digits(integer=7,fraction=3) BigDecimal storageCapacity,
        @Min(1) @Max(90) int planningDays,@PositiveOrZero @Max(1_000_000_000L) Long budgetMinor) {}
    public record Purchase(String variantId,String pack,int packCount,BigDecimal amount,CurrentOffers.Quote quote,long subtotalMinor) {}
    public record Advice(String action,String reason,String productId,String name,String unit,BigDecimal pantry,BigDecimal required,
        BigDecimal maxPurchase,Long budgetMinor,Integer daysBeforeExpiry,Purchase normal,Purchase recommended,boolean sample) {}
    private final Db db;
    private final CatalogService catalogue;
    private final CurrentOffers offers;
    private final BudgetService budgets;

    public BulkBuying(Db db,CatalogService catalogue,CurrentOffers offers,BudgetService budgets){
        this.db=db;this.catalogue=catalogue;this.offers=offers;this.budgets=budgets;
    }

    public Household household(String user) {
        return db.maybe("SELECT * FROM shopping_intelligence_preferences WHERE user_id=?",user).map(r->new Household(
            (int)num(r,"household_size"),Diet.valueOf(text(r,"dietary_preference")),text(r,"preferred_cuisine"),number(r,"shopping_budget_minor")))
            .orElse(null);
    }

    @Transactional public Household household(String user,Household input) {
        db.one("SELECT id FROM users WHERE id=? FOR UPDATE",user);
        db.run("DELETE FROM shopping_intelligence_preferences WHERE user_id=?",user);
        db.insert("shopping_intelligence_preferences",fields("user_id",user,"household_size",input.householdSize(),"dietary_preference",input.diet().name(),
            "preferred_cuisine",input.preferredCuisine(),"shopping_budget_minor",input.shoppingBudgetMinor(),"updated_at",Instant.now()));
        return input;
    }

    public Limits limits(String user,Product product) {
        return db.maybe("SELECT * FROM bulk_buy_preferences WHERE user_id=? AND product_id=? AND base_unit=?",user,
            product.canonicalProductId(),product.baseUnit()).map(r->new Limits(decimal(r,"per_person_daily"),decimal(r,"storage_capacity"),
            (int)num(r,"planning_days"),number(r,"budget_minor"))).orElse(null);
    }

    @Transactional public Limits limits(String user,Product product,Limits limits) {
        db.one("SELECT id FROM users WHERE id=? FOR UPDATE",user);
        db.run("DELETE FROM bulk_buy_preferences WHERE user_id=? AND product_id=? AND base_unit=?",user,product.canonicalProductId(),product.baseUnit());
        db.insert("bulk_buy_preferences",fields("user_id",user,"product_id",product.canonicalProductId(),"base_unit",product.baseUnit(),
            "per_person_daily",limits.perPersonDaily(),"storage_capacity",limits.storageCapacity(),"planning_days",limits.planningDays(),
            "budget_minor",limits.budgetMinor(),"updated_at",Instant.now()));
        return limits;
    }

    public Advice advise(String user,Product selected,String location,Integer expiry) {
        var home=household(user);var rules=limits(user,selected);
        BigDecimal owned=pantry(user,selected);
        var baseline=offers.forPack(selected,location,user).stream().findFirst().map(q->purchase(selected,q,1)).orElse(null);
        Long budget=budget(user,home,rules==null?null:rules.budgetMinor());
        if(home==null||rules==null) return advice("INSUFFICIENT_INFORMATION","Set household size, daily use, storage capacity and planning days.",selected,owned,null,null,budget,expiry,baseline,null);
        BigDecimal daily=rules.perPersonDaily().multiply(BigDecimal.valueOf(home.householdSize()));
        BigDecimal need=daily.multiply(BigDecimal.valueOf(rules.planningDays())).subtract(owned).max(BigDecimal.ZERO);
        BigDecimal space=rules.storageCapacity().subtract(owned).max(BigDecimal.ZERO);
        BigDecimal safe=space.min(expiry==null?need:daily.multiply(BigDecimal.valueOf(expiry)).subtract(owned).max(BigDecimal.ZERO));
        if(need.signum()==0||space.signum()==0||expiry!=null&&safe.signum()==0)
            return advice("DO_NOT_BUY_EXTRA","Your pantry covers the plan, or storage and use-by time leave no room for an extra purchase.",selected,owned,need,safe,budget,expiry,baseline,null);
        if(baseline==null) return advice("INSUFFICIENT_INFORMATION","A fresh available price for the chosen normal pack is missing.",selected,owned,need,safe,budget,expiry,null,null);
        boolean normalFits=baseline.amount().compareTo(space)<=0&&(expiry==null||baseline.amount().compareTo(safe)<=0)
            && (budget==null||baseline.subtotalMinor()<=budget);
        if(need.compareTo(selected.amountBase())<=0&&normalFits) return advice("BUY_NORMAL_QUANTITY","One normal pack covers the planned need within the known limits.",selected,owned,need,safe,budget,expiry,baseline,baseline);
        if(expiry==null||budget==null) return advice("INSUFFICIENT_INFORMATION","A shopping budget and confirmed remaining use-by days are required before recommending extra packs.",selected,owned,need,safe,budget,expiry,baseline,null);

        List<Purchase> options=new ArrayList<>();
        boolean stockUnknown=false;
        for(var variant:compatiblePacks(selected,location)) {
            if(variant.amountBase().compareTo(selected.amountBase())<0) continue;
            var count=need.divide(variant.amountBase(),0,RoundingMode.CEILING);
            if(count.compareTo(BigDecimal.valueOf(99))>0) continue;
            for(var quote:offers.forPack(variant,location,user)) {
                var candidate=purchase(variant,quote,count.intValueExact());
                if(candidate.amount().compareTo(safe)>0||candidate.subtotalMinor()>budget) continue;
                if(!variant.id().equals(selected.id()) && !cheaperThanNormal(candidate,baseline)) continue;
                if(quote.stockPacks()==null){stockUnknown=true;continue;}
                if(quote.stockPacks()<candidate.packCount())continue;
                options.add(candidate);
            }
        }
        var best=options.stream().min(Comparator.comparingLong(Purchase::subtotalMinor).thenComparing(Purchase::amount)
            .thenComparing(Purchase::variantId).thenComparing(p->p.quote().retailer())).orElse(null);
        if(best==null&&stockUnknown) return advice("INSUFFICIENT_INFORMATION","The available price does not come with a confirmed stock count for the proposed purchase.",
            selected,owned,need,safe,budget,expiry,baseline,normalFits?baseline:null);
        if(best==null) return advice("DO_NOT_BUY_EXTRA","No extra pack option met confirmed stock, use-by time, storage, item budget and comparable price rules.",
            selected,owned,need,safe,budget,expiry,baseline,normalFits?baseline:null);
        String action=best.packCount()>1?"BUY_MULTIPLE_PACKS":"BUY_LARGER_PACK";
        String reason=best.variantId().equals(selected.id())?"The confirmed quantity covers planned use after pantry stock; there is no verified unit-price saving.":
            "This compatible pack has a lower price per standard unit and costs less than equivalent normal packs at recorded prices.";
        return advice(action,reason+" Estimates exclude basket fees.",selected,owned,need,safe,budget,expiry,baseline,best);
    }

    List<Product> compatiblePacks(Product selected,String location) {
        var specs=new HashMap<String,List<String>>();
        db.rows("""
            SELECT d.* FROM grocery_variant_details d JOIN product_variants v ON v.id=d.variant_id WHERE v.product_id=?
            """,selected.canonicalProductId()).forEach(row->specs.put(text(row,"variant_id"),Arrays.asList(text(row,"flavour"),
                text(row,"formulation"),text(row,"ingredients"),text(row,"dietary_attributes"))));
        var unknown=Arrays.<String>asList(null,null,null,null);
        return catalogue.variants(selected,location).stream().filter(p->Objects.equals(specs.getOrDefault(selected.id(),unknown),
            specs.getOrDefault(p.id(),unknown))).toList();
    }

    BigDecimal pantry(String user,Product product) {
        return db.maybe("SELECT amount_base FROM user_pantry WHERE user_id=? AND product_id=? AND base_unit=?",user,product.canonicalProductId(),
            product.baseUnit()).map(r->decimal(r,"amount_base")).orElse(BigDecimal.ZERO);
    }

    Long budget(String user,Household home,Long requested) {
        Long balance=budgets.budget(user,BudgetService.month()).remainingMinor();
        return java.util.stream.Stream.of(requested,home==null?null:home.shoppingBudgetMinor(),balance).filter(Objects::nonNull)
            .map(v->Math.max(0,v)).min(Long::compareTo).orElse(null);
    }

    private static Purchase purchase(Product product,CurrentOffers.Quote quote,int count) {
        return new Purchase(product.id(),product.quantity(),count,product.amountBase().multiply(BigDecimal.valueOf(count)),quote,quote.priceMinor()*count);
    }
    private static boolean cheaperThanNormal(Purchase candidate,Purchase normal) {
        var cost=BigDecimal.valueOf(candidate.subtotalMinor());var base=BigDecimal.valueOf(normal.subtotalMinor());
        return cost.multiply(normal.amount()).compareTo(base.multiply(candidate.amount()))<0
            &&cost.compareTo(base.multiply(candidate.amount().divide(normal.amount(),0,RoundingMode.CEILING)))<0;
    }
    private Advice advice(String action,String reason,Product product,BigDecimal pantry,BigDecimal need,BigDecimal capacity,Long budget,Integer expiry,Purchase normal,Purchase recommendation) {
        return new Advice(action,reason,product.id(),product.name(),product.baseUnit(),pantry,need,capacity,budget,expiry,normal,recommendation,catalogue.sample());
    }
}
