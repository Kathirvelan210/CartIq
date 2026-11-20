package com.smartcart.controller;

import com.smartcart.dto.Models.*;
import com.smartcart.service.CatalogService;
import com.smartcart.service.AuthService;
import com.smartcart.repository.*;
import com.smartcart.exception.ApiException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;
import static com.smartcart.repository.Db.*;

@RestController @RequestMapping("/api/admin")
public class AdminController {
    private final Db db;private final UserRepository users;private final CatalogService catalog;
    public AdminController(Db db,UserRepository users,CatalogService catalog){this.db=db;this.users=users;this.catalog=catalog;}
    @GetMapping("/overview") public Map<String,Object> overview(){var platforms=db.rows("SELECT p.id,p.enabled,MAX(l.observed_at) AS synced_at FROM grocery_platforms p LEFT JOIN platform_products pp ON pp.platform_id=p.id LEFT JOIN location_prices l ON l.platform_product_id=pp.id GROUP BY p.id,p.enabled").stream().map(r->{Map<String,Object> p=new LinkedHashMap<>();p.put("id",text(r,"id"));p.put("enabled",bool(r,"enabled"));p.put("status",catalog.sample()?"Sample data":"Provider connection required");p.put("syncedAt",r.get("synced_at")==null?null:time(r,"synced_at"));return p;}).toList();return Map.of("users",users.count(),"products",db.count("SELECT COUNT(*) FROM product_variants"),"categories",db.count("SELECT COUNT(*) FROM product_categories"),"platforms",platforms);}
    @GetMapping("/users") public List<User> users(){return users.findAll().stream().map(AuthService::dto).toList();}
    public record PlatformState(boolean enabled) {}
    @PatchMapping("/platforms/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void platform(@PathVariable String id,@RequestBody PlatformState input){db.one("SELECT id FROM grocery_platforms WHERE id=?",id);db.run("UPDATE grocery_platforms SET enabled=?,updated_at=? WHERE id=?",input.enabled(),Instant.now(),id);}
    @PutMapping("/products/{id}") @Transactional public Product save(@PathVariable String id,@RequestBody Product p){if(!id.matches("[a-zA-Z0-9-]{1,60}")||p.name()==null||p.name().trim().length()<2||p.name().length()>150||p.brand()==null||p.brand().length()>100||p.description()==null||p.description().length()>1000||p.color()==null||!p.color().matches("#[a-fA-F0-9]{6}")||!Set.of("carton","bag","bottle","box","jar").contains(p.art()))throw ApiException.bad("Check the product name, brand, description and appearance.");String category=text(db.one("SELECT id FROM product_categories WHERE name=? OR id=?",p.category(),p.category()),"id");var existing=db.maybe("SELECT product_id FROM product_variants WHERE id=?",id);if(existing.isPresent()){db.run("UPDATE products SET name=?,brand=?,description=?,category_id=?,color=?,art=?,updated_at=? WHERE id=?",p.name().trim(),p.brand().trim(),p.description(),category,p.color(),p.art(),Instant.now(),text(existing.get(),"product_id"));}else{java.util.regex.Matcher match=java.util.regex.Pattern.compile("(?i)^\\s*(\\d+(?:\\.\\d+)?)\\s*(kg|g|ml|litres?|liters?|l|pieces?|pcs)\\s*$").matcher(p.quantity()==null?"":p.quantity());if(!match.matches())throw ApiException.bad("Use a normalized pack size such as 500 g, 1 kg, 1 litre or 6 pieces.");String unit=match.group(2).toLowerCase(Locale.ROOT);java.math.BigDecimal amount=new java.math.BigDecimal(match.group(1));if(Set.of("kg","l","litre","litres","liter","liters").contains(unit))amount=amount.multiply(java.math.BigDecimal.valueOf(1000));unit=unit.equals("kg")||unit.equals("g")?"g":unit.equals("pcs")||unit.startsWith("piece")?"piece":"ml";if(amount.signum()<=0)throw ApiException.bad("Pack amount must be positive.");db.insert("products",fields("id",id,"category_id",category,"name",p.name(),"brand",p.brand(),"variant_name","Standard","description",p.description(),"color",p.color(),"art",p.art()));db.insert("product_variants",fields("id",id,"product_id",id,"pack_label",p.quantity(),"amount_base",amount,"base_unit",unit));}return catalog.product(id,catalog.locations().get(0).id());}
    @DeleteMapping("/products/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String id){var variant=db.one("SELECT product_id FROM product_variants WHERE id=?",id);db.run("UPDATE products SET active=FALSE,updated_at=? WHERE id=?",Instant.now(),text(variant,"product_id"));}
    @PostMapping("/locations") @ResponseStatus(HttpStatus.CREATED) public Location location(@RequestBody Location input){if(input.id()==null||!input.id().matches("[a-zA-Z0-9-]{1,60}")||input.name()==null||input.name().isBlank()||input.name().length()>100||input.city()==null||input.city().isBlank()||input.city().length()>60||input.postcode()==null||!input.postcode().matches("[1-9][0-9]{5}"))throw ApiException.bad("Check the delivery area and six-digit PIN.");db.insert("delivery_locations",fields("id",input.id(),"name",input.name(),"city",input.city(),"postcode",input.postcode()));return input;}
    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED) public Category category(@Valid @RequestBody Category input){if(input.id()==null||!input.id().matches("[a-z0-9-]{1,50}")||input.name()==null||input.name().length()<2||input.name().length()>80)throw ApiException.bad("Check category ID and name.");db.insert("product_categories",fields("id",input.id(),"name",input.name()));return input;}
}
