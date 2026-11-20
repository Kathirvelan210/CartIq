package com.smartcart.service;

import com.smartcart.dto.Models.*;
import com.smartcart.exception.ApiException;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class BasketOptimizer {
    private final CatalogService catalog;
    public BasketOptimizer(CatalogService catalog){this.catalog=catalog;}
    public Optimization optimize(BasketRequest request){return optimize(request,null);}
    public Optimization optimize(BasketRequest request,CurrentOffers recorded){
        return optimize(request,recorded,null);
    }
    public Optimization optimize(BasketRequest request,CurrentOffers recorded,String owner){
        if(request.items().stream().map(BasketLine::productId).distinct().count()!=request.items().size())
            throw ApiException.bad("Combine duplicate products before optimizing.");
        List<Platform> platforms=catalog.platforms().stream().filter(Platform::enabled).toList();
        var activeRetailers=platforms.stream().map(Platform::id).collect(java.util.stream.Collectors.toSet());
        List<List<Assignment>> choices=new ArrayList<>();
        List<String> labels=new ArrayList<>();
        for(BasketLine item:request.items()){
            Product p=catalog.product(item.productId(),request.locationId());
            labels.add(p.name()+" ("+p.quantity()+")");
            if(recorded==null) choices.add(p.offers().stream().filter(catalog::eligible).filter(o->activeRetailers.contains(o.platform()))
                .map(o->new Assignment(p.id(),o.platform(),item.quantity(),o.priceMinor()))
                .sorted(Comparator.comparingLong(Assignment::unitPriceMinor)).toList());
            else choices.add(recorded.forPack(p,request.locationId(),owner).stream().filter(q->activeRetailers.contains(q.retailer()))
                .filter(q->q.stockPacks()!=null?q.stockPacks()>=item.quantity():item.quantity()==1)
                .map(q->new Assignment(p.id(),q.retailer(),item.quantity(),q.priceMinor())).toList());
        }
        List<BasketOption> single=new ArrayList<>();
        for(Platform platform:platforms){
            List<Assignment> lines=new ArrayList<>();
            List<String> missing=new ArrayList<>();
            for(int i=0;i<choices.size();i++){
                var offer=choices.get(i).stream().filter(a->a.platform().equals(platform.id())).findFirst();
                if(offer.isPresent())lines.add(offer.get());
                else missing.add(labels.get(i));
            }
            single.add(missing.isEmpty()?evaluate(platform.name(),lines,platforms)
                :new BasketOption(platform.name(),false,null,null,null,
                    (recorded==null?"No fresh available price for ":"No fresh price or confirmed required quantity for ")+String.join(", ",missing)+".",List.copyOf(lines)));
        }
        if(choices.stream().anyMatch(List::isEmpty))return new Optimization(single,new BasketOption("No complete basket",false,null,null,null,"At least one required item has no fresh available price. It has not been silently removed.",List.of()),null,true,catalog.sample(),"Refresh missing offers or choose alternatives before comparing complete baskets.");
        Search search=new Search(choices,platforms);for(BasketOption option:single)search.consider(option);search.consider(evaluate("Split basket",choices.stream().map(o->o.get(0)).toList(),platforms));search.walk(0,new ArrayList<>(),0);
        BasketOption best=search.best==null?new BasketOption("No feasible basket",false,null,null,null,"Minimum orders or unknown fees prevent a confirmed complete comparison.",List.of()):search.best;Long baseline=single.stream().filter(BasketOption::feasible).map(BasketOption::totalMinor).min(Long::compare).orElse(null);return new Optimization(single,best,baseline!=null&&best.totalMinor()!=null?Math.max(0,baseline-best.totalMinor()):null,!search.truncated,catalog.sample(),(catalog.sample()?"All prices and delivery rules are sample data. ":"")+(search.truncated?"Search limit reached: this is the best evaluated basket, not a guarantee of the global minimum.":"Complete baskets were evaluated with store minimums and delivery thresholds. ")+"Excluded products are never treated as a cheaper complete basket.");
    }
    BasketOption evaluate(String label,List<Assignment> lines,List<Platform> platforms){Map<String,Long> subtotals=new HashMap<>();for(var a:lines)subtotals.merge(a.platform(),Math.multiplyExact(a.unitPriceMinor(),a.quantity()),Long::sum);long subtotal=subtotals.values().stream().mapToLong(Long::longValue).sum(),fees=0;for(var e:subtotals.entrySet()){Platform p=platforms.stream().filter(s->s.id().equals(e.getKey())).findFirst().orElseThrow();if(p.deliveryMinor()==null||p.handlingMinor()==null||p.minimumMinor()==null||(!catalog.sample()&&!"VERIFIED".equals(p.feeSource())))return new BasketOption(label,false,subtotal,null,null,"Fees or minimum-order rules are not verified.",List.copyOf(lines));if(e.getValue()<p.minimumMinor())return new BasketOption(label,false,subtotal,null,null,p.name()+" minimum order is not met.",List.copyOf(lines));fees+=p.handlingMinor()+(p.freeDeliveryMinor()!=null&&e.getValue()>=p.freeDeliveryMinor()?0:p.deliveryMinor());}return new BasketOption(label,true,subtotal,fees,subtotal+fees,"All required items included; "+subtotals.size()+" store(s).",List.copyOf(lines));}

    record PlanLine(Product product,int count,CurrentOffers.Quote quote) {
        Assignment assignment(){return new Assignment(product.id(),quote.retailer(),count,quote.priceMinor());}
    }
    record Demand(String label,List<List<PlanLine>> options) {}
    public record PlanBasket(String label,Long totalMinor,long pricedItemsMinor,Long feesMinor,List<String> missing,
                             String reason,List<PackEvidence> packs) {}
    public record PackEvidence(String productId,String name,String pack,int count,CurrentOffers.Quote quote) {}
    public record PlanComparison(List<PlanBasket> singleRetailers,PlanBasket split,PlanBasket recommended,boolean searchComplete) {}

    PlanComparison comparePlan(List<Demand> demands) {
        var platforms=catalog.platforms().stream().filter(Platform::enabled).toList();
        if(demands.isEmpty()) {
            var empty=new PlanBasket("Nothing to buy",0L,0,0L,List.of(),"The pantry covers the selected meals and no extra packs were selected.",List.of());
            return new PlanComparison(List.of(),null,empty,true);
        }
        var singles=new ArrayList<PlanBasket>();
        boolean complete=true;
        for(var retailer:platforms) {
            var search=new PlanSearch(retailer.name(),retailer.id(),demands,platforms);
            search.walk(0,new ArrayList<>(),new ArrayList<>());
            singles.add(search.best);complete&=!search.truncated;
        }
        var mixed=new PlanSearch("Split retailers",null,demands,platforms);
        mixed.walk(0,new ArrayList<>(),new ArrayList<>());
        var best=singles.stream().filter(Objects::nonNull).min(BasketOptimizer::compare).orElse(null);
        if(mixed.best!=null&&(best==null||compare(mixed.best,best)<0))best=mixed.best;
        if(best==null)best=new PlanBasket("No priced basket",null,0,null,demands.stream().map(Demand::label).toList(),"No eligible recorded retailer prices for this plan.",List.of());
        return new PlanComparison(List.copyOf(singles),mixed.best,best,complete&&!mixed.truncated);
    }

    private static int compare(PlanBasket a,PlanBasket b) {
        if(a.totalMinor()!=null&&b.totalMinor()!=null)return Long.compare(a.totalMinor(),b.totalMinor());
        if(a.totalMinor()!=null)return -1;
        if(b.totalMinor()!=null)return 1;
        int coverage=Integer.compare(a.missing().size(),b.missing().size());
        return coverage==0?Long.compare(a.pricedItemsMinor(),b.pricedItemsMinor()):coverage;
    }

    private final class PlanSearch {
        private final String label,retailer;
        private final List<Demand> demands;
        private final List<Platform> platforms;
        private final Map<List<String>,Integer> taken=new HashMap<>();
        private final Set<String> allowed;
        private int visited;
        private boolean truncated;
        private PlanBasket best;
        PlanSearch(String label,String retailer,List<Demand> demands,List<Platform> platforms) {
            this.label=label;this.retailer=retailer;this.demands=demands;this.platforms=platforms;
            this.allowed=new HashSet<>(platforms.stream().map(Platform::id).toList());
        }
        void walk(int index,List<PlanLine> lines,List<String> missing) {
            if(++visited>8000){truncated=true;return;}
            if(best!=null && missing.size()>best.missing().size())return;
            if(index==demands.size()){consider(lines,missing);return;}
            var demand=demands.get(index);
            for(var bundle:demand.options()) {
                if(truncated)return;
                if(!bundle.stream().allMatch(this::canTake))continue;
                bundle.forEach(line->reserve(line,line.count()));lines.addAll(bundle);
                walk(index+1,lines,missing);
                bundle.forEach(line->reserve(line,-line.count()));lines.subList(lines.size()-bundle.size(),lines.size()).clear();
            }
            if(best==null||best.totalMinor()==null){
                missing.add(demand.label());walk(index+1,lines,missing);missing.remove(missing.size()-1);
            }
        }
        private boolean canTake(PlanLine line) {
            String platform=line.quote().retailer();
            return allowed.contains(platform)&&(retailer==null||retailer.equals(platform))
                && taken.getOrDefault(key(line),0)+line.count()<=line.quote().packLimit();
        }
        private List<String> key(PlanLine line){return List.of(line.quote().retailer(),line.product().id());}
        private void reserve(PlanLine line,int count){taken.merge(key(line),count,Integer::sum);}
        private void consider(List<PlanLine> lines,List<String> missing) {
            if(retailer==null&&lines.stream().map(line->line.quote().retailer()).distinct().count()<2)return;
            long subtotal=lines.stream().mapToLong(line->line.count()*line.quote().priceMinor()).sum();
            var evaluated=missing.isEmpty()&&!lines.isEmpty()?evaluate(label,lines.stream().map(PlanLine::assignment).toList(),platforms):null;
            var option=new PlanBasket(label,evaluated==null?null:evaluated.totalMinor(),subtotal,evaluated==null?null:evaluated.feesMinor(),
                List.copyOf(missing),evaluated==null?"Some required quantities lack fresh compatible offers or sufficient stock. Fees are excluded.":evaluated.reason(),
                lines.stream().map(line->new PackEvidence(line.product().id(),line.product().name(),line.product().quantity(),line.count(),line.quote())).toList());
            if(best==null||compare(option,best)<0)best=option;
        }
    }
    private class Search {
        final List<List<Assignment>> choices;final List<Platform> platforms;final long[] suffix;int visited;boolean truncated;BasketOption best;
        Search(List<List<Assignment>> c,List<Platform> p){choices=c;platforms=p;suffix=new long[c.size()+1];for(int i=c.size()-1;i>=0;i--)suffix[i]=suffix[i+1]+c.get(i).get(0).unitPriceMinor()*c.get(i).get(0).quantity();}
        void consider(BasketOption b){if(b.feasible()&&(best==null||b.totalMinor()<best.totalMinor()))best=b;}
        void walk(int index,List<Assignment> lines,long cost){if(++visited>250000){truncated=true;return;}if(best!=null&&cost+suffix[index]>best.totalMinor())return;if(index==choices.size()){consider(evaluate("Best evaluated basket",lines,platforms));return;}for(var a:choices.get(index)){if(truncated)return;lines.add(a);walk(index+1,lines,cost+a.unitPriceMinor()*a.quantity());lines.remove(lines.size()-1);}}
    }
}
