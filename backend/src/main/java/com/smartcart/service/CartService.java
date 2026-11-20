package com.smartcart.service;

import com.smartcart.dto.Models.*;
import com.smartcart.repository.Db;
import com.smartcart.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static com.smartcart.repository.Db.*;

@Service
public class CartService {
    private final Db db;private final CatalogService catalog;
    public CartService(Db db,CatalogService catalog){this.db=db;this.catalog=catalog;}
    public List<CartItem> list(String user){return db.rows("SELECT i.*,d.platform_id,d.retailer_sku,c.location_id,p.intent,p.max_price_minor,p.target_price_minor,p.max_unit_price_minor,p.bulk_budget_minor,p.max_useful_packs,p.consumption_days,p.monitor_price FROM cart_items i JOIN shopping_carts c ON c.id=i.cart_id JOIN purchase_preferences p ON p.cart_item_id=i.id LEFT JOIN provider_mapping_decisions d ON d.decision_id=i.mapping_decision_id WHERE c.user_id=? ORDER BY i.created_at",user).stream().map(this::item).toList();}
    private CartItem item(Map<String,Object> r){Long packs=number(r,"max_useful_packs"),days=number(r,"consumption_days");return new CartItem(text(r,"id"),text(r,"variant_id"),text(r,"location_id"),(int)num(r,"quantity"),new Preferences(text(r,"intent"),number(r,"max_price_minor"),number(r,"target_price_minor"),number(r,"max_unit_price_minor"),number(r,"bulk_budget_minor"),packs==null?null:packs.intValue(),days==null?null:days.intValue(),bool(r,"monitor_price")),text(r,"mapping_decision_id")==null?null:new CartListing(text(r,"platform_id"),text(r,"retailer_sku"),text(r,"mapping_decision_id")));}
    public static void validate(Preferences p){
        if(p==null)throw ApiException.bad("Choose how you plan to purchase this item.");
        switch(p.intent()){
            case "NORMAL", "ESSENTIAL"->{if(p.targetPriceMinor()!=null||p.maxUnitPriceMinor()!=null||p.bulkBudgetMinor()!=null||p.maxUsefulPacks()!=null||p.consumptionDays()!=null||p.monitorPrice())throw ApiException.bad("Normal purchases use quantity.");}
            case "NON_ESSENTIAL"->{if(p.maxPriceMinor()!=null||p.maxUnitPriceMinor()!=null||p.bulkBudgetMinor()!=null||p.maxUsefulPacks()!=null||p.consumptionDays()!=null)throw ApiException.bad("Optional items use a target price.");if(p.monitorPrice()&&p.targetPriceMinor()==null)throw ApiException.bad("Set a target price before enabling monitoring.");}
            case "PLANNED_BULK", "BULK"->{if(p.maxPriceMinor()!=null||p.targetPriceMinor()!=null||p.consumptionDays()==null)throw ApiException.bad("Planned or bulk purchases need a consumption period.");}
            default->throw ApiException.bad("Unknown purchase category.");
        }
        for(Long n:new Long[]{p.maxPriceMinor(),p.targetPriceMinor(),p.maxUnitPriceMinor(),p.bulkBudgetMinor()})if(n!=null&&(n<=0||n>1000000000L))throw ApiException.bad("Price limits must be positive and within supported bounds.");
    }
    public void lockOwner(String user){
        // Lock an existing row even when the shopper has not created a cart yet.
        db.one("SELECT id FROM users WHERE id=? FOR UPDATE",user);
    }

    @Transactional public CartItem addMarketplacePack(String user,String variant,String area,Integer stock,String decisionId,boolean replace){
        lockOwner(user);
        var prior=list(user).stream().filter(item->item.locationId().equals(area)&&item.productId().equals(variant)).findFirst();
        if(replace && prior.isEmpty())throw ApiException.bad("The cart item is no longer present.");
        if(!replace && prior.isPresent() && prior.get().listing()!=null && !decisionId.equals(prior.get().listing().decisionId()))
            throw ApiException.bad("Confirm the retailer change for this cart item first.");
        int quantity=replace?prior.orElseThrow().quantity():prior.map(CartItem::quantity).orElse(0)+1;
        if(quantity>99||stock!=null&&quantity>stock)throw ApiException.bad("The cart quantity exceeds the confirmed stock or cart limit.");
        var preferences=prior.map(CartItem::preferences)
            .orElse(new Preferences("NORMAL",null,null,null,null,null,null,false));
        var item=save(user,new CartInput(variant,area,quantity,preferences));
        db.run("UPDATE cart_items SET mapping_decision_id=? WHERE id=?",decisionId,item.id());
        return list(user).stream().filter(row->row.id().equals(item.id())).findFirst().orElseThrow();
    }

    @Transactional public CartItem save(String user,CartInput input){
        Preferences p=input.preferences();
        validate(p);
        lockOwner(user);
        catalog.product(input.productId(),input.locationId());
        String cart=db.maybe("SELECT id FROM shopping_carts WHERE user_id=? AND location_id=?",user,input.locationId())
            .map(r->text(r,"id")).orElseGet(()->{
                String cartId=id();
                db.insert("shopping_carts",fields("id",cartId,"user_id",user,"location_id",input.locationId()));
                return cartId;
            });
        var prior=db.maybe("SELECT id FROM cart_items WHERE cart_id=? AND variant_id=?",cart,input.productId());
        String itemId=prior.map(r->text(r,"id")).orElseGet(Db::id);
        if(prior.isPresent()){
            db.run("UPDATE cart_items SET quantity=?,updated_at=? WHERE id=?",input.quantity(),Instant.now(),itemId);
            db.run("DELETE FROM purchase_preferences WHERE cart_item_id=?",itemId);
        }else{
            db.insert("cart_items",fields("id",itemId,"cart_id",cart,"variant_id",input.productId(),"quantity",input.quantity()));
        }
        String storedIntent=switch(p.intent()){
            case "NORMAL"->"ESSENTIAL";
            case "PLANNED_BULK"->"BULK";
            default->p.intent();
        };
        db.insert("purchase_preferences",fields("cart_item_id",itemId,"intent",storedIntent,"max_price_minor",p.maxPriceMinor(),
            "target_price_minor",p.targetPriceMinor(),"max_unit_price_minor",p.maxUnitPriceMinor(),"bulk_budget_minor",p.bulkBudgetMinor(),
            "max_useful_packs",p.maxUsefulPacks(),"consumption_days",p.consumptionDays(),"monitor_price",p.monitorPrice()));
        return list(user).stream().filter(i->i.id().equals(itemId)).findFirst().orElseThrow();
    }

    @Transactional public void remove(String user,String itemId){
        lockOwner(user);
        db.one("SELECT i.id FROM cart_items i JOIN shopping_carts c ON c.id=i.cart_id WHERE i.id=? AND c.user_id=?",itemId,user);
        deleteItem(itemId);
    }

    @Transactional public void clear(String user,String location){
        lockOwner(user);
        for(CartItem item:list(user)){
            if(item.locationId().equals(location))deleteItem(item.id());
        }
    }

    private void deleteItem(String itemId){
        // MySQL 8.0 ignores the inline REFERENCES clause in the existing schema.
        db.run("DELETE FROM purchase_preferences WHERE cart_item_id=?",itemId);
        db.run("DELETE FROM cart_items WHERE id=?",itemId);
    }
}
