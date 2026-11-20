# CartIQ grocery comparison UI verification

On 19 November 2026 at 21:36 IST, Chrome submitted one approved comparison from the local CartIQ storefront for “Amul Taaza toned milk 1 litre”. Spring Boot sent three independent QuickCommerceAPI search requests with the shopper's saved Coimbatore location, PIN 641008, latitude 10.942972 and longitude 76.953417. The test used the signed-in local QA account and file-backed H2. Shared MySQL was untouched.

| Platform | Provider HTTP | Platform outcome | Listings | Verified credit debit | Provider request ID |
|---|---:|---|---:|---:|---|
| BlinkIt | 200 | Success | 24 | 1 | 363b1c9c-64f5-4d20-a1c5-18bc4a4d0496 |
| Zepto | 200 | Provider reported a failed platform | 0 | 0 | c8f09e1b-3538-459f-b8f2-a43ed766f0ae |
| Swiggy | 200 | Provider reported a failed platform | 0 | 0 | e3d40c0a-90b0-4250-a09f-365ecf4c2b92 |

The pre-dispatch account check and ledger agreed on 86 credits. The final provider balance and settled ledger agreed on 85. Nineteen of 20 experiment attempts have been used. One attempt remains, which is insufficient for another three-retailer batch. The protected floor is 77. No requests were retried.

## Observed shopping workflow

- The real search screen restored the saved delivery location, showed departments, and previewed the three authorized platforms.
- BlinkIt SKU 176 rendered with the provider's Amul brand, Taaza Homogenised Toned Milk title, 1 ltr pack, item price and MRP of ₹77, in-stock status, product photo and eight-minute delivery estimate. Fees were unknown. The other two columns showed provider failures. This establishes neither delivery unavailability nor a cheapest cross-retailer price.
- Add to Cart submitted the chosen retailer and native SKU. Spring Boot returned HTTP 200 after resolving the offer from the authenticated comparison. Reopening Chrome restored that exact item, retailer, pack, quantity and quoted price in the CartIQ session cart. This was a planning action and no order was placed.
- The session cart is scoped to the account and selected coordinates/PIN. The backend holds it in memory for 30 minutes of inactivity. Prices have their own expiry. No retailer observation was inserted into price history or mapped into a canonical product by this flow.
- The final styling adds retailer artwork from QuickCommerceAPI's public platform pages and retailer filters. Chrome checks verified the desktop filter and mobile category selector after the paid run. The earlier screenshot of the genuine result was preserved as captured. No extra request was made to recreate it.
- No separate Buyhatke screenshot was identifiable in the accessible attachments for this request. The implementation follows the specified horizontal product and retailer-column layout.

## Chrome evidence

[Saved location](01-delivery.png) · [Search and retailer logos](08-search-with-logos.png) · [Mobile search](09-mobile-search.png) · [Approved preview](03-authorized-preview.png) · [Actual three-retailer result](04-provider-results.png) · [Add to Cart](06-cart.png) · [Cart restored after reopening Chrome](07-restored-cart.png) · [Sanitized receipt](receipt.json)

## Automated verification

| Command | Result |
|---|---|
| npm test | 77 passed, 0 failed, 0 skipped |
| npm run typecheck | Passed |
| npx --no-install expo export --platform web | Passed and served locally |
| mvn -o -Dtest=ConcurrentComparisonTest,ShopperComparisonControllerTest,QuickCommerceProviderTest test | 54 passed, 0 failed, 0 skipped |

The targeted backend suite used a historical BlinkIt capture and local test fixtures. The new cart test initially used an unsupported mock of a final Java record. It passed after switching to the actual record type. The grouping and ranking tests cover pack conflicts, ambiguous duplicates, partial responses and stale offers. The live run returned offers from one retailer, so the lowest-price badge remains verified by automated calculation tests only.
