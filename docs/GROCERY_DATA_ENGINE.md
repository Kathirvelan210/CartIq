# Grocery data engine

## Phase 10 · 18 November 2026

V20 adds nullable `cart_items.mapping_decision_id` with a foreign key to the existing human review journal. Existing planning rows remain readable. A saved selection identifies the canonical pack and its reviewed retailer listing separately. The marketplace cart action can explicitly switch listings without increasing quantity. A read of cart quotes rechecks account, selected position, operational area, mapping decision, exact SKU, stock, quantity and freshness. The quote price and retrieval timestamp stay transient. Missing quotes remain unpriced, and fees remain separate.

Marketplace comparison groups only the same verified variant with fresh priced listings reporting availability from at least two different retailers. Amounts refer to one pack. An empty list, missing price or platform failure supplies no evidence of cheapest delivery.


## Marketplace intake and reuse

Phase 9 connects category query suggestions and free-text search to the controlled marketplace workflow. The same approved exact-pack offer is available to Product Details, cart evidence and the current-offer reader for weekly plans, basket comparisons, bulk buy, personalized recommendations and the assistant. Pantry stays ingredient quantity data. A search result never mutates pantry or creates a canonical product.

The marketplace retains a bounded normalized cache in process memory. Cache reads are scoped to the account, approved catalogue area, query and retailer, and disappear at expiry or after restart. Results cannot persist into the historical observation pipeline without the existing separate intake and retention approval.

## Offer data quality operations

The retailer dashboard separates current approval coverage from recorded offer eligibility and current-account pilot coverage. A mapping alone yields no available price. Recorded snapshots retain their own observation source and date. Freshness is measured from the recorded observation time and, for a transient response, from the earlier of fetch time and a valid provider time. `CARTIQ_OFFER_FRESHNESS_SECONDS` defaults to 900 and can be tightened to 60–900 seconds. Future timestamps do not qualify. The historical series has its separate observation-age rules.

The user-facing exact-pack view shows when identity, stock, freshness or price evidence prevents a recommendation. Current-offer reads enforce the selected location and current matching evidence. Recorded or expired availability does not become a live result. [Coverage workflow](RETAILER_LISTING_FOUNDATION.md#retailer-operations-dashboard) · [Price data dictionary](PRICE_DATA_DICTIONARY.md)

The engine uses the existing QuickCommerceAPI adapter, MySQL catalogue, approved listing revisions and recipe planner. Live `search` and `item` results remain transient. The adapter exposes the original and normalized title, brand and pack, optional price, MRP, discount, stock and identifiers, query location, provider correlation ID when supplied, and CartIQ's fetch time. The 15-minute `freshUntil` indicates lookup age. It does not establish when the retailer changed its price or stock. Blinkit and Zepto were previously verified live. Swiggy remains independently gated by its provider outcome.

## Catalogue and recipe identity

Phase 6 adds a transient source to the shared current-offer reader. An authenticated account can use listings with a current approved mapping and an exact authorized area. Weekly plans, basket optimization, recipe pack choice, personal recommendations, bulk-buy and the unified assistant use the same current-offer reader. The optimizer still requires the supported quantity and applicable verified retailer fees for a complete total. A new live response never becomes a retained observation. [Transient fields](PRICE_DATA_DICTIONARY.md#transient-quickcommerceapi-offers)

`GET /api/grocery/variants/{variant}/identity?locationId=...` returns the canonical product and variant, matched display and normalized identity, reviewed barcodes, normalized pack, and existing catalogue label provenance. `PUT /api/admin/grocery/variants/{variant}/details` adds evidence-backed subcategory, flavour, formulation, ingredients and dietary attributes. A missing attribute stays unset. Source label statements are not dietary or allergy guarantees. Existing GTIN checks and reviewer decisions remain authoritative. Barcode equality cannot override conflicting multipack, flavour or formulation evidence.

The existing `POST /api/admin/provider/mapping/review` returns the matching decision, heuristic confidence, score and evidence reasons. Evidence-backed variant details are included when the reviewer checks the listing.

`GET /api/grocery/recipes/{recipe}/components` returns the existing recipe serving count, quantities and units plus optional curated ingredient identities, product links and directional same-unit substitutions. Admin routes under `/api/admin/grocery/ingredients` create identities, product links and substitution rules. `PUT /api/admin/grocery/recipe-components/{component}/ingredient/{ingredient}` attaches the ingredient to an existing component. Existing recipe scaling, product selection and authenticated pantry reads remain in the recipe planner. New substitutions are available to the next milestone for explicit selection and are not silently applied.

## Licensed observation intake

An authorized reviewer first checks provider and retailer terms for the specific listing, location, collection window and permitted use. They approve the listing in the existing mapping workflow and record the terms reference and SHA-256. `POST /api/admin/grocery/retention-grants` records the approved scope. Its `historyAllowed` flag covers presentation to signed-in CartIQ users. `datasetAllowed` covers authorized internal dataset preparation and evaluation with the listed fields. Only set a flag when the grant explicitly permits that use.

`POST /api/admin/grocery/observations` accepts a single verified observation and evidence reference within that grant. The service checks current mapping, SKU and exact canonical and approved listing packs, including multipack count, and enforces time and value bounds. The API does not verify a contract's authenticity. Its admin authorization represents operator acceptance of the referenced evidence. A normal adapter result does not enter this endpoint, and no background QuickCommerce collection is enabled.

`DELETE /api/admin/grocery/retention-grants/{id}` marks the grant revoked and deletes its observations. History and datasets immediately exclude expired grants, with physical deletion by the next 15-minute purge during uptime. Plan an earlier configured expiry if the agreement requires deletion by a hard deadline, and apply the provider's policies to backups and previously exported files. Grant metadata itself remains as the audit record. Any retention restriction on that metadata needs an operator-approved retention procedure.

V13 adds the ingredient catalogue and links, variant detail, retention grants and normalized observation tables. It leaves legacy price tables and recipes intact. Current offers, cart estimates, assistant and forecasting continue using their existing paths. The new Expo Price trends panel shows permission-cleared retailer history separately. [Price dictionary](PRICE_DATA_DICTIONARY.md) · [Dataset specification](AI_DATASET_SPECIFICATION.md)

On a package rollback to V12, suspend listing reassignment and observation intake until a V13-aware package returns. Leave the new tables in place and preserve the grant and observation audit trail.
