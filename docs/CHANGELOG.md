# CHANGELOG.md

## GitHub repository publication · 20 November 2026

- Initialized Git repository for CartIQ on `main` branch.
- Sanitized directory before staging: removed stray transient cache/test files (`5ae*`, `856*`, `ai-service/%SystemDrive%`, `query`).
- Verified `.gitignore` properly excludes `.env`, `node_modules/`, `backend/target/`, `ai-service/.venv/`, `.runtime/`, `*.log`, and all credentials. Added `.env.example` template.
- Committed full CartIQ project (frontend, Spring Boot backend, FastAPI AI service, assets, test suites, and documentation).
- Configured remote origin: `https://github.com/Kathirvelan210/CartIq.git`.
- Successfully pushed `main` branch to GitHub remote `origin/main` with upstream tracking.

## Weekly recipe variety · 20 November 2026

The Recipes screen checks AI suggestions against the selected catalogue filters. A single suggested dish occupies one day. With two or more selected dishes, the planner spaces repeats across seven days. Shoppers can include other matching catalogue recipes without another generation request. Replacing suggestions preserves manually added meals.

## Recipe-generation timeout · 20 November 2026

Recipe and weekly-meal generation now allow 75 seconds between reads from Google, 100 seconds for Spring to receive the private service response, and 115 seconds for the browser. The connection timeout is eight seconds. The recipe page shows progress while it waits. The short provider timeout for shopping intent classification is unchanged.

## Recipe assistant · 20 November 2026

The Recipes tab now generates a measured recipe from one form for the dish, servings and optional cooking preferences. Ingredients can be marked on hand and the remaining list opened before adding the meal to a plan. The instructions remain visible in cooking order. Weekly AI planning and the optional catalogue remain accessible.

## Shopping search allowance and browser history

The local operator granted 20 further attempts after 19 recorded attempts, for a lifetime ceiling of 39 on this ledger. Each confirmed three-store comparison requires three available attempts. Recent search terms are stored per account on the browser. Short-lived receipt references reopen previous results through CartIQ while available. Retailer prices and payloads stay out of browser storage.

## QuickCommerce credit floor

Confirmed requests can use the last available credit. The ledger still permits 20 lifetime experiment attempts and reserves all three attempts before a three-retailer comparison.

## Phase 10.9 · Gemini 3.8 Flash compatibility (19 November 2026)

Recorded the Gemini 3.8 Flash migration finding and the configuration-only stop. No adapter or runtime configuration changed. [Phase 10.9 evidence](evidence/phase10-9/README.md)

## Phase 10.5 · Authorized live Gemini attempt (19 November 2026)

Recorded the separately authorized live recipe attempt, correlated the upstream 503 with the browser-visible GEMINI_SERVER result, and added a sanitized receipt. No service, configuration or credential changes were required. [Live receipt and offline test evidence](evidence/phase10-5/README.md)

## Phase 10.5 · Gemini preflight (19 November 2026)

Recorded the stopped live recipe preflight, the absence of a provider receipt and the subsequent offline regression results. No runtime configuration changed. [Phase 10.5 evidence](evidence/phase10-5/README.md)

## Phase 10.4 · Local Gemini diagnostic verification (19 November 2026)

Successful recipe drafts now carry sanitized provider status and request ID headers through FastAPI and Spring. The local pair was restarted with the existing session credential. Added a reproducible, isolated Chrome and loopback diagnostic workflow covering success and eight failures. [Restart and verification](evidence/phase10-4/README.md)

## 2026-11-19 · Gemini 502 diagnosis

Added correlated, sanitized Gemini diagnostics across Spring and private FastAPI and retained upstream status and request IDs when supplied. Provider authentication, quota, server errors, network failures and invalid generated content now have distinct codes. The changes were verified offline. [Incident trace and tests](evidence/phase10-2/GEMINI_502.md)

## 2026-11-19 · Local Gemini service activation

- Added a paired Windows launcher for Spring and private FastAPI with masked session input or existing DPAPI storage, a child-process preflight, and authenticated loopback readiness.
- Added model-envelope checks and rejection of unsupported retail claims in recipe text. Rapid frontend taps share the existing in-flight operation, and the Gemini assistant button shows its own loading state.
- Activated the operator's existing credential for the local session. The three shopper actions passed offline Chrome verification. Live Google verification remains pending approval. [Results](evidence/phase10-2/README.md)

## 2026-11-18 — Android foreground location

- Fixed repeat requests to upgrade an existing approximate Android permission. The native location flow now reads the current grant before prompting and allows up to 30 seconds for a fresh device reading.
- The picker distinguishes approximate and precise permission from the measured accuracy, offers an app-settings recovery action, and displays the saved accuracy in metres. The store rechecks fix expiry on foreground resume.
- Added precision, permission reuse, expiry on restart and authenticated account-isolation regressions. Four physical-device confirmations reached Spring Boot with HTTP 200. No migration or provider request was required. [Test results and screenshots](evidence/phase10-1/TEST_RESULTS.md)

## 18 November 2026 · Device location, marketplace cart and Gemini shopping

- Device GPS is available on explicit action independently of Google PIN lookup. CartIQ carries the sensor's accuracy and timestamp to a private per-account Spring selection, rejects stale and invalid fixes, and checks the active selection before controlled retailer dispatch.
- Marketplace browsing uses 15 category query groups. Confirmed compatible cross-retailer listings show per-pack prices and fetch times. Unknown stock, expired results, unreviewed packs and missing prices cannot establish a cheapest offer.
- Flyway V20 adds a nullable mapping-decision reference to cart lines. A fresh, approved listing can be added, explicitly switched, reloaded and repriced against account- and location-scoped transient evidence. The migration contains no product, price or location inserts. Customer builds surface unavailable backend configuration and reject a sample-mode backend.
- Recipes supports seven-day Gemini selection over curated recipe IDs. AI Smart Shopping can send a question to the private Gemini intent service, then calculate an answer using existing CartIQ services. Both are explicit actions. Missing AI configuration and invalid responses surface as failures.
- Verified the release candidate on Chrome and disposable MySQL 8.0.44. The [Phase 10 tests](TESTING_STATUS.md#phase-10-verification) include exact commands, limitations and a link to the changed files.


---

## Phase 9.5 Google PIN locality matching

- Removed the strict reverse-centroid check from Google PIN selection. The picker presents compatible forward candidates, labels their coordinates approximate, and asks the user to choose a locality. A manual catalogue search remains available when verification is incomplete.
- Added sanitized Google parsing diagnostics and regression fixtures for multiple localities, state and PIN conflicts, state-only responses and browser GPS. Admin area registration and retailer authorization retain their existing checks.

## Local Google Maps location picker

- Enabled PIN and browser GPS in the signed-in local Google demo picker. Both actions require geocoding consent and corroborating forward and reverse results. Google responses that identify the same PIN, city and state can verify a postcode area even when a sublocality is absent.
- Displayed Google Maps attribution and kept selected Google demo locations session-only. Admin area approval and paid retailer safeguards remain separate.

## Google Geocoding API v4 pilot preparation phase 9.4

- Added explicit Google demo provider selection, backend-only key injection through the Windows launcher, and separate local admin lookup routes for forward PIN and reverse verification. The demo keeps provider responses transient and rejects ambiguous reverse evidence.
- Preserved the Nominatim provider and existing area registration process. No external geocoding or retailer requests ran and the proposed area remains unverified. [Pilot steps](MARKETPLACE_PREDISPATCH.md#phase-94-google-geocoding-api-v4-demo)

## 2026-11-16 – Public Nominatim pilot preparation phase 9.3

- Added opt-in public Nominatim support to the existing backend geocoder. Public lookups use a monitored operator email in the User-Agent, accept PIN-only searches, and restrict reverse queries to an explicitly approved delivery-area point. Added shared request pacing, bounded in-memory caching, and linked OpenStreetMap attribution.
- Added an offline check for consistent PIN and reverse evidence. The proposed Coimbatore area remains unverified and the admin registration draft remains unsubmitted. No external geocoding, retailer or model requests ran. The retailer ledger and shared MySQL were untouched. [Operator handoff](MARKETPLACE_PREDISPATCH.md#next-operator-action-and-lookup-verification)

## 2026-11-16 – Marketplace pre-dispatch phase 9.2

- Recorded operator-supplied Coimbatore PIN `641008` as unverified and prepared the existing admin location registration payload. Registration and duplicate verification await authorized PIN and coordinate evidence. No geocoder call or pilot change occurred.
- Rechecked the isolated V19 database, historical Blinkit SKU `176` and existing credit ledger offline. The mapping preview still requires title reconciliation, and the approved PIN and written permission reference are outstanding.
- Documented the exact area, mapping, permission and authorization handoff and a read-only fingerprint check. No application code, pilot state, live requests or credits changed. [Pre-dispatch report](MARKETPLACE_PREDISPATCH.md)

## 2026-11-16 – Marketplace phase 9

- Added category and free-text marketplace browsing through query-scoped one-use approvals, reusable transient results, and exact-pack cart admission. V19 adds the optional marketplace query to pilot attempts. All retailer requests still pass through the existing adapter and credit ledger.
- Scoped saved delivery locations to the active account. Added location selection, renaming and removal. Kept catalogue products in their own section rather than substituting them for retailer responses.
- Added tests for authorization scope, reuse, expiry, incompatible packs and shopping-plan repricing. No live retailer request or shared database change occurred.

## 2026-11-16 – Visual API integration

- Corrected cart presentation for retailers without usable prices. All three stores show the actual number of priced lines, unknown subtotals stay unknown, and partially priced totals are labelled.
- Gave the admin recommendation feedback, retailer operations and price coverage panels distinct account-scoped React keys.
- Verified Chrome against the running Spring Boot backend and disposable H2, including browser reloads, account isolation, V18 SQL readback, and the synthetic history exclusion control. Added request correlation, screenshots and the verification report. [Results](VISUAL_API_INTEGRATION_REPORT.md)

## 2026-11-16 – Controlled grocery pilot phase 8

- Added local admin pilot setup, exact mapping selection, a capped experiment window, separate one-use shopper authorizations and request receipts in V18.
- Bound controlled dispatch and refreshed offer eligibility to the authorized account, retailer, operation, exact mapping and approved position. Added a user-triggered shopper action and receipt. Live results stay in memory and genuine dispatch remains pending separate approval.

## 2026-11-16 – Grocery data quality and retailer operations phase 7

- Added admin-only coverage and freshness filters, separate recorded and current-account live counts, provenance, diagnostics and complete mapping review and history. Approval, rejection and correction use the existing evidence and revision rules.
- Added configurable offer freshness, removed stale candidate prices from comparison, and strengthened evidence explanations. Confirmed coordinates are now required to use cached live quotes, and barcode reassignment invalidates affected cached offers.
- Added an eight-case deterministic regression baseline across matching, bulk-buy, personalization and recipe mapping. Flyway remains at V17. [Verification](TESTING_STATUS.md#grocery-data-quality-and-retailer-operations-phase-7)

## 2026-11-16 – Live grocery offers phase 6

- Added explicit local exact-pack search and item refresh to Product details, Shopping plan and Bulk buy. Retailer outcomes, request IDs, last successful refresh, match confidence and freshness appear independently.
- Connected approved transient offers to weekly and saved-cart baskets, recipe pack selection, bulk-buy, personalized recommendations and the unified assistant. A fresh availability change can remove an older quote and reprice a plan. Persisted history and catalogue mappings keep their existing approval paths.
- Added response-only handling for the Swiggy Instamart name and durable billing references for verified charged and uncharged outcomes. Uncertain outcomes hold the reservation. Flyway stays at V17. [Verification](TESTING_STATUS.md#live-grocery-offers-phase-6)

## 2026-11-15 – Unified shopping intelligence phase 5

- Added editable seven-day plans, pantry-adjusted ingredients, explicit cart import, versioned saves and a separate offline assistant in AI Smart Tools. Kept individual Phase 1–4 screens accessible. Timing answers can reuse a validated forecast only when it matches the current recorded offer and still passes permission checks.
- Reused the recipe planner and verified offer reader. Combined reviewed pack bundles and exact extras in fee-aware single-store and split baskets. Missing stock, stale quotes and unknown charges remain visible as gaps. Plan edits and expiry invalidate displayed weekly comparisons.
- Added V17 storage for private plan inputs and voluntary feedback. Admin dashboards can show privacy-thresholded outcome counts. Added offline manifest contracts for matching, recipe ranking, bulk buying and personalization. No recommendation weights, forecasts, retail requests or external model calls were introduced.

## 2026-11-15 – Bulk-buy and personalized grocery intelligence phase 4

- Added account-owned household and consumption preferences in Flyway V16 and authenticated APIs for pack advice, personalized suggestions and basket budgets. Current offer selection checks variant identity, catalogue location, freshness, stock evidence and permissions for retained observations.
- Added Bulk buy, For you and Shopping insights to AI Smart Tools. Connected the assistant to current account data for weekly shopping, bulk buying, pantry recipes, compatible alternatives and budget checks.
- Added H2 regressions for household and account isolation, stock and expiry constraints, formulation conflicts, exact-quantity alternatives, fee-inclusive budgets and incomplete retailer terms. The implementation uses deterministic rules and adds no paid provider calls, historical observations or model training. [Bulk-buy rules](BULK_BUY_INTELLIGENCE.md) · [Personalized workflows](PERSONALIZED_RECOMMENDATIONS.md)

## 2026-11-15 – Genuine price dataset acquisition phase 3B

- Added source authorizations, preview and commit for historical CSV and JSON, replayable audit metadata, and an admin coverage dashboard. Historical availability can be evaluated from reviewed source evidence without rewriting the CartIQ ingestion timestamp. Existing 60-day and holdout gates remain in place.
- Reviewed available commercial and public sources. No accepted permission document or genuine sample was available. No provider calls, purchases, genuine intake or training took place.

## 2026-11-15 – Price prediction phase 3

- Added explicit training/inference approval evidence, a scoped coverage report and admin experiment API, three rolling forecast methods, test-gated empirical uncertainty, and scoped metrics aggregation.
- Added the signed-in forecast view with separate observed and predicted prices, freshness and timing guidance. Added a zero-network Gemini readiness command. No genuine training data was loaded and no live provider requests were made.

## 2026-11-15 – Recipe intelligence phase 2

- Added source-referenced Indian recipe discovery, structured optional Gemini drafts through the private FastAPI service, and a signed-in Expo planning screen for quantities, pantry overrides, shopping lists and cart.
- Extended planning to combine repeated ingredients across days, use reviewed directional substitutions and compare bounded Blinkit and Zepto pack combinations from eligible recorded offers. The existing credit-protected live retailer selector remains explicit. Existing V13 schema supports the new workflow.

## 2026-11-15 – Grocery data engine phase 1

- Added V13 for reviewed variant enrichment, recipe ingredient mapping and substitutions, scoped retention approvals and normalized retailer observations. Added authenticated grocery identity and history reads and admin intake, revoke and chronological dataset APIs.
- Integrated permission-cleared retailer history into Expo Price trends and source/freshness metadata into transient QuickCommerce results. No raw provider payloads or genuine retailer observations were stored during this implementation.

## 2026-11-15 – Production release candidate

- Hardened the Spring production profile with explicit HTTPS origins, secure non-cacheable sessions, no demo or API docs, production rate limits, and a separate migration launcher. The application performs schema validation at startup and exposes liveness and MySQL-backed readiness.
- Added safe request and retailer correlation logs, disabled forecasting API docs in production, and required internal service authentication at forecast startup.
- Added Docker build-context allowlists, the isolated Compose and TLS proxy configuration, EAS build profiles, and production, deployment, rollback, security and operations guides. No shared MySQL or paid provider changes. [Results](TESTING_STATUS.md#production-release-verification)

## 2026-11-15 – Shopping intelligence and evidence-aware comparisons

- Extended live retailer results with local filters, sorting, pack and barcode evidence, review of uncertain pairs, per-retailer fetch time, standard-unit prices and matched-offer savings. Kept partial failures visible alongside other retailers.
- Extended the existing shopping assistant to explain recorded increases, current cart decisions, feasible basket totals and available alternative packs. Cart comparisons now name missing variants per retailer and discard prior results when the cart, catalogue area or account changes.
- Made forecasts opt-in and restricted forecast requests to comparable observations for one listing. Verified history stays available when forecasting is disabled. Kept QuickCommerce billing, Firebase enablement and shared MySQL unchanged. [Verification and remaining limits](TESTING_STATUS.md#shopping-intelligence-verification)

## 2026-11-15 – Live retailer verification and billing diagnostics

- Added a local, admin-only balance route using QuickCommerceAPI's documented free credit endpoint. A successful retailer response carries a validated provider correlation ID when supplied.
- A live Blinkit lookup returned HTTP 200 with Blinkit listed in `failed_platforms` and an unchanged balance. CartIQ originally checked for a debit too early and held the reservation. Reconciled the uncharged attempt at 96 from the operator-supplied request evidence while retaining its duplicate fingerprint.
- Updated the adapter to classify that envelope as a retailer failure and settle an uncharged dispatch only when the provider balance header also matches. Remaining retailers can proceed in that case. Missing or conflicting billing evidence still stops the batch.
- Billing validation accepts the exact charge header with an absent body field or equivalent canonical integer text. Present contradictory or malformed balance evidence still fails closed. Added sanitized billing logs and regression tests. Used the previously supplied Blinkit response body in an opt-in adapter test without committing it. Shared MySQL was untouched. [Verification](TESTING_STATUS.md#live-retailer-verification-and-billing-stop)
- Resumed live verification with Zepto and Swiggy, then ran a real Expo Web comparison. Blinkit and Zepto produced two matching-pack price comparisons, and Blinkit item detail succeeded. Both Swiggy dispatches failed upstream without a charge. The final ledger settled at 92 credits after four charges this session.

## 2026-11-15 – Fresh grocery search and retailer selection

- Live search shows the current shopping location, selected retailers, and maximum credit cost before dispatch. Users can submit a single retailer or a subset, and later add results from an unattempted retailer for the same search. Retailer cards show their own fetch times and distinguish duplicates, empty results and failures.
- Spring Boot validates the optional retailer list before contacting QuickCommerceAPI. Existing callers that omit it still request all three. Requests use the existing serial reservation and duplicate checks. Uncertain outcomes pause the search, and a different query, location or account clears the transient results.
- Added tests for single-retailer dispatch, invalid selections, duplicate suppression and frontend result scoping. Kept live access disabled and made no changes to the real ledger, shared MySQL or price storage. [Verification and proposed live test](TESTING_STATUS.md#fresh-grocery-search-verification)

## 2026-11-15 – Milestone 4 shopping location and live retailer UI

- Replaced the display-only GPS action and saved-area PIN filter with a shared location picker. Added Expo Location, foreground consent, optional reverse address labels, PIN locality choices and explicit confirmation of approximate points.
- Added the Spring Boot location lookup routes, authenticated shopping route flag, retailer outcomes and fetch timestamps. The comparison can continue after a documented provider failure when the billing header confirms no charge. An uncertain response still blocks further dispatch.
- Updated Discover and product comparison to use the confirmed location. Added retailer cards, matching-pack price comparison and retailer item details. Cart and catalogue identity remain governed by verified catalogue records. No migrations, paid requests, provider observations or shared MySQL changes. [Milestone verification](TESTING_STATUS.md#milestone-4-shopping-location-and-retailer-verification)

## 2026-11-15 – Final QuickCommerceAPI offline verification

- Checked the supplied Blinkit JSON against the reconciled request. It reports provider success, 24 products and 96 remaining credits. Its rows contain string listing IDs and numeric top-level store IDs. CartIQ returned 502 on the original live attempt. The original HTTP status, headers and diagnostic trace are still unavailable.
- Extended synthetic controller coverage for both ID shapes, unknown optional fields, zero results, invalid responses, and safe serialization failures. Added a controller-local HTTP 500 handler that omits converter exception details and returns `Cache-Control: no-store`.
- Left the provider disabled and verified the reconciled ledger read-only. No live requests, additional credit use, provider data writes or shared MySQL changes. [Test results and limitations](TESTING_STATUS.md#final-quickcommerceapi-offline-verification)

## 2026-11-15 – Blinkit reconciliation and offline 502 fix

- Settled the original Blinkit Milk reservation at 96 credits on operator-supplied provider evidence. The ledger keeps the original fingerprint, provider request ID, and evidence reference. One attempt remains recorded and 19 experiment credits remain above the protected floor of 77.
- Updated `QuickCommerceBudget.java` and `Initialize-QuickCommerceLedger.ps1` to preserve reconciliation evidence when the ledger is read or updated. Updated `QuickCommerceProvider.java` to normalize numeric listing and store IDs, tolerate unrecognized optional product metadata, and distinguish sanitized transport, decoding, billing, scope and listing failures.
- Added an offline Blinkit regression through the admin controller and evidence preservation coverage in `QuickCommerceProviderTest.java`. Provider remains disabled. No additional live requests, provider records or database changes. [Results and original-502 limitations](TESTING_STATUS.md#blinkit-reservation-reconciliation-and-offline-502-regression)

## 2026-11-15 – Authorized Blinkit smoke test outcome

- Verified the real protected key, disposable file-backed H2 and Flyway V11, and the renewed 97-credit dashboard checkpoint. Added sanitized upstream HTTP status reporting in `QuickCommerceProvider.java` and passed 16 targeted tests.
- Ran one Blinkit Milk attempt through CartIQ. The adapter returned HTTP 502 and held one pending reservation. Upstream receipt and billing were unknown at the time. Stopped the isolated backend with no retries or other retailer calls. The normal adapter remains disabled. No provider data or shared MySQL changes. [Smoke-test evidence](TESTING_STATUS.md#authorized-one-credit-blinkit-smoke-test)

---

## 2026-11-15 – Project repair and isolated verification

- Added secret ignores and Windows-protected key save and reload to `configuration/Start-CartIqBackend.ps1`. Normal local startup can run with the adapter off and the key missing. Added explicit online Maven mode for fetching missing dependencies. Tested child-process inheritance with a protected synthetic key.
- `QuickCommerceProvider.java` uses explicit no-retry and no-redirect transport settings. `QuickCommerceBudget.java` fails closed when its configured ledger is missing. `configuration/Initialize-QuickCommerceLedger.ps1` can reconcile a single pending attempt while retaining its fingerprint and consuming its experiment slot. No changes to the real key or ledger were made.
- `AssistantService.java`, `RecipeService.java` and `CatalogService.java` use relevant catalogue searches or exact canonical pack queries beyond the first unfiltered page. Expo comparisons exclude offers with missing verification, recipe totals stay unknown when required prices are missing, and price analytics no longer show a prior product or location after selection changes. Added regression coverage in `WorkspaceIntegrationTest.java`, `QuickCommerceProviderTest.java`, and `tests/comparison.test.ts`.
- Verified Java 17 startup, H2 and disposable MySQL V1–V11, the local HTTP shopper journey, Expo Web export, and automated test suites. The live smoke test did not run. [Test commands and results](TESTING_STATUS.md#project-repair-and-isolated-verification)

---

## 2026-11-15 – One-credit Blinkit smoke test preflight

- Inspected the single-retailer route, reservation mechanism, local listener, and existing credit ledger. The running CartIQ JVM on loopback port 8080 had explicit `local` profile and local H2 arguments. The launch shell available to Codex returned `MISSING`, and the running JVM's key-presence message and Flyway V11 completion were not independently confirmed.
- Stopped before dispatch. The prepared coordinates are provider examples. No serviceable location for this run was confirmed. The request count was zero, and the ledger remained settled at 97 with zero reservations and all 20 experiment credits available above the 77-credit floor. Shared MySQL, the catalogue, verified offers, and price history were untouched. [Preflight results](TESTING_STATUS.md#one-credit-blinkit-smoke-test-preflight)

---

## 2026-11-15 – QuickCommerceAPI single-provider configuration

- Added one explicit, serial development lookup for all three retailers through `QuickCommerceController` and `ComparisonService`. Updated `QuickCommerceProvider` to normalize provider price, pack, stock, source fetch time, location, image and listing URL. A discount remains unknown unless an explicit valid percentage is supplied. `QuickCommerceBudget` distinguishes settled duplicates from failures so comparison can skip a duplicate safely. No automatic matching, publication or storage.
- Added the shared `RetailerComparison.tsx` panel to `Discover.tsx` and `FourSourceComparison.tsx` and backend-only client methods in `src/services/api.ts`. The existing public catalogue and verified comparison routes never dispatch paid calls.
- Removed `RewaltoProvider.java` and `RetailerObservationProvider.java` and blocked external observation sync in `ProviderSyncService`. Retained the mapping journal, historical record read paths, and storage fixture coverage. Retired obsolete cases in `ProviderBoundaryTest.java` and `ProviderContractTest.java`, and extended `QuickCommerceProviderTest.java` and `QuickCommerceAdminTest.java`.
- Updated `application-local.yml` and `configuration/Start-CartIqBackend.ps1` with the local single-provider configuration and opt-in activation. The ledger and account were not reset. No live calls or credits were used.

---

## 2026-11-15 – QuickCommerceAPI persistent budget and dry run

- Added a local disk ledger with atomic reservation and settlement, cross-process locking, and durable duplicate fingerprints. Missing or mismatched checkpoints, uncertain billing, storage errors and exhausted budgets stop transport.
- Reused the existing pack parser for transient lookup results. Validated optional location and search echoes, preserved multipacks and unknown stock or price, and converted INR from exact decimals.
- Changed `QuickCommerceBudget.java`, `QuickCommerceProvider.java`, `ProductMatching.java`, `QuickCommerceProviderTest.java` and `QuickCommerceAdminTest.java`. Updated the four handoff documents and added `QUICKCOMMERCE_DRY_RUN.md`. No live requests or credit spend.

---

## 2026-11-15 – QuickCommerceAPI development integration

- Added `backend/src/main/java/com/smartcart/integration/QuickCommerceProvider.java` and `controller/QuickCommerceController.java` under the same Java package for gated, local admin search and item lookups. Built in serial requests, duplicate suppression, the credit floor and attempt cap, scoped response validation, and no response cache or persistence.
- Added mocked provider coverage in `backend/src/test/java/com/smartcart/integration/QuickCommerceProviderTest.java` and an H2 admin boundary check in `backend/src/test/java/com/smartcart/QuickCommerceAdminTest.java`.
- Updated the master context, changelog, testing status, and retailer source register. The environment key was unavailable. No credits were spent and no live claims were made. No frontend, catalogue, or database schema changes.

---

## 2026-11-15 – Authorized sample receipt and human annotation readiness

- Added separate Rewalto and GS1 India DataKart grant verification entries and a controlled receipt and validation report. Recorded both intakes as held pending written grants and approval by an authorized CartIQ representative.
- Added the current delivery and human sign-off status to the handoff documents and recorded the next evaluation gate. No provider request was sent, sample processed, or human decision recorded.
- Updated `CARTIQ_MASTER_CONTEXT.md`, `CHANGELOG.md`, `TESTING_STATUS.md`, `PRODUCT_MATCHING_BENCHMARK.md`, and `INDIA_RETAILER_DATA_SOURCES.md`, and added `AUTHORIZED_SAMPLE_INTAKE_RECORD.md` under `docs/`. The existing 73-case dataset, evaluator and matcher were left as-is.

---

## 2026-11-15 – Authorized data readiness and matching evidence

- Specified the written Rewalto and GS1 India DataKart permissions, source fields, evidence states, reviewer access, retention, and snapshot checks. No external requests were made and no sample or permission was obtained.
- Preserved the 73-case benchmark and prepared a separate human annotation protocol and blank review, adjudication, and snapshot forms. No labels were added and no model or rule tuning ran.
- Added a V11 decision table and admin workflow for new mapping approvals, rejections, and unresolved reviews. New approvals require an identity and evidence record. Follow-up decisions link to the prior decision for the same candidate. Approval after an unresolved or rejected decision requires a new evidence digest. The observation reassignment guard remains in force.
- Extended mapping coverage for immutable replay, permission and evidence reference retention, explicit supersession, admin authorization, pack conflicts despite a shared GTIN, and the preservation of legacy mappings, current observations and price history across the schema upgrade.

**Changed files**

- Backend: `controller/ProviderController.java`, `integration/ProviderSyncService.java`, `integration/ProviderMappingJournal.java`, and `db/migration/V11__provider_mapping_decisions.sql` under `backend/src/main/` (`java/com/smartcart` and `resources`).
- Tests: `CatalogueImportFoundationTest.java`, `MysqlMilestone2Test.java`, `PriceObservationIntegrationTest.java`, and `ProviderMappingMigrationTest.java` under `backend/src/test/java/com/smartcart/`.
- Documentation: `CARTIQ_MASTER_CONTEXT.md`, `CHANGELOG.md`, `TESTING_STATUS.md`, `PRODUCT_MATCHING_BENCHMARK.md`, `INDIA_RETAILER_DATA_SOURCES.md`, and `HUMAN_MATCHING_ANNOTATION_TEMPLATE.md` under `docs/`.

---

## 2026-11-15 – Licensed sample research and independent annotation

- Rechecked provider contact routes, documented open rights by source, and drafted a scoped external request for offline evaluation and separate public comparison rights. No request was sent and no licence, sample, or production access was obtained.
- Added 19 source-evidenced observed pairs. A separate blinded AI review agreed on 17 pairs and both disagreements stayed unresolved. Preserved the 54 earlier cases and recorded reviewer decisions, evidence, split ownership and hashes.
- Added opt-in benchmark rules and error slices for missing or conflicting codes and multipack comparisons. Historical default evaluator output remained reproducible.
- Extended the existing H2 and MySQL mapping regression to verify the history-only reassignment guard. Reassignment returns 409, retains the original SKU, reviewer and approval time, and leaves the history row counts unchanged. No schema changes or shared MySQL writes.

**Changed files**

- Evaluation: `database/evaluate-catalogue-matching.py` and `docs/india-matching-annotation-benchmark.json`.
- Regression: `backend/src/test/java/com/smartcart/PriceObservationIntegrationTest.java`.
- Handoff: `CARTIQ_MASTER_CONTEXT.md`, `CHANGELOG.md`, `TESTING_STATUS.md`, `PRODUCT_MATCHING_BENCHMARK.md`, and `INDIA_RETAILER_DATA_SOURCES.md` under `docs/`.

---

## 2026-11-15 – India-focused hybrid matching and data authorization

- Added an opt-in hybrid advisory matcher to the existing admin compare and batch review routes, preserving the default baseline. The new rules compare pack structure and explicit label evidence before recommending a match.
- Added canonical variant mapping preview, conflict checks on evidence-backed manual approvals and idempotent reviewer attribution. The existing observation reassignment guard continues to protect price history.
- Evaluated 38 observed India-market pairs and 16 controlled inputs. The hybrid improved routing of unresolved holdout pairs from two of three to three of three, with no recall gain. Preserved the earlier benchmark output and default scoring. [Evaluation and executed suites](TESTING_STATUS.md)
- Audited retailer, distributor, brand-owner and open data permissions. No approved retailer feed was connected and no observations were collected. The offline evaluation is documented separately. [Sources](INDIA_RETAILER_DATA_SOURCES.md) · [Benchmark](PRODUCT_MATCHING_BENCHMARK.md)

**Changed files**

- Backend: `catalogimport/ProductMatching.java`, `controller/CatalogueMatchingController.java`, `controller/ProviderController.java` and `integration/ProviderSyncService.java` under `backend/src/main/java/com/smartcart`.
- Tests: `ProductMatchingTest.java`, `CatalogueImportFoundationTest.java` and `PriceObservationIntegrationTest.java` under `backend/src/test/java/com/smartcart`.
- Evaluator and data: `database/evaluate-catalogue-matching.py` and `docs/india-matching-benchmark.json`.
- Handoff: `CARTIQ_MASTER_CONTEXT.md`, `CHANGELOG.md`, `TESTING_STATUS.md`, `PRODUCT_MATCHING_BENCHMARK.md`, `INDIA_RETAILER_DATA_SOURCES.md` and `RETAILER_LISTING_FOUNDATION.md` under `docs/`.

---

## 2026-11-14 – Matching evaluation and retailer integration preparation

- Re-reviewed all 33 held candidate records using barcode-linked packaging and brand-owner evidence. Approved four inactive drafts and rejected two medication records from grocery intake in disposable MySQL. Kept 27 on hold and published none.
- Added a 47-pair source-linked matching benchmark and a reproducible evaluator for the existing scorer, GTIN-only baseline, and normalized brand/title baseline. Reported holdout errors and review routing separately.
- Added individually configurable Blinkit, Zepto and Swiggy Instamart adapters behind the existing gated Rewalto transport. Validated native listing identity, PIN, price fields and display URL.
- Made observation replays idempotent by the existing whole-second storage key. Preserved comparison evidence on replay, kept late records from overwriting current availability, and verified source attribution and location separation.
- Passed 37 frontend tests, 119 backend unit and H2 tests with nine MySQL-gated skips, and nine separate disposable MySQL tests. Price fixture rows rolled back. No retailer data collection or shared database migrations ran. [Commands and results](TESTING_STATUS.md)

**Changed files**

- Retailer code: `ProviderSyncService.java`, `RetailerObservationProvider.java`, and `RewaltoProvider.java` under `backend/src/main/java/com/smartcart/integration`.
- Tests: `ComparisonHttpTest.java`, `MysqlMilestone4Test.java`, `PriceObservationIntegrationTest.java`, `ProviderBoundaryTest.java`, and `ProviderContractTest.java` under `backend/src/test/java/com/smartcart`.
- Benchmark: `database/evaluate-catalogue-matching.py`, `docs/product-matching-benchmark.json`, and `docs/PRODUCT_MATCHING_BENCHMARK.md`.
- Handoff: `CARTIQ_MASTER_CONTEXT.md`, `CHANGELOG.md`, `TESTING_STATUS.md`, `INDIA_IDENTITY_EVIDENCE.md`, and `RETAILER_LISTING_FOUNDATION.md`.

---

## 2026-11-14 – Verified catalogue expansion and product matching

- Re-reviewed the 37 flagged India pilot identities. Corrected and approved three, rejected one and held 33. Approved Rostaa from the prior ready queue and six additional food records, all as inactive drafts in disposable MySQL.
- Extended advisory matching with metric aliases, explicit multipack counts, source-assigned barcode handling and bounded admin batch review. Recorded the rule output for 21 real archive pairs, including unresolved cases.
- Added an SKU reassignment guard for listings that have observations and a locked SKU recheck on ingestion. Documented the existing retailer listing and observation model and its remaining limits.
- Reused MySQL Flyway V1–V10 with no new migrations. Frontend type check and 37 tests passed. The backend unit/H2 run passed 114 tests with eight MySQL skips, and eight disposable MySQL tests passed separately. [Results and commands](TESTING_STATUS.md)

**Changed files**

- Backend matching: `catalogimport/ProductMatching.java` and `controller/CatalogueMatchingController.java` under `backend/src/main/java/com/smartcart`.
- Retailer mapping: `integration/ProviderSyncService.java` under `backend/src/main/java/com/smartcart`.
- Tests: `ProductMatchingTest.java`, `CatalogueImportFoundationTest.java`, and `ComparisonHttpTest.java` under `backend/src/test/java/com/smartcart`.
- Handoff: `CARTIQ_MASTER_CONTEXT.md`, `CHANGELOG.md`, `TESTING_STATUS.md`, `INDIA_IDENTITY_EVIDENCE.md`, `RETAILER_LISTING_FOUNDATION.md`, and `india-product-matching-evaluation.json` under `docs/`.

---

## 2026-11-14 – Catalogue quality review, category sampling and matching

- Classified all 46 remaining candidates from the India pilot and recorded the source, GTIN, pack, category, market, origin and reason for each decision. Eight approvals created inactive product and variant drafts in disposable MySQL. Replaying all eight approvals returned duplicate status.
- Added an India-market category extractor that retains raw HTTP responses and metadata, fingerprints inputs and outputs, records quality and coverage, and replays archived responses without a network request.
- Added an admin-only comparison endpoint and a deterministic matching scorer for GTIN, brand, product name, normalized pack, flavour and formulation. It returns a decision with reasons and confidence and leaves catalogue data unchanged. Added seven archive-linked pairs for future evaluation.
- Passed 37 frontend tests, 111 backend tests with eight gated MySQL skips, and eight tests in a separate disposable MySQL run. The existing Flyway history was reused. [Commands and results](TESTING_STATUS.md)

**Changed files**

- Extractor: `database/extract-india-category-sample.py`.
- Backend: `catalogimport/ProductMatching.java` and `controller/CatalogueMatchingController.java` under `backend/src/main/java/com/smartcart`.
- Backend tests: `CatalogueImportFoundationTest.java` and `ProductMatchingTest.java` under `backend/src/test/java/com/smartcart`.
- Handoff and review: `CARTIQ_MASTER_CONTEXT.md`, `CHANGELOG.md`, `TESTING_STATUS.md`, `INDIA_CANDIDATE_REVIEW.md` and `india-product-matching-labels.json` under `docs/`.

---

## 2026-11-14 – India catalogue expansion and disposable MySQL verification

- Added a bounded India extractor for official Open Food Facts bulk JSONL and archived India API responses. The manifest captures source metadata, hashes, selection rules and quality counts. The existing uploader supports manifest verification and a controlled stop after committed chunks.
- Extended catalogue APIs and Discover with brand, normalized pack-size and barcode lookup. Existing search, categories, details and pagination continue to use the same catalogue.
- Fixed the unpublished V10 migration and dependent SQL by quoting `row_number`, a MySQL 8 reserved identifier. Applied V8–V10 in order on a fresh disposable V7 schema and validated its migration history and constraints.
- Staged 52 genuine candidate records in disposable MySQL, published six products and six variants after review, and left 46 pending. No retailer mappings, prices, or availability were added by the import.

**Changed files**

- Operator tools: `database/extract-off-india.py` and `database/stage-off-snapshot.py`.
- Backend: `catalogimport/{CatalogueBatchService,CatalogueImportService,ImportNormalizer}.java`, `controller/{CatalogueBatchController,CatalogController}.java`, and `service/CatalogService.java` under `backend/src/main/java/com/smartcart`.
- Migration: `backend/src/main/resources/db/migration/V10__catalogue_batches.sql`.
- Expo: `src/screens/Discover.tsx` and `src/services/api.ts`.
- Tests: `CatalogHttpTest.java`, `CatalogueImportFoundationTest.java`, and `MysqlCatalogueImportTest.java` under `backend/src/test/java/com/smartcart`.
- Handoff: all three files under `docs/`. [Reproduction and checks](TESTING_STATUS.md)

---

## 2026-11-14 – Real product catalogue pilot

- Extended the existing Open Food Facts import workflow with checkpointed JSONL chunks, retained raw records, paginated review and issue reporting, and explicit public activation after approval. The admin CLI stages a local snapshot and resumes by content fingerprint.
- Added category suggestions, additional normalized pack units, explicit attachment of other pack sizes to matching inactive products, and product-label metadata from legacy API and current bulk export formats. The existing barcode registry remains the identity constraint.
- Added API-backed import review and paginated catalogue management in Expo. Product details show GTINs, available label data and Open Food Facts attribution.
- The real-data H2 pilot staged two source records, published one product and one variant, and retained one for correction. It created zero retailer offers. V10 was applied only to isolated H2. See [testing status](TESTING_STATUS.md) for executed commands and outcomes.

**Changed files**

- Backend ingestion and catalogue: `catalogimport/{CatalogueBatchService,CatalogueImportService,ImportNormalizer}.java`, `controller/{CatalogueBatchController,CatalogueImportController,CatalogController}.java`, `service/CatalogService.java` under `backend/src/main/java/com/smartcart`.
- Database and operator tooling: `backend/src/main/resources/db/migration/V10__catalogue_batches.sql`, `database/stage-off-snapshot.py`.
- Expo: `src/components/{CatalogueReview,ProductMetadata}.tsx`, `src/screens/{Admin,Details}.tsx`, `src/services/api.ts`, `src/types.ts`.
- Tests and handoff: `backend/src/test/java/com/smartcart/CatalogueImportFoundationTest.java` and all three mandatory files under `docs/`.

---

## 2026-11-14 – Native mobile verification

- Inspected the Expo SDK 54 configuration, native session storage, API network setup, and permission entry points. Physical phone testing was deferred.
- Passed TypeScript, 37 frontend tests, 100 backend tests with six gated MySQL skips, and Expo Android/iOS Hermes bundle exports. Runtime outcomes are recorded in [testing status](TESTING_STATUS.md).
- Prepared an isolated in-memory H2 backend and an Android 14 emulator with Expo Go 54.0.8. The emulator reported a System UI hang and no CartIQ device-to-API request was observed. The main MySQL instance was left untouched. Changed the three handoff documents.

---

## 2026-11-14 – Frontend and backend integration verification

- Connected category browse, paginated search, product detail, profile refresh and planning-cart interactions to the existing Spring Boot endpoints. Restricted embedded retailer listings to offline sample mode and added loading, retry and empty states for connected flows.
- Added a request deadline, cancellation, a single authenticated replay after refresh, and same-site loopback URL normalization. Applied the configured CORS allowlist on the server, tightened JWT and BCrypt validation, and normalized common HTTP error responses.
- Serialized cart writes by account and explicitly removed purchase preferences when deleting items, including on MySQL. Kept unpriced products available for planning with no confirmed checkout price. When the forecast service is unavailable, the API returns an unavailable estimate and still returns the historical observations and summary.
- Verified local integration on an isolated MySQL 8.0.44 instance with the existing V1–V7 migrations and starter catalogue. The local MySQL profile defaults to V7. The shared instance was left untouched. See [testing status](TESTING_STATUS.md) for the executed commands, results, and limitations.
- All 13 operations passed through Expo Web in Chrome's Pixel 7 viewport, Spring HTTP, and MySQL. Frontend typecheck, 37 frontend tests, 100 distinct test-profile backend cases, six MySQL MockMvc tests, and six AI service tests passed across the recorded runs. Native device testing and live retailer comparison remain pending.

**Changed files**

- Frontend entry and state: `App.tsx`, `src/store.tsx`.
- Frontend API: `src/services/api.ts`, `src/services/api-client.ts`.
- Frontend screens: `src/screens/Account.tsx`, `CartExperience.tsx`, `Details.tsx`, `Discover.tsx`, `SmartHub.tsx`.
- Backend: `backend/src/main/java/com/smartcart/security/SecurityConfig.java`, `exception/ApiErrors.java`, `service/AnalyticsService.java`, `service/AuthService.java`, `service/CartService.java`, `service/CatalogService.java`, `service/RecipeService.java`, and `backend/src/main/resources/application-mysql-local.yml`.
- Tests: `tests/api-client.test.ts`, `tests/run.cjs`, and `backend/src/test/java/com/smartcart/` files `MobileApiHttpIntegrationTest.java`, `MysqlMilestone2Test.java`, `MysqlMilestone3Test.java`, `MysqlMilestone4Test.java`.
- Handoff: `docs/CARTIQ_MASTER_CONTEXT.md`, `docs/CHANGELOG.md`, and `docs/TESTING_STATUS.md`.

---

## 2026-11-14 – Documentation Initialization

**Milestone:** Project foundation (Milestone 1) and initial project setup.

**Changes made:**
- Added `docs/CARTIQ_MASTER_CONTEXT.md` with comprehensive project overview, technology stack, architecture, database information, roadmap, current work, and agent handoff.
- Created placeholder `docs/CHANGELOG.md` and `docs/TESTING_STATUS.md` files.

**Files created:**
- `docs/CARTIQ_MASTER_CONTEXT.md`
- `docs/CHANGELOG.md`
- `docs/TESTING_STATUS.md`

**Files modified:** None.

**Database changes:** None.

**Tests performed:** None.

**Result:** Documentation files added to repository.

**Remaining work:** Populate `CHANGELOG.md` with ongoing development entries, and `TESTING_STATUS.md` with actual test execution results.

---

## 2026-11-14 – MySQL & Flyway Verification and Import Readiness Audit

**Milestone:** Milestone 3 (Backend & DB) / Milestone 4 (Product Catalogue)

**Changes made:**
- Completed full codebase inspection of backend architecture, all 9 migration scripts, import service implementation, adapter, normaliser, 18 test classes.
- Verified MySQL 8.0.44 server is running (Windows service `MYSQL80`).
- Confirmed MySQL client, Java 17, Maven 3.9.16 are available.
- Attempted MySQL connectivity: Initial check blocked pending credentials.
- Ran full H2 test suite: **99 tests passed, 0 failures, 0 errors, 6 skipped** (MySQL-specific tests).
- Verified Flyway applies all 9 migrations cleanly on H2.
- Analysed all migration SQL for MySQL compatibility and readiness.
- Counted starter catalogue: 27 entries, 24 unique products, 27 variants, 4 with retailer SKUs.
- Produced migration readiness report for V6–V9.
- Updated all three documentation files with verified findings.

**Files modified:**
- `docs/CARTIQ_MASTER_CONTEXT.md`
- `docs/CHANGELOG.md`
- `docs/TESTING_STATUS.md`

---

## 2026-11-14 – Full Stack Execution, Environment Setup & End-to-End Verification

**Milestone:** Milestone 2 (UI & Core Experience) / Milestone 3 (Backend & DB) / Milestone 8 (AI Service)

**Changes made:**
- Executed `npm ci` at project root, installing 851 frontend packages for React Native / Expo.
- Created Python 3.12 virtual environment (`ai-service\.venv`) and installed all AI service requirements (`fastapi`, `uvicorn`, `pandas`, `scikit-learn`, `httpx`, `pytest`).
- Executed frontend TypeScript check (`npm run typecheck`): 0 errors.
- Executed frontend test suite (`npm run test`): 35 tests run, 35 passed, 0 failed.
- Executed AI service test suite (`pytest`): 6 tests run, 6 passed, 0 failed.
- Started and verified Spring Boot Backend on `http://127.0.0.1:8080`:
  - Profile: `local` (H2 file database `.runtime/smartcart`, Flyway V1–V9 validated & applied).
  - Health check (`/actuator/health`): `UP`.
  - API endpoint verification (`/api/locations`): returned 3 active delivery locations.
- Started and verified FastAPI AI Service on `http://127.0.0.1:8001`:
  - Health check (`/health`): `status: ok`, mode: `experimental`.
- Started and verified Expo Web Frontend on `http://localhost:8092`:
  - Metro Bundler initialized and serving web client (`HTTP 200 OK`).

**Files modified:**
- `docs/CARTIQ_MASTER_CONTEXT.md`
- `docs/TESTING_STATUS.md`
- `docs/CHANGELOG.md`
- `documentation/CURRENT_PROGRESS.md`

---

## 2026-11-14 – MySQL 8 Provisioning, Migrations V1–V7 Applied & MySQL Contract Verification

**Milestone:** Milestone 3 (Backend & Database) – COMPLETED

**Changes made:**
- Verified MySQL root authentication with credentials provided by user.
- Provisioned database `cartiq_db` (`utf8mb4` character set, `utf8mb4_unicode_ci` collation).
- Provisioned database user `cartiq_user` with full access privileges on `cartiq_db.*`.
- Applied Flyway migrations V1 through V7 to MySQL 8 with target version 7 per user approval:
  - `V1__normalized_schema.sql` (1667 ms)
  - `V2__provider_mappings.sql` (70 ms)
  - `V3__email_verification.sql` (29 ms)
  - `V4__catalogue_lifecycle.sql` (73 ms)
  - `V5__local_stores.sql` (110 ms)
  - `V6__local_comparison_terms.sql` (34 ms)
  - `V7__online_comparison_evidence.sql` (45 ms)
- Initialized starter reference data:
  - 24 products, 27 variants, 7 product categories, 3 grocery platforms, 1 delivery location.
- Executed all 6 MySQL milestone tests against live MySQL 8:
  - `MysqlMilestone2Test` (1 test): PASSED
  - `MysqlMilestone3Test` (4 tests): PASSED
  - `MysqlMilestone4Test` (1 test): PASSED
  - Result: **6 / 6 passed, 0 failures, 0 skipped**.
- Combined with H2 tests (93), AI tests (6), and Frontend tests (35), all 140 automated assertions in the project pass with 100% success.

**Files modified:**
- `docs/TESTING_STATUS.md`
- `docs/CARTIQ_MASTER_CONTEXT.md`
- `docs/CHANGELOG.md`
- `documentation/CURRENT_PROGRESS.md`

---

## 2026-11-14 – Authentication Flow Clarification, User Provisioning & Frontend API Binding

**Milestone:** Milestone 2 (UI & Core Experience) / Milestone 3 (Backend & DB)

**Changes made:**
- Investigated `UNAUTHORIZED` message (`{"code":"UNAUTHORIZED","message":"Please sign in again."}`):
  - Located in `SecurityConfig.java` line 40 as the OAuth2 Resource Server entry point for protected endpoints.
  - Root URL `/` and protected endpoints (`/api/me`, `/api/workspace/**`, `/api/admin/**`, `/api/cart/**`) require a valid JWT bearer token.
  - Public endpoints (`/api/locations`, `/api/categories`, `/api/products`, `/api/docs`, etc.) are open and functional without authentication.
- Created `.env` in root with `EXPO_PUBLIC_API_URL=http://127.0.0.1:8080`, restarted Expo Metro bundler to bind the web frontend directly to the live backend.
- Verified registration and login flows:
  - Tested `POST /api/auth/register` with `test@cartiq.local`.
  - Tested `POST /api/auth/login` and verified token issuance.
  - Verified `GET /api/me` returns HTTP 200 with user profile when Authorization Bearer token is provided.
- Documented authentication usage for both web UI and REST API.

**Files created:**
- `.env` (`EXPO_PUBLIC_API_URL=http://127.0.0.1:8080`)

**Files modified:**
- `docs/CHANGELOG.md`
- `docs/CARTIQ_MASTER_CONTEXT.md`

---

## 2026-11-14 – API URL Normalization, MethodNotSupported Exception Handling & Service Stability

**Milestone:** Milestone 2 (UI & Core Experience) / Milestone 3 (Backend & DB)

**Changes made:**
- **Resolved `UNAUTHORIZED` (`{"code":"UNAUTHORIZED","message":"Please sign in again."}`) error:**
  - Root cause: Frontend `src/services/api.ts` defines all endpoint paths relative to `/api` (e.g. `/locations`, `/products`, `/auth/login`), while Spring Boot maps all endpoints under `/api`. When `EXPO_PUBLIC_API_URL` was configured as `http://127.0.0.1:8080`, frontend requests were directed to `http://127.0.0.1:8080/locations` rather than `http://127.0.0.1:8080/api/locations`. Because `/locations` did not match the permitted `/api/**` patterns, Spring Security treated it as a protected endpoint and returned 401 UNAUTHORIZED.
  - Fix: Normalized `API_URL` in `src/services/api.ts` to automatically append `/api` if not already present. Updated `.env` to `EXPO_PUBLIC_API_URL=http://127.0.0.1:8080/api`.
- **Resolved `INTERNAL` (`{"code":"INTERNAL","message":"The request could not be completed. Please try again."}`) error:**
  - Root cause: When a client sent an HTTP method not supported by an endpoint (e.g. GET on `@PostMapping("/auth/login")`), Spring MVC threw `HttpRequestMethodNotSupportedException`. In `ApiErrors.java`, `@ExceptionHandler(Exception.class)` intercepted this client-side 405 error and converted it to an HTTP 500 INTERNAL error.
  - Fix: Added explicit `@ExceptionHandler(HttpRequestMethodNotSupportedException.class)` in `ApiErrors.java` returning HTTP 405 `METHOD_NOT_ALLOWED` with the specific message (e.g., `Request method 'GET' is not supported`). Added `@ExceptionHandler(NoResourceFoundException.class)` returning HTTP 404 `NOT_FOUND`. Enhanced logging in `unexpected()` to output the HTTP method, URI, and exception details for any genuine internal errors.
- **Service Verification:**
  - Restarted Spring Boot backend (`http://127.0.0.1:8080`) and Expo web frontend (`http://localhost:8092`).
  - Verified GET `/api/auth/login` now cleanly returns `HTTP 405 METHOD_NOT_ALLOWED`.
  - Verified GET `/api/locations` returns `HTTP 200 OK` with 3 delivery locations.
  - Verified POST `/api/auth/demo` and subsequent `GET /api/me` with Bearer token return `HTTP 200 OK`.
  - Verified `GET /api/products/milk/comparison?locationId=blr-indiranagar&latitude=12.9716&longitude=77.5946&radiusKm=5&quantity=1&fulfillment=delivery` returns `HTTP 200 OK`.
  - Re-executed full test suites: 35 frontend tests passed (0 failures), 6 AI service tests passed (0 failures), 93 H2 backend tests passed (0 failures).

**Files modified:**
- `src/services/api.ts`
- `backend/src/main/java/com/smartcart/exception/ApiErrors.java`
- `.env`
- `docs/CARTIQ_MASTER_CONTEXT.md`
- `docs/CHANGELOG.md`
- `docs/TESTING_STATUS.md`
- `documentation/CURRENT_PROGRESS.md`

**Database changes:** None.

**Tests performed:**
- `npm run test` (35 passed, 0 failed).
- `npm run typecheck` (0 errors).
- `cd ai-service; .\.venv\Scripts\pytest` (6 passed, 0 failed).
- `cd backend; mvn test "-Dspring.profiles.active=test"` (93 passed, 0 failed, 6 skipped).
- Live HTTP curl checks against `/actuator/health`, `/health`, and Expo web on port 8092.

**Result:** All 3 application tiers running healthy and error responses normalized.

**Remaining work:** Milestone 4 preparation for controlled product import.

---

## 2026-11-14 – Frontend & Backend Cross-Origin Connectivity & Root Redirect

**Milestone:** Milestone 2 (UI & Core Experience) / Milestone 3 (Backend & DB)

**Changes made:**
- **Identified and resolved frontend-to-backend CORS block:**
  - When the browser at `http://localhost:8092` initiated requests to `http://127.0.0.1:8080/api/**`, preflight OPTIONS requests failed with `HTTP 403 Invalid CORS request` because port 8092 was missing from `smartcart.allowed-origins`.
  - Updated `SecurityConfig.java` to use `c.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*", "http://[::1]:*"))`, allowing all dynamic local development ports while strictly honoring `allowCredentials: true`.
  - Updated `application.yml` `allowed-origins` to include `http://localhost:8092` and `http://127.0.0.1:8092`.
- **Resolved root URL 401 UNAUTHORIZED:**
  - Added `"/"` to `permitAll()` in `SecurityConfig.java`.
  - Created `RootController.java`:
    - Browser visits (`Accept: text/html`) receive an automatic `HTTP 302 Found` redirect to the frontend at `http://localhost:8092`.
    - API / JSON requests receive `{"status":"UP", "name":"CartIQ Backend API", "frontend":"http://localhost:8092", "docs":"/api/docs"}`.
- **Verified End-to-End Connectivity:**
  - Preflight OPTIONS from `http://localhost:8092` now returns `HTTP 200 OK` with `Access-Control-Allow-Origin: http://localhost:8092` and `Access-Control-Allow-Credentials: true`.
  - Authenticated calls (`POST /api/auth/demo` and `POST /api/auth/login`) verified returning valid JWT tokens and user profiles to origin `http://localhost:8092`.
  - Catalogue endpoints (`/api/locations`, `/api/categories`, `/api/meta`, `/api/products`) return HTTP 200 with complete JSON payloads.
  - Re-verified test suite: 35 frontend tests, TypeScript check, 6 AI tests all pass with 0 errors.

**Files created:**
- `backend/src/main/java/com/smartcart/controller/RootController.java`

**Files modified:**
- `backend/src/main/java/com/smartcart/security/SecurityConfig.java`
- `backend/src/main/resources/application.yml`
- `docs/CARTIQ_MASTER_CONTEXT.md`
- `docs/CHANGELOG.md`
- `docs/TESTING_STATUS.md`
- `documentation/CURRENT_PROGRESS.md`

**Database changes:** None.

**Tests performed:**
- Tested OPTIONS preflight with Origin `http://localhost:8092` (passed: 200 OK).
- Tested GET `/api/locations` with Origin `http://localhost:8092` (passed: 200 OK).
- Tested POST `/api/auth/demo` with Origin `http://localhost:8092` (passed: 200 OK, token issued).
- Tested POST `/api/auth/login` with `test@cartiq.local` from Origin `http://localhost:8092` (passed: 200 OK).
- Tested browser redirect on `GET /` with `Accept: text/html` (passed: 302 Found -> `http://localhost:8092`).
- `npm run test` (35 passed, 0 failed).
- `npm run typecheck` (0 errors).
- `cd ai-service; .\.venv\Scripts\pytest` (6 passed, 0 failed).

**Result:** Frontend and backend are connected, CORS is enabled for web browser clients, and visiting port 8080 automatically directs users to the frontend web app on port 8092.

**Remaining work:** Milestone 4 preparation for controlled product import.

---

*All future development agents must record every meaningful change here before completing their tasks.*
