# CartIQ in-app checkout — integration and delivery notes

## Implemented frontend

The existing Expo application is preserved. Catalogue cards, product comparisons and saved finds feed the same persistent cart. Saved finds have a Move to cart action. Quantity controls, individual removal, location-scoped clear with confirmation, strict persisted-cart validation, and separate location carts are supported.

Best-price mode selects each item's lowest fresh comparable offer and groups it by retailer. Single-store mode lets users select Blinkit or Instamart, shows that store's available items, and identifies products excluded from checkout. Zepto is a visibly disabled future integration. Store estimates show how many cart lines they cover; partial estimates are not presented as complete orders.

The cart includes item-price timestamps, stale/unavailable exclusions, price/store change alerts, per-store subtotals, savings versus highest comparable prices, MRP discounts already included, fee uncertainty, and a sticky per-store checkout action. Multiple retailers produce separate order flows and deliveries. Refreshing can change the best-price store; checkout stops and sends users back if an assigned store is no longer eligible.

Checkout has five stages: order review, saved/add/edit/default delivery address, delivery information, gateway-ready payment choices, and retailer order status. No sample checkout success is included. Unsupported checkout explicitly keeps the cart and reports unconfirmed order/payment status. No external purchase redirects occur.

Addresses must match the active location, city and PIN; retailer-side serviceability remains authoritative. Up to eight addresses are stored in native SecureStore, individually to avoid oversized values. The browser preview keeps addresses in memory for the current session. Payment credentials are never collected by CartIQ fields or persisted. Light/dark palettes follow the device and can be switched using the header icon. Quantity and checkout transitions use native layout/opacity animations.

## Retailer backend contract

All endpoints are hosted on your own authorized backend under EXPO_PUBLIC_API_URL. Never call private retailer endpoints directly from the app. Add the following routes to the existing API contract:

| Method | Route | Purpose |
|---|---|---|
| GET | /retailers/:platform/capabilities | Return {ordering:boolean,paymentMethods:[]} |
| POST | /retailers/:platform/cart | Get a retailer cart quote for {locationId,items,address?} |
| POST | /retailers/:platform/cart/validate | Fresh price, stock, serviceability and fee validation |
| POST | /retailers/:platform/orders | {quoteId,paymentMethod}, Idempotency-Key header; return actual {id} |
| POST | /retailers/:platform/payments | {orderId,method}, Idempotency-Key header; return gateway intent |
| GET | /retailers/:platform/orders/:id | Return backend-verified OrderStatus |
| GET | /retailers/:platform/orders/by-request/:key | Recover an uncertain request; OrderStatus or JSON null |
| POST | /retailers/:platform/orders/:id/cancel | Request cancellation and return verified OrderStatus |

Canonical models are in src/services/checkout.ts. Monetary values use INR rupees, consistently rounded to two decimals. RetailerQuote contains a real quote ID, retailer, location, exact item IDs/quantities/unit prices, fresh timestamps, stock confirmation, address validation, subtotal, additional discount, nullable delivery/handling charges, nullable estimated total, expiry and optional delivery estimate. Do not invent fees or ETAs. A null fee cannot yield a confirmed total. The frontend rejects stale/mismatched quotes and invalid totals; the backend must independently enforce all validations and stock reservations.

Capabilities default to ordering=false in sample mode; Zepto remains disabled. For unconfigured production adapters, return ordering=false rather than fabricated data. Provider failure must propagate as an actionable error and must not enable ordering.

OrderStatus must contain the actual retailer/backend order ID, platform, location, exact purchased items and quantities, status, paymentStatus and total. Valid order states: pending, confirmed, completed, cancelled, failed. Valid payment states: pending, paid, failed, cod_due. Only matching confirmed/completed orders with paid/cod_due status remove purchased quantities. Additional units added after the purchase snapshot and other locations' carts are preserved.

## Payment gateway integration

Register an authorized native gateway SDK adapter with registerPaymentBridge() in src/services/retailers.ts. Its methods list controls which online choices are enabled. present(intent) must open the gateway's own secure payment UI. It must never collect credentials using CartIQ inputs. A completed gateway callback is not payment proof: the app requests the backend order status afterward. The backend must verify signed gateway webhooks and amounts, bind the quote/address/user, and enforce account ownership. Cash on delivery is enabled only if the retailer advertises it.

The final action revalidates stock, address, fees and prices. Changed totals require another review and acceptance. A quote with unknown charges cannot be ordered. Before the network mutation, the app persists an opaque idempotency request key and item snapshot (without address or payment credentials); it saves the backend order ID if received. After timeouts or restart, it checks the same request/order status rather than silently creating another order. Your backend MUST enforce idempotency transactionally and implement recovery by request key. Status lookup must reconcile incomplete requests and return their authoritative state. Failed/cancelled attempts can be cleared; unresolved attempts remain protected from duplicate submission.

cancelOrder() is exposed in the adapter for integration. Cancellation policy, refund handling and a production cancellation UI depend on retailer capabilities and are not claimed as implemented retailer operations.

## Changed and added files

| File | Change |
|---|---|
| App.tsx | Checkout navigation, per-store checkout routing, sticky cart bar, theme switching and checkout-aware back/location handling |
| src/screens/Cart.tsx | Stable entry point re-exporting the cart components |
| src/screens/CartExperience.tsx | Full cart UI, modes, totals, exclusions, alerts and clear controls |
| src/screens/Checkout.tsx | Five-stage flow, refresh/quote validation, payment bridge, request recovery and verified order status |
| src/components/AddressBook.tsx | Saved/add/edit/default address forms and location validation |
| src/services/cart.ts | Pure cart modes, grouping, persistence parsing and quantity helpers |
| src/services/checkout.ts | Common retailer interface, models, quote/address checks and confirmed-item cleanup |
| src/services/retailers.ts | Backend adapter binding and authorized payment bridge registration |
| src/services/attempts.ts | Persisted idempotent order-attempt recovery without credentials |
| src/services/addresses.ts | Native secure address persistence; session-only browser preview |
| src/store.tsx | Mode/store state, refresh results, cart helpers and confirmed-purchase cleanup |
| src/components/ProductCard.tsx, src/screens/Discover.tsx | Move saved products to cart |
| src/theme.ts, src/components/ui.tsx, app.json | Theme palette and automatic light/dark support |
| Existing screens | Shared surface colors updated for dark-theme compatibility |
| tests/comparison.test.ts | Expanded comparison/cart/checkout/integration failure tests |

## Verification and remaining dependencies

TypeScript strict check and 22 automated tests pass. Web, Android and iOS production JavaScript/Hermes exports pass. Browser checks cover store modes, checkout review, invalid PIN rejection, sample address save, delivery uncertainty, disabled unconnected payment methods, honest unavailable status, and retained cart. No live retailer order or payment was submitted.

Production still requires authorized retailer ordering agreements/APIs, backend stock and quote services, gateway SDK setup and webhook verification, privacy/consent and address lifecycle policies, signing, and device QA. Android hardware back/iOS device navigation, native SecureStore persistence, gateway cancellation, real API failures and successful retailer confirmation require physical-device and staging-backend integration tests. Bundle exports are not APK/AAB/IPA builds or store certification. Review privacy disclosures before deployment; browser addresses intentionally do not persist across reloads.

Reference implementation guidance: [Expo SecureStore](https://docs.expo.dev/versions/latest/sdk/securestore/), [React Native LayoutAnimation](https://reactnative.dev/docs/layoutanimation/), [React Native Appearance](https://reactnative.dev/docs/appearance).
