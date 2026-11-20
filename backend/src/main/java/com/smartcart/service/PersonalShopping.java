package com.smartcart.service;

import com.smartcart.catalogimport.ProductMatching;
import com.smartcart.dto.Models.*;
import com.smartcart.recipe.*;
import com.smartcart.repository.Db;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import static com.smartcart.repository.Db.*;

@Service
@Profile("!mysql-local")
public class PersonalShopping {
    public record Alternative(Product product,CurrentOffers.Quote quote,int packCount,long subtotalMinor) {}
    public record Suggestion(Product product,String reason,CurrentOffers.Quote quote,Alternative alternative) {}
    public record RecipeIdea(String id,String name,String cuisine,int missingIngredients,Long estimatedSubtotalMinor) {}
    public record Gap(String name,String reason) {}
    public record Recommendations(List<Suggestion> products,List<RecipeIdea> recipes,List<Gap> pantryGaps,Long budgetMinor,boolean sample) {}
    public record Basket(Optimization comparison,Long budgetMinor,String status,boolean sample) {}
    private record ProfileSignals(Set<String> favorites,Set<String> cart,Set<String> previous,Set<String> brands) {}
    private static final Pattern BUDGET_REQUEST=Pattern.compile("(?:under|within|budget(?: of)?)\\s*(?:₹\\s*|(?:rs\\.?|inr)\\s*)?(\\d[\\d,]{0,6}(?:\\.\\d{1,2})?)(?![\\d,.])");
    private static final Pattern BULK_REQUEST=Pattern.compile("\\bbulk\\b");
    private static final Pattern USE_BY=Pattern.compile("(?:use.by|expir\\w* in)\\s+(\\d{1,3})\\s+days?");
    private final Db db;
    private final CatalogService catalogue;
    private final CartService carts;
    private final BudgetService budgets;
    private final BulkBuying bulk;
    private final CurrentOffers offers;
    private final RecipeLibrary recipes;
    private final MealPlanner meals;
    private final BasketOptimizer optimizer;

    public PersonalShopping(Db db,CatalogService catalogue,CartService carts,BudgetService budgets,BulkBuying bulk,
                            CurrentOffers offers,RecipeLibrary recipes,MealPlanner meals,BasketOptimizer optimizer) {
        this.db=db;this.catalogue=catalogue;this.carts=carts;this.budgets=budgets;this.bulk=bulk;this.offers=offers;
        this.recipes=recipes;this.meals=meals;this.optimizer=optimizer;
    }

    public Recommendations recommend(String owner,String location,Long requestedBudget) {
        catalogue.requireLocation(location);
        var home=bulk.household(owner);
        Long budget=bulk.budget(owner,home,requestedBudget);
        var signals=signals(owner,location);
        var due=new LinkedHashMap<String,String>();
        budgets.replenishment(owner).stream().filter(Replenishment::due).forEach(r->due.put(r.productId(),r.name()));
        var candidates=new LinkedHashMap<String,Product>();
        var planned=ids(db.rows("""
            SELECT MIN(v.id) AS variant_id FROM bulk_buy_preferences b
            JOIN product_variants v ON v.product_id=b.product_id AND v.base_unit=b.base_unit
            WHERE b.user_id=? GROUP BY b.product_id,b.base_unit ORDER BY MIN(v.id) LIMIT 24
            """,owner));
        for(var ids:List.of(signals.cart(),signals.favorites(),due.keySet(),signals.previous(),planned))
            ids.stream().limit(12).forEach(id->addActive(candidates,id,location));
        for(String brand:signals.brands())
            catalogue.products(location,"",null,6,0,brand,null).forEach(p->candidates.putIfAbsent(p.id(),p));
        catalogue.products(location,"",null,32,0).forEach(p->candidates.putIfAbsent(p.id(),p));

        var ranked=candidates.values().stream().filter(p->dietMatches(p,home))
            .sorted(Comparator.<Product>comparingInt(p->score(p,signals,due)).reversed()).toList();
        var suggestions=new ArrayList<Suggestion>();
        var shown=new HashSet<String>();
        var gaps=new ArrayList<Gap>();
        for(var p:ranked) {
            var limits=bulk.limits(owner,p);
            if(limits!=null && home!=null && gaps.size()<6) {
                var use=limits.perPersonDaily().multiply(BigDecimal.valueOf((long)home.householdSize()*limits.planningDays()));
                if(use.compareTo(bulk.pantry(owner,p))>0 && gaps.stream().noneMatch(g->g.name().equals(p.name())))
                    gaps.add(new Gap(p.name(),"Recorded pantry quantity is below planned household use."));
            }
            if(suggestions.size()==8||shown.contains(p.canonicalProductId())) continue;
            var quote=offers.forPack(p,location,owner).stream().findFirst().orElse(null);
            if(quote!=null&&budget!=null&&quote.priceMinor()>budget)continue;
            if(quote==null&&score(p,signals,due)==0)continue;
            shown.add(p.canonicalProductId());
            suggestions.add(new Suggestion(p,reason(p,signals,due),quote,alternative(owner,p,location,quote,1)));
        }
        due.values().stream().distinct().filter(name->gaps.stream().noneMatch(g->g.name().equals(name)))
            .limit(Math.max(0,8-gaps.size())).forEach(name->gaps.add(new Gap(name,"Your configured reminder is due. Check pantry quantity.")));
        return new Recommendations(suggestions,recipeIdeas(owner,location,home,budget),gaps,budget,catalogue.sample());
    }

    public Basket basket(String owner,String location,Long requestedBudget) {
        catalogue.requireLocation(location);
        return basket(owner,cart(owner,location),location,bulk.budget(owner,bulk.household(owner),requestedBudget));
    }

    private Basket basket(String owner,List<BasketLine> lines,String location,Long budget) {
        if(lines.isEmpty()) return new Basket(null,budget,"EMPTY",catalogue.sample());
        if(lines.size()>30) return new Basket(null,budget,"TOO_MANY_LINES",catalogue.sample());
        var result=optimizer.optimize(new BasketRequest(location,lines),offers,owner);
        var choice=result.recommended();
        String status=!choice.feasible()?"INCOMPLETE":budget==null?"BUDGET_NOT_SET":
            choice.totalMinor()<=budget?"WITHIN_BUDGET":result.searchComplete()?"OVER_BUDGET":"LIMIT_REACHED";
        return new Basket(result,budget,status,catalogue.sample());
    }

    private ProfileSignals signals(String owner,String location) {
        var favorites=ids(db.rows("SELECT variant_id FROM saved_products WHERE user_id=? ORDER BY variant_id LIMIT 100",owner));
        var previous=ids(db.rows("""
            SELECT DISTINCT i.variant_id FROM shopping_history_items i JOIN shopping_history h ON h.id=i.history_id
            WHERE h.user_id=? AND h.location_id=? AND (h.sample=FALSE OR ?=TRUE) ORDER BY i.variant_id LIMIT 100
            """,owner,location,catalogue.sample()));
        var brands=new LinkedHashSet<String>();
        db.maybe("SELECT preferred_brands FROM user_preferences WHERE user_id=?",owner).ifPresent(row->
            Arrays.stream(Objects.toString(text(row,"preferred_brands"),"").split(",")).map(String::trim)
                .filter(name->!name.isBlank()).limit(8).map(ProductMatching::normalized).forEach(brands::add));
        var cart=new LinkedHashSet<String>();
        cart(owner,location).forEach(line->cart.add(line.productId()));
        return new ProfileSignals(favorites,cart,previous,brands);
    }

    private List<BasketLine> cart(String owner,String location) {
        return carts.list(owner).stream().filter(line->location.equals(line.locationId()))
            .map(line->new BasketLine(line.productId(),line.quantity())).toList();
    }

    private static Set<String> ids(List<Map<String,Object>> rows) {
        var ids=new LinkedHashSet<String>();
        rows.forEach(row->ids.add(text(row,"variant_id")));
        return ids;
    }

    private void addActive(Map<String,Product> items,String id,String location) {
        if(!items.containsKey(id)&&db.maybe("""
            SELECT v.id FROM product_variants v JOIN products p ON p.id=v.product_id WHERE v.id=? AND p.active=TRUE
            """,id).isPresent()) items.put(id,catalogue.product(id,location));
    }

    private boolean dietMatches(Product p,BulkBuying.Household home) {
        if(home==null||home.diet()==BulkBuying.Diet.ANY)return true;
        String label=home.diet()==BulkBuying.Diet.VEGETARIAN?"vegetarian":"non vegetarian";
        return db.rows("""
            SELECT dietary_attributes FROM grocery_variant_details WHERE variant_id=? AND TRIM(evidence_reference)<>''
            """,p.id()).stream().flatMap(row->Arrays.stream(Objects.toString(text(row,"dietary_attributes"),"").split("[,;|]")))
            .map(ProductMatching::normalized).anyMatch(label::equals);
    }

    private static int score(Product p,ProfileSignals s,Map<String,String> due) {
        return (due.containsKey(p.id())?16:0)+(s.cart().contains(p.id())?8:0)+(s.favorites().contains(p.id())?4:0)
            +(s.previous().contains(p.id())?2:0)+(s.brands().contains(ProductMatching.normalized(p.brand()))?1:0);
    }

    private static String reason(Product p,ProfileSignals s,Map<String,String> due) {
        if(due.containsKey(p.id()))return "Your replenishment reminder is due.";
        if(s.cart().contains(p.id()))return "In your cart for this location.";
        if(s.favorites().contains(p.id()))return "In your saved products.";
        if(s.previous().contains(p.id()))return "In your recorded shopping history.";
        if(s.brands().contains(ProductMatching.normalized(p.brand())))return "One of your preferred brands.";
        return "Fresh recorded offer in the catalogue for this location.";
    }

    private Alternative alternative(String owner,Product p,String location,CurrentOffers.Quote original,int originalCount) {
        if(original==null)return null;
        var quantity=p.amountBase().multiply(BigDecimal.valueOf(originalCount));
        var options=new ArrayList<Alternative>();
        for(var variant:bulk.compatiblePacks(p,location)) {
            if(variant.id().equals(p.id()))continue;
            var divided=quantity.divideAndRemainder(variant.amountBase());
            if(divided[1].signum()!=0||divided[0].signum()<=0||divided[0].compareTo(BigDecimal.valueOf(99))>0)continue;
            int packs=divided[0].intValueExact();
            for(var price:offers.forPack(variant,location,owner)) {
                long subtotal=price.priceMinor()*packs;
                if((price.stockPacks()==null?packs==1:price.stockPacks()>=packs)&&subtotal<original.priceMinor()*originalCount)
                    options.add(new Alternative(variant,price,packs,subtotal));
            }
        }
        return options.stream().min(Comparator.comparingLong(Alternative::subtotalMinor)).orElse(null);
    }

    private List<RecipeIdea> recipeIdeas(String owner,String location,BulkBuying.Household home,Long budget) {
        var diet=home==null||home.diet()==BulkBuying.Diet.ANY?null:RecipeCard.Diet.valueOf(home.diet().name());
        return recipes.browse("",home==null?null:home.preferredCuisine(),null,diet,null,null).stream().limit(8).map(entry->{
            var recipe=entry.recipe();
            int servings=home==null?recipe.servings():Math.min(home.householdSize(),20);
            var plan=meals.plan(owner,new MealPlanner.Request(location,List.of(new MealPlanner.Meal(entry.id(),1,servings)),null,Map.of(),Map.of()),offers);
            Long cost=plan.baskets().stream().map(MealPlanner.Basket::estimatedSubtotalMinor).filter(Objects::nonNull).min(Long::compareTo).orElse(null);
            int missing=(int)plan.ingredients().stream().filter(i->i.missing().signum()>0).count();
            return new RecipeIdea(entry.id(),recipe.name(),recipe.cuisine(),missing,cost);
        }).filter(r->budget==null||r.estimatedSubtotalMinor()==null||r.estimatedSubtotalMinor()<=budget)
            .sorted(Comparator.comparingInt(RecipeIdea::missingIngredients)).limit(5).toList();
    }

    public AssistantAnswer answer(String owner,AssistantInput input) {
        String question=input.message().toLowerCase(Locale.ROOT);
        String location=input.locationId();
        var limit=BUDGET_REQUEST.matcher(question);
        boolean under=limit.find();
        boolean weekly=(question.contains("this week")||question.contains("weekly"))&&(question.contains("buy")||question.contains("grocer")||question.contains("shop"));
        boolean pantry=question.contains("pantry") && (question.contains("recipe")||question.contains("cook")||question.contains("prepare"));
        boolean bulkRequest=BULK_REQUEST.matcher(question).find();
        boolean alternatives=question.contains("alternative")&&(question.contains("shopping list")||question.contains("cart"));
        if(!weekly&&!pantry&&!bulkRequest&&!alternatives&&!under)return null;
        if(bulkRequest) {
            var useBy=USE_BY.matcher(question);
            Integer expiry=useBy.find()?Integer.valueOf(useBy.group(1)):null;
            if(expiry!=null&&(expiry<1||expiry>365))
                return reply("Enter confirmed use-by days from 1 to 365.",List.of(),List.of());
            // A valid free-text date applies only to a single exact cart item.
            var basket=cart(owner,location);
            Integer confirmedDays=basket.size()==1?expiry:null;
            var advice=basket.stream().limit(6)
                .map(line->bulk.advise(owner,catalogue.product(line.productId(),location),location,confirmedDays)).toList();
            String text=advice.isEmpty()?"Add the normal packs you buy to your cart, then enter household use and storage in Bulk buy."
                :String.join(" ",advice.stream().map(a->a.name()+": "+a.reason()
                    +(a.recommended()==null?"":" "+a.recommended().packCount()+" × "+a.recommended().pack()+"; "+quote(a.recommended().quote())+".")).toList());
            return reply(text+(confirmedDays==null?" Enter confirmed use-by days per item in Bulk buy to assess extra packs.":""),
                List.of(),List.of("Your cart and bulk-buy settings"));
        }
        Long requested=under?new BigDecimal(limit.group(1).replace(",","")).movePointRight(2).longValueExact():null;
        var lines=cart(owner,location);
        if(alternatives) {
            var found=new ArrayList<Alternative>();
            for(var line:lines.stream().limit(12).toList()) {
                Product p=catalogue.product(line.productId(),location);
                var original=offers.forPack(p,location,owner).stream()
                    .filter(q->q.stockPacks()==null?line.quantity()==1:q.stockPacks()>=line.quantity()).findFirst().orElse(null);
                var option=alternative(owner,p,location,original,line.quantity());
                if(option!=null)found.add(option);
            }
            return reply(found.isEmpty()?"There are no cheaper recorded alternatives of the same product and pack quantity for this cart."
                :String.join(" ",found.stream().map(a->a.product().name()+" ("+a.packCount()+" × "+a.product().quantity()+"): "
                    +money(a.subtotalMinor())+" before fees. Per pack: "+quote(a.quote())+".").toList()),
                found.stream().map(Alternative::product).toList(),List.of("Your cart and fresh compatible offers"));
        }
        var data=recommend(owner,location,requested);
        if(pantry) {
            var ideas=data.recipes().stream().map(r->r.name()+" ("+r.missingIngredients()+" ingredients to check or buy)").toList();
            return reply(ideas.isEmpty()?"No curated recipes match your current preferences and budget."
                :String.join("; ",ideas)+". Open Recipes for quantities and the shopping list.",List.of(),List.of("Curated recipes and your pantry"));
        }
        if(under) {
            boolean proposed=lines.isEmpty();
            if(proposed) lines=planWithinBudget(owner,data,location);
            var result=basket(owner,lines,location,data.budgetMinor());
            var best=result.comparison()==null?null:result.comparison().recommended();
            String text=best==null?"I couldn't confirm a grocery plan within your limit with the recorded prices, stock and fees."
                :best.totalMinor()==null?"A complete total is unavailable. "+best.reason()
                :"The "+(proposed?"suggested":"saved")+" basket totals "+money(best.totalMinor())+" with recorded fees for "+lines.size()+" product lines. "
                    +switch(result.status()) {
                        case "WITHIN_BUDGET" -> "It fits your effective limit of "+money(result.budgetMinor())+".";
                        case "LIMIT_REACHED" -> "The evaluated basket exceeds your limit. The search stopped before it could establish whether a cheaper basket fits.";
                        default -> "It exceeds your effective limit of "+money(result.budgetMinor())+".";
                    };
            var included=new ArrayList<Product>();
            if(proposed) for(var line:lines)included.add(catalogue.product(line.productId(),location));
            return reply(text,included,List.of("Your budget, exact pack offers and retailer fee records"));
        }
        String gaps=String.join(", ",data.pantryGaps().stream().limit(4).map(Gap::name).toList());
        var priced=data.products().stream().filter(s->s.quote()!=null).limit(4).toList();
        String quotes=String.join(" ",priced.stream().map(s->s.product().name()+" ("+s.product().quantity()+"): "+quote(s.quote())+".").toList());
        return reply((gaps.isEmpty()?"No configured pantry checks are due. ":"Check these against your pantry: "+gaps+". ")
            +(quotes.isEmpty()?"Fresh prices are unavailable for these suggestions.":quotes+" Prices exclude basket fees."),
            priced.stream().map(Suggestion::product).toList(),List.of("Your preferences, pantry and recorded offers"));
    }

    private List<BasketLine> planWithinBudget(String owner,Recommendations data,String location) {
        var candidates=data.products().stream().filter(s->s.quote()!=null).limit(8).map(s->new BasketLine(s.product().id(),1)).toList();
        // Seeds with two items let small baskets meet a retailer's minimum order.
        for(int first=0;first<candidates.size();first++) {
            for(int second=first;second<candidates.size();second++) {
                var proposed=new ArrayList<BasketLine>();
                proposed.add(candidates.get(first));
                if(second!=first)proposed.add(candidates.get(second));
                if(!"WITHIN_BUDGET".equals(basket(owner,proposed,location,data.budgetMinor()).status()))continue;
                for(var extra:candidates) {
                    if(proposed.size()==5)break;
                    if(proposed.contains(extra))continue;
                    proposed.add(extra);
                    if(!"WITHIN_BUDGET".equals(basket(owner,proposed,location,data.budgetMinor()).status()))proposed.remove(proposed.size()-1);
                }
                return proposed;
            }
        }
        return List.of();
    }

    private AssistantAnswer reply(String text,List<Product> products,List<String> sources) {
        return new AssistantAnswer(text,"rules",products,sources,catalogue.sample());
    }
    private static String quote(CurrentOffers.Quote quote) {
        return money(quote.priceMinor())+" on "+quote.retailer()+" (recorded "+quote.observedAt()+")";
    }
    private static String money(long paise) { return "₹"+BigDecimal.valueOf(paise,2).toPlainString(); }
}
