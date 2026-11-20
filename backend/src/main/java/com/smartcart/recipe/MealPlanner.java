package com.smartcart.recipe;

import com.smartcart.catalogimport.ProductMatching;
import com.smartcart.dto.Models.*;
import com.smartcart.exception.ApiException;
import com.smartcart.repository.Db;
import com.smartcart.service.CartService;
import com.smartcart.service.CatalogService;
import com.smartcart.service.CurrentOffers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.smartcart.repository.Db.*;

@Service
public class MealPlanner {
    private static final List<String> RETAILERS=List.of("blinkit","zepto");
    private record StarterLink(String name,String unit,String productId,String productName,String brand) {}
    // Reviewed against the bundled CartIQ catalogue, and rechecked against current product identity before use.
    private static final List<StarterLink> STARTER_LINKS=List.of(
        new StarterLink("eggs","piece","eggs","Farm Fresh Eggs","Fresho"),
        new StarterLink("cooking oil","ml","oil","Sunlite Refined Oil","Fortune"),
        new StarterLink("tomato","g","tomatoes","Fresh Tomatoes","Fresh harvest"),
        new StarterLink("basmati rice","g","rice","Basmati Rice","India Gate"),
        new StarterLink("onion","g","onion","Fresh Onion","Fresh harvest"),
        new StarterLink("salt","g","blinkit-105","Vacuum Evaporated Iodised Salt","Tata Salt"));
    private final Db db;
    private final RecipeLibrary library;
    private final CatalogService catalogue;
    private final CartService carts;

    public MealPlanner(Db db,RecipeLibrary library,CatalogService catalogue,CartService carts) {
        this.db=db; this.library=library; this.catalogue=catalogue; this.carts=carts;
    }

    public record Meal(@NotBlank @Size(max=60) String recipeId,@Min(1) @Max(7) int day,@Min(1) @Max(20) int servings,
        @Size(max=20) Set<@NotNull @Min(0) @Max(19) Integer> alreadyHave) {
        public Meal(String recipeId,int day,int servings) { this(recipeId,day,servings,Set.of()); }
    }
    public record Request(@NotBlank @Size(max=60) String locationId,@NotNull @Size(min=1,max=14) List<@NotNull @Valid Meal> meals,
        @PositiveOrZero @Max(100000000) Long budgetMinor,@Size(max=100) Map<@NotBlank @Size(max=120) String,@NotNull @PositiveOrZero @Max(200000) BigDecimal> onHand,
        @Size(max=100) Map<@NotBlank @Size(max=120) String,@NotBlank @Size(max=60) String> substitutions) {}
    public record CartRequest(@NotNull @Valid Request plan,@NotBlank @Pattern(regexp="best|blinkit|zepto") String retailer) {}
    public record Swap(String id,String name,BigDecimal ratio) {}
    public record Pack(String variantId,String productName,String brand,String label,int count,long eachMinor,Instant observedAt) {}
    public record PackRef(String variantId,String productName,String brand,String label) {}
    public record Choice(String retailer,long subtotalMinor,List<Pack> packs) {}
    public record Ingredient(String key,String name,String unit,BigDecimal required,BigDecimal pantry,BigDecimal missing,
        List<Swap> substitutions,List<Choice> choices,List<PackRef> cataloguePacks) {}
    public record Basket(String retailer,boolean complete,int missingIngredients,long pricedSubtotalMinor,Long estimatedSubtotalMinor) {}
    public record Plan(List<Meal> meals,List<Ingredient> ingredients,List<Basket> baskets,Long budgetMinor,String budgetStatus,boolean sample) {}
    private record Key(String name,String unit) { String label() {return name+"/"+unit;} }
    private record Need(String name,BigDecimal amount) {}
    private record Option(Product product,long price,Instant at,int stock) {}

    @Transactional(readOnly=true)
    public Plan plan(String owner,Request request) {
        return plan(owner,request,null);
    }

    @Transactional(readOnly=true)
    public Plan plan(String owner,Request request,CurrentOffers verified) {
        catalogue.requireLocation(request.locationId());
        var needs=new LinkedHashMap<Key,Need>();
        var confirmedOnHand=new HashMap<Key,BigDecimal>();
        for (Meal meal:request.meals()) {
            var recipe=library.find(owner,meal.recipeId()).recipe();
            var checked=meal.alreadyHave()==null?Set.<Integer>of():meal.alreadyHave();
            if (checked.stream().anyMatch(i -> i<0 || i>=recipe.ingredients().size())) throw ApiException.bad("Unknown recipe ingredient.");
            for (int i=0; i<recipe.ingredients().size(); i++) {
                var part=recipe.ingredients().get(i);
                var amount=RecipeCard.scale(part,meal.servings(),recipe.servings());
                Key key=new Key(ProductMatching.normalized(part.name()),amount.unit());
                if (checked.contains(i)) confirmedOnHand.merge(key,amount.amount(),BigDecimal::add);
                needs.merge(key,new Need(part.name(),amount.amount()),(a,b)->new Need(a.name(),a.amount().add(b.amount())));
            }
        }
        if (needs.size()>100) throw ApiException.bad("Please plan at most 100 distinct ingredients at once.");
        var pantry=new HashMap<String,BigDecimal>();
        db.rows("SELECT product_id,base_unit,amount_base FROM user_pantry WHERE user_id=?",owner)
            .forEach(row->pantry.put(text(row,"product_id")+"/"+text(row,"base_unit"),decimal(row,"amount_base")));
        Instant now=Instant.now();
        var ingredientIds=ingredientIds();
        var ingredients=new ArrayList<Ingredient>();
        for (var entry:needs.entrySet()) {
            Key key=entry.getKey(); Need need=entry.getValue();
            var known=ingredientIds.getOrDefault(key.name(),List.of());
            var id=known.size()==1?known.get(0):null;
            var swaps=id==null?List.<Swap>of():swaps(id,key.unit());
            var selection=request.substitutions()==null?null:request.substitutions().get(key.label());
            if (selection!=null) {
                var swap=swaps.stream().filter(s->s.id().equals(selection)).findFirst()
                    .orElseThrow(()->ApiException.bad("This substitution has not been approved for the ingredient and unit."));
                id=swap.id();
                need=new Need(swap.name(),need.amount().multiply(swap.ratio()).setScale(3,RoundingMode.CEILING));
            }
            var products=mappedProducts(id);
            if (known.isEmpty() && selection==null) products=starterProducts(key);
            BigDecimal manual=request.onHand()==null?null:request.onHand().get(key.label());
            BigDecimal owned=BigDecimal.ZERO;
            // Reserve against this plan only. An explicit amount replaces the stored estimate for this ingredient.
            BigDecimal reservation=manual==null?need.amount():manual.min(need.amount());
            for (String product:products) {
                String stockKey=product+"/"+key.unit();
                BigDecimal stock=pantry.getOrDefault(stockKey,BigDecimal.ZERO);
                BigDecimal used=stock.min(reservation.subtract(owned));
                owned=owned.add(used);
                pantry.put(stockKey,stock.subtract(used));
            }
            if (manual!=null) owned=reservation;
            else if (selection==null) owned=owned.max(confirmedOnHand.getOrDefault(key,BigDecimal.ZERO));
            BigDecimal missing=need.amount().subtract(owned);
            var candidatePacks=missing.signum()==0?List.<List<Product>>of():products.stream()
                .map(product->catalogue.packsForProduct(product,key.unit(),request.locationId())).toList();
            var choices=choices(candidatePacks,request.locationId(),missing,now,verified,owner);
            var refs=candidatePacks.stream().flatMap(Collection::stream).limit(20)
                .map(p->new PackRef(p.id(),p.name(),p.brand(),p.quantity())).toList();
            ingredients.add(new Ingredient(key.label(),need.name(),key.unit(),need.amount(),owned,missing,swaps,choices,refs));
        }
        var baskets=new ArrayList<Basket>();
        baskets.add(basket(ingredients,"best"));
        RETAILERS.forEach(retailer->baskets.add(basket(ingredients,retailer)));
        var best=baskets.get(0);
        String budget=request.budgetMinor()==null?"NO_BUDGET":best.pricedSubtotalMinor()>request.budgetMinor()?"OVER_BUDGET":
            best.complete()?"WITHIN_ESTIMATED_SUBTOTAL":"INSUFFICIENT_PRICE_DATA";
        return new Plan(request.meals(),List.copyOf(ingredients),List.copyOf(baskets),request.budgetMinor(),budget,catalogue.sample());
    }

    @Transactional
    public List<CartItem> add(String owner,CartRequest request) {
        carts.lockOwner(owner);
        Plan computed=plan(owner,request.plan());
        var choice=computed.baskets().stream().filter(b->b.retailer().equals(request.retailer())).findFirst().orElseThrow();
        if (!choice.complete()) throw ApiException.bad("Some ingredients lack a comparable pack. Review the shopping list first.");
        var variants=new LinkedHashMap<String,Integer>();
        for (var row:computed.ingredients()) if (row.missing().signum()>0) {
            pick(row,request.retailer()).orElseThrow().packs().forEach(pack->variants.merge(pack.variantId(),pack.count(),Integer::sum));
        }
        var existing=carts.list(owner);
        for (var variant:variants.entrySet()) {
            var prior=existing.stream().filter(c->c.productId().equals(variant.getKey())&&c.locationId().equals(request.plan().locationId())).findFirst();
            int quantity=variant.getValue()+prior.map(CartItem::quantity).orElse(0);
            if (quantity>99) throw ApiException.bad("One pack exceeds the cart quantity limit.");
            var prefs=prior.map(CartItem::preferences).orElse(new Preferences("ESSENTIAL",null,null,null,null,null,null,false));
            carts.save(owner,new CartInput(variant.getKey(),request.plan().locationId(),quantity,prefs));
        }
        return carts.list(owner);
    }

    private Map<String,List<String>> ingredientIds() {
        var rows=db.rows("SELECT id,name FROM grocery_ingredients ORDER BY id LIMIT 10001");
        if (rows.size()>10000) throw ApiException.bad("The ingredient catalogue exceeds the current lookup limit.");
        var byName=new HashMap<String,List<String>>();
        rows.forEach(row->byName.computeIfAbsent(ProductMatching.normalized(text(row,"name")),key->new ArrayList<>()).add(text(row,"id")));
        return byName;
    }

    private List<String> mappedProducts(String id) {
        return id==null?List.of():db.rows("""
            SELECT m.product_id FROM grocery_ingredient_products m JOIN products p ON p.id=m.product_id
            WHERE m.ingredient_id=? AND p.active=TRUE ORDER BY m.product_id
            """,id).stream().map(row->text(row,"product_id")).toList();
    }

    private List<String> starterProducts(Key key) {
        return STARTER_LINKS.stream().filter(link->link.name().equals(key.name())&&link.unit().equals(key.unit()))
            .filter(link->db.maybe("SELECT name,brand FROM products WHERE active=TRUE AND id=?",link.productId())
                .filter(row->link.productName().equals(text(row,"name"))&&link.brand().equals(text(row,"brand"))).isPresent())
            .map(StarterLink::productId).toList();
    }

    private List<Swap> swaps(String id,String unit) {
        return db.rows("""
            SELECT s.substitute_id,g.name,s.quantity_ratio FROM grocery_ingredient_substitutions s
            JOIN grocery_ingredients g ON g.id=s.substitute_id WHERE s.ingredient_id=? AND s.base_unit=? ORDER BY s.substitute_id
            """,id,unit).stream().map(r->new Swap(text(r,"substitute_id"),text(r,"name"),decimal(r,"quantity_ratio"))).toList();
    }

    private List<Choice> choices(List<List<Product>> products,String location,BigDecimal missing,Instant now,CurrentOffers verified,String owner) {
        var best=new LinkedHashMap<String,Choice>();
        for (var product:products) {
            var byRetailer=new HashMap<String,List<Option>>();
            for (var pack:product) {
                if(verified!=null) {
                    for(var q:verified.forPack(pack,location,owner))
                        byRetailer.computeIfAbsent(q.retailer(),r->new ArrayList<>()).add(new Option(pack,q.priceMinor(),q.observedAt(),q.packLimit()));
                } else for(String retailer:RETAILERS) {
                    option(pack,retailer,location,now).ifPresent(value->byRetailer.computeIfAbsent(retailer,r->new ArrayList<>()).add(value));
                }
            }
            for (String retailer:RETAILERS) {
                var candidate=bundle(retailer,byRetailer.getOrDefault(retailer,List.of()),missing);
                if (candidate!=null && (!best.containsKey(retailer)||candidate.subtotalMinor()<best.get(retailer).subtotalMinor())) best.put(retailer,candidate);
            }
        }
        return List.copyOf(best.values());
    }

    private Optional<Option> option(Product pack,String retailer,String location,Instant now) {
        var legacy=pack.offers().stream().filter(o->retailer.equals(o.platform())).findFirst();
        var observed=db.maybe("""
            SELECT o.observed_at,o.price_minor,o.stock_status FROM approved_price_observations o
            JOIN price_retention_grants g ON g.id=o.grant_id JOIN platform_products l ON l.id=o.listing_id
            JOIN grocery_platforms p ON p.id=l.platform_id
            WHERE o.variant_id=? AND o.location_id=? AND l.platform_id=? AND p.enabled=TRUE
              AND g.revoked_at IS NULL AND g.retain_until>? AND g.history_allowed=TRUE AND o.observed_at<=?
            ORDER BY o.observed_at DESC LIMIT 1
            """,pack.id(),location,retailer,now,now);
        if (observed.isPresent()) {
            var row=observed.get(); Instant at=time(row,"observed_at");
            if (legacy.isEmpty() || !legacy.get().updatedAt().isAfter(at) || "SAMPLE".equals(legacy.get().verificationStatus())) {
                Long price=number(row,"price_minor");
                return price!=null&&price<=100000000L&&catalogue.freshness().fresh(at,now)&&"AVAILABLE".equals(text(row,"stock_status"))
                    ?Optional.of(new Option(pack,price,at,99)):Optional.empty();
            }
        }
        return legacy.filter(catalogue::eligible).filter(o->o.priceMinor()<=100000000L).map(o->new Option(pack,o.priceMinor(),o.updatedAt(),99));
    }

    private static Choice bundle(String retailer,List<Option> options,BigDecimal missing) {
        var candidates=options.stream().sorted(Comparator.comparingLong(o->o.price())).limit(20).toList();
        Choice best=null;
        for (var first:candidates) for (var second:candidates) {
            boolean same=first==second;
            for (int a=0;a<=(same?0:first.stock());a++) {
                var outstanding=missing.subtract(first.product().amountBase().multiply(BigDecimal.valueOf(a))).max(BigDecimal.ZERO);
                BigDecimal required=outstanding.divide(second.product().amountBase(),0,RoundingMode.CEILING);
                if (required.compareTo(BigDecimal.valueOf(second.stock()))>0) continue;
                int b=required.intValueExact();
                long cost=first.price()*a+second.price()*b;
                if (best==null || cost<best.subtotalMinor()) {
                    var packs=new ArrayList<Pack>();
                    if (a>0) packs.add(pack(first,a));
                    if (b>0) packs.add(pack(second,b));
                    best=new Choice(retailer,cost,List.copyOf(packs));
                }
                if (b==0) break;
            }
        }
        return best;
    }

    private static Pack pack(Option option,int count) {
        var p=option.product();
        return new Pack(p.id(),p.name(),p.brand(),p.quantity(),count,option.price(),option.at());
    }

    private static Optional<Choice> pick(Ingredient row,String retailer) {
        return row.choices().stream().filter(c->retailer.equals("best")||retailer.equals(c.retailer()))
            .min(Comparator.comparingLong(Choice::subtotalMinor));
    }

    private static Basket basket(List<Ingredient> rows,String retailer) {
        int gaps=0; long known=0;
        for (var row:rows) if (row.missing().signum()>0) {
            var choice=pick(row,retailer);
            if (choice.isEmpty()) gaps++;
            else known+=choice.get().subtotalMinor();
        }
        return new Basket(retailer,gaps==0,gaps,known,gaps==0?known:null);
    }
}
