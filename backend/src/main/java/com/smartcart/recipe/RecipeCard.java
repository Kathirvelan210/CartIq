package com.smartcart.recipe;

import com.smartcart.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record RecipeCard(@NotBlank @Size(max=100) String name,@NotBlank @Size(max=400) String description,
    @NotBlank @Size(max=100) String cuisine,@NotNull Meal meal,@NotNull Diet diet,@Min(1) @Max(20) int servings,
    @Min(0) @Max(240) int prepMinutes,@Min(0) @Max(240) int cookMinutes,@NotNull Difficulty difficulty,
    @NotNull @Size(min=1,max=20) List<@NotNull @Valid Part> ingredients,
    @NotNull @Size(min=1,max=16) List<@NotBlank @Size(max=500) String> steps,
    @NotNull @Size(max=8) List<@NotBlank @Size(max=500) String> substitutions) {
    public enum Meal { BREAKFAST,LUNCH,DINNER,SNACK }
    public enum Diet { VEGETARIAN,NON_VEGETARIAN }
    public enum Difficulty { EASY,MEDIUM,HARD }
    public record Part(@NotBlank @Size(max=100) String name,@NotNull @Positive @Digits(integer=5,fraction=3) BigDecimal quantity,
        @NotBlank @Pattern(regexp="g|kg|ml|l|tsp|tbsp|piece") String unit) {}
    public record Amount(BigDecimal amount,String unit) {}

    static Amount scale(Part part,int servings,int baseServings) {
        int factor=switch(part.unit()) {case "kg","l"->1000;case "tsp"->5;case "tbsp"->15;default->1;};
        String unit=switch(part.unit()) {case "kg"->"g";case "l","tsp","tbsp"->"ml";default->part.unit();};
        var base=part.quantity().multiply(BigDecimal.valueOf(factor));
        if (base.compareTo(BigDecimal.valueOf(unit.equals("piece")?200:10000))>0) throw ApiException.bad("An ingredient quantity is too large.");
        var scaled=base.multiply(BigDecimal.valueOf(servings)).divide(BigDecimal.valueOf(baseServings),3,RoundingMode.CEILING);
        return new Amount(scaled.stripTrailingZeros(),unit);
    }
}
