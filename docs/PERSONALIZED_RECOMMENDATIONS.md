# Personalized recommendations

**AI Smart Tools → For you** uses saved products, the cart at the selected location, recorded shopping selections, preferred brands, pantry quantities, configured replenishment reminders, budget and explicit household preferences. Results explain which signal contributed. Dietary filters use explicitly evidenced catalogue attributes and curated recipe tags. Missing labels remain unknown. No sensitive traits or consumption rates are inferred.

Curated recipe ideas use the existing pantry and recipe planner and show how many ingredients need checking or buying. Ingredient costs are pack subtotal estimates and remain blank when the planner cannot price the complete ingredient set. A cheaper pack alternative must share the exact canonical identity and compatible recorded formulation, flavour, ingredients and unit. Its pack combination must supply the same total quantity and have a lower item subtotal. Multiple packs require a recorded stock count.

**AI Smart Tools → Shopping insights** compares the signed-in account's cart at the selected location against an optional one-time limit. The optimizer evaluates whole carts and split baskets. Each cart line retains its selected variant and must be available in the requested quantity. Checkout totals require known delivery, handling and minimum-order terms. Store coverage, a subtotal or an unknown fee never becomes a complete total. If the bounded search stops early, the result identifies the best evaluated basket. Budget checks use the stricter of the one-time, household and remaining monthly limits.

**AI Smart Tools → Assistant** handles weekly shopping, pantry recipes, bulk quantities, spending limits, and cheaper cart alternatives with deterministic rules before the existing intent classifier. A suggested basket starts with eligible exact product packs and only adds an item if the complete price stays within budget. An existing cart is evaluated as entered. The assistant reports source, price observation time and limitations where relevant. It neither triggers retailer searches nor writes offers, shopping history or forecasts.

| Method | Endpoint |
| --- | --- |
| GET | `/api/me/shopping-intelligence/recommendations?locationId=…&budgetMinor=…` |
| GET | `/api/me/shopping-intelligence/basket?locationId=…&budgetMinor=…` |
| POST | `/api/me/assistant` |

The feature uses recorded fresh, exact offers. Transient retailer search results are kept on the existing live comparison path. Observations without permission for display, verified stock counts without matching offer evidence, and unverified fulfillment charges cannot support stronger recommendations. No model was trained or scored on real-world outcomes.

## Unified shopping plan

Open **AI Smart Tools → Shopping plan**. Select curated meals and days, set servings and an optional weekly budget, and choose an explicit diet. Add extras from the catalogue or copy exact packs from your cart. Select **Compare my weekly plan** to see combined ingredient demand, saved pantry deductions, compatible packs and missing quantities. Confirm individual pantry amounts, edit the list and run comparison again. Manual extras add to the recipe needs. The plan keeps the specific pack of each extra. Save when ready or reload the last saved plan for this location.

The basket search checks available stock across ingredients and extras that share the same retailer listing. It compares the best recorded item bundle per ingredient and retailer, then applies the existing delivery, handling, waiver and minimum-order rules. For unknown fees, missing quantities or stale quotes it labels the known item costs as partial and lists missing lines where applicable. Budget fit remains unknown for incomplete estimates, except where the priced items alone already exceed the limit. Search is bounded at 8,000 states per option. Pack combinations, location coverage, and confirmed retailer fulfillment terms limit the available choices. One available pack can be considered when its exact stock count is unknown. Retailer stock is never reserved.

The assistant routes typed requests to a fixed set of Spring services, including exact catalogue search, pantry, plan comparison, curated recipes, compatible alternatives, bulk-buy and saved price targets. It adds unit prices, observed timestamps, stock confidence and explanations to recorded offers. Timing can report a saved target match or read a previously validated forecast for the exact recorded offer if its approval still holds. A waiting suggestion requires the existing non-essential purchase safeguard. Otherwise the assistant uses the deterministic fallback. Unified assistant messages stay in the current UI session. The assistant never dispatches a shopping, Gemini, OpenAI or forecasting network request.

| Method | Endpoint |
| --- | --- |
| GET, PUT, DELETE | `/api/me/shopping-intelligence/weekly?locationId=…` |
| POST | `/api/me/shopping-intelligence/weekly/preview?locationId=…` |
| POST | `/api/me/shopping-intelligence/weekly/assistant?locationId=…` |
| PUT | `/api/me/shopping-intelligence/feedback/{reference}` |
| DELETE | `/api/me/shopping-intelligence/feedback` |
| GET | `/api/admin/shopping-intelligence/feedback` |

V17 retains one editable plan per account and location with an optimistic revision. Only scoped inputs are saved. The shopper can delete the plan or their feedback. Feedback records a reference, category, outcome and timestamp under the account. It stores no prices, product history, conversation or free text. An account can revise its vote for the same reference. Admin analytics reports counts from the last 90 days only when an outcome has contributions from at least five distinct accounts. Counts represent voluntary self-reports. Track accepted, rejected, modified and irrelevant outcomes to prioritize reviewed rule evaluations. No feedback changes production recommendations automatically or authorizes model training.
