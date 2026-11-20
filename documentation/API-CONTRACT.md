# Spring Boot integration contract

Base URL is EXPO_PUBLIC_API_URL, e.g. https://api.example.com/v1. JSON request/response bodies; timestamps are ISO 8601 UTC. Send Authorization: Bearer <token> after sign in. The server must validate roles and ownership on every protected request. Never pass provider credentials to the client.

| Method | Path | Response / action |
|---|---|---|
| GET | /locations | Location[] |
| GET | /products?locationId=... | Product[]; return healthy provider results when another fails |
| POST | /auth/register | {name,email,password,phone?} → Session |
| POST | /auth/login | {email,password} → Session |
| POST | /auth/logout | Revoke current token; 204 |
| GET | /me | User |
| PATCH | /me | {name} → User |
| POST | /me/email-change | {email}; send verification; 204 |
| POST | /me/password | {currentPassword,newPassword}; 204 |
| GET | /me/wishlist | string[] product IDs |
| PUT | /me/wishlist/:productId | Idempotently save; 204 |
| DELETE | /me/wishlist/:productId | Remove; 204 |
| GET | /admin/overview | AdminData |
| GET | /admin/users | User[]; admin only |
| PUT | /admin/products/:id | Product → Product; admin only |
| DELETE | /admin/products/:id | Admin only; 204 |
| PATCH | /admin/platforms/:id | {enabled:boolean}; admin only; 204 |

Canonical TypeScript models live in src/types.ts. Example product:

```json
{
  "id":"milk-1l", "name":"Amul Taaza", "brand":"Amul",
  "quantity":"1 litre", "category":"Dairy & eggs",
  "description":"Toned milk", "color":"#78B5DA", "art":"carton",
  "offers":[{
    "platform":"blinkit", "price":68, "mrp":75,
    "status":"available", "updatedAt":"2026-11-04T08:00:00.000Z",
    "productId":"milk-1l", "variant":"1 litre", "locationId":"blr-indiranagar",
    "purchaseUrl":"https://blinkit.com/authorized-product-path"
  }]
}
```

Use current actual timestamps, never the example date. Missing prices are null. Availability: available, out_of_stock, unavailable, no_data. The frontend considers prices older than 15 minutes stale and excludes them from lowest-price selection. Configure this policy in src/services/comparison.ts to match provider SLAs. Equivalent product matching must be established server-side; productId, exact normalized variant and locationId are checked again in the client. Taxes should be consistently included in selling prices; fees are excluded.

Location: {id,name,city,postcode}. User: {id,name,email,role:customer|admin}. Session: {token,user}. AdminData: {users:number,products:number,categories:number,platforms:[{id,enabled,status,syncedAt:string|null}]}.

HTTP 401 clears native credentials and requests sign-in; 403 denies access; 409 indicates duplicate email; 5xx displays a retryable service error. Requests time out after 12 seconds. Serve safe validation errors without disclosing credentials or internal infrastructure. Configure CORS for browser preview. Use TLS for production; native credentials use SecureStore and web sessions are memory-only.

For a large catalogue, add paginated server search before launch; the current frontend retrieves and filters the location catalogue in memory. Add refresh-token rotation or your chosen session renewal mechanism server-side and extend the client before long-lived production sessions. Product image URLs, category CRUD, configuration management and account deletion endpoints are intentional integration extensions, not implemented API claims.


## In-app checkout extension

See CHECKOUT-INTEGRATION.md for retailer capability, quote, validation, order, payment, status recovery and cancellation routes. The common interface and exact models are in src/services/checkout.ts. No retailer checkout or gateway capability is assumed from catalogue access.
