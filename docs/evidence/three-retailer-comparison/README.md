# Three-retailer comparison preflight

On 19 November 2026, the signed-in CartIQ shopper opened the local storefront in Chrome 158.0.8127.36. The saved manual delivery location was Coimbatore, PIN 641008, latitude 10.942972, longitude 76.953417. Spring Boot ran the local profile on 127.0.0.1:8080 with file-backed H2. The frontend ran on localhost:8092.

## Browser evidence

| Search | Preview HTTP | Ledger duplicate | Confirmation | Paid requests |
|---|---:|---|---|---:|
| milk | 200 | BlinkIt | Disabled for the entire batch | 0 |
| amul taaza toned milk 1 litre | 200 | None of the three | Enabled at the time of preview | 0 |

See [saved location](01-saved-coimbatore.png), [requested milk preview](02-milk-preview.png), [exact-pack alternative](03-exact-pack-preview.png) and [authenticated preview values](preview.json). No customer listings or retailer prices were persisted. The browser guard blocked paid confirmation during verification. No retailer returned a new search response in this run.

The Spring Boot adapter read the supported platforms and available credits. The provider platform for Swiggy Instamart is Swiggy. The account and ledger agreed on 90 credits, 10 experiment attempts remained, and the minimum balance was 77. The ledger SHA-256 before and after was 2B7C4B15B717E269057144ACB6B85C4FCD5AD27C8CF0A4D4837EECBD4A7AE41E. An ancillary free check through the separate MCP connection returned an authentication error. The balance readings in the screenshots came through the working CartIQ backend adapter.

## Paid execution boundary

The alternative requires separate approval. For each of BlinkIt, Zepto and Swiggy, the route is GET /v1/search with q=amul taaza toned milk 1 litre, pincode=641008, lat=10.942972 and lon=76.953417. Three successful debits would leave 87 credits and seven experiment attempts. Refresh the short-lived preview and verify the balance again before dispatch.

The local backend binds the preview to the account and selected position. Confirmation atomically reserves the three fingerprints in the persistent ledger before the three workers start. Each worker sends one request. Reopening the local status endpoint reads progress in memory. The provider balance and per-request evidence must reconcile before settlement. An interrupted batch retains a batch-specific pending ledger with its three fingerprints. Preserve that file for an operator reconciliation against provider receipts. The old single-reservation reconciliation command rejects this format.

## Verification

| Command | Result |
|---|---|
| npm test | 75 passed, zero failures or skips |
| npm run typecheck | Exit 0 |
| npx --no-install expo export --platform web | Exit 0 |
| mvn -o -Dtest=ConcurrentComparisonTest,ShopperComparisonControllerTest,QuickCommerceProviderTest test (backend) | 53 passed, zero failures or skips with CARTIQ_CAPTURED_BLINKIT_BODY set to the existing local historical receipt |

The HTTP concurrency tests used loopback and disposable ledger files. They verified overlapping requests, individual retailer failures, absent billing metadata, settlement mismatches, account isolation, single confirmation use and crash protection. The preserved historical BlinkIt receipt replay parsed 24 previously returned products. Transport headers in that replay are fixtures. The price comparison tests used fixtures to distinguish listed prices from complete payable amounts, exclude contradictory stock and incompatible variants, and calculate differences between equivalent packs. A genuine three-retailer winner remains unverified.

The documented search response supplies item prices and may supply platform delivery estimates. Complete checkout fee schedules and retailer minimums have not been established for these search results. The comparison displays item prices where complete fee evidence is unavailable. No shared MySQL access, migrations, retailer debits, geocoding or model requests occurred.
