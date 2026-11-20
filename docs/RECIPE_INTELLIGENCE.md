# Recipe intelligence

## Recipe assistant and on-hand selections

The main Recipes tab sends the selected preferences to the authenticated recipe-generation endpoint on explicit submission. The pantry sharing switch controls whether stored ingredient names go to the model. On-hand checkboxes operate locally until the meal is submitted to the planner. The optional `alreadyHave` indices on a meal refer to the original ingredient order for that recipe. The planner combines these amounts across meals, credits the larger of confirmed and stored pantry coverage, and honors explicit planner quantity overrides. A substitution requires its own on-hand evidence.

On Windows, the session-only recipe and shopping launcher supports `-PromptForExistingKey -WithLocalShopping -VerifiedDashboardBalance <verified-balance>`. To take over an existing matching loopback shopping backend, add `-ReplaceLocalShoppingBackend`. The launcher checks authenticated FastAPI readiness and the settled shopping ledger before replacing the local H2 backend. The model credential stays in the private FastAPI process. Keep the launcher open while using the app.

## Phase 10 · 18 November 2026

In Recipes, enter the meal request, set servings and optional dietary and pantry inputs, then choose **Suggest seven meals with Gemini**. Spring sends only the request text, opted-in pantry names and allowed curated choices to the private FastAPI workflow. Each selected day references a permitted recipe. Choose **Calculate** in the meal planner to scale and consolidate ingredients, subtract pantry quantities and review exact catalogue matches and known prices. A generated individual recipe is labelled separately from a curated meal selected by AI.

The authenticated Spring route is `POST /api/me/recipe-intelligence/week`. The private service route is `POST /recipes/week`. Both services validate the seven returned IDs. Out-of-catalogue IDs and partial weeks fail the request. Generation, weekly selection and optional shopping intent routing share the per-account admission limit and the single-process FastAPI model-call gate. An upstream failure leaves the existing plan available.


Open **AI Smart Tools → Recipes**. Browse curated adaptations by title or ingredient, cuisine, meal, diet, difficulty, total time or estimated budget. Start with the referenced South Indian upma and sundal, cumin rice, and egg bhurji. CartIQ wrote the quantities and directions as adaptations of the linked recipes. These were source-reviewed without a kitchen trial. Eggs are classified as non-vegetarian. The existing recipe planner is accessible through **Open existing catalogue recipes**.

Reference dishes include [rava upma](https://hebbarskitchen.com/UPMA-RECIPE-3-WAYS-SOOJI-UPMA-RAVA-UPMA/), [jeera rice](https://www.indianhealthyrecipes.com/jeera-rice-recipe/), [egg bhurji](https://www.indianhealthyrecipes.com/egg-bhurji-andhra-egg-porutu/) and [chickpea sundal](https://www.indianhealthyrecipes.com/chana-sundal-recipe/).

## Recipe drafts

The Expo client calls Spring Boot. Spring checks authentication and a per-account cooldown, optionally fetches the signed-in user's pantry product names, then calls the existing private FastAPI service with `X-Service-Token`. FastAPI uses Gemini `generateContent` with structured JSON and validates the returned recipe before passing it back. Spring validates the card again. Fields cover description, cuisine, meal, diet, servings, prep and cook times, difficulty, quantified ingredients, steps and optional culinary substitution suggestions. No nutrition, health certification, price, or catalogue mapping is supplied by the model. User prompts are sent only on explicit generation. Pantry names are sent only when selected. No account identifiers, purchase history, pantry quantities, prices or locations are sent to Gemini.

The generated draft is private to the current account and expires after about 30 minutes or on restart. It is never automatically published. One generation can be in flight per FastAPI process. Invalid or uncertain model responses are surfaced without an automatic retry. Review the draft before cooking. AI substitutions are suggestions. Only evidence-backed, dimension-compatible substitutions can be applied to the shopping list.

### Enable on an approved environment

1. Use the existing internal `AI_SERVICE_SECRET` in Spring and FastAPI and set `AI_SERVICE_URL` to the private FastAPI address.
2. Set `CARTIQ_RECIPE_GENERATION_ENABLED=true` in both processes. Provide the existing Gemini key through `GEMINI_API_KEY` and specify an authorized structured-output model in `GEMINI_MODEL` on FastAPI only. Verify provider data handling and quotas before enabling for users.
3. For the Compose reference, add `-f configuration/compose.recipe.example.yml` only in the approved environment. The override allows forecast-service egress while keeping its listener private. No credential is needed for curated discovery or the recorded-price planner. [Gemini structured output](https://ai.google.dev/gemini-api/docs/structured-output?lang=rest) · [generateContent reference](https://ai.google.dev/api/generate-content?hl=en)

## Meal plan and shopping list

- Add up to 14 meals across seven days with 1–20 servings each. The planner scales quantities in grams, millilitres or pieces. `kg` and `l` multiply by 1,000. Use metric teaspoons of 5 ml and tablespoons of 15 ml. Units of mass, volume and count never cross without explicit evidence. Matching uses normalized ingredient names and keeps ingredients separate from packaged products.
- Repeated needs are combined before applying saved or user-entered pantry amounts. A stock balance can only be reserved once in a plan. Planning does not consume pantry inventory. Use the amount input to confirm stock on hand. Approved substitutions use the Phase 1 directional ratio and the same measurement unit.
- Products come from reviewed Phase 1 ingredient links. A few explicit starter-catalogue links require an exact active product ID, name, brand, and compatible unit. Ambiguous or unmapped ingredients remain on the shopping list. Cooked and uncooked ingredients are not treated as the same product.
- The planner compares fresh, available, eligible recorded Blinkit and Zepto offers for compatible catalogue variants. For licensed observations, a grant must permit signed-in display and remain active. Comparisons search combinations of up to two pack sizes per canonical product and retailer. The search covers up to 20 priced variants per product and 99 packs per variant. Prices older than 15 minutes, unknown stock and missing prices do not make a complete budget estimate.
- Totals are pack subtotals before fees, not checkout or delivery quotes. The lowest item subtotal can use multiple retailers. Incomplete baskets show the known subtotal and missing ingredient count. Budget filters exclude unknown totals. Cart addition revalidates on the server and adds the canonical packs under the authenticated account. The chosen estimate does not reserve stock or pin the cart to a retailer.
- **Open live search** uses the existing explicit, credit-protected Blinkit and Zepto comparison component for the chosen pack, including its own location confirmation and prices. The planner never sends a retailer request itself or imports a transient result into the cart estimate. Check brand and exact pack before using a live listing.

## Routes and schema

All routes require an authenticated shopper and use `Cache-Control: no-store`.

| Route | Input and result |
|---|---|
| `GET /api/me/recipe-intelligence` | `q`, `cuisine`, `meal`, `diet`, `difficulty`, `maxMinutes` and optional `budgetMinor` with `locationId`. Returns curated cards. |
| `POST /api/me/recipe-intelligence/generate` | `prompt`, `usePantry`. Returns an owner-scoped draft with an ID. |
| `POST /api/me/recipe-intelligence/plan` | Location, meals, budget in paise, optional `onHand` and reviewed substitution selections. Returns ingredients, packs and basket coverage. |
| `POST /api/me/recipe-intelligence/cart` | Same plan payload and a basket selector. Recalculates and saves compatible pack quantities. |

The recipe cards are packaged in the Spring resource directory. New workflows use the existing V13 ingredient, substitution, and observation tables. No new migration is required. The legacy `mysql-local` profile targets an earlier schema and does not expose these routes.

## Unified weekly planner

**AI Smart Tools → Shopping plan** uses curated recipes with the same scaling, consolidated ingredient quantities and pantry reservation rules. Set a day and serving count, apply an explicit diet to the plan and enter any confirmed pantry amounts. Add exact extras manually or copy the signed-in cart for the selected catalogue location. Review ingredients and change the plan before saving. A saved weekly plan stores curated recipe IDs and input quantities. A private generated draft can still be used in the standalone Recipes workflow for its existing lifetime.

Weekly comparison checks the shared fresh verified offer service and known multi-pack stock, then evaluates whole baskets with applicable retailer terms. Each ingredient can use up to two compatible pack sizes from one retailer. The standalone Recipes screen keeps its ingredient subtotal workflow. [Unified workflow](PERSONALIZED_RECOMMENDATIONS.md#unified-shopping-plan)
