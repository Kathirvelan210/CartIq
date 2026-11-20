package com.smartcart.service;

import com.smartcart.dto.Models.*;
import com.smartcart.integration.IntentClassifier;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.*;

@Service
public class AssistantService {
    private final CatalogService catalog;private final BudgetService budgets;private final CartService carts;private final BasketOptimizer optimizer;private final IntentClassifier classifier;
    private final AnalyticsService analytics;private final DecisionService decisions;
    private final org.springframework.beans.factory.ObjectProvider<PersonalShopping> personal;
    public AssistantService(CatalogService catalog,BudgetService budgets,CartService carts,BasketOptimizer optimizer,
                            IntentClassifier classifier,AnalyticsService analytics,DecisionService decisions,
                            org.springframework.beans.factory.ObjectProvider<PersonalShopping> personal){
        this.catalog=catalog;this.budgets=budgets;this.carts=carts;this.optimizer=optimizer;this.classifier=classifier;
        this.analytics=analytics;this.decisions=decisions;this.personal=personal;
    }
    private static List<String> productWords(String text) {
        return Arrays.asList(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"));
    }

    private static String productQuery(String text) {
        var ignored=Set.of("find","the","cheapest","cheap","cooking","please","show","alternative","alternatives","suggest",
            "product","price","buy","for","where","can","could","you","me","some","what","which","compare","prices","of","at");
        var query=new StringJoiner(" ");
        for(var word:productWords(text)) {
            if(word.length()<2||ignored.contains(word))continue;
            if(query.length()+word.length()+1>100)break;
            query.add(word);
        }
        return query.toString();
    }
    private String money(long p){return "₹"+String.format(Locale.ROOT,"%.2f",p/100.0);}
    public AssistantAnswer answer(String user,AssistantInput input){
        catalog.requireLocation(input.locationId());
        var personalized=personal.getIfAvailable();
        if(personalized!=null) {
            var answer=personalized.answer(user,input);
            if(answer!=null)return answer;
        }
        var intent=classifier.classify(user,input.message());String message=input.message().toLowerCase(Locale.ROOT),text;List<Product> selected=new ArrayList<>();List<String> sources=new ArrayList<>();String location=input.locationId();
        switch(intent.type()){
            case "SPENDING"->{String month=message.contains("last")?java.time.YearMonth.parse(BudgetService.month()).minusMonths(1).toString():BudgetService.month();Budget b=budgets.budget(user,month);text="Your recorded grocery spending for "+month+" is "+money(b.spentMinor())+". "+(b.limitMinor()==null?"No budget is set for that month.":"Your budget is "+money(b.limitMinor())+".");sources.add("Your budget transactions for "+month);}
            case "REPLENISH"->{var due=budgets.replenishment(user).stream().filter(Replenishment::due).toList();text=due.isEmpty()?"No configured replenishment items are due. Add consumption cycles to make this useful.":"Check your pantry for "+String.join(", ",due.stream().map(Replenishment::name).toList())+". These are estimates from your configured cycles, not confirmed shortages.";sources.add("Your replenishment records");}
            case "BASKET"->{
                var items=carts.list(user).stream().filter(c->c.locationId().equals(location))
                    .map(c->new BasketLine(c.productId(),c.quantity())).toList();
                if(items.isEmpty()) text="Your cart is empty for this location. Add products before comparing complete baskets.";
                else {
                    Optimization o=optimizer.optimize(new BasketRequest(location,items));
                    var best=o.recommended();
                    if(!best.feasible()) text=best.reason();
                    else {
                        var retailers=best.assignments().stream().map(Assignment::platform).distinct().toList();
                        text="The best evaluated complete basket costs "+money(best.totalMinor())+" at "
                            +String.join(" + ",retailers)+", including known delivery and handling fees.";
                        if(o.savingsMinor()!=null&&o.savingsMinor()>0) text+=" It costs "+money(o.savingsMinor())
                            +" less than the cheapest feasible single-retailer basket.";
                        if(!o.searchComplete()) text+=" The search hit its evaluation limit.";
                    }
                }
                sources.add("Your cart, current location offers and configured fee evidence");
            }
            case "PRICE_CHANGES"->{
                var increases=analytics.recentIncreases(location);
                text=increases.isEmpty()?"There are no recorded increases from comparable observations at this location."
                    :increases.stream().map(change->change.name()+" ("+change.pack()+") on "+change.platform()
                        +" increased from "+money(change.before())+" to "+money(change.after())+".")
                        .collect(java.util.stream.Collectors.joining(" "));
                for(var change:increases) sources.add(change.platform()+" · "+change.name()+" · "
                    +change.previousAt()+" to "+change.observedAt());
                if(increases.isEmpty()) sources.add("Recorded price history for this location");
            }
            case "BUY_NOW"->{
                var advice=carts.list(user).stream().filter(item->item.locationId().equals(location))
                    .map(decisions::decide).filter(decision->"BUY_NOW".equals(decision.action())&&decision.priceMinor()!=null)
                    .limit(5).toList();
                if(advice.isEmpty()) text="No items in your cart have a current buy-now recommendation at this location.";
                else {
                    var descriptions=new ArrayList<String>();
                    for(var decision:advice){
                        var product=catalog.product(decision.productId(),location);
                        selected.add(product);
                        descriptions.add(product.name()+" ("+product.quantity()+") at "+money(decision.priceMinor())
                            +" per pack. "+decision.reason());
                    }
                    text=String.join(" ",descriptions);
                }
                sources.add("Your cart preferences and current catalogue offers");
            }
            case "BUDGET"->{Matcher matcher=Pattern.compile("(?:₹|rs\\.?\\s*)?(\\d[\\d,]*(?:\\.\\d{1,2})?)").matcher(message);long limit=matcher.find()?Math.round(Double.parseDouble(matcher.group(1).replace(",",""))*100):100000;for(var essential:Map.of("milk","ml","rice","g","oil","ml","eggs","piece").entrySet()){
                catalog.products(location,essential.getKey(),null,100,0).stream()
                    .filter(p->p.baseUnit().equals(essential.getValue())&&productWords(p.name()).contains(essential.getKey())&&!selected.contains(p))
                    .filter(p->catalog.best(p).isPresent())
                    .min(Comparator.comparingLong(p->catalog.best(p).orElseThrow().priceMinor())).ifPresent(selected::add);
            }if(selected.isEmpty())text="There are no fresh available essential offers for this location.";else{Optimization o=optimizer.optimize(new BasketRequest(location,selected.stream().map(p->new BasketLine(p.id(),1)).toList()));text=!o.recommended().feasible()?o.recommended().reason():o.recommended().totalMinor()>limit?"The available essentials basket costs "+money(o.recommended().totalMinor())+", above your "+money(limit)+" limit. I have kept those essentials in the plan; review pack sizes or revise the budget.":"A starter basket with one pack of each available essential costs "+money(o.recommended().totalMinor())+" with configured fees, within "+money(limit)+". Adjust it for your household's dietary and quantity needs.";}sources.add("Current catalogue prices and basket fee rules");}
            default->{String query=productQuery(intent.query().isBlank()?message:intent.query());
                List<Product> matches=query.isBlank()?List.of():catalog.products(location,query,null,100,0).stream()
                    .filter(p->catalog.best(p).isPresent()).sorted(Comparator.comparingLong(p->catalog.best(p).orElseThrow().priceMinor())).toList();
                if(matches.isEmpty()) text="I could not match that request to a product with a fresh available price. Try a product or brand name such as milk, rice or Fortune oil.";
                else {
                    Product p=matches.get(0);Offer o=catalog.best(p).orElseThrow();
                    if(intent.type().equals("ALTERNATIVE")){
                        selected.addAll(catalog.variants(p,location).stream().filter(variant->!variant.id().equals(p.id()))
                            .filter(variant->catalog.best(variant).isPresent()).limit(4).toList());
                        text=selected.isEmpty()?"No other pack of "+p.name()+" has a fresh price at this location."
                            :"These are other available packs of "+p.name()+". Check their quantities and unit prices.";
                        for(var alternative:selected){
                            var offer=catalog.best(alternative).orElseThrow();
                            sources.add(alternative.quantity()+" · "+offer.platform()+" · "+offer.source()+" observed "+offer.updatedAt());
                        }
                    }else {
                        selected.addAll(matches.stream().limit(4).toList());
                        text=p.name()+" ("+p.quantity()+") is "+money(o.priceMinor())+" on "+o.platform()
                            +", or "+money(o.unitPriceMinor())+" per "+o.unitLabel()+". Delivery fees are separate. Compare matching variants and pack sizes before choosing.";
                        sources.add(o.source()+" observed "+o.updatedAt());
                    }
                }}
        }
        return new AssistantAnswer((catalog.sample()?"Sample data. ":"")+text,intent.engine(),selected,sources,catalog.sample());
    }
}
