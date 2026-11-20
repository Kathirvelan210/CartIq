# Backend API workflows

Base URL: `/api`. JSON DTO definitions are in `backend/src/main/java/com/smartcart/dto/Models.java`. The live OpenAPI schema at `/api/openapi` is the complete endpoint reference and can be imported into Postman.

| Area | Endpoints |
| --- | --- |
| Accounts | `POST /auth/register`, `/auth/login`, `/auth/demo`, `/auth/refresh`, `/auth/logout`; `GET/PATCH /me`; `POST /me/password` |
| Email verification | `POST /me/email-change`, `POST /me/email-change/confirm` |
| Catalogue | `GET /locations`, `/categories`, `/platforms`, `/meta`, `/products?locationId=...&q=...&category=...&limit=...&offset=...`, `/products/{id}?locationId=...` |
| Cart and decisions | `GET/PUT /me/cart`, `DELETE /me/cart/{id}`, `DELETE /me/cart?locationId=...`, `GET /me/decisions?locationId=...`, `POST /cart/optimize` |
| Saved products | `GET /me/wishlist`, `PUT/DELETE /me/wishlist/{id}` |
| Watches | `GET/PUT /me/watchlist`, `DELETE /me/watchlist/{id}`, `POST /me/watchlist/check` |
| Notifications | `GET /me/notifications`, `PATCH /me/notifications/{id}/read`, `GET/PUT /me/preferences`, `POST/DELETE /me/device-tokens` |
| Budgets/history | `GET/PUT /me/budget`, `POST /me/expenses`, `GET /me/history`, `POST /me/history/sample`, `GET /me/spending-trend`, `GET /me/overview?locationId=...` |
| Replenishment | `GET/PUT /me/replenishment`, `POST /me/replenishment/{id}/restocked`, `DELETE /me/replenishment/{id}` |
| Recipes/pantry | `GET /recipes?q=...`, `POST /recipes/{id}/plan`, `POST /recipes/{id}/cart`, `GET/PUT /me/pantry` |
| Models/assistant | `GET /products/{id}/analytics?locationId=...`, `POST /me/assistant`, `GET /me/recommendations?locationId=...`, `GET /me/models`, `GET /me/ingredients/match?query=...` |
| Training export | Local sample only: `GET /training/sample`; administrator-only real observations: `GET /admin/training/data` |
| Administration | `GET /admin/overview`, `/admin/users`; `PUT/DELETE /admin/products/{id}`; `POST /admin/categories`, `/admin/locations`; `PATCH /admin/platforms/{id}` |
| Provider operations | `PUT /admin/provider/mapping`, `POST /admin/provider/sync?locationId=...` |
| Checkout capability | `GET /retailers/{platform}/capabilities` reports ordering disabled; no retailer order/payment routes are implemented by this shopping-list backend. |

Private endpoints require `Authorization: Bearer <token>`. A session response remains compatible with the supplied frontend: `{token,user}`. Registration requires 12–72 characters and at most 72 UTF-8 password bytes. Refresh credentials are browser cookies, not React localStorage values.

Purchase preference fields use paise and nullable optional limits. Essential settings permit a maximum per-pack price. Optional settings permit a target price and monitoring. Bulk settings require a useful pack count and consumption period, with optional unit-price and total-item budgets. Requests with fields inappropriate to the intent are rejected.

Basket evaluation includes every requested item. Missing prices and unknown fees remain explicit. Price and availability must be fresh and verified (or explicitly SAMPLE in local sample mode). Sample purchase recording requires a request key and is idempotent per user; it does not imply a retailer order.

Recipe plans adjust base quantities for servings, subtract pantry amounts and explicit owned ingredients, compare matching packs, and round up whole packs. Adding the plan uses a transaction and retains existing cart preferences. A missing available ingredient prevents an incomplete plan from being silently added as complete.

Provider mappings require an authorized administrator and an exact reviewed SKU/variant match. Provider observations retain original time, unknown stock and nullable prices. The adapter follows the published Rewalto observations envelope; fixture contract tests are synthetic and do not establish live account access. Manual synchronization can consume provider credits when enabled; no live sync was run in this delivery.

Validation errors use a safe `{code,message}` body. Ownership violations return not found. Live service failures do not become fabricated data. Product archival preserves shopping history and reference integrity.

