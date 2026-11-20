package com.smartcart.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class Models {
    private Models() {}
    public record User(String id,String name,String email,String role,boolean sample) {}
    public record Session(String token,User user) {}
    public record Register(@NotBlank @Size(min=2,max=80) String name,@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(min=12,max=72) String password,@Pattern(regexp="^$|^\\+?[0-9 -]{10,15}$") String phone) {}
    public record Login(@NotBlank @Email String email,@NotBlank @Size(max=72) String password) {}
    public record Name(@NotBlank @Size(min=2,max=80) String name) {}
    public record Password(@NotBlank String currentPassword,@NotBlank @Size(min=12,max=72) String newPassword) {}
    public record Location(String id,String name,String city,String postcode) {}
    public record Category(String id,String name) {}
    public record Offer(String platform,Double price,Double mrp,String status,Instant updatedAt,String productId,String variant,String locationId,String source,String verificationStatus,Long priceMinor,Long unitPriceMinor,String unitLabel,String purchaseUrl) {}
    public record Product(String id,String canonicalProductId,String name,String brand,String quantity,String category,String description,String color,String art,String imageUrl,BigDecimal amountBase,String baseUnit,List<Offer> offers) {}
    public record ProductPage(List<Product> items,int page,int pageSize,long totalElements,long totalPages,boolean hasNext,boolean hasPrevious) {}
    public record Platform(String id,String name,Long deliveryMinor,Long handlingMinor,Long minimumMinor,Long freeDeliveryMinor,String feeSource,boolean enabled) {}
    public record Preferences(@NotBlank @Pattern(regexp="NORMAL|PLANNED_BULK|ESSENTIAL|NON_ESSENTIAL|BULK") String intent,@Positive Long maxPriceMinor,@Positive Long targetPriceMinor,@Positive Long maxUnitPriceMinor,@Positive Long bulkBudgetMinor,@Min(1) @Max(99) Integer maxUsefulPacks,@Min(1) @Max(365) Integer consumptionDays,boolean monitorPrice) {}
    public record CartInput(@NotBlank String productId,@NotBlank String locationId,@Min(1) @Max(99) int quantity,@NotNull @Valid Preferences preferences) {}
    public record CartListing(String retailer,String nativeId,String decisionId) {}
    public record CartItem(String id,String productId,String locationId,int quantity,Preferences preferences,CartListing listing) {}
    public record Decision(String productId,String action,String reason,Long priceMinor,String platform,boolean sample,List<String> alternatives) {}
    public record BasketLine(@NotBlank String productId,@Min(1) @Max(99) int quantity) {}
    public record BasketRequest(@NotBlank String locationId,@NotEmpty @Size(max=30) List<@Valid BasketLine> items) {}
    public record Assignment(String productId,String platform,int quantity,long unitPriceMinor) {}
    public record BasketOption(String label,boolean feasible,Long subtotalMinor,Long feesMinor,Long totalMinor,String reason,List<Assignment> assignments) {}
    public record Optimization(List<BasketOption> singleStores,BasketOption recommended,Long savingsMinor,boolean searchComplete,boolean sample,String explanation) {}
    public record WatchInput(@NotBlank String productId,@NotBlank String locationId,@Positive long targetMinor) {}
    public record Watch(String id,String productId,String name,String quantity,String locationId,long targetMinor,Long currentMinor,String platform,boolean enabled,boolean sample) {}
    public record Notice(String id,String title,String body,String kind,boolean sample,boolean read,Instant createdAt,String deliveryStatus) {}
    public record NotificationSettings(boolean notificationsEnabled,boolean priceIncreaseAlerts,boolean replenishmentAlerts,@Size(max=1000) String preferredBrands) {}
    public record BudgetInput(@Pattern(regexp="\\d{4}-(0[1-9]|1[0-2])") String month,@Min(100) @Max(1000000000) long limitMinor) {}
    public record ExpenseInput(@NotBlank String categoryId,@Min(1) @Max(100000000) long amountMinor,@NotBlank @Size(max=200) String note) {}
    public record CategorySpend(String category,long amountMinor) {}
    public record MonthSpend(String month,long amountMinor) {}
    public record Budget(String month,Long limitMinor,long spentMinor,Long remainingMinor,List<CategorySpend> categories) {}
    public record PurchaseInput(@NotBlank String requestKey,@NotBlank String locationId,@NotBlank String platform,@NotEmpty @Size(max=30) List<@Valid BasketLine> items) {}
    public record Purchase(String id,String platform,long totalMinor,long savingsMinor,Instant purchasedAt,boolean sample,int itemCount) {}
    public record ReplenishmentInput(@NotBlank String productId,@Min(1) @Max(365) int cycleDays) {}
    public record Replenishment(String id,String productId,String name,int cycleDays,Instant lastPurchasedAt,Instant estimatedNextAt,boolean due,String explanation) {}
    public record Recipe(String id,String name,int servings,String instructions,String source,boolean sample,String color,int ingredientCount,int pantryMatches) {}
    public record RecipePlanInput(@Min(1) @Max(20) int servings,@NotBlank String locationId,List<String> ownedProductIds) {}
    public record RecipeAddInput(@NotNull @Valid RecipePlanInput plan,java.util.Map<String,String> selectedVariantIds) {}
    public record Ingredient(String productId,String name,BigDecimal requiredBase,BigDecimal pantryBase,BigDecimal missingBase,String baseUnit,String suggestedVariantId,int packs,Long estimatedMinor) {}
    public record RecipePlan(String recipeId,int servings,List<Ingredient> ingredients,long estimatedMinor,boolean complete,boolean sample) {}
    public record PantryInput(@NotBlank String productId,@DecimalMin("0") @DecimalMax("1000000") BigDecimal amountBase,@Pattern(regexp="g|ml|piece") String baseUnit) {}
    public record Pantry(String productId,String name,BigDecimal amountBase,String baseUnit) {}
    public record Point(Instant observedAt,String platform,long priceMinor) {}
    public record Forecast(Long estimateMinor,Long lowMinor,Long highMinor,double confidence,String method,String caveat) {}
    public record PriceAnalytics(String productId,List<Point> history,Long minMinor,Long maxMinor,Long averageMinor,Forecast forecast,boolean sample) {}
    public record Recommendation(Product product,String reason) {}
    public record AssistantInput(@NotBlank @Size(max=1000) String message,@NotBlank String locationId) {}
    public record AssistantAnswer(String text,String engine,List<Product> products,List<String> sources,boolean sample) {}
    public record Overview(Budget budget,List<Purchase> recentPurchases,List<Recommendation> recommendations,List<MonthSpend> trend,int watchCount,int unreadNotifications,int dueCount) {}
    public record DeviceInput(@NotBlank @Size(max=1000) @jakarta.validation.constraints.Pattern(regexp="[A-Za-z0-9_:-]+") String token,
        @Size(max=1000) @jakarta.validation.constraints.Pattern(regexp="[A-Za-z0-9_:-]+") String previousToken) {}
    public record ErrorBody(String code,String message) {}
}
