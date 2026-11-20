package com.smartcart.grocery;

import com.smartcart.catalogimport.ProductMatching;
import com.smartcart.exception.ApiException;
import com.smartcart.repository.Db;
import com.smartcart.service.CatalogService;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.smartcart.repository.Db.*;

@Service
public class GroceryCatalogue {
    public record Details(@Size(max=100) String subcategory,@Size(max=100) String flavour,@Size(max=150) String formulation,
        @Size(max=4000) String ingredients,@Size(max=500) String dietaryAttributes,@NotBlank @Size(max=500) String evidenceReference) {}
    public record Identity(String productId,String variantId,String name,String brand,String normalizedName,String normalizedBrand,
        String category,String pack,BigDecimal totalAmount,String unit,ProductMatching.Pack normalizedPack,
        CatalogService.Metadata provenance,Details details) {}
    public record Ingredient(@NotBlank @Size(max=120) String name,@NotBlank @Size(max=500) String evidenceReference) {}
    public record IngredientLink(@NotBlank @Size(max=60) String productId,@NotBlank @Size(max=500) String evidenceReference) {}
    public record Substitute(@NotBlank @Size(max=60) String substituteId,@NotNull @Positive @Digits(integer=8,fraction=4) BigDecimal quantityRatio,
        @NotBlank @Pattern(regexp="g|ml|piece") String unit,@NotBlank @Size(max=500) String evidenceReference) {}
    public record RecipeComponents(String recipeId,int servings,List<Map<String,Object>> ingredients) {}
    private final Db db;
    private final CatalogService catalogue;

    public GroceryCatalogue(Db db,CatalogService catalogue) { this.db=db; this.catalogue=catalogue; }

    @Transactional(readOnly=true)
    public Identity identity(String variant,String area) {
        var p=catalogue.product(variant,area);
        var details=db.maybe("SELECT * FROM grocery_variant_details WHERE variant_id=?",variant).map(r->new Details(
            text(r,"subcategory"),text(r,"flavour"),text(r,"formulation"),text(r,"ingredients"),text(r,"dietary_attributes"),text(r,"evidence_reference"))).orElse(null);
        return new Identity(p.canonicalProductId(),variant,p.name(),p.brand(),ProductMatching.normalized(p.name()),ProductMatching.normalized(p.brand()),
            p.category(),p.quantity(),p.amountBase(),p.baseUnit(),ProductMatching.pack(p.quantity()),catalogue.metadata(variant,area),details);
    }

    @Transactional
    public void details(String variant,Details d) {
        db.one("SELECT id FROM product_variants WHERE id=? FOR UPDATE",variant);
        db.run("DELETE FROM grocery_variant_details WHERE variant_id=?",variant);
        db.insert("grocery_variant_details",fields("variant_id",variant,"subcategory",d.subcategory(),"flavour",d.flavour(),
            "formulation",d.formulation(),"ingredients",d.ingredients(),"dietary_attributes",d.dietaryAttributes(),"evidence_reference",d.evidenceReference()));
    }

    public String addIngredient(Ingredient ingredient) {
        String id=id();
        db.insert("grocery_ingredients",fields("id",id,"name",ingredient.name().strip(),"evidence_reference",ingredient.evidenceReference()));
        return id;
    }

    public void addLink(String ingredient,IngredientLink link) {
        db.insert("grocery_ingredient_products",fields("ingredient_id",ingredient,"product_id",link.productId(),"evidence_reference",link.evidenceReference()));
    }

    public void addSubstitute(String ingredient,Substitute swap) {
        if (ingredient.equals(swap.substituteId())) throw ApiException.bad("Choose a different ingredient for the substitution.");
        db.insert("grocery_ingredient_substitutions",fields("ingredient_id",ingredient,"substitute_id",swap.substituteId(),
            "quantity_ratio",swap.quantityRatio(),"base_unit",swap.unit(),"evidence_reference",swap.evidenceReference()));
    }

    public void attach(String component,String ingredient) {
        if (db.run("UPDATE recipe_ingredients SET grocery_ingredient_id=? WHERE id=?",ingredient,component)==0) throw ApiException.missing();
    }

    @Transactional(readOnly=true)
    public RecipeComponents recipe(String recipe) {
        int servings=(int)num(db.one("SELECT servings FROM recipes WHERE id=?",recipe),"servings");
        var ingredients=db.rows("""
            SELECT r.id,r.product_id,r.amount_base,r.base_unit,g.id AS ingredient_id,g.name,g.evidence_reference
            FROM recipe_ingredients r LEFT JOIN grocery_ingredients g ON g.id=r.grocery_ingredient_id
            WHERE r.recipe_id=? ORDER BY r.id
            """,recipe);
        return new RecipeComponents(recipe,servings,ingredients.stream().map(row->{
            String ingredient=text(row,"ingredient_id");
            String unit=text(row,"base_unit");
            return fields("id",text(row,"id"),"productId",text(row,"product_id"),"amount",decimal(row,"amount_base"),"unit",unit,
                "ingredientId",ingredient,"name",text(row,"name"),"evidenceReference",text(row,"evidence_reference"),
                "products",ingredient==null?List.of():db.rows("""
                    SELECT m.product_id,m.evidence_reference FROM grocery_ingredient_products m
                    JOIN products p ON p.id=m.product_id WHERE m.ingredient_id=? AND p.active=TRUE ORDER BY m.product_id
                    """,ingredient),
                "substitutions",ingredient==null?List.of():db.rows("""
                    SELECT s.substitute_id,g.name,s.quantity_ratio,s.evidence_reference FROM grocery_ingredient_substitutions s
                    JOIN grocery_ingredients g ON g.id=s.substitute_id WHERE s.ingredient_id=? AND s.base_unit=? ORDER BY s.substitute_id
                    """,ingredient,unit));
        }).toList());
    }
}
