# CARTIQ_MASTER_CONTEXT.md

## Phase 10.9 · Gemini 3.8 Flash compatibility (19 November 2026)

The local model remains unchanged (last verified gemini-3.5-flash). The Gemini 3.8 Flash visual run stopped at the compatibility gate because the current adapter sends an explicitly unsupported candidateCount field. [Phase 10.9 evidence](evidence/phase10-9/README.md)

## Phase 10.5 · Authorized live Gemini attempt (19 November 2026)

One authenticated Expo recipe submission reached Gemini using gemini-3.5-flash with pantry sharing off. The provider returned HTTP 503. Chrome rendered the classified server error through Spring and FastAPI. Live recipe generation remains unverified for successful output. [Live receipt and offline test evidence](evidence/phase10-5/README.md)

## Phase 10.5 · Gemini preflight (19 November 2026)

The Phase 10.5 live recipe preflight stopped when both configured loopback services refused connections. Current model enablement could not be confirmed. Zero generation requests were submitted. [Phase 10.5 evidence](evidence/phase10-5/README.md)

## Phase 10.4 · Local Gemini diagnostic verification (19 November 2026)

The local Spring and private FastAPI pair now runs the Gemini diagnostic fix. Authenticated readiness confirmed generation enabled for `gemini-3.5-flash`. Chrome verified distinct errors and request correlation on the isolated loopback stack. No external generation ran in this phase. Google acceptance and billing for the original HTTP 502 remain unknown. [Evidence](evidence/phase10-4/README.md)

## Phase 10.2 · Gemini activated locally (19 November 2026)

The Windows local launcher now passes the operator's session-only Gemini credential to private FastAPI and a shared service token to Spring and FastAPI. Generation is enabled for `gemini-3.5-flash`; the authenticated local readiness check passed. Recipe drafts and substitutions, seven-day curated recipe selection, and pantry-aware assistant routing worked in Chrome with mocked model output. Google entitlement and live generation await separate authorization. [Setup and evidence](evidence/phase10-2/README.md)

## Phase 10.1 — physical Android location (18 November 2026)

A physical Android 16 handset in Expo Go 54.0.8 submitted four confirmed device locations to the authenticated Spring Boot endpoint on isolated H2 V20. The app now respects an existing approximate grant, shows the permission precision separately from the measured accuracy, retains the original capture time, and offers Android settings recovery for a blocked permission. Active fixes are rechecked when the app returns to the foreground. No API contract or migration changed. Device evidence covers Expo Go. Standalone release verification remains open. [Physical-device evidence](evidence/phase10-1/README.md)

## Phase 10 · 18 November 2026

The customer location picker now accepts a browser or Expo foreground device fix independently of geocoding. On confirmation Spring associates its actual coordinates, capture time and accuracy with the authenticated shopper. Resolved PIN selections retain their approximate origin. Device capture and selection confer no retailer rights.

All 15 grocery categories use explicit search terms with the existing BlinkIt, Zepto and Swiggy pilot. Opening a category reads account-scoped evidence and available tickets. Spending requires the shopper to press the retailer-specific button and pass the one-use scope and ledger checks. Approved exact listings can be selected in the cart, changed explicitly and revalidated. The web build fails closed when a verified backend is unavailable.

Recipes now supports a seven-day Gemini selection from curated recipe identities. AI Smart Shopping also offers Gemini-assisted tool selection. CartIQ retains responsibility for product identity, pantry subtraction, offer freshness and price calculations. See the Phase 10 entry in TESTING_STATUS.md for the boundary of verification.


> **Last updated** 2026-11-16 (IST)

## Real-time grocery marketplace phase 9

- The shopper screen separates retailer results from the canonical catalogue, with 15 grocery categories and query suggestions. Free-text and category searches use one-use operator approvals and the existing QuickCommerce ledger. Provider output stays transient and unknown data stays unknown.
- Confirmed PIN or GPS locations are scoped to the signed-in account on the device. V19 adds query-scoped pilot approvals. Eligible exact-pack results can enter the account cart and existing planning services. Live retailer and production PIN verification remain pending authorized access. [Marketplace integration](RETAILER_LISTING_FOUNDATION.md#marketplace-discovery) · [Live request proposal](CONTROLLED_GROCERY_PILOT.md#phase-9-marketplace-pilot)

## Controlled real-world grocery pilot phase 8

- V18 adds local pilot setup, an exact-pack scope and one-use account-bound request authorizations. Shopper refresh still routes through the existing QuickCommerce ledger. The administrator sees linked provider outcomes, per-request debits and count-only evidence. Provider prices and stock stay transient.
- The pilot ships disabled. Genuine selected packs, written pilot rights and separate authorization for the first dispatch are pending. [Pilot controls and evidence](CONTROLLED_GROCERY_PILOT.md)

## Grocery data quality and retailer operations phase 7

- The admin retailer dashboard shows active catalogue coverage, approved mappings, usable recorded offers, scoped live counts, expiry, missing evidence and sanitized request diagnostics. Its mapping editor compares evidence, records human decisions and displays every revision using the existing journal.
- Matching safeguards keep conflicting packs and assigned barcodes out of recommendations. Current freshness can be tightened by environment configuration. The credit ledger and schema remain at their prior versions. [Retailer operations](RETAILER_LISTING_FOUNDATION.md#retailer-operations-dashboard) · [Release gates](PRODUCTION_READINESS.md)

## Live grocery offers phase 6

- Product details, Shopping plan and Bulk buy can explicitly refresh selected retailers for one exact catalogue variant in an approved local pilot area. Account-scoped transient quotes flow through the shared current-offer reader into weekly baskets, the assistant, personalized alternatives and bulk-buy calculations. The UI distinguishes live, recorded, unresolved, failed and expired results.
- The local pilot requires documented display and catalogue-join permission, existing provider and shopper flags, a configured matching area and an active approved listing mapping. Live payloads and observations stay out of durable shopping data. Flyway remains at V17. [Retailer workflow](RETAILER_LISTING_FOUNDATION.md#quickcommerceapi-transient-offers) · [Operator gates](OPERATIONS_RUNBOOK.md#quickcommerce-transient-offer-pilot)

## Unified shopping intelligence phase 5

- **AI Smart Tools → Shopping plan** connects weekly meals, editable exact packs, pantry quantities, retailer baskets, bulk-buy checks, recommendations and an offline assistant. The planner shows missing quantities, verified offer evidence and fee-inclusive totals only where coverage and retailer terms support them. It reads approved catalogue data and respects all existing permissions.
- Flyway V17 adds account/location plans and optional account feedback. Saved plans contain editable inputs without prices or computed recommendations. Admins can see aggregate outcome cohorts. Deterministic policies remain the fallback and no model was trained. [Workflow and privacy](PERSONALIZED_RECOMMENDATIONS.md#unified-shopping-plan) · [Verification](TESTING_STATUS.md#unified-shopping-intelligence-phase-5)

## Bulk-buy and personalized grocery intelligence phase 4

- AI Smart Tools includes Bulk buy, For you and Shopping insights. The authenticated API combines location-scoped current offers, explicit household limits, pantry, saved products, past selections, curated recipes, and budget. The assistant can handle weekly shopping, pantry recipes, bulk quantities and budget or alternative requests without a new model.
- Flyway V16 creates two account-owned preference tables. Extra packs require usable stock counts and confirmed use-by input. Basket totals require compatible exact variants and known fulfillment costs. Provider searches, credit controls, history and forecasting keep their existing paths. [Bulk-buy rules](BULK_BUY_INTELLIGENCE.md) · [Personalized workflows](PERSONALIZED_RECOMMENDATIONS.md)

## Genuine price dataset acquisition phase 3B

- Added V15 for the price source registry, digest-linked intake and row decisions, and evidenced source availability. The admin dashboard shows approved history, simulated exclusions, permissions and exact-series gaps. No genuine source license or file has been received. [Acquisition and permissions](PRICE_DATASET_ACQUISITION_REPORT.md)

## Price prediction phase 3

- Added a chronological, per-retailer and per-location forecasting pipeline with explicit V14 training and serving permissions. The authenticated UI separates approved observations from estimates and explains why a forecast is unavailable. Production prediction remains disabled and there is no validated genuine model. [Experiments and coverage](PRICE_PREDICTION_EXPERIMENTS.md)

## Recipe intelligence phase 2

- Recipe discovery and private AI drafts feed the same multi-day planner. It combines ingredient quantities, accounts for the signed-in pantry, applies reviewed substitutions and compares eligible Blinkit and Zepto pack subtotals. The shopping list can add verified packs to the existing cart. Live lookup remains an explicit separate action.
- The optional Gemini integration uses structured FastAPI validation and stays disabled until authorized configuration is provided. No new schema migration or model training. [Recipes](RECIPE_INTELLIGENCE.md) · [AI roadmap](AI_MODEL_ROADMAP.md)

## Grocery data engine phase 1

- Flyway V13 adds separate licensed price observations, scoped retention grants, evidence-backed variant details and recipe ingredient identities and relationships. The shopper history and admin dataset APIs share the same approved observations while enforcing separate usage permissions.
- Live searches remain transient. No licensed retailer history was ingested in this phase. Forecasting and recipe cart selection keep their existing paths. [Engine](GROCERY_DATA_ENGINE.md) · [Price dictionary](PRICE_DATA_DICTIONARY.md) · [Dataset specification](AI_DATASET_SPECIFICATION.md)

## Production release candidate

- The `prod` profile requires HTTPS CORS origins and uses secure refresh cookies, disables demo/API docs, and validates migrations without applying them at startup. A separate Java migration entry point is available for operator-approved rollout. Liveness and MySQL-backed readiness are separate probes.
- Added correlated, sanitized request and retailer diagnostics, production-sensitive endpoint rate limits, an isolated Compose reference stack, and mobile build profiles. Forecast production startup requires the internal service secret, and forecasting remains opt-in.
- The Windows loopback HTTPS, disposable MySQL restore and JAR rollback rehearsals passed. Docker execution, signed device builds, live PIN geocoding and Firebase project/device delivery are external release gates. [Readiness](PRODUCTION_READINESS.md) · [Release verification](TESTING_STATUS.md#final-external-deployment-preparation)

## Shopping intelligence and evidence

- Live retailer results can be filtered by name, brand, pack, category or keywords and sorted by price. The comparison uses known INR prices and confirmed availability, and shows per-retailer timestamps, unit prices and the price difference against the highest eligible matching offer.
- Matching requires the same normalized pack, brand and full title. Supplied flavour, formulation and barcode are checked before comparison. A shared barcode with conflicting pack or variant details stays in the review list. The 0–100 score describes rule evidence. Live listings and review findings remain transient.
- The cart compares canonical products using recorded current offers and verified delivery, handling and minimum-order evidence. The assistant uses the signed-in user's cart, its configured catalogue area and recorded history. Price increases are compared for the same listing and location. Live search results are kept separate from saved offers, history and user notifications.
- Historical charts still use recorded observations. `CARTIQ_PRICE_FORECAST_ENABLED` defaults to false and enabling it requires comparable history for a single listing. The project has no evaluated production forecast or calibrated interval. Existing price-watch and replenishment events can use the Firebase backend and Web registration when configured. Native delivery and actual PIN coverage still need deployment verification. [Implementation checks](TESTING_STATUS.md#shopping-intelligence-verification)

## Live retailer verification checkpoint

- A verified QuickCommerceAPI account check returned 96 available credits. Blinkit `Toor dal` returned upstream HTTP 200 with no products, Blinkit in `failed_platforms`, and an unchanged balance. CartIQ originally returned 502 because it checked for a charge before reading the failure list. Operator-supplied evidence settled that attempt at 96 with no credit consumed and preserved its fingerprint and request ID.
- The account balance check is an admin-only, no-store local endpoint. It reads the provider's free `/v1/credits` endpoint without touching the ledger. Billing diagnostics now identify the type and consistency of the provider evidence without logging raw responses.
- The historical operator-supplied Blinkit body parsed all 24 rows through mocked transport. On the resumed live run, Zepto returned 24 priced `Toor dal` results. An Expo Web live comparison for `Amul Taaza Milk` rendered 24 Blinkit and 13 Zepto listings, including two matching-pack price comparisons. Blinkit item detail returned one item. Swiggy returned an uncharged failure for both queries and did not erase the other retailers' results.
- This session made seven retailer lookups and two free balance checks. Four credits were consumed. The ledger settled at 92, zero pending reservations and 12 experiment slots left, with a floor of 77. The disposable backend was stopped and the normal provider remains disabled. Swiggy requires provider diagnosis before live support at this location can be claimed. [Live and offline evidence](TESTING_STATUS.md#live-retailer-verification-and-billing-stop)

## Milestone 4 – Shopping location and retailer results

- Expo has one confirmed shopping location for retailer search and item details. Web uses browser geolocation on HTTPS or localhost. Native Expo uses foreground permission and the device location API. GPS coordinates are kept when reverse geocoding only supplies a nearby address. PIN matches require locality selection and explicit confirmation of approximate coordinates.
- Spring Boot resolves Indian PINs and reverse addresses through an operator-configured Nominatim-compatible service (`CARTIQ_GEOCODER_URL`). The configured catalogue and cart areas remain separate from transient retailer lookups and keep their existing IDs.
- Blinkit, Zepto and Swiggy Instamart results are grouped by retailer, include CartIQ fetch time, and preserve unknown fields. Price comparisons require an exact normalized brand, complete title and pack match across at least two available offers. This is a candidate comparison and does not create verified catalogue mappings.
- Changing query, shopping location or account invalidates the live result view. A fresh retailer lookup requires an explicit action. The persistent credit ledger still serializes dispatch and stops on uncertain billing. The provider and the optional authenticated shopping route are disabled by default. No live retailer or geocoder call was made for this milestone. [Configuration and live-test gate](QUICKCOMMERCE_DRY_RUN.md#milestone-4-shopping-configuration)

## Authorized Blinkit smoke test outcome

- The real key is saved in a restricted Windows-protected backend file and was detected by a disposable Spring Boot JVM. Flyway V1–V11 applied on its file-backed H2 database. One Blinkit Milk attempt reached the CartIQ adapter and returned HTTP 502 without a verified upstream result. The ledger is pending at a provisional 96, with one attempt recorded and further requests blocked. The normal adapter is disabled and shared MySQL was untouched. [Actual smoke-test result](TESTING_STATUS.md#authorized-one-credit-blinkit-smoke-test)

## Project repair and isolated verification

- The Windows launcher saves the existing QuickCommerce key to ignored `backend/.env.local` using Windows DPAPI and a current-user ACL. It can start a local H2 backend with QuickCommerce disabled and no key. Protected save, reload and JVM presence were verified with a synthetic key in a temporary workspace. The real key file is still missing. [Setup and smoke-test gate](QUICKCOMMERCE_DRY_RUN.md)
- QuickCommerce explicitly disables HTTP retries and redirects. The runtime requires an existing ledger, and a pending attempt can be reconciled offline against its settled dashboard outcome without losing the fingerprint or returning its experiment slot. The active ledger remains at 97 credits with no attempts. No live search ran.
- Catalogue-backed assistant search and recipe planning no longer depend on the first unfiltered catalogue page. The Expo UI keeps incomplete recipe totals unknown and omits stale analytics for a changed product or location. Offers without verification cannot become the cheapest comparable price. [Repair tests](TESTING_STATUS.md#project-repair-and-isolated-verification)
- A standalone file-backed H2 startup and registration-to-cart HTTP journey passed. Disposable MySQL 8.0.44 validated V1–V11 and the V10-to-V11 preservation contract. Shared MySQL was untouched. Native Android/iOS verification, a signed-in browser UI walkthrough for this repair, and the one-credit provider smoke test remain pending. Production provider retention, publication, and order placement require separate authorized integration.

## QuickCommerceAPI single external retailer-price provider

- QuickCommerceAPI is the only live grocery-price transport in CartIQ. Spring Boot uses a single backend key and the documented `BlinkIt`, `Zepto`, and `Swiggy` platform IDs. The local development adapter is disabled by default. Retired Rewalto transport and sync access are unavailable. Existing mapping and history records are preserved. [Provider scope and rights](INDIA_RETAILER_DATA_SOURCES.md#quickcommerceapi-development-access)
- Expo development admins can explicitly compare a single query across the three retailers from search or product comparison and request item details by native ID. Results stay separate by retailer and pack. The paid comparison uses serial single-platform requests, stops on error, and never promotes candidates into verified offers or persists provider data. [Activation and verification](TESTING_STATUS.md#quickcommerceapi-single-provider-verification)

## QuickCommerceAPI budget persistence and dry run

- QuickCommerce now reserves each charge in a local ledger before dispatch and holds a file lock through the lookup. The 20-attempt limit, 77-credit floor, duplicate fingerprints and uncertain reservations survive backend restarts under the same OS user. [Budget and tests](TESTING_STATUS.md#quickcommerceapi-budget-persistence-and-dry-run)
- Search and item responses remain transient. The adapter validates the upstream platform and location, preserves raw pack labels with normalized pack counts and quantities, and converts INR with exact decimal handling. The key was absent from the environment and the adapter was disabled. The dry run made zero network requests. [Dry-run report](QUICKCOMMERCE_DRY_RUN.md)

## QuickCommerceAPI development integration

- Added local-profile, admin-only Spring Boot lookup routes for single-platform search and item detail, covering the documented Blinkit, Zepto and Swiggy Instamart platforms. QuickCommerce is disabled by default and separate from retailer sync, catalogue and public comparison. [Provider scope and terms](INDIA_RETAILER_DATA_SOURCES.md#quickcommerceapi-development-access)
- The adapter uses `QUICKCOMMERCE_API_KEY` in the server-side `X-API-Key` header, one serial request at a time, with duplicate suppression, at most 20 attempts per process and a 77-credit floor. The environment key was missing. No live lookup, credit spend, or dashboard verification occurred. [Development checks](TESTING_STATUS.md#quickcommerceapi-development-verification)

## Authorized sample receipt and human annotation

- **Partially complete.** Rewalto and GS1 India DataKart each have a provider-grant verification record and an offline receipt report. Both remain on hold pending written grants, authorized CartIQ acceptance, and matching sample deliveries. No records were validated or imported. [Grant and intake record](AUTHORIZED_SAMPLE_INTAKE_RECORD.md)
- Reviewer assignment, both human sign-offs, adjudication, and evaluation are pending. The 73-case baseline was checked locally. No database or retailer adapter was used. [Benchmark status](PRODUCT_MATCHING_BENCHMARK.md) · [Readiness checks](TESTING_STATUS.md)

## Authorized data readiness and matching evidence

- Rewalto and GS1 India DataKart intake now has a written-rights checklist, field specification, evidence states, and retention and snapshot instructions. The drafted request is unsent. No retailer or DataKart sample or permission was obtained, and the adapters stayed disabled. [Intake and outstanding permissions](INDIA_RETAILER_DATA_SOURCES.md)
- New retailer approvals record source and permission references, the evidence bundle SHA-256, submitted fields, canonical identity and pack checks, reviewer, server time, and the prior active mapping. V11 adds a decision table. The service writes decisions and any active mapping change in one transaction. Legacy mapping and price records retain their original values. [Mapping workflow and limitations](INDIA_RETAILER_DATA_SOURCES.md#provider-mapping-evidence-and-revision-history)
- The frozen 73-case benchmark remains the baseline. A separate intake and blind review protocol requires two human sign-offs and explicit adjudication for disagreements. The forms are ready. Authorized evidence and human review are pending. The default matcher and advisory hybrid are unchanged. [Benchmark protocol](PRODUCT_MATCHING_BENCHMARK.md) · [Blank human review forms](HUMAN_MATCHING_ANNOTATION_TEMPLATE.md)

## Licensed sample research and independent annotation

- The provider register now includes business contact routes and a scoped sample request. Research found no available CartIQ grant for persistent comparison, research, or public display. Live adapters remained disabled. [Contact and rights register](INDIA_RETAILER_DATA_SOURCES.md)
- The expanded offline benchmark has 73 cases, including 19 new observed pairs. Two AI reviewers agreed on 17 of the 19. Both disagreements stayed unresolved, with no human sign-off. The extension adds eight fresh holdout cases grouped by family, source record and GTIN. [Benchmark and annotation](PRODUCT_MATCHING_BENCHMARK.md)
- The hybrid, GTIN-only and an evaluator-only conservative rule each recovered one of three new held-out positives with zero false matches. The default recovered zero. All five methods missed both confirmed positives with a missing GTIN. CartIQ retains the default scorer and advisory hybrid. [Metrics and limitations](TESTING_STATUS.md)
- The mapping regression verifies a 409 when only historical observations remain, with the original SKU, actor and approval time and the same history row counts. The existing database stores current approval metadata. A complete revision audit and source evidence persistence remain pending. [Tests and database verification](TESTING_STATUS.md)

## India-focused matching and retailer data authorization

- The admin catalogue comparison and batch review endpoints accept `method=hybrid`. The existing deterministic comparison remains the default baseline. The hybrid uses normalized title, brand, pack structure, GTIN, flavour and formulation and can return a corroborated one-barcode suggestion. It never creates a mapping. [Benchmark methodology](PRODUCT_MATCHING_BENCHMARK.md)
- The 54-case India benchmark separates 38 observed pairs from 16 controlled variants. The hybrid sent all three unresolved observed holdout cases to review, compared with two for the baseline. It deferred all three confirmed matches, so recall did not improve. Keep the hybrid advisory while collecting more independently verified positives. [Metrics](TESTING_STATUS.md)
- Admin mapping preview reads the canonical pack and registered barcodes and returns conflicts. An explicitly evidence-backed mapping request rejects contradictory submitted fields. A manual replay of the same listing retains its original approval actor and time. Observation history continues to block SKU reassignment. [Retailer foundation](RETAILER_LISTING_FOUNDATION.md)
- No approved licensed feed was available for the intended benchmark storage, persistent observations and public comparison. Live adapters stayed disabled. Open catalogue and product-label evidence supported offline evaluation. [Source and rights matrix](INDIA_RETAILER_DATA_SOURCES.md)

## Matching evaluation and retailer integration preparation

- Re-reviewed 33 held identities. Exact pack evidence supported four inactive drafts in disposable MySQL. Two barcode-linked medication records were rejected from grocery intake and 27 candidates remain on hold. The four new drafts are Bisleri and Kinley 1 L packaged drinking water, Parle Happy Happy Choco-Chip Cookies 31.5 g, and Cadbury Dairy Milk 11 g. [Identity evidence](INDIA_IDENTITY_EVIDENCE.md)
- Evaluated 47 manually labelled pairs built from 60 source records across 11 training, 20 validation and 16 test pairs. On the held-out split the scorer sent three confirmed positives to review and made zero false matches. GTIN-only and normalized title/brand baselines expose the effect of barcode-rich sampling and the cost of missing pack evidence. No model was trained. [Dataset](product-matching-benchmark.json) · [Metrics and commands](TESTING_STATUS.md)
- Dedicated Blinkit, Zepto and Swiggy Instamart configurations use the existing gated Rewalto transport and validate listing, PIN and source link. Observations retain currency unit, source time and availability. Timestamp replays leave current comparison evidence in place, and late history cannot replace the current row. Live data remained disabled. [Retailer listing foundation](RETAILER_LISTING_FOUNDATION.md)
- Disposable MySQL retained the existing V1–V10 schema, 30 active products, 22 inactive products, 55 variants and zero current or historical price observations. Shared MySQL was untouched. [Testing and verification](TESTING_STATUS.md)

## Verified catalogue expansion

- Revisited all 37 flagged identities with Open Food Facts provenance, packaging and available manufacturer references. Corrected and approved three flagged rows as inactive drafts, approved the previously ready Rostaa row, rejected one conflicted source record and held 33 cases. Six additional food candidates from the category extract were staged and approved. Ten new products and ten variants remain inactive in disposable MySQL. Public publication counts did not change. [Evidence and decisions](INDIA_IDENTITY_EVIDENCE.md)
- A verified 200 mL Gowardhan cow ghee draft extends coverage. A Fortune mustard oil entry stayed on hold because the record displayed another product's package. The Open Products Facts household export timed out. Exact household GTIN evidence and an authorized source are pending. [Source research](INDIA_IDENTITY_EVIDENCE.md)
- The advisory matcher recognizes metric aliases and explicit multipack counts. The admin batch review endpoint preserves the submitted pair references. The 21-pair archive dataset includes positive, distinct and unresolved pairs grouped by product family and GTIN. No model was trained. [Evaluation data](india-product-matching-evaluation.json)
- Retailer mappings retain the SKU that owns each observation. Remapping a listing that already has observations returns 409. No real listings or observations were collected. Shared MySQL and its Flyway configuration were unchanged. [Retailer listing foundation](RETAILER_LISTING_FOUNDATION.md)

## Catalogue quality and matching foundation

- Reviewed all 46 unpublished candidates from the archived India pilot. Nine were ready for approval, 30 require correction or reconciliation, six lacked sufficient evidence, and one had a conflicting identity. No confirmed duplicate was found. Eight approved products and eight variants were saved as inactive drafts in disposable MySQL. Rostaa Salted Mix remains ready for review approval. [Candidate report](INDIA_CANDIDATE_REVIEW.md)
- The reproducible 2026-11-14 category sample examined 258 source objects and selected 76 distinct GTINs across 11 categories for review. Twenty-one overlap the 52-candidate pilot. The sample yielded one ghee variant and no household records. Personal-care records remain in a separate Open Beauty Facts research extract. No records from the new sample were staged. [Category statistics and replay](TESTING_STATUS.md)
- The admin-only `POST /api/admin/catalogue-matching/compare` returns advisory evidence for a pair of records. It validates and normalizes GTINs, compares normalized names, brands, packs, flavours and formulations, and returns a weighted score with reasons and a review decision. It creates no catalogue or retailer mappings. Seven labelled archive-linked pairs are available for future evaluation. [Labelled pairs](india-product-matching-labels.json)
- Source archives, hashes and licences are maintained outside the repository with the India category extract. The next catalogue milestone is independent label review and staged food coverage expansion, with more sources for cooking oils and household essentials. The shared MySQL instance, migrations and retailer data were untouched. Native verification remains deferred. [Verification results](TESTING_STATUS.md)

## India catalogue expansion

- The reproducible extractor keeps Open Food Facts market, manufacturing and ingredient-origin tags separate. The bulk pilot selected market or manufacturing tags. The India API supplement selected market tags, with the original HTTP response preserved separately from the derived JSONL.
- The pilot examined 135,111 complete bulk records and 39 API records. It staged 52 unique GTIN records, published six products and six variants after individual review, and left 46 candidates pending, including 26 flagged for correction.
- Expo catalogue search supports exact brand, normalized pack-size and category filters, with pagination. Barcode lookup returns an exact active variant. The operator script supports pausing at a committed chunk and uses the extractor manifest to verify snapshot fingerprint and source URL.
- Flyway V8–V10 applied in order from V7 on disposable MySQL 8.0.44. MySQL rejected the reserved identifier `row_number` in the original unpublished V10. Quoting it in the migration and corresponding statements resolved the failure. Shared MySQL and the default V7 target were left untouched.
- Before shared deployment, expand review coverage across Indian grocery categories and plan Open Food Facts attribution, share-alike and image reuse. The pilot does not establish national coverage, manufacturer origin, stock or price. [Commands and results](TESTING_STATUS.md)

## Real product catalogue

- The existing catalogue continues to serve active products and variants through Spring Boot search, category, detail and variant routes. Connected Expo screens use those routes. Product details also fetch GTINs, ingredients, supported nutrients, and source attribution. The admin screen uses paged catalogue search and offers an import review panel.
- A batch starts with a bounded Open Food Facts JSONL or JSONL.gz extract. The operator script sends chunks to the admin API. The database stores raw source rows, validation outcomes, candidate links and committed progress. Re-running against the same file resumes at the stored offset. Staged records require admin review. Approval creates or extends an inactive product, and publication makes the approved product and its reviewed packs visible.
- Product identity uses normalized GTINs, pack amounts and units. Known Open Food Facts category tags suggest a CartIQ category. An admin confirms the category and flavour. An additional pack can attach to an explicitly identified, matching inactive product. Conflicting identities, duplicate packs with different barcodes, unsupported quantities, and ambiguous labels require review.
- The first isolated pilot staged two India-tagged source records. It published one real product and one variant. The other remained in correction review. No retailer observations or availability were imported. The small source-ordered sample cannot estimate India-wide coverage.
- New V10 adds batch progress and raw-row audit tables. It was reviewed and tested on isolated H2. Shared MySQL was untouched. The MySQL local profile still targets V7. Plan the next import against a disposable MySQL copy and review approval for V8–V10 before any shared deployment.
- The next catalogue milestone is a reproducible, larger India-filtered extract, with coverage and rejection statistics by category, plus review of Open Food Facts attribution, image reuse, and share-alike obligations before public distribution.

## Native verification milestone

- Physical phone testing was deferred. The Android 14 (API 34) emulator booted and Expo Go 54.0.8 installed. Android System UI became unresponsive before a CartIQ launch or device-to-API request was verified. iOS runtime verification still requires an iPhone or iOS simulator. See [testing status](TESTING_STATUS.md) for the verified scope.
- For Android development, use a disposable backend with starter catalogue, sample prices disabled, and a device-reachable API URL. The desktop default `127.0.0.1:8080` is available only on the desktop. The Android emulator reaches the host loopback through `10.0.2.2`. Use a reachable HTTPS test API for physical device verification.
- The native app stores access JWTs in SecureStore and relies on the platform cookie jar for refresh. Camera scanning and native push registration are pending. The location picker supports manual entry and browser geolocation.

## Frontend and backend integration handoff

- With `EXPO_PUBLIC_API_URL` configured, CartIQ loads catalogue, account and cart data from Spring Boot. Category names come from `GET /api/categories`. Browse and search use `GET /api/products/page` with the current location, search terms, category, and page. Details and pack comparison use the product, variants, and comparison endpoints.
- The planning cart uses the authenticated `/api/me/cart` endpoints for list, add, quantity updates, and removal. Products without confirmed prices can be planned and are excluded from checkout. Cart writes are serialized per account, and removal deletes the associated purchase preferences explicitly for MySQL compatibility.
- The client normalizes `/api` and matches local loopback hostnames for web refresh cookies. Configure `EXPO_PUBLIC_API_URL` with a device-reachable host for native use, and add the exact Expo Web origin to backend `ALLOWED_ORIGINS` for ports outside the defaults. Use same-site HTTPS and secure refresh cookies for web deployment. The frontend environment variable is public and must contain only the service URL.
- The API client applies a 12-second deadline per request attempt, coalesces session refresh, and replays a protected request once after a 401 and successful renewal. Transient failures surface with retry controls. Browser access tokens are in memory with an HttpOnly, SameSite=Strict refresh cookie. Native access tokens use SecureStore. Native refresh-cookie behavior still requires device verification.
- The offline sample catalogue, explicitly enabled server sample mode, sample advertisement slots, and checkout preview remain available. The starter catalogue contains product metadata. Live retailer observations and authorized ordering/payment integrations are pending. Forecasts require observations and production model quality remains unverified.
- The local MySQL profile defaults Flyway to V7. V8/V9 remain pending approval for MySQL. The current audit used a separate MySQL 8.0.44 instance on port 13306. See [testing status](TESTING_STATUS.md) for current results and [the changelog](CHANGELOG.md) for changed files.
- All 13 requested user, catalogue, comparison, cart, and isolation operations passed from Expo Web in a mobile browser viewport through Spring Boot and the isolated MySQL database. Real-data comparison and native Android/iOS session verification remain open. Run the native smoke test with a device-reachable API before moving the integration to staging.

The original setup, roadmap, and pre-audit runtime notes follow for historical context.

---

## Project Overview

- **What CartIQ is:** An AI-powered grocery price comparison and shopping intelligence platform.
- **Main objectives:** Help users find the cheapest grocery sources, predict future prices, get bulk-purchase recommendations around festivals, and discover recipes with cost-optimised ingredient lists.
- **Problems solved:** Manual price checking across platforms, lack of price-trend visibility, inefficient festival/bulk buying, and fragmented grocery data.
- **Target customers:** Urban shoppers in India who buy groceries online and from local stores.
- **Grocery price comparison:** Aggregates pricing from **Blinkit**, **Zepto**, **Swiggy Instamart**, and **nearby offline stores**.
- **CartIQ shopping cart:** Unified cross-retailer cart with basket optimisation, checkout flow, budget tracking, and purchase history.
- **Four planned AI features:**
  1. Future price prediction (XGBoost time-series model).
  2. Cross-platform product matching (barcode + attribute + text similarity).
  3. Festival-driven bulk-purchase recommendation.
  4. Recipe-to-cart recommendation and cost comparison.
- **AI Shopping Assistant:** LLM-powered conversational interface (Gemini API) for product, price, and cart queries.

---

## Technology Stack

| Layer | Technologies | Local Running Status |
|-------|-------------|----------------------|
| **Frontend** | React Native, Expo ~54.0.0, TypeScript ~5.9.2, React 19, React Native Web | ✅ **Running** on `http://localhost:8092` (HTTP 200 OK) |
| **Backend** | Java 17, Spring Boot 3.5.16, Spring Security, Spring Data JPA, Spring MVC, Flyway | ✅ **Running** on `http://127.0.0.1:8080` (Actuator: UP) |
| **AI Service** | Python 3.12.10, FastAPI 0.162.7, Uvicorn, Pandas, Scikit-learn, XGBoost | ✅ **Running** on `http://127.0.0.1:8001` (Status: ok) |
| **Database** | MySQL 8.0.44 (`cartiq_db` provisioned, V1–V7 applied), H2 (active local & test) | ✅ Live MySQL 8 & Local H2 active |
| **Notifications** | Firebase Cloud Messaging (optional, disabled by default) | Disabled |
| **AI Integration** | Gemini API (optional intent classifier) | Configured |
| **Caching** | Redis (optional, disabled by default) | Disabled |
| **API Docs** | SpringDoc OpenAPI / Swagger UI | Available at `/api/docs` and `/api/openapi` |
| **Testing** | JUnit 5, Mockito, Spring MockMvc, pytest, Node test runner | ✅ 140 / 140 tests passing (93 H2, 6 MySQL, 6 AI, 35 frontend) |
| **Build** | Maven 3.9.16, npm 11.16.0 | Operational |

---

## Project Architecture

### Frontend (`src/`)
- `screens/` – Account, Admin, Cart, CartExperience, CartInsights, Checkout, CheckoutPreview, Details, Discover, SmartHub.
- `services/` – api, cart, checkout, comparison, four-source, product-search, purchase-links, retailers, selection, workspace, push, addresses, response, storage-scope, attempts.
- `navigation/`, `components/`, `constants/`, `hooks/`, `types.ts`.
- `tests/comparison.test.ts` – frontend comparison logic tests (35/35 passing).

### Backend (`backend/src/main/java/com/smartcart/`)

| Package | Contents |
|---------|----------|
| `catalogimport/` | `CatalogueImportService`, `OpenFoodFactsAdapter`, `ImportNormalizer` |
| `controller/` | Admin, Auth, Cart, Catalog, CatalogueImport, Comparison, Email, LocalComparisonTerms, LocalStore, Model, OnlineComparisonEvidence, Provider, Retailer, Training, Workspace |
| `service/` | Analytics, Assistant, Auth, BasketOptimizer, Budget, Cart, Catalog, Comparison, Decision, Demo, EmailChange, LocalStore, Recipe, Watch |
| `integration/` | EmailSender, GroceryDataProvider, IntentClassifier, MockOnlineProviderAdapter, ModelService, OnlineProviderAdapter, ProviderSyncService, ResendEmailSender, RewaltoProvider |
| `entity/` | `UserEntity` (JPA-mapped) |
| `repository/` | `Db` (JDBC template wrapper), `UserRepository` (JPA) |
| `security/` | JWT auth filters, security configuration |
| `dto/` | Request/response models |
| `exception/` | `ApiException` and error handling |
| `notification/` | FCM integration |
| `config/` | Application configuration beans |

### AI Service (`ai-service/`)
- `app.py` – FastAPI endpoints for price prediction inference.
- `training.py` – Model training pipeline.
- `test_app.py`, `test_training.py` – Unit tests (6/6 passing).
- `requirements.txt` / `requirements.lock.txt` – Dependencies.
- `Dockerfile` – Container build.

### Database (`backend/src/main/resources/`)
- `db/migration/` – Flyway SQL V1–V9.
- `catalogue/products.json` – 27-entry starter catalogue (24 unique products, 27 variants).
- `application.yml` – Main config (Flyway enabled, Hibernate validate-only).
- `application-local.yml` – H2 file-based local profile (`.runtime/smartcart`).
- `application-mysql-local.yml` – MySQL profile (`cartiq_db`, `cartiq_user`).
- `application-prod.yml` – Production config (env-var driven).
- `application-test.yml` – H2 in-memory test profile.

---

## Database Information

### MySQL 8 Status
- **Instance:** MySQL 8.0.44 Community Server running on `127.0.0.1:3306`.
- **Database provisioned:** `cartiq_db` (`utf8mb4` character set).
- **User provisioned:** `cartiq_user` with full access to `cartiq_db.*`.
- **Flyway schema history:** Table `flyway_schema_history` active in `cartiq_db`.
- **Verified Product count in MySQL:** 24.
- **Verified Variant count in MySQL:** 27.
- **Verified Category count in MySQL:** 7.
- **Verified Platform count in MySQL:** 3 (Blinkit, Instamart, Zepto).
- **Verified Location count in MySQL:** 1 (`blr-indiranagar`).

### Migration Status (V1–V9)

| Version | File | Description | H2 Status | MySQL Status |
|---------|------|-------------|-----------|-------------|
| V1 | `V1__normalized_schema.sql` | Core schema: 34 tables | ✅ Applied | ✅ **Applied & Verified** |
| V2 | `V2__provider_mappings.sql` | `provider_mappings` table | ✅ Applied | ✅ **Applied & Verified** |
| V3 | `V3__email_verification.sql` | `email_change_requests` table | ✅ Applied | ✅ **Applied & Verified** |
| V4 | `V4__catalogue_lifecycle.sql` | Adds `active` column to `products` | ✅ Applied | ✅ **Applied & Verified** |
| V5 | `V5__local_stores.sql` | `local_stores`, `local_store_listings` + indexes | ✅ Applied | ✅ **Applied & Verified** |
| V6 | `V6__local_comparison_terms.sql` | `local_store_comparison_terms` table | ✅ Applied | ✅ **Applied & Verified** |
| V7 | `V7__online_comparison_evidence.sql` | `online_comparison_evidence` table | ✅ Applied | ✅ **Applied & Verified** |
| V8 | `V8__catalogue_import_foundation.sql` | Import tables: `catalogue_import_jobs`, candidates, barcodes, locks | ✅ Applied | ⚠️ Not applied (pending approval) |
| V9 | `V9__import_recovery.sql` | Recovery columns, attempts, candidate corrections | ✅ Applied | ⚠️ Not applied (pending approval) |

---

## Ten-Milestone Roadmap

### Milestone 1 – Project Foundation ✅
- **Completed:** Repository, Dockerfiles, CI skeleton, project config, `pom.xml`, `package.json`, environment setup.
- **Verification:** All builds, dependencies, and environment runtimes verified.

### Milestone 2 – User Interface & Core Experience ✅
- **Completed:** 10 screens, navigation, services layer, TypeScript models.
- **Verification:** `npm run typecheck` passed (0 errors); 35 unit/integration tests passed (`tests/comparison.test.ts`); web server live on `http://localhost:8092`.

### Milestone 3 – Backend & Database ✅
- **Completed:** Spring Boot API running on `http://127.0.0.1:8080`, 15 controllers, 14 services, JWT auth, Flyway migrations V1–V7 applied and verified on live MySQL 8, JDBC repository layer, OpenAPI docs.
- **Verification:** All 93 H2 backend tests passed; all 6 MySQL milestone tests passed (`MysqlMilestone2Test`, `MysqlMilestone3Test`, `MysqlMilestone4Test`). Baseline 24 products and 27 variants verified in MySQL.

### Milestone 4 – Product Catalogue 🟡
- **Completed:** `CatalogueImportService` (fetch, stage, approve, reject, correct, resume), `OpenFoodFactsAdapter`, `ImportNormalizer`, `CatalogueImportController`, staging tables, barcode registry.
- **In Progress:** Preparation for controlled MySQL import.
- **Pending:** User approval for applying V8/V9 migrations to MySQL before executing MySQL import.
- **Verification:** 59 import-related tests pass (H2, mocked HTTP).

### Milestone 5 – Large-Scale Import ⬜
- **Pending:** Streaming/paged import from Open Food Facts, bounded-memory batch processing, >100k product ingestion.
- **Dependencies:** Milestone 4 complete (MySQL-verified).

### Milestone 6 – Retailer Integration ⬜
- **Completed:** Provider adapter interface (`GroceryDataProvider`, `OnlineProviderAdapter`), mock adapter, Rewalto provider skeleton, `ProviderSyncService`.
- **Pending:** Live Blinkit, Zepto, Swiggy Instamart API integration. No provider credentials configured.

### Milestone 7 – Four-Source Price Comparison Engine 🟡
- **Completed:** `ComparisonService` (online + offline comparison with evidence verification, fee calculation, basket total, eligibility checks), `ComparisonController`, comparison security tests.
- **Pending:** Live provider data, real evidence records, live comparison testing.

### Milestone 8 – AI & Smart Recommendations 🟡
- **Completed:** AI service running on `http://127.0.0.1:8001`, model training pipeline, `IntentClassifier` (Gemini), `AssistantService`, `ModelService` integration, 6 pytest tests passing.
- **Pending:** Real-data model training, accuracy evaluation on production dataset.

### Milestone 9 – Cart, User Features & Optimisation 🟡
- **Completed:** Cart CRUD, basket optimiser, checkout flow, notifications (in-app), budget tracking, purchase history, watchlists, pantry, recipes, replenishment.
- **Pending:** Real FCM delivery, production notification testing, UI polish.

### Milestone 10 – Testing, Deployment & Production ⬜
- **Completed:** 93 backend tests, 6 MySQL tests, 6 AI service tests, 35 frontend tests all passing (140 total); all 3 services running locally.
- **Pending:** Docker Compose orchestration, staging deployment, production monitoring.

---

## Agent Handoff

- **Current assigned task:** Connect frontend and backend, configure full CORS support for Expo Web on port 8092, add root browser redirect, resolve `UNAUTHORIZED` and `INTERNAL` runtime errors, and ensure all 3 application tiers run smoothly.
- **Files modified:**
  - `src/services/api.ts` (API_URL normalization ensuring `/api` prefix is appended)
  - `backend/src/main/java/com/smartcart/exception/ApiErrors.java` (Added explicit 405 `METHOD_NOT_ALLOWED` and 404 `NOT_FOUND` handlers; added URI logging for unexpected exceptions)
  - `backend/src/main/java/com/smartcart/security/SecurityConfig.java` (Enabled CORS `setAllowedOriginPatterns` for all localhost and 127.0.0.1 ports with credentials; permitted root `"/"`)
  - `backend/src/main/java/com/smartcart/controller/RootController.java` (Created root handler that redirects browser visits to `http://localhost:8092` and provides JSON status)
  - `backend/src/main/resources/application.yml` (Added port 8092 to `allowed-origins`)
  - `.env` (`EXPO_PUBLIC_API_URL=http://127.0.0.1:8080/api`)
  - `docs/CARTIQ_MASTER_CONTEXT.md`
  - `docs/CHANGELOG.md`
  - `docs/TESTING_STATUS.md`
  - `documentation/CURRENT_PROGRESS.md`
- **Current Runtime Status:**
  - **Spring Boot Backend:** Running on `http://127.0.0.1:8080` (PID 22932, profile `local`, Health: UP, Root: redirects to frontend or returns API discovery).
  - **FastAPI AI Service:** Running on `http://127.0.0.1:8001` (PID 28868, Python 3.12 venv, Health: status ok).
  - **Expo Web Frontend:** Running on `http://localhost:8092` (Metro bundler active, HTTP 200 OK, full bidirectional API connection verified with CORS credentials).
  - **MySQL 8 Database:** `cartiq_db` provisioned on port 3306, migrations V1–V7 applied and verified with 24 products and 27 variants.
- **Resolved Runtime Issues:**
  1. `UNAUTHORIZED` (`{"code": "UNAUTHORIZED", "message": "Please sign in again."}`):
     - Cause 1: Frontend previously omitted `/api` in requests, triggering Spring Security's `.anyRequest().authenticated()` fallback. Resolved by normalizing `API_URL` in `src/services/api.ts` and `.env`.
     - Cause 2: Opening backend port `http://127.0.0.1:8080/` in browser previously hit protected root. Resolved by permitting `"/"` and serving a 302 redirect to `http://localhost:8092` for browsers.
     - Cause 3: Preflight CORS requests from `http://localhost:8092` were blocked because port 8092 was missing from `allowed-origins`. Resolved by adding `setAllowedOriginPatterns` for all localhost ports.
  2. `INTERNAL` (`{"code": "INTERNAL", "message": "The request could not be completed. Please try again."}`): Caused by unhandled `HttpRequestMethodNotSupportedException` being caught by `ApiErrors.java`'s `@ExceptionHandler(Exception.class)` and converted to a 500 status code. Resolved by adding explicit `@ExceptionHandler(HttpRequestMethodNotSupportedException.class)` returning 405 Method Not Allowed and `@ExceptionHandler(NoResourceFoundException.class)` returning 404 Not Found.
- **Outstanding issues:**
  - Migrations V8 and V9 are ready and tested on H2, but remain unapplied on MySQL per explicit design and user choice.
  - When the user approves advancing to Milestone 4 MySQL import, V8 and V9 can be applied.
- **Instructions for next agent:**
  - Keep all 3 files in `docs/` updated.
  - Never commit credentials to files or outputs.
  - Both H2 local runtime and MySQL 8 target are verified operational.
