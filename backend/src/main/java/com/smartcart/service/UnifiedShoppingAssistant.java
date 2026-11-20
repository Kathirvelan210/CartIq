package com.smartcart.service;

import com.smartcart.dto.Models.*;
import com.smartcart.grocery.PricePrediction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!mysql-local")
public class UnifiedShoppingAssistant {
    public enum Intent { PRODUCT, RECIPES, PANTRY, WEEKLY_PLAN, BULK, PERSONALIZED, BASKET, BUDGET, TIMING, HELP }
    public record Message(@NotBlank @Size(max=1000) String message,@Valid WeeklyShopping.Draft draft,
        @Size(max=60) String variantId,@Min(1) @Max(365) Integer daysBeforeExpiry) {}
    public record ProductEvidence(String variantId,String title,String pack,List<CurrentOffers.Quote> offers) {}
    public record TimingEvidence(String variantId,String retailer,long lowMinor,long highMinor,
        java.time.LocalDate date,String modelVersion,PricePrediction.Advice advice) {}
    public record Reply(Intent intent,String method,String text,List<ProductEvidence> products,List<Pantry> pantry,
        WeeklyShopping.Preview plan,BulkBuying.Advice bulk,PersonalShopping.Recommendations suggestions,TimingEvidence timing,
        boolean sample,UUID feedbackId) {}
    private final WeeklyShopping weekly;
    private final PersonalShopping personal;
    private final CatalogService catalogue;
    private final CurrentOffers offers;
    private final RecipeService recipes;
    private final BulkBuying bulk;
    private final CartService cart;
    private final PricePrediction prediction;

    public UnifiedShoppingAssistant(WeeklyShopping weekly,PersonalShopping personal,CatalogService catalogue,
        CurrentOffers offers,RecipeService recipes,BulkBuying bulk,CartService cart,PricePrediction prediction) {
        this.weekly=weekly;this.personal=personal;this.catalogue=catalogue;
        this.offers=offers;this.recipes=recipes;this.bulk=bulk;this.cart=cart;this.prediction=prediction;
    }

    public Reply answer(String owner,String location,Message input) {
        return answer(owner,location,input,null);
    }
    public Reply answer(String owner,String location,Message input,Intent suggested) {
        catalogue.requireLocation(location);
        var intent=suggested==null?detect(input.message()):suggested;
        var products=new ArrayList<ProductEvidence>();
        List<Pantry> pantry=List.of();
        WeeklyShopping.Preview plan=null;
        BulkBuying.Advice bulkAdvice=null;
        PersonalShopping.Recommendations suggestions=null;
        TimingEvidence timing=null;
        String explanation;
        switch(intent) {
            case WEEKLY_PLAN, BASKET, BUDGET -> {
                var workspace=weekly.workspace(owner,location);
                var draft=input.draft()==null?workspace.draft():input.draft();
                if(draft.meals().isEmpty()&&draft.items().isEmpty()&&!workspace.cart().isEmpty())
                    draft=new WeeklyShopping.Draft(List.of(),workspace.cart(),draft.budgetMinor(),draft.diet(),Map.of());
                if(intent==Intent.BUDGET) {
                    Long raisedLimit=budgetFrom(input.message());
                    if(raisedLimit!=null) {
                        long cap=draft.budgetMinor()==null?raisedLimit:Math.min(raisedLimit,draft.budgetMinor());
                        draft=new WeeklyShopping.Draft(draft.meals(),draft.items(),cap,draft.diet(),draft.onHand());
                    }
                }
                if(draft.meals().isEmpty()&&draft.items().isEmpty()){
                    suggestions=personal.recommend(owner,location,draft.budgetMinor());
                    explanation="Select meals or add exact packs to price a plan. Browse the account suggestions below to get started.";
                } else {
                    plan=weekly.preview(owner,location,draft);
                    explanation="I checked the selected quantities against the pantry and recorded retailer offers. "+plan.comparison().recommended().reason();
                }
            }
            case PANTRY -> {
                pantry=recipes.pantry(owner).stream().limit(30).toList();
                explanation=pantry.isEmpty()?"Your pantry has no saved quantities. You can confirm on-hand amounts in the plan.":"Here is the quantity you last saved for each pantry item. Confirm quantities before shopping.";
            }
            case RECIPES, PERSONALIZED -> {
                suggestions=personal.recommend(owner,location,input.draft()==null?null:input.draft().budgetMinor());
                explanation=intent==Intent.RECIPES?"These curated recipe suggestions use your saved pantry and account preferences. Add meals to check quantities together."
                    :"These suggestions use your saved products, preferences and recent selections. Check pack sizes and current recorded prices.";
            }
            case BULK -> {
                if(input.variantId()==null||input.variantId().isBlank())explanation="Choose the normal pack in Bulk-buy check. Include confirmed use-by days if you know them.";
                else {
                    var selected=catalogue.product(input.variantId(),location);
                    bulkAdvice=bulk.advise(owner,selected,location,input.daysBeforeExpiry());
                    explanation=bulkAdvice.reason();
                }
            }
            case PRODUCT, TIMING -> {
                String query=productQuery(input.message());
                List<Product> matches=query.isBlank()?List.of():catalogue.products(location,query,null,5,0);
                for(var product:matches)products.add(new ProductEvidence(product.id(),product.name(),product.quantity(),offers.forPack(product,location,owner)));
                if(intent==Intent.TIMING) {
                    var selected=cart.list(owner).stream().filter(line->location.equals(line.locationId())).toList();
                    long targetMatches=products.stream().filter(p->selected.stream().anyMatch(line->line.productId().equals(p.variantId())
                        &&line.preferences().targetPriceMinor()!=null&&p.offers().stream().anyMatch(q->q.packLimit()>=line.quantity()&&q.priceMinor()<=line.preferences().targetPriceMinor()))).count();
                    timing=targetMatches==0?timing(owner,location,products,selected):null;
                    explanation=targetMatches>0?"At least one exact selected pack meets your saved price target at the requested quantity. Base essential purchases on need and availability."
                        :timing==null?"I could not confirm that an exact selected pack meets a saved price target. This check has no approved forecast to use. Base essential purchases on need and current availability."
                        :timing.advice().reason();
                } else {
                    explanation=products.isEmpty()?"I could not match a catalogue product. Try a short product or brand name."
                        :"Compare retailers within a single exact pack. Prices and unit prices below exclude basket fees. Empty offers mean current price or availability is unconfirmed.";
                }
            }
            default -> explanation="Try a grocery name, “What can I cook?”, “Show my pantry”, “Compare my basket”, or “Plan groceries under ₹1,000”.";
        }
        return new Reply(intent,suggested!=null?"GEMINI_ROUTED":timing==null?"RULE_BASED":"MODEL_ASSISTED",explanation,List.copyOf(products),pantry,plan,bulkAdvice,suggestions,timing,catalogue.sample(),UUID.randomUUID());
    }

    private TimingEvidence timing(String owner,String location,List<ProductEvidence> products,List<CartItem> cartLines) {
        for(var product:products) for(var quote:product.offers()) {
            if(!Set.of("blinkit","zepto","instamart").contains(quote.retailer()))continue;
            int count=cartLines.stream().filter(line->line.productId().equals(product.variantId())).findFirst().map(CartItem::quantity).orElse(1);
            if(quote.packLimit()<count)continue;
            var fit=prediction.approvedTiming(owner,new PricePrediction.Scope(product.variantId(),quote.retailer(),location));
            if(fit.isEmpty())continue;
            var result=fit.get();
            var observed=result.latestObservation();
            if(observed!=null&&Objects.equals(observed.priceMinor(),quote.priceMinor())&&observed.observedAt().equals(quote.observedAt()))
                return new TimingEvidence(product.variantId(),quote.retailer(),result.lowMinor(),result.highMinor(),
                    result.forecastDate(),result.modelVersion(),result.advice());
        }
        return null;
    }

    static Intent detect(String input) {
        String text=input.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}₹.]+"," ");
        if(text.matches(".*\\b(buy now|buy today|wait|price target)\\b.*"))return Intent.TIMING;
        if(text.matches(".*\\b(bulk|larger pack|multiple packs)\\b.*"))return Intent.BULK;
        if(text.matches(".*\\b(cook|recipes?|meals? ideas)\\b.*"))return Intent.RECIPES;
        if(text.matches(".*\\b(budget|under|within|less than)\\b.*"))return Intent.BUDGET;
        if(text.matches(".*\\b(basket|cart|shopping list)\\b.*"))return Intent.BASKET;
        if(text.matches(".*\\b(weekly|this week|next week|what should i buy|plan)\\b.*"))return Intent.WEEKLY_PLAN;
        if(text.matches(".*\\b(pantry|on hand)\\b.*"))return Intent.PANTRY;
        if(text.matches(".*\\b(alternatives?|personalized|recommend|suggest)\\b.*"))return Intent.PERSONALIZED;
        return productQuery(input).isBlank()?Intent.HELP:Intent.PRODUCT;
    }

    private static String productQuery(String query) {
        return Arrays.stream(query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")).filter(token->!token.isBlank())
            .filter(token->!Set.of("could","you","can","i","for","the","find","where","buy","now","or","wait","today","me",
                "search","compare","cheap","cheapest","which","what","price","prices","of","should","show","and","please","hi","hello","help",
                "my","is","it","target","retailer","on","blinkit","zepto","swiggy","instamart").contains(token))
            .limit(8).reduce((a,b)->a+" "+b).map(s->s.substring(0,Math.min(s.length(),100))).orElse("");
    }

    private static Long budgetFrom(String message) {
        var amount=java.util.regex.Pattern.compile("(?i)(?:under|within|less than|budget(?: of)?)\\s*(?:₹\\s*|(?:rs\\.?|inr)\\s*)?(\\d[\\d,]{0,9}(?:\\.\\d{1,2})?)(?![\\d.,])").matcher(message);
        if(!amount.find())return null;
        long minor=new java.math.BigDecimal(amount.group(1).replace(",","")).movePointRight(2).longValueExact();
        return minor<=100_000_000L?minor:null;
    }
}
