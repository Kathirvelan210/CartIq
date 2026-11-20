package com.smartcart.service;

import com.smartcart.dto.Models.*;
import com.smartcart.repository.Db;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class DecisionService {
    private final CatalogService catalog;private final Db db;
    public DecisionService(CatalogService catalog,Db db){this.catalog=catalog;this.db=db;}
    public Decision decide(CartItem item){Product p=catalog.product(item.productId(),item.locationId());var best=catalog.best(p);Preferences pref=item.preferences();CartService.validate(pref);List<String> alternatives=catalog.variants(p,item.locationId()).stream().filter(v->!v.id().equals(p.id())).map(v->v.name()+" · "+v.quantity()).toList();if(best.isEmpty())return result(item,"COMPARE_ANOTHER_STORE","No fresh available offer is established for this exact pack and location. Refresh or review other stores; an essential purchase is not automatically postponed.",null,alternatives);
        Offer o=best.get();long total=Math.multiplyExact(o.priceMinor(),item.quantity());
        if("ESSENTIAL".equals(pref.intent())){if(pref.maxPriceMinor()!=null&&o.priceMinor()>pref.maxPriceMinor())return result(item,"CHOOSE_ALTERNATIVE_PRODUCT","The lowest fresh price exceeds your maximum per pack. Review other stores or pack sizes and decide what you need now; this essential has not been delayed automatically.",o,alternatives);return result(item,"BUY_NOW","This essential is available"+(pref.maxPriceMinor()!=null?" within your maximum per-pack price":"")+". This compares item prices; check the fee-inclusive basket before purchasing.",o,alternatives);}
        if("NON_ESSENTIAL".equals(pref.intent())){if(pref.targetPriceMinor()==null||o.priceMinor()<=pref.targetPriceMinor())return result(item,"BUY_NOW",pref.targetPriceMinor()==null?"The optional item is available. Set a target if you want to wait for a particular price.":"The current item price meets your target. Review the full basket and fees before deciding.",o,alternatives);Long historical=db.rows("SELECT MIN(h.price_minor) AS minimum_price FROM price_history h JOIN platform_products pp ON pp.id=h.platform_product_id WHERE pp.variant_id=? AND h.location_id=? AND h.verification=?",p.id(),item.locationId(),catalog.sample()?"SAMPLE":"VERIFIED").stream().map(r->Db.number(r,"minimum_price")).filter(Objects::nonNull).findFirst().orElse(null);if(historical!=null&&historical<=pref.targetPriceMinor())return result(item,"WAIT_AND_MONITOR","This optional item's target has been observed in the available history. Monitoring may help if you can wait, but a future price drop is not guaranteed.",o,alternatives);return result(item,"COMPARE_ANOTHER_STORE","Your target is below the available price history. There is no evidence that waiting will reach it; compare other packs or adjust the target.",o,alternatives);}
        if(pref.maxUsefulPacks()!=null&&item.quantity()>pref.maxUsefulPacks())return result(item,"CHOOSE_ALTERNATIVE_PRODUCT","The selected quantity exceeds the number of packs you expect to use. Reduce the quantity or revise your consumption needs before planning a bulk purchase.",o,alternatives);
        if(pref.bulkBudgetMinor()!=null&&total>pref.bulkBudgetMinor())return result(item,"CHOOSE_ALTERNATIVE_PRODUCT","The item total exceeds your bulk budget before delivery fees. Consider fewer or smaller packs.",o,alternatives);
        if(pref.maxUnitPriceMinor()!=null&&o.unitPriceMinor()>pref.maxUnitPriceMinor())return result(item,"COMPARE_ANOTHER_STORE","The unit price exceeds your maximum per "+o.unitLabel()+". Compare other stores and equivalent pack sizes.",o,alternatives);
        var smaller=catalog.variants(p,item.locationId()).stream().filter(v->v.amountBase().compareTo(p.amountBase())<0).map(catalog::best).flatMap(Optional::stream).min(Comparator.comparingLong(Offer::unitPriceMinor));
        if(smaller.isPresent()&&o.unitPriceMinor()<smaller.get().unitPriceMinor())return result(item,"BUY_NOW","This pack has a lower unit price than the available smaller equivalent packs, and your planned consumption period is "+pref.consumptionDays()+" days. Include delivery and storage considerations in your final choice.",o,alternatives);
        return result(item,"COMPARE_ANOTHER_STORE","A genuine bulk advantage over a smaller equivalent pack has not been established. Compare pack sizes and your consumption needs rather than relying on the advertised discount.",o,alternatives);
    }
    private Decision result(CartItem i,String action,String reason,Offer offer,List<String> alternatives){return new Decision(i.productId(),action,(catalog.sample()?"Sample calculation. ":"")+reason,offer==null?null:offer.priceMinor(),offer==null?null:offer.platform(),catalog.sample(),alternatives);}
}
