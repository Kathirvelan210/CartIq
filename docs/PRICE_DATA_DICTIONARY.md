# Price data dictionary

## Marketplace reuse

The marketplace response carries the normalized provider listing and per-row offer evidence. Native listing ID, prices and supply details are provider values. Unavailable optional fields stay null. Reused data is labelled **Previously retrieved** and retains the original fetch time, an upstream observation time when available, expiry and the provider request ID. A shopping-plan quote derived from that cache has origin `RETRIEVED_OFFER`. It is transient and is removed when the result expires or the operator disables its pilot.

The cart persists canonical variant, location and quantity. The retailer SKU, last price, stock and provenance are presented from the transient exact-pack offer reader while eligible. No provider field is saved to price history or to the durable cart. A missing price stays unknown, and retailer costs with missing fees remain incomplete.

## Transient QuickCommerceAPI offers

| Field | Meaning |
| --- | --- |
| `retailer`, `nativeId`, `variantId`, `productId` | Provider retailer and listing ID. Canonical IDs are set only after a verified current mapping and identity check. |
| `pack`, `unit`, `unitPriceMinor` | Source label and standard unit. The per-unit price uses the complete normalized pack, including the multipack quantity. |
| `priceMinor`, `mrpMinor`, `available`, `stockPacks` | INR paise and reported availability or stock count. Missing data remains null. |
| `observedAt`, `fetchedAt`, `freshUntil` | Source observation if a valid time was supplied, CartIQ fetch time and validity bound. An older source timestamp can shorten the configurable current-offer window, which defaults to 15 minutes. An invalid or future source timestamp makes the offer ineligible. |
| `locationId`, `source`, `requestId`, `match`, `confidence`, `score` | Scoped placement and provenance, provider UUID when verifiable, and current human-mapping plus heuristic comparison outcome. Scores are neither calibrated probabilities nor mapping approval. |

The normalized current quote uses `origin` to distinguish `LIVE_RESPONSE` from `RECORDED`. Its `observedAt` is CartIQ's fetch time for a live response, with the optional upstream time separately identified as `providerObservedAt`. Transient offers are excluded from the durable observation schema, feedback, price history and training.

## Retained observations

| Field | Meaning |
|---|---|
| `variant_id`, `listing_id`, `retailer_sku` | Canonical purchasable variant, reviewed internal retailer listing, and native listing ID at capture. The listing carries the retailer platform. |
| `location_id` | CartIQ delivery area in the grant. Matches are exact by ID. PIN similarity does not merge observations. |
| `price_minor`, `mrp_minor` | Nullable selling and maximum retail price in INR paise. A returned zero remains zero. |
| `discount_percent` | Nullable provider-reported percentage, from 0 to 100 with at most two decimal places. It is never derived from a missing or inconsistent field. |
| `stock_status` | `AVAILABLE`, `OUT_OF_STOCK`, or `UNKNOWN`. No quantity or serviceability is inferred. |
| `pack_label`, `pack_count`, `each_amount`, `base_unit` | Canonical label, number of packs, and amount per pack in `g`, `ml` or `piece`. A single 1 L unit differs from two 500 mL units. |
| `observed_at`, `recorded_at` | Source observation time and CartIQ intake time. Observations are stored at whole-second precision. Internal lookups retain their own transient fetch time. |
| `source_provider`, `source_record_id`, `source_evidence` | Provider, immutable source record identifier and reference to reviewed evidence. Raw payloads and credentials are outside this store. |
| `grant_id` | Reviewer-approved scope contains the current mapping decision, location, timestamps, permissions, evidence SHA-256 and revoke state. |

Observation replay with the same immutable identity and values returns the existing ID. A conflicting record for the same source ID or listing/location/timestamp is rejected. Serving users cannot read grant evidence or provider record IDs through the shopper history route.

`GET /api/grocery/variants/{variant}/price-history?locationId=...` requires authentication, defaults to a 90-day window, and optionally accepts ISO-8601 `from` and `until` as a half-open range of up to 366 days. It computes count, priced count, minimum, maximum, rounded average, per-retailer/listing UTC daily aggregates, last observed date and change from the previous known price when the latest reading contains a price. Only readings with a reported price contribute to price aggregates. Stock status remains separate and can be unavailable even when a price was reported. No price gap is filled. The latest observation in the requested window is `FRESH` for 24 hours, then `STALE`. This status describes observation age, not a current offer guarantee.
