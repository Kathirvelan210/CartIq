# Controlled grocery pilot

Phase 9.1 remains blocked on location, mapping, written permissions, a current dashboard balance and separate authorization. [Pre-dispatch findings and exact request template](MARKETPLACE_PREDISPATCH.md)

## Phase 9 marketplace pilot

The existing operator workflow can issue a one-use marketplace search for a single exact query, retailer, account and area. Choose an approved exact-pack catalogue mapping as the anchor for cart and planning eligibility. Under **Controlled retailer pilot**, enter the query in **Marketplace query**, confirm written display rights for all listings returned by that query, and supply the separate request authorization reference. Marketplace tickets use the existing pilot attempt caps and billing receipt. A normal pack search or item ticket cannot execute a marketplace search, and marketplace tickets cannot execute the exact-pack routes.

| Proposed first live request | Review value |
| --- | --- |
| Retailer and operation | Blinkit only, one QuickCommerceAPI `GET /v1/search` through the authenticated CartIQ marketplace action |
| Query and anchor | `Amul Taaza 1 litre`, catalogue variant `milk`, subject to current human-approved Blinkit listing evidence |
| Location | Coimbatore at the previously used `10.942972, 76.953417`, subject to a newly approved catalogue area and matching PIN and confirmed coordinates |
| Cost and expiry | One credit maximum. Propose one attempt and one credit for the pilot and a ticket expiring 15 minutes after issue, within the pilot window. |
| Ledger after a confirmed charge | From the recorded 92, one charge would leave 91 settled credits and 11 experiment attempts. The floor stays 77. Reconcile against the signed-in dashboard and verify the exact request fingerprint first. |

No request is authorized by this proposal. First obtain written private display rights for all candidate listings, temporary catalogue-match rights, and rights to retain the query authorization and sanitized billing metadata. Confirm the approved area and exact-pack mapping, verify the geocoder and dashboard balance, and obtain separate approval for this one dispatch. Review its result and settlement before requesting any future category or retailer verification. Do not resubmit a duplicate fingerprint or retry an uncertain outcome. The earlier Phase 8.1 exact-pack proposal remains separate.

## Phase 8.1 first search proposal

**Status for human review. Dispatch is blocked.** No pilot was enabled and no live authorization was issued.

| Field | Proposed scope |
| --- | --- |
| Retailer | Blinkit only (`blinkit` in CartIQ, `BlinkIt` at QuickCommerceAPI). |
| Exact catalogue pack | Amul Taaza toned milk, **1 litre** (1,000 ml), catalogue product `milk`, variant `milk`. |
| Location | Coimbatore, Tamil Nadu, India, at `10.942972, 76.953417`. These are the previously used coordinates. A pilot-approved catalogue area ID and corresponding PIN are pending. The backend uses the approved area's PIN in the search. |
| Approved mapping and evidence | Pending. The inspected local catalogue has zero approved QuickCommerceAPI mapping decisions. Record the exact Blinkit native listing ID and current human-approved decision ID, with evidence of the matching title, brand, 1-litre single-pack quantity, variant or formulation and GTIN when available. Record the evidence reference, hash and applicable permission reference. Historical Blinkit search `d58d5a7a-f981-4bae-9e04-e2881077153f` establishes earlier search connectivity. |
| Operation | One user-triggered `POST /api/shopping/retailers/offers/milk/refresh` for `SEARCH`. The adapter would make one `GET /v1/search`. The existing query builder produces `Amul Amul Taaza 1 litre` from the catalogue's brand, name and pack label. |
| Attempt and credit limits | One attempt and one credit maximum, with one credit expected. Confirm the exact request fingerprint against the persistent ledger after the area and PIN are known. Duplicate suppression remains in effect. |
| Authorization expiry | Proposed expiry is 15 minutes after a human operator issues the one-use authorization, capped at the approved pilot end. The operator must record the actual expiry timestamp and bind the authorization to one named shopper account. |
| Budget after a confirmed one-credit settlement | From the observed local ledger of 92 settled credits and 12 remaining experiment attempts, the confirmed charge would leave 91 settled credits, 11 remaining experiment attempts and zero remaining attempts in this pilot. The protected floor remains 77. Recheck the signed-in dashboard balance against the ledger before any later dispatch. |

Before enabling this pilot, an authorized CartIQ representative needs to accept written permission covering the exact Blinkit and Coimbatore scope, transient private price and stock display, temporary catalogue joins for identity matching, and retention of sanitized operational metadata and derived counts. Record the grant's restrictions and retention period. Raw payload retention, historical prices, public redistribution and model use remain outside this proposal. A human reviewer must approve the specific listing mapping, and a human operator must confirm the area, pilot window, shopper account and separate live authorization. The inspected local H2 file is at Flyway V11. Use an isolated local pilot database on V18, with the retailer and legacy paths gated as described below.

The offline checks exercised scope rejection, account binding, expiry, replay rejection, credit reservation and settlement, and blocking after an uncertain outcome. The loopback browser fixture rendered separate live, recorded and expired offer labels. Sample-mode data is identified in the shopping workspace, and the live offer gate requires sample mode off. See [testing evidence](TESTING_STATUS.md#first-live-pilot-readiness-phase-81).

## Readiness and evidence

The local pilot controls, shopper dispatch, exact-pack normalization and basket update are implemented. The real-world measurement remains pending. The project records reviewed for this milestone supplied neither written permission for this specific pilot nor a separately authorized request. No pilot was enabled or populated with genuine products. Historical Blinkit and Zepto results are prior evidence. Swiggy's historical failed-platform response remains unresolved.

| Evidence field | Phase 8 real-world result |
| --- | --- |
| Genuine scoped products and approved pilot variants selected | 0 |
| Authorized location, retailer and time window for a new dispatch | Pending separate approval and confirmation |
| Written transient display, catalogue matching and operational metadata rights | Pending review |
| New live requests, provider IDs, outcomes and timestamps | 0 requests. No Phase 8 provider response |
| Actual QuickCommerce credit debits | 0 |
| Settled ledger balance | 92. Zero pending reservations. 12 experiment attempts and a floor of 77 |
| Genuine shopper results, cheapest compatible retailer, pantry and bulk evaluation | Pending authorized offers |

## Setup for an approved operator

1. Review written rights for transient private display, catalogue matching and retention of operational metadata and derived counts. Put the existing permission reference and exact area coordinates in the local backend using the Phase 6 environment settings. The steps below require an isolated local backend with sample mode off. Keep all retailer flags off for offline work.
2. In **Profile → Admin dashboard → Controlled retailer pilot**, choose one retailer and the approved catalogue area. Load current approved exact-pack mappings under the permission reference. Select up to ten variants and assign a target group. Aim for milk, rice, dal, cooking oil and biscuits or packaged snacks. Add a second pack size only where it has its own exact mapping. The screen shows missing groups. An empty or unsupported group stays empty.
3. Record the UTC-offset timestamps, attempt and credit limits, and attest to the evidence. The first save creates a disabled pilot. A window may span at most 30 days. Each action can reserve one credit. The pilot accepts at most 12 attempts and 12 credits, subject to the stricter existing persistent ledger. The operator may choose smaller numbers.
4. Only after separate live authorization, configure `QUICKCOMMERCE_PILOT_ENABLED=true` alongside the existing `QUICKCOMMERCE_ENABLED` and `QUICKCOMMERCE_SHOPPING_ENABLED` gates and the verified dashboard balance checkpoint. Keep `QUICKCOMMERCE_LEGACY_LOOKUPS_ENABLED` unset for this pilot. Enable the saved pilot explicitly. The pilot mode rejects legacy search, item and balance calls through the provider adapter.
5. Record one authorization for the named shopper, exact variant, immutable reviewed listing decision and operation. Set an expiry in the next hour and within the pilot window. Use a distinct reference to the explicit authorization for each request. Issuing authorization makes no provider request. The shopper signs in, confirms the exact position, opens the product or selects the pack in Shopping plan, and explicitly presses the action marked **1 credit maximum**.
6. Review title, brand, pack, GTIN when supplied, variant or formulation, price, MRP, stock, normalized unit price, matching disposition, provider ID and timestamps in the transient response. Confirm the receipt in the admin report. Add the exact pack to the planning cart and copy it into the weekly plan, or add it directly in Shopping plan. Check the detailed fees, stock and missing components before treating the basket as complete.
7. Stop the pilot when the authorized work ends. An uncertain or in-flight receipt freezes additional attempts for that pilot. Use the existing manual ledger procedure and the independently verified provider record. A pending pilot receipt requires separate operator review. Restarting the process does not reissue the authorization. Cached offers expire by the earlier of their freshness deadline and the pilot end.

## Evaluation to record after authorized dispatch

| Check | Acceptance evidence |
| --- | --- |
| Exact-pack offer and cheapest compatible result | Current reviewed SKU and canonical pack, retailer quote timestamps, compatible quantity and positive availability evidence. If only one retailer responds, report its price and leave cross-retailer savings unavailable. |
| Unit price | Show price per standard unit with the normalized single-pack or multipack quantity. Preserve nullable price and MRP. |
| Basket optimization | Verify quantities and fees at the confirmed location. Record missing lines and fees separately. Only compare complete totals on like-for-like contents. |
| Bulk buy | Record the applicable pantry quantity, consumption, storage, budget, confirmed stock and use-by evidence. When evidence is absent, report the reason for abstaining. |
| Pantry suggestions | Confirm ingredient quantities and the signed-in pantry deductions against reviewed exact ingredient links. |

The pilot tables store configuration, selected mapping references and operational receipts. No raw responses, retailer prices, stock quantities or shopper plan content are written to those tables. Use the admin report for provider IDs, billed credits and count-only evidence where the permission allows it. Any future request should append its own evidence rather than editing this zero-dispatch result.
