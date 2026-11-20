# Retailer listing foundation

## Marketplace discovery

On the local authenticated shopper route `/api/shopping/retailers/marketplace`, GET reads current search authorizations and reusable normalized results, POST `/search` executes one authorized query, and POST `/cart` admits one fresh exact approved pack. The admin issues marketplace tickets through `POST /api/admin/retailer-pilot/{id}/authorize-discovery`. The controller uses the same QuickCommerceAPI adapter, controlled pilot and persistent ledger. No request is made when the screen opens or when a usable result is already cached. Existing exact-pack item lookups remain available through their separate authorization.

Unmatched returns may be shown under written private display permission. They do not receive canonical IDs or cart controls. A marketplace ticket can contribute the anchored mapping to the existing account and location scoped offer reader after the normal pack, GTIN, flavour and formulation checks. Candidate records are deduplicated by retailer and native listing ID. Shopper actions do not publish mappings. [Pilot setup](CONTROLLED_GROCERY_PILOT.md#phase-9-marketplace-pilot)

## Retailer operations dashboard

Open **Profile → Admin dashboard → Retailer coverage and data quality**. Choose a delivery area, retailer, category and offer status. The catalogue and recorded coverage totals use distinct active products. The table pages exact variants and retailers and shows the observation source, observed time, missing stock and verified fee status. Offer status filters the table. Summary counts describe the selected retailer, category and location. Counts for price, stock, fee and pack-quality problems can overlap. **Usable recorded products** means the approved product and retailer have a fresh display-eligible priced availability signal. Exact stock and verified fees are evaluated separately for bulk or complete basket recommendations. **Live for this account and confirmed location** counts only the signed-in admin's currently eligible pilot quotes. No broader account-level live data is exposed.

Review a row to enter source listing fields and a permission and evidence reference. The preview shows the catalogue identity, normalized pack checks, confidence and blockers. After reviewing the evidence, confirm the action and record approval, rejection or unresolved status. Choose **Correct or supersede** in history to prefill the listing fields. Supply the new evidence and rationale for that revision. The API checks the predecessor for the exact candidate and requires new evidence to approve a previous rejected or unresolved candidate. A conflict with an existing listing that has price observations remains blocked. History pages through the append-only V11 decision journal. Unreviewed transient provider data stays outside this workflow unless a reviewer supplies independently authorized evidence.

The admin routes are `GET /api/admin/retailer-operations/filters`, `GET /api/admin/retailer-operations/coverage`, and the existing `/api/admin/provider/mapping` workflow with a new read-only `POST /mapping/preview` action under the provider prefix. No migration was introduced.

## QuickCommerceAPI transient offers

QuickCommerceAPI remains the only active external adapter. The outbound platform names are `BlinkIt`, `Zepto` and `Swiggy`. The adapter recognizes the response display name `Swiggy Instamart` only for the requested Swiggy platform. A successful wrapper with a failed platform becomes a retailer-level failure. Batch selection uses serial single-platform lookups. An uncharged confirmed retailer failure can leave other retailers eligible. An uncertain, configuration or rate-limit outcome stops the remaining batch.

The local authenticated routes under `/api/shopping/retailers/offers/{variant}` are GET for current quotes, POST `/refresh` for exact-pack search and POST `/item` for a fresh verified native listing from that account and location. The server builds the search query from the canonical brand, title and pack. It checks the configured area, confirmed exact coordinates and matching PIN when supplied, then uses the catalogue PIN consistently for the request fingerprint. Both paid operations keep the existing persistent credit ledger. Other catalogue areas retain recorded-only estimates. No background or automatic paid lookups are scheduled.

Retailer candidates show their native ID, nullable offer fields, freshness and match score. Planning eligibility requires the latest approved QuickCommerceAPI SKU-to-variant mapping under the configured permission reference and compatible live identity evidence. Live values are held only in bounded process memory. Every quote read rechecks the approval. Exact stock remains unknown when the provider omits it. A successful refresh with no eligible offer suppresses that retailer's older quote within the pilot window. Failed refreshes show their failure independently of any earlier unexpired result. [Identity rules](PRODUCT_MATCHING_BENCHMARK.md#live-offer-identity-gate) · [Operations](OPERATIONS_RUNBOOK.md#quickcommerce-transient-offer-pilot)

## Original retailer listing foundation

Flyway V1–V10 established separate catalogue, listing and observation records.

| Concern | Existing representation |
|---|---|
| Canonical identity | `products` describes the brand, product and formulation. `product_variants` describes a sellable pack. `variant_barcodes` holds canonical GTINs and `external_product_sources` holds raw catalogue provenance. |
| Retailer listings | `grocery_platforms` and `platform_products` identify retailer and retailer SKU. The listing points to an exact canonical variant. Current uniqueness permits one listing per retailer and variant, with SKU uniqueness per retailer. |
| Mapping review | `provider_mappings` records reviewer and time. The admin catalogue matcher provides advisory evidence. A reviewer still needs a barcode-linked label or equally specific authorized source for variant, formulation and packaging. |
| Current observations | `location_prices` stores the latest observation per listing and delivery location. It carries nullable price and MRP in minor INR units, reported availability, original observation timestamp, provider and verification status. Unknown and out-of-stock are distinct. |
| Historical evidence | `price_history` stores dated non-null price samples. `online_comparison_evidence` records exact identity, stock quantity and fulfilment terms with source, reviewer, and timestamps. |
| Freshness | Comparison requires a verified eligible offer and independently fresh stock and fulfilment evidence. Cached responses retain the original observation timestamp. Catalogue publication creates no offers. |

An existing SKU can be corrected before observation history exists. Once a listing has a current or historical observation, a different SKU returns 409 and retains the original data. Sync rechecks the SKU under the listing lock before writing an observation. This prevents a fetch started for a previous SKU from being attached after remapping.

For a future authorized feed, add immutable retailer listing identity independent of the canonical match, then audit the mapping decision with source, reviewer, and evidence. Keep identifiers, licensing and original source snapshots tied to the feed. Store separate price and availability timestamps if the partner reports them independently. The current schema records stock in current observations and comparison evidence. It does not provide a complete availability history or support multiple active retailer listings for one canonical variant. Those extensions need a reviewed migration and allowed-use agreement.

The existing provider remains gated by configuration, credentials and publication rights. This milestone did not collect or add retailer listings, prices, or availability.

## Retailer adapter preparation

The server routes Blinkit (`blinkit`), Zepto (`zepto`) and Swiggy Instamart (`instamart`) through separate retailer adapters. These use the existing Rewalto transport and check its response retailer, exact native listing ID and six-digit PIN before returning an observation. Each adapter requires an HTTPS source URL on a known retailer host for a displayable link. Untrusted URLs are omitted. The provider record keeps nullable INR selling price and MRP in paise, unknown versus reported availability, original observation time and provider attribution. The canonical variant is still selected through explicit admin mapping.

The transport uses the published Rewalto Core 0.203.0 observation shape and requests at most one dated record per retailer, SKU and PIN. It rejects malformed, out-of-range and negative money fields. Existing comparison freshness rules continue to use the original observation time. The current schema carries the provider and listing URL. Persistent provider observation IDs, original per-observation source URLs and receipts, and an availability history need a reviewed schema extension before a production feed is adopted. [Rewalto commerce contract](https://rewalto.com/developers/docs/data) · [Downloadable OpenAPI](https://rewalto.com/developers/openapi.json)

Live requests still require the default-off `REWALTO_ENABLED` and `REWALTO_PUBLIC_DISPLAY_APPROVED` flags and a server-side `REWALTO_API_KEY`. Once the global gates are enabled, `REWALTO_BLINKIT_ENABLED`, `REWALTO_ZEPTO_ENABLED` and `REWALTO_INSTAMART_ENABLED` can independently disable a retailer. Rewalto's published terms limit purchased data to internal use unless written permission or a separate agreement covers outside sharing. Archive the agreement, allowed retailers and locations, retention rules and display rights before turning on a feed. The public contract was examined without account access, data calls or purchases. [Rewalto terms](https://rewalto.com/terms)

The official Blinkit Seller Hub and Zepto Brand Portal document seller onboarding. Swiggy's Instamart MCP provides location-scoped product search under its own partner and OAuth terms. A direct retailer adapter needs its own approved access and permission to retain and display comparison observations. This milestone added no direct seller-portal, MCP or scraping connector. [Blinkit Seller Hub](https://seller.blinkit.com/) · [Zepto Brand Portal](https://brands.zepto.co.in/) · [Instamart MCP reference](https://mcp.swiggy.com/builders/docs/reference/instamart/) · [Swiggy partner contract](https://mcp.swiggy.com/builders/docs/operate/)

### Observation validation and replay

- Sync checks the requested retailer, exact listing ID and PIN before writing. Current availability distinguishes available, out of stock and unknown. An availability-only record never creates a history price.
- The current schema keys observations by listing, location and whole-second source time. Replays at the current timestamp are ignored, preserving saved comparison evidence. Older priced observations can fill gaps in price history while the newer current row remains in effect. A new current observation invalidates the comparison evidence bound to the previous timestamp.
- Two authorized events for the same listing and location within one second share a storage key. A stable external event ID and finer source timing would require a reviewed migration. The matching scorer remains advisory and retailer mapping still requires manual review.

### Evidence-assisted manual mapping

`POST /api/admin/provider/mapping/review` takes a canonical variant ID and the published listing identity supplied by an admin. It returns the selected pack, registered codes, advisory hybrid decision, and blockers. It performs no writes. `PUT /api/admin/provider/mapping` accepts that identity optionally for compatibility with existing clients. When supplied, the service rejects wrong or unconfirmed packs, declared brand or variant conflicts, malformed or restricted codes, and a code conflicting with the selected variant or registered elsewhere. The selected canonical variant remains the unit of comparison and price history.

A human still approves the mapping. The current schema stores the original approval actor/time for an idempotent replay and updates the actor/time on an allowed SKU change. It does not persist preview evidence, a separate disposition for ambiguous suggestions, or a full mapping revision history. Verified formulation data is unavailable on existing canonical rows. The strict V7 observation-evidence route still gates exact comparison identity independently. [Source permissions and licensed pilot requirements](INDIA_RETAILER_DATA_SOURCES.md)
