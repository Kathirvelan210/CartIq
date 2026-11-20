# Bulk-buy intelligence

In **AI Smart Tools → Bulk buy**, save your household size, pick the exact pack you normally buy, and enter daily use per person, total storage capacity and planning days. Use grams, millilitres or pieces as shown. Add a budget and confirmed days before the packs expire to evaluate extra purchases. The planner reads the pantry quantity for the same canonical product and unit. It saves the account's consumption limits, but the use-by estimate applies to that check only.

The planner can recommend a normal quantity, a larger pack, multiple packs, no extra purchase, or ask for missing information. It deducts the pantry from household need and from usable storage, checks how much the household can consume before expiry, and applies the lowest of the item limit, household shopping budget and remaining monthly budget. It requires a fresh price for the normal pack and a confirmed stock count sufficient for each extra purchase. A substitute pack must share the canonical product, unit, and recorded flavour, formulation, ingredients and diet. A larger pack must cost less per standard unit and less overall than enough normal packs to cover its quantity. Multiple normal packs may cover planned use without a unit-price saving.

Offers are read from the selected catalogue location. Verified catalogue offers have a 15-minute freshness window. Stock counts require evidence tied to the exact listing, pack, location and price snapshot. Retained observations can contribute only under current display permission with a matching active mapping and retention grant. They carry no numeric stock count. Sample mode marks its output explicitly. Item recommendations exclude retailer fees and make no historical or future price claims.

## API and data

The authenticated `/api/me/shopping-intelligence` endpoints use the token's account and return `Cache-Control: no-store`.

| Method | Path below the prefix | Purpose |
| --- | --- | --- |
| GET, PUT | `/preferences` | Household size, explicit diet, cuisine and shopping budget |
| GET, PUT | `/bulk/{variant}/limits?locationId=…` | Consumption, storage, planning horizon and optional pack budget |
| GET | `/bulk/{variant}?locationId=…&daysBeforeExpiry=…` | Explainable pack and purchase quantities, timestamps and evidence-based availability |

Flyway V16 adds `shopping_intelligence_preferences` and `bulk_buy_preferences`. The migration creates two account-owned tables without altering retailer, cart, or price history tables. The endpoint requires the normalized catalogue schema.

In Phase 5, **Shopping plan → Bulk-buy check** runs the same rules for a selected exact pack. You can supply confirmed use-by days and review the quoted pack, price, unit price, stock evidence, observation time and budget before deciding. The plan does not add extra packs on your behalf. Use **Set consumption and storage** to edit persistent limits. [Flow and feedback](PERSONALIZED_RECOMMENDATIONS.md#unified-shopping-plan)
