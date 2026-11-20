# Phase 10 implementation and evidence

Browser screenshots show synthetic retailer data in isolated H2. Chrome used its geolocation override. The local Spring requests and database writes were real. The browser evidence includes request paths and statuses without JWTs, location query parameters, raw provider responses or user credentials.

[Desktop marketplace](1440x900-marketplace.png) · [Mobile marketplace](390x844-marketplace.png) · [Weekly plan](1440x900-plan.png) · [Browser requests](browser-evidence.json) · [Disposable MySQL verification](mysql-rehearsal.json)

## API changes

| Method and path | Effect |
|---|---|
| PUT /api/me/shopping-position | Confirm a device or resolved PIN position for the authenticated shopper. Returns the matching pilot area ID when its configured scope matches. |
| DELETE /api/me/shopping-position | Clear the account’s transient selection. |
| POST /api/shopping/retailers/marketplace/search | Require the shopper’s selected position and existing exact one-use authorization before dispatch. |
| POST /api/shopping/retailers/marketplace/cart | Save the exact approved mapping choice. Explicit replacement retains quantity. |
| POST /api/shopping/retailers/marketplace/cart-quotes | Recheck account-scoped transient quotes for selected listings without dispatch. |
| GET /api/me/cart | Include the internal mapping decision and retailer listing identity when present. |
| POST /api/me/recipe-intelligence/week | Validate seven recipe IDs suggested by Gemini and return their curated recipe records. |
| POST /api/me/shopping-intelligence/weekly/assistant/gemini | Use a validated model-selected intent to call the existing account-scoped shopping service. |
| POST /recipes/week and /shopping/intent | Private, service-authenticated FastAPI operations. |

## Acceptance limits

Physical GPS and live Gemini access require separate verification. The customer-facing retailer workflow remains subject to the pilot account, exact coordinates, catalogue mapping, display rights and one-use ticket. A position outside the authorized area can still be selected. Retailer requests are unavailable there. No model training or new historical observations were part of this release.

The first proposed retailer test is BlinkIt, query **Amul Taaza toned milk 1 litre**, at the operator’s Coimbatore coordinates **10.942972, 76.953417**, with PIN **641008** if verified. The operation is one GET /v1/search, through Spring. Maximum cost is one credit. SKU 176 remains a candidate pending the documented exact-pack human decision and permission evidence. The proposed search is unsubmitted.

## Location and customer data handling

- [App.tsx](../../../App.tsx)
- [LocationPicker.tsx](../../../src/components/LocationPicker.tsx)
- [ProductArt.tsx](../../../src/components/ProductArt.tsx)
- [device-location.ts](../../../src/services/device-location.ts)
- [shopping-location.ts](../../../src/services/shopping-location.ts)
- [api.ts](../../../src/services/api.ts)
- [store.tsx](../../../src/store.tsx)
- [ShoppingPositionController.java](../../../backend/src/main/java/com/smartcart/controller/ShoppingPositionController.java)
- [ShoppingPositions.java](../../../backend/src/main/java/com/smartcart/service/ShoppingPositions.java)
- [CatalogController.java](../../../backend/src/main/java/com/smartcart/controller/CatalogController.java)
- [CatalogService.java](../../../backend/src/main/java/com/smartcart/service/CatalogService.java)
- [application.yml](../../../backend/src/main/resources/application.yml)

## Marketplace and cart

- [grocery-categories.ts](../../../src/services/grocery-categories.ts)
- [MarketplaceOffers.tsx](../../../src/components/MarketplaceOffers.tsx)
- [marketplace.ts](../../../src/services/marketplace.ts)
- [marketplace-comparison.ts](../../../src/services/marketplace-comparison.ts)
- [cart.ts](../../../src/services/cart.ts)
- [CartExperience.tsx](../../../src/screens/CartExperience.tsx)
- [MarketplaceController.java](../../../backend/src/main/java/com/smartcart/controller/MarketplaceController.java)
- [LiveOffersController.java](../../../backend/src/main/java/com/smartcart/controller/LiveOffersController.java)
- [LiveOffers.java](../../../backend/src/main/java/com/smartcart/service/LiveOffers.java)
- [CartService.java](../../../backend/src/main/java/com/smartcart/service/CartService.java)
- [Models.java](../../../backend/src/main/java/com/smartcart/dto/Models.java)
- [V20__cart_listing_choice.sql](../../../backend/src/main/resources/db/migration/V20__cart_listing_choice.sql)

## Recipes and shopping assistant

- [app.py](../../../ai-service/app.py)
- [recipe_generation.py](../../../ai-service/recipe_generation.py)
- [Gemini-Recipe-Preflight.cjs](../../../configuration/Gemini-Recipe-Preflight.cjs)
- [RecipeIntelligenceController.java](../../../backend/src/main/java/com/smartcart/controller/RecipeIntelligenceController.java)
- [RecipeLibrary.java](../../../backend/src/main/java/com/smartcart/recipe/RecipeLibrary.java)
- [WeeklyShoppingController.java](../../../backend/src/main/java/com/smartcart/controller/WeeklyShoppingController.java)
- [UnifiedShoppingAssistant.java](../../../backend/src/main/java/com/smartcart/service/UnifiedShoppingAssistant.java)
- [AssistantService.java](../../../backend/src/main/java/com/smartcart/service/AssistantService.java)
- [IntentClassifier.java](../../../backend/src/main/java/com/smartcart/integration/IntentClassifier.java)
- [recipe-intelligence.ts](../../../src/services/recipe-intelligence.ts)
- [RecipeIntelligence.tsx](../../../src/screens/RecipeIntelligence.tsx)
- [weekly-shopping.ts](../../../src/services/weekly-shopping.ts)
- [UnifiedShopping.tsx](../../../src/screens/UnifiedShopping.tsx)

## Regression tests

- [test_recipe_generation.py](../../../ai-service/test_recipe_generation.py)
- [shopping-location.test.ts](../../../tests/shopping-location.test.ts)
- [marketplace-comparison.test.ts](../../../tests/marketplace-comparison.test.ts)
- [ShoppingPositionsTest.java](../../../backend/src/test/java/com/smartcart/ShoppingPositionsTest.java)
- [ControlledPilotTest.java](../../../backend/src/test/java/com/smartcart/ControlledPilotTest.java)
- [MarketplaceBrowserTest.java](../../../backend/src/test/java/com/smartcart/MarketplaceBrowserTest.java)
- [Test-MarketplaceBrowser.cjs](../../../configuration/Test-MarketplaceBrowser.cjs)

## Documentation

- [CARTIQ_MASTER_CONTEXT.md](../../../docs/CARTIQ_MASTER_CONTEXT.md)
- [CHANGELOG.md](../../../docs/CHANGELOG.md)
- [TESTING_STATUS.md](../../../docs/TESTING_STATUS.md)
- [GROCERY_DATA_ENGINE.md](../../../docs/GROCERY_DATA_ENGINE.md)
- [AI_MODEL_ROADMAP.md](../../../docs/AI_MODEL_ROADMAP.md)
- [RECIPE_INTELLIGENCE.md](../../../docs/RECIPE_INTELLIGENCE.md)
- [PRODUCTION_READINESS.md](../../../docs/PRODUCTION_READINESS.md)
- [DEPLOYMENT_GUIDE.md](../../../docs/DEPLOYMENT_GUIDE.md)
