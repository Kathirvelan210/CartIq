# TESTING_STATUS.md

## Weekly recipe variety · 20 November 2026

Frontend tests passed (83/83), including identical suggestions, seven distinct suggestions and balanced rotation with smaller pools. TypeScript and Expo Web export passed. FastAPI recipe tests passed (36/36). Chrome on localhost:8092 used intercepted API responses with seven copies of Jeera rice. It displayed one meal, rotated seven days after a second recipe was included without another request, and preserved a manually added meal when suggestions were replaced. No live Gemini or retailer requests were used.

## Slow recipe-generation regression · 20 November 2026

FastAPI returned a valid draft after an 11-second loopback provider delay with one dispatch. Spring Boot returned a recipe after a 21-second private-service fixture delay. Chrome kept the loading message visible and rendered instructions after a 21-second mocked backend response with one submission. FastAPI recipe tests: 36 passed. Spring RecipeIntelligenceTest: 8 passed against isolated H2. Frontend: 80 passed, TypeScript and Expo Web export passed. No Google or retailer calls were made. The original timeout did not establish Google acceptance or billing.

## Recipe assistant · 20 November 2026

`npm run typecheck` and the Expo Web export passed. `npm test` passed 80 tests. The isolated Spring Boot `RecipeIntelligenceTest` passed 7 tests, including per-meal on-hand quantities and invalid ingredient indices. FastAPI `pytest -q test_recipe_generation.py` passed 35 mocked tests. A Chrome fixture workflow at 714 × 667 verified one generation submission, measured ingredient checkboxes, removing an on-hand item from the shopping list and rendered cooking instructions. These checks used zero live model or retailer requests.

## Phase 10.9 · Gemini 3.8 Flash compatibility (19 November 2026)

Static review found that the 3.8 migration requires omitting candidateCount. Zero live requests or retries; Chrome recipe rendering was not tested, and no screenshot or provider receipt was produced. [Phase 10.9 evidence](evidence/phase10-9/README.md)

## Phase 10.5 · Authorized live Gemini attempt (19 November 2026)

The single live recipe submission produced upstream 503 and CartIQ 502 (GEMINI_SERVER). Chrome displayed the error in 11.312 seconds. No draft and no retry. Post-request offline checks passed 35 FastAPI, 68 frontend and 8 Spring tests, with zero failures or skips. [Live receipt and offline test evidence](evidence/phase10-5/README.md)

## Phase 10.5 · Gemini preflight (19 November 2026)

Live Gemini recipe verification was blocked before dispatch. Spring on port 8080 and FastAPI on port 8001 refused connections. The offline recipe suite passed 35/35 and the frontend suite passed 68/68, with zero failures or skips. [Phase 10.5 evidence](evidence/phase10-5/README.md)

## Phase 10.4 · Local Gemini diagnostic verification (19 November 2026)

The restarted local Spring and FastAPI services passed health checks. The final Chrome run passed all nine offline Gemini diagnostic scenarios, including a real loopback refusal and read timeout. Each action produced one dispatch. Frontend 68/68, FastAPI 53/53 and focused Spring/H2 13/13 passed with zero skips. Expo Web export passed. [Commands and per-case evidence](evidence/phase10-4/README.md)

## 2026-11-19 · Gemini 502 diagnosis

Offline diagnosis of the first Gemini 502 recovered the original Spring access record. FastAPI 53/53 and focused Spring/H2 13/13 passed with zero failures or skips. The diagnosis made no provider requests. Google acceptance, usage and billing for the original attempt remain unknown. [Incident trace and tests](evidence/phase10-2/GEMINI_502.md)

## 2026-11-19 · Local Gemini activation

Frontend 68/68, FastAPI 38/38 and focused Spring/H2 9/9 passed with zero failures or skips. TypeScript and Expo Web export passed. Four loopback Chrome checks verified rendering of a recipe substitution, a seven-day plan, the assistant's pantry answer and a repeat request's HTTP 429 message. Model responses were mocked. The separately launched operator runtime passed authenticated FastAPI readiness and Spring health. No live Gemini, retailer or geocoder call, credit use or shared MySQL access occurred. [Commands and redacted evidence](evidence/phase10-2/README.md)

## 2026-11-18 — Phase 10.1 physical Android GPS

Android 16 / Expo Go 54.0.8 over USB → authenticated Spring Boot local profile → isolated H2 V20. Four confirmed positions returned HTTP 200 for the correct account, including a 2,000 m coarse fix. The final device capture time matched the HTTP request, with 25.381 m accuracy before UI rounding. Permission denial, Android settings recovery, foreground resume and cold restart were exercised. An earlier coarse request timed out; the UI surfaced it and no selection was submitted for that attempt.

Frontend 68/68 and focused Spring 4/4 passed with zero failures or skips. TypeScript and the offline Android export passed. Standalone signed Android, emulator, Chrome and external PIN lookup were not exercised in this run. Zero retailer, geocoder or AI calls and zero credits consumed. Shared MySQL was untouched. [Exact commands and evidence](evidence/phase10-1/TEST_RESULTS.md)

## Phase 10 verification

November 18, 2026. The standard Spring suite ran with Java 17, Spring Boot 3.5.16 and the test profile on isolated H2. Source copies for Maven and the exports were placed under `%USERPROFILE%\CartIQ-data\phase10-1794993652281`. External keys, provider flags and database environment overrides were removed from the Maven test shell. The package and disposable MySQL checks used the local verified V19 predecessor (SHA-256 starts with `114cb70f`).

| Check | Command or operation | Actual result |
|---|---|---|
| Frontend and TypeScript | `npm.cmd test` and `npm.cmd run typecheck` | 65 tests passed, zero skipped. TypeScript passed. |
| Backend unit, mocked and H2 integration | `mvn.cmd -o -B -Dspring.profiles.active=test -DargLine=-Duser.home=. test` | 275 discovered, 263 passed, 12 gated skips, zero errors or failures. Ten skips are dedicated MySQL or migration suites. Browser and captured-body tests ran separately. |
| Historical Blinkit response | `mvn.cmd -o -B -Dtest=QuickCommerceProviderTest#normalizesTheOperatorSuppliedBlinkitBodyWithoutRetainingIt -Dspring.profiles.active=test -DargLine=-Duser.home=. test` | One passed. Original supplied JSON decoded to 24 products. HTTP status and credits header were mocked. No live replay. |
| Python and Gemini contracts | From ai-service, `.\.venv\Scripts\python.exe -m pytest -q` | 31 passed, one Starlette TestClient deprecation warning. No model call. |
| App exports | `npx.cmd --no-install expo export --platform web --max-workers 2 --output-dir <scratch>/web` and `npx.cmd --no-install expo export --platform android --platform ios --max-workers 2 --output-dir <scratch>/native` | Web and both native Hermes bundle exports succeeded with Expo offline, dotenv and telemetry disabled. The web build used same-origin `/api`. Native used an HTTPS loopback build-time URL for verification only. |
| Browser and real Spring endpoints | `mvn.cmd -o -B -Dtest=MarketplaceBrowserTest -Dspring.profiles.active=test -DargLine=-Duser.home=. test` with the runner environment below | One gated integration test passed at 1440×900, 1366×768 and 390×844. Chrome emulated GPS, Spring used isolated H2 and the provider was mocked. |
| MySQL migration | `node configuration/Test-IsolatedMySql.cjs --artifact <V20.jar> --previous-artifact <verified-V19.jar> --mysql-bin "C:/Program Files/MySQL/MySQL Server 8.0/bin" --output-dir <scratch>/mysql-release --retailer-operations` | MySQL 8.0.44 validated V1–V20. Backup, restore, application rollback and roll-forward passed. Disposable operational checks returned 200 authenticated and 401 anonymous. |
| External verification | No provider dispatch | Zero Google geocoding, Gemini and retailer requests. The actual local credit ledger remained byte-identical at 92 settled credits, zero pending, 12 attempts available and floor 77. Shared MySQL was untouched. Physical GPS, signed builds, account-specific Gemini entitlement, current retailer serviceability and genuine current prices remain unverified. |

To reproduce Chrome, set `CARTIQ_MARKETPLACE_BROWSER=true`, `CARTIQ_BROWSER_RUNNER` to the absolute path of `configuration/Test-MarketplaceBrowser.cjs`, `CARTIQ_PLAYWRIGHT_PATH` to the installed playwright-core package, `CARTIQ_TEST_WEB` to the same-origin Expo export and `CARTIQ_BROWSER_EVIDENCE` to a writable test directory. Chrome must be installed. The harness uses synthetic users and fixture mappings, blocks off-origin traffic and supplies the ephemeral shopper credential directly to its child.

Chrome recorded three successful position selections, five fixture searches, one exact-listing cart add, one cart quote check and one weekly preview. Reload kept the line and another account had an empty cart. Initial runs exposed stale test assumptions about confirming positions and handling a new account's privacy dialog, plus a mobile actionability timeout. The final rerun passed without changing authorization or bypassing user consent. An earlier rehearsal with a different historical V19 JAR failed migration. Its V19 SQL differed from the verified predecessor. No Flyway history was repaired or rewritten.

[Network evidence](evidence/phase10/browser-evidence.json) · [Desktop marketplace](evidence/phase10/1440x900-marketplace.png) · [Mobile marketplace](evidence/phase10/390x844-marketplace.png) · [Weekly plan](evidence/phase10/1440x900-plan.png) · [MySQL report](evidence/phase10/mysql-rehearsal.json) · [Implementation files](evidence/phase10/README.md)


> Earlier verification entries follow.

## Phase 9.5 Google PIN locality matching

- `mvn.cmd -o -B '-Dtest=ShoppingGeocoderTest,GoogleGeocodingTest,GoogleLocationPickerSecurityTest' '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' test` passed 30 tests with zero failures or skips in the isolated `%USERPROFILE%\CartIQ-data\phase95-140136056` source copy. Mocked responses covered PIN `641008` with several localities, unnamed results, postal town, competing PIN and state evidence, reverse GPS coordinates, quota exhaustion, timeouts and diagnostic redaction. The security test used in-memory H2 2.3.232 at Flyway V19.
- `npm.cmd run typecheck` passed. `npm.cmd test` passed 63 tests, including explicit selection of a same-PIN locality and refusal to select when evidence is incomplete or conflicting. `node --test configuration/review-pilot-location.test.cjs` passed four tests. Expo Web returned the updated picker bundle with HTTP 200.
- The original live Google payload was unavailable. No live Google or retailer request was sent, no area was registered, and the QuickCommerce ledger checksum was unchanged. Shared MySQL was untouched. Restart the local backend to load the parser and diagnostic updates. [Pre-dispatch findings](MARKETPLACE_PREDISPATCH.md#phase-95-google-pin-locality-selection)

## Local Google Maps location picker

- `mvn.cmd -o -B '-Dtest=ShoppingGeocoderTest,GoogleGeocodingTest' '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' test` passed 27 tests in an isolated source copy. HTTP mocks verified the picker options, consent requirement, forward and reverse results, postcode-only city responses, and previous Nominatim behavior.
- `mvn.cmd -o -B '-Dtest=GoogleLocationPickerSecurityTest' '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' test` passed against in-memory H2 2.3.232 at Flyway V19. The Spring Security filter chain required sign-in and consent and kept the diagnostic admin routes restricted.
- `npm.cmd run typecheck` and `npm.cmd test` passed. The frontend suite ran 61 tests with zero failures or skips, including agreement on PIN area, conflicting evidence, browser geolocation permission errors, and session-only Google results. Expo's running web bundle returned HTTP 200 and contained the updated picker.
- The running Google demo backend still served the prior options contract. Restart on port 18094 through the private key prompt to load the change. No live Google or retailer lookup was sent, shared MySQL was untouched, and the QuickCommerce ledger checksum was unchanged.

## Phase 9.4 Google Geocoding API v4 demo

- `mvn.cmd -o -B '-Dtest=ShoppingGeocoderTest,GoogleGeocodingTest' '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' test` passed 24 tests with zero failures or skips in the isolated `%USERPROFILE%\CartIQ-data\phase94-132253705` source copy. The Google cases mocked HTTP and covered both v4 routes, header-only authentication, multiple Indian PINs, country and PIN filtering, conflicting reverse results, redacted credential errors, quota cooldown, timeout and local/test-only configuration. The targeted suites did not use a database.
- `node --test configuration/review-pilot-location.test.cjs` passed four tests. The in-memory review fixture rejected ambiguous evidence and printed only the verdict. `npm.cmd run typecheck` passed and `npm.cmd test` passed 59 tests.
- The Google key was absent in the current process. PowerShell launcher parsing passed. A real Google launch and live response validation remain pending. No external geocoding or retailer calls, reservations, credit use, authorizations, location registrations or shared MySQL access occurred. The retailer ledger checksum matched the prior checkpoint. [Operator handoff](MARKETPLACE_PREDISPATCH.md#phase-94-google-geocoding-api-v4-demo)

## Phase 9.3 public Nominatim readiness

| Command | Actual result |
| --- | --- |
| `mvn.cmd -o -B '-Dtest=ShoppingGeocoderTest' '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' test` | 16 passed, zero failures or skips. Mock Spring MVC, mocked upstream HTTP and loopback fixtures covered PIN and reverse parsing, India and postcode filtering, public input restrictions, contact handling, caching, rate limits, provider errors and timeout. Run from an isolated backend source copy without a database connection. Evidence at `%USERPROFILE%\CartIQ-data\phase93-130043497\target\surefire-reports\com.smartcart.integration.ShoppingGeocoderTest.txt`. |
| `node --test configuration/review-pilot-location.test.cjs configuration/release-check.test.cjs` | 8 passed, zero failures, one gated HTTPS smoke check skipped without a local TLS fixture. Address evidence used synthetic fixtures. The Compose checks parsed configuration statically. |
| `npm.cmd run typecheck` and `npm.cmd test` | Type check passed. 59 frontend tests passed, zero failures or skips. |

The public endpoint and operator contact were absent from the inspected Windows process, user and machine settings. The location gate remains blocked while contact, separate lookup approval and real matching area evidence are outstanding. No external geocoding, retailer or model calls ran. No PIN registration, credit usage, reservation or authorization occurred. The retailer ledger SHA-256 matched before and after. Shared MySQL was untouched. [Pre-dispatch report](MARKETPLACE_PREDISPATCH.md)

## Phase 9.2 operator-supplied PIN

- Recorded `641008` for Coimbatore at `10.942972, 76.953417` as operator-supplied, unverified input. The PIN passed syntax validation. `CARTIQ_GEOCODER_URL` was missing from the current Windows process, user and machine environments. No geocoder request ran.
- Read-only H2 inspection confirmed V19, zero area rows for `641008`, zero Coimbatore areas and zero pilot authorizations. Prepared the existing admin registration payload and deferred the read-only duplicate verdict until evidence ties the PIN to the exact point and delivery area. The ledger digest still matched Phase 9.1. Mapping and permission approvals, live calls, credit use and shared MySQL changes remained at zero. [Registration draft and evidence gate](MARKETPLACE_PREDISPATCH.md#draft-location-registration)

## Pre-dispatch blockers phase 9.2

- In the isolated Phase 9 backend copy, `mvn.cmd -o -B '-Dtest=ShoppingGeocoderTest,ProductMatchingTest' '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' test` passed 23 tests, with zero failures and skips. The geocoder cases used mocked or loopback fixtures. A separate JShell invocation of `ProviderSyncService.reviewMapping` against the existing read-only H2 rehearsal returned `TITLE_UNCONFIRMED` for the actual historical SKU `176` identity.
- Read-only SQL confirmed H2 2.3.232 at V19, the discovery claim guard, zero Coimbatore catalogue areas, zero approved SKU `176` mappings and zero pilot authorizations. Inspection of the existing controller, ledger and V19 guard confirmed account scope, one use, expiry, duplicate suppression and the pending-billing stop rule. The exact proposed fingerprint awaits the approved PIN.
- At the initial Phase 9.2 review, the operator PIN, authorized geocoder base URL, accepted scope reference, and fresh signed-in dashboard balance were unavailable. The ledger digest matched Phase 9.1 at 92 settled, zero pending, 12 attempts remaining and a floor of 77. There were zero live calls, authorizations, reservations, credit debits or shared MySQL modifications. [Current blocker table and operator handoff](MARKETPLACE_PREDISPATCH.md#phase-93-blocker-decision-and-operator-handoff)

## Live marketplace readiness phase 9.1

- The isolated file-backed H2 2.3.232 rehearsal started with the `local` profile and migrated V1–V19. Read-only inspection after shutdown found the V19 claim guard, no Coimbatore area, no approved Blinkit SKU `176` mapping and zero pilots or authorizations. The local PIN endpoint returned 400 for an invalid PIN and 503 for a valid-format PIN without a configured geocoder.
- Twelve offline geocoder tests and one historical Blinkit body normalization test passed. The current matcher rejected immediate approval of SKU `176` on title evidence. The ledger hash remained unchanged at 92 settled credits, zero pending reservations and 12 remaining attempts. Zero live requests, credit usage or shared MySQL access. [Pre-dispatch report](MARKETPLACE_PREDISPATCH.md)

## Real-time grocery marketplace phase 9

- `npm.cmd run typecheck`, `npm.cmd test` (59 tests), and `npm.cmd run build:web -- --clear --output-dir <scratch>/web` passed with `EXPO_NO_DOTENV=1` and `EXPO_PUBLIC_API_URL=/api` for the export. All 15 categories have validated query suggestions.
- In a disposable backend copy, `mvn.cmd -o -B '-Dtest=Phase9BrowserTest,ControlledPilotTest,QuickCommerceProviderTest,*ShoppingGeocod*Test,LiveOfferIntegrationTest' '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' test` passed 65 tests with zero skips or failures. The scratch browser harness supplied the Chrome case. `CARTIQ_CAPTURED_BLINKIT_BODY` pointed to the existing operator attachment. The captured historical body normalized all 24 products with mocked HTTP metadata. H2 applied V1–V19 and exercised the claim guard, account scoping, expiry, de-duplication, platform failures, settlement and basket math.
- Chrome 157 passed at 1440 × 900, 1366 × 768 and 390 × 844 using the Expo export, real loopback Spring Boot HTTP and mocked retailer and geocoder adapters. Desktop exercised Fruits, Vegetables, Milk and Dairy, Snacks and typed `Amul Taaza milk`. Five fixture searches, one cart write, and the weekly preview returned HTTP 200. Reload reused the cached milk result and retained the cart. The peer saw neither the location nor the cart. The initial browser run found an unrelated category query could clear a fresh anchored pack. The fix retains the quote if another search omits that SKU and revalidates the anchor if it is returned. [Browser evidence and screenshots](evidence/marketplace-phase9/README.md)
- `node configuration/Test-IsolatedMySql.cjs --artifact <V19.jar> --previous-artifact <V18.jar> --mysql-bin <MySQL-bin> --output-dir <scratch>/mysql-final --retailer-operations` passed on disposable MySQL 8.0.44. The final V19 schema, backup and restore, rollback and roll-forward, and authenticated readiness passed. [MySQL report](evidence/marketplace-phase9/mysql-rehearsal.json)
- Local `CARTIQ_GEOCODER_URL` was missing. PIN and multiple locality behavior were verified offline. Live PIN coverage, genuine category response counts, and eligible comparison totals remain pending a licensed geocoder and separately authorized retailer searches.
- External requests, live retailer credits, and shared MySQL access remained at zero. The persistent ledger hash was unchanged at 92 settled credits, zero pending reservations, 12 remaining experiment attempts and a floor of 77. [Live request proposal](CONTROLLED_GROCERY_PILOT.md#phase-9-marketplace-pilot)

## Chrome visual and real backend integration

- 46 direct HTTP checks passed against Spring Boot in the `local` profile with isolated H2 2.3.232 and Flyway V18. Google Chrome passed 57 visual workflows across desktop, laptop and mobile viewports, plus three admin retests. All 387 distinct recorded request IDs matched Spring Boot logs. The detailed report links 60 screenshots and the sanitized network records.
- Read-only H2 queries confirmed cart update and deletion, account-owned pantry and weekly plans, no duplicate rows and clean peer isolation. The sample-mode control returned 180 synthetic milk history points. The permission-cleared API excluded all of them and forecast eligibility was insufficient.
- TypeScript and all 57 frontend tests passed. Ten FastAPI recipe tests passed through mocked model transport with one pre-existing deprecation warning. Actual offline website generation returned the expected 503. Real priced retailer coverage remains pending. No external provider calls, credits, ledger changes or shared MySQL access. [Visual API integration report](VISUAL_API_INTEGRATION_REPORT.md)

## First live pilot readiness phase 8.1

- In an isolated backend copy, `mvn.cmd -o -B '-Dtest=ControlledPilotTest,LiveOfferIntegrationTest,QuickCommerceProviderTest,QuickCommerceAdminTest' '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' test` reported **53 passed, zero failures and one skip**. The optional captured Blinkit body was unavailable. H2 test contexts applied V18. The new case rejects changes to retailer, location, coordinates, operation and pack, and rejects an expired authorization before and after issuance without calling the provider. Existing cases cover account binding, replay, settlement, stale and conflicting offers, and basket repricing. Adapter tests use mocked transport and temporary credit ledgers to verify reservation, the 77-credit floor, duplicate and concurrent request suppression, settlement, and persistence of an uncertain reservation.
- Three synthetic loopback Expo Web checks passed using `node browser-product-to-plan.cjs`, `node browser-pilot.cjs` and `node browser-offer-origins.cjs` from the Phase 8 and 8.1 evidence directories. They exercised Product Details to planning cart to weekly basket, one explicit refresh with a synthetic receipt, and distinct **Live response**, **Recorded offer** and **Expired offer** labels. The browser blocked off-origin requests.
- A read-only copy of the local H2 file showed Flyway V11, the Amul Taaza 1-litre catalogue pack, no Coimbatore area and no approved QuickCommerceAPI mappings. The proposal still requires its approved area, PIN, mapping decision, written permissions and separate human authorization. This readiness check did not apply migrations to the local file or repeat the Phase 8 MySQL rehearsal.
- No live retailer, Gemini or OpenAI requests, credit use, key changes or live authorization occurred. The persistent ledger's SHA-256 was unchanged. Its recorded state was 92 settled credits, zero pending reservations, 12 remaining experiment attempts and a floor of 77. The dashboard balance was not verified in this check. Shared MySQL was untouched.

Evidence is under `%USERPROFILE%/CartIQ-data/phase8-readiness-055652`. [First pilot request proposal](CONTROLLED_GROCERY_PILOT.md#phase-81-first-search-proposal)

## Controlled grocery pilot phase 8

- `mvn.cmd -o -B package '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'` in the isolated `release` copy passed 240 tests, with 11 existing opt-in skips and zero failures. Three new H2/MockMvc cases use a mocked provider and cover default-off configuration, permissions, role and account scoping, exactly-once authorization, confirmed debit, experiment limit, uncertain outcomes, basket repricing and zero observation writes. Fixture responses and balances are synthetic.
- `npm.cmd run typecheck` and `npm.cmd test` passed, with 57 tests. The Expo fixture export used `EXPO_NO_DOTENV=1`, `EXPO_PUBLIC_API_URL=/api` and `npm.cmd run build:web -- --clear --output-dir <scratch>/web`. `node <scratch>/browser-pilot.cjs` and `node <scratch>/browser-product-to-plan.cjs` passed against a synthetic loopback API with off-origin requests blocked. Checks covered the one-use refresh and receipt, opening Product Details, adding the exact pack to the planning cart and weekly plan, and a partial basket subtotal with unknown fees.
- In `ai-service`, `./.venv/Scripts/python.exe -B -m pytest -q -p no:cacheprovider` passed 28 tests with the existing TestClient warning. The initial run found a time-dependent test fixture with a future intake time. The corrected fixture still exercises late arrivals and works before and after UTC midnight. Production forecast logic was untouched.
- `node configuration/Test-IsolatedMySql.cjs --artifact <V18.jar> --previous-artifact <V17.jar> --mysql-bin <MySQL-8-bin> --output-dir <scratch>/mysql-verified --retailer-operations` passed on disposable MySQL 8.0.44. Flyway upgraded and validated V1–V18. Backup and restore, V17 rollback and V18 roll-forward passed. The disabled pilot fixture survived both package switches. The first rehearsal applied V18, then failed inserting a pilot because the synthetic retailer reference row was missing. Adding the prerequisite to the isolated rehearsal fixed it.
- No live retailer or model request was sent and no API credit was used. The existing ledger remained at 92 settled credits, zero pending reservations, 12 experiment attempts and a 77-credit floor. Shared MySQL was untouched. Live enrolment, genuine shopper outcomes and permission evidence remain pending. [Pilot report](CONTROLLED_GROCERY_PILOT.md)

Evidence is under `%USERPROFILE%/CartIQ-data/phase8-053757`. The 11 opt-in skips cover gated MySQL suites and an optional provider response capture.

## Grocery data quality and retailer operations phase 7

| Environment | Command and evidence |
| --- | --- |
| Spring Boot and H2 | `mvn.cmd -o -B package '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'` in the isolated candidate copy. Java 17 and H2 2.3.232. 237 passed, 11 existing opt-in skips and zero failures. Verified coverage and filters, evidence blocking, review and mapping revisions, protected existing observations, scoped live prices, expiry, diagnostics classifications and account isolation. |
| Expo and synthetic browser | `npm.cmd run typecheck` and `npm.cmd test` passed, with 57 tests. Set `EXPO_NO_DOTENV=1` and `EXPO_PUBLIC_API_URL=/api` in the shell, then run `npm.cmd run build:web -- --clear --output-dir <scratch>/web`. `node <scratch>/browser-operations.cjs` verified coverage, filters, sanitized failure reasons, mapping history, blocked approval and explicit rejection. `node <scratch>/browser-offers.cjs` verified the shopper refresh flow and basket reprice with a partial subtotal. Both used loopback fixtures with off-origin requests blocked. The original export retained the configured machine API in Metro's cache. Clearing it and using same-origin configuration fixed the browser smoke. |
| FastAPI | `./.venv/Scripts/python.exe -B -m pytest -q -p no:cacheprovider` from `ai-service`. 28 passed with the existing Starlette TestClient warning. |
| Disposable MySQL | `node configuration/Test-IsolatedMySql.cjs --artifact <Phase-7.jar> --previous-artifact <Phase-6.jar> --mysql-bin <MySQL-8-bin> --output-dir <scratch>/mysql --retailer-operations`. MySQL 8.0.44, Flyway V1–V17 and zero new migrations. Backup, restore, rollback and roll-forward passed. Authenticated coverage returned 200 with its expected synthetic scope. Anonymous access returned 401. |
| Evaluation | `node configuration/Report-RecommendationBaseline.cjs <surefire-reports>` recorded 8 of 8 passing synthetic cases. No model was trained and the frozen product benchmark remained unchanged. |
| External safety | Zero live retailer, Gemini or OpenAI calls. Shared MySQL was untouched. The unchanged ledger held 92 settled credits, zero pending reservations, 12 experiment attempts, and a protected floor of 77. |

Evidence is under `%USERPROFILE%/CartIQ-data/phase7-044236`. The eleven opt-in skips are gated MySQL suites and an optional captured provider response. The disposable MySQL rehearsal is independent of those skips. No deployment, native-device verification or live-provider verification ran. [Operations](OPERATIONS_RUNBOOK.md#retailer-data-quality-triage)

Implementation files include `RetailerCoverage.java`, `RetailerOperationsController.java`, `RetailerDiagnostics.java`, `OfferFreshness.java`, `ProviderController.java`, `ProviderSyncService.java`, `QuickCommerceProvider.java`, `CurrentOffers.java`, `LiveOffers.java`, `RetailerOperations.tsx` and `retailer-operations.ts`. The shopper scope, quote presentation and comparison changes are in `src/services/api.ts`, `src/store.tsx`, `SmartHub.tsx`, `ShoppingIntelligence.tsx`, `UnifiedShopping.tsx`, `LivePackOffers.tsx` and `RetailerComparison.tsx`. The isolated MySQL harness and recommendation baseline reporter are under `configuration`.

## Live grocery offers phase 6

| Check | Result |
| --- | --- |
| Spring Boot and H2 | `mvn.cmd -o -B package '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'` in the isolated `candidate` source copy passed 232 tests with 11 existing opt-in skips and zero failures. The HTTP fixture tests verified scope validation, JWT, exact SKU mapping, no observation writes, stale and conflicting packs, account isolation, basket repricing and unavailable item details. Adapter tests used mocked transport for all three platform identifiers, numeric/string IDs, unknown fields, failed-platform envelopes, partial success, duplicate and uncertain requests, protected balance and charged/uncharged correlation references. The first run caught two previous ledger-length expectations that needed the new evidence row. The corrected full suite passed. |
| Expo | `npm.cmd run typecheck`, `npm.cmd test` (57 passed) and `npm.cmd run build:web -- --output-dir <scratch>/web-candidate` passed. `node <scratch>/browser-offers.cjs` used a synthetic loopback API. It checked no dispatch on screen mount, explicit retailer selection, successful Blinkit and failed Swiggy side by side, a verified offer, disabled repeat, automatic local planner repricing and a partial subtotal with unknown fees. Browser requests to other origins were blocked. |
| FastAPI | From `ai-service`, `./.venv/Scripts/python.exe -B -m pytest -q -p no:cacheprovider` passed 28 tests with the existing Starlette TestClient deprecation warning. |
| Disposable MySQL | `node configuration/Test-IsolatedMySql.cjs --artifact <Phase-6.jar> --previous-artifact <Phase-5.jar> --mysql-bin <MySQL-8-bin> --output-dir <scratch>/mysql` passed on MySQL 8.0.44. Both packages used V1–V17. Flyway validation, readiness, backup and restore, package rollback and roll-forward passed. V17 fixture records survived. Phase 6 added no migration. |
| External calls and ledger | Zero live retailer requests, Gemini or OpenAI requests, credit debits, retained provider offers and shared MySQL access. The existing ledger hash matched before and after. It remained at 92 settled credits with no pending reservation, 12 experiment attempts and a floor of 77. |

Evidence is under `%USERPROFILE%/CartIQ-data/phase6-000439` in `backend-final.log`, `frontend.log`, `fastapi.log`, `web-candidate.log`, `browser.log`, and `mysql.json`. The eleven skips cover gated MySQL suites and an optional captured Blinkit response. Prior Blinkit and Zepto live results remain historical. Swiggy remains unresolved upstream and the new pilot was not enabled against the provider. [Runbook](OPERATIONS_RUNBOOK.md#quickcommerce-transient-offer-pilot)

## Unified shopping intelligence phase 5

| Check | Command and outcome |
| --- | --- |
| Spring Boot unit and integration | `mvn.cmd -o -B package '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'` from the isolated source copy. Java 17.0.12 and H2 2.3.232. 229 passed, 11 opt-in skipped, zero failures or errors. Five new workflow cases cover scaling and consolidation, pantry and account isolation, compatible units, single and split basket totals, budget, stale and unavailable stock, missing fees, local intent routing, feedback privacy and revisioned plans. One additional unit test uses synthetic forecasts to check exact offer matching and the deterministic fallback. V1–V17 applied on H2. The first compile found a type-use annotation on a qualified Java type and the import correction passed. |
| TypeScript and frontend | `npm.cmd run typecheck` and `npm.cmd test`. 57 passed, zero failures or skips, including exact cart pack import, budget input and freshness expiry. |
| Expo Web and browser | `npm.cmd run build:web -- --output-dir <scratch>/web-candidate` and `node <scratch>/browser-weekly-h2-final.cjs` passed. Headless Chrome used the exported bundle, local Spring Boot and disposable file-backed H2. The sample account selected and scaled meals, confirmed pantry stock, compared the plan, submitted feedback, asked for recipes and timing without a forecast, and reloaded saved inputs. The browser blocked off-origin requests. |
| FastAPI and offline data contracts | From `ai-service`, `./.venv/Scripts/python.exe -B -m pytest -q -p no:cacheprovider`. Python 3.12.10, 28 passed, zero failures and one existing Starlette TestClient deprecation warning. Four added tests validate permission evidence, digests, split chronology and cross-cohort leakage using synthetic metadata. No dataset was trained. |
| Disposable MySQL | `node configuration/Test-IsolatedMySql.cjs --artifact <V17.jar> --previous-artifact <V16.jar> --mysql-bin <MySQL-8-bin> --output-dir <scratch>/mysql-final` passed on MySQL 8.0.44. Flyway upgraded V16→V17 and validated V1–V17 before and after restoration. The V17 package reached readiness on the restored database. Synthetic V17 plan and feedback records persisted after restore, V16 package rollback and V17 roll-forward. |
| Live and production | No live retailer, Gemini or OpenAI verification, paid external calls, orders or production deployment. No shared MySQL access or modification. The QuickCommerce ledger digest matched before and after. It held 92 settled credits, zero pending reservations, 12 experiment attempts and the 77-credit floor. |

The 11 Java skips were existing opt-in MySQL tests and an optional operator-supplied Blinkit capture. The disposable MySQL rehearsal is separate from those tests. Reproducible logs, browser script, JAR and Web export are under `%USERPROFILE%/CartIQ-data/phase5-222614`. Native devices and genuinely authorized training or retailer coverage need separate verification. [Weekly plan behavior](PERSONALIZED_RECOMMENDATIONS.md#unified-shopping-plan) · [Model evidence requirements](AI_MODEL_ROADMAP.md#unified-recommender-evaluation-contracts)

## Bulk-buy and personalized grocery intelligence phase 4

- Backend: `mvn.cmd -o -B package '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'` in an isolated source copy passed 223 tests with 11 opt-in tests skipped and zero failures. Five new Spring MVC/H2 tests cover verified pack selection, storage and use-by limits, pantry and location isolation, formulation conflicts, stale and unknown stock, explicit dietary evidence, fee completeness, exact-quantity alternatives and budget-constrained assistant suggestions. Fixture prices and stock were synthetic. H2 applied V16.
- Frontend: `npm.cmd run typecheck`, `npm.cmd test` (54 passed), and `npm.cmd run build:web -- --output-dir <scratch>/web-final` passed. `node <scratch>/browser-shopping-smoke.cjs` exercised the three Smart Tools tabs on a fixture API, including missing prices and known stock. `node <scratch>/browser-h2-smoke.cjs` then passed against the Expo Web build, a separate local Spring Boot process and disposable file-backed H2. It covered authenticated household settings that persisted after reload, exact-pack advice, personalized recipes and one-time basket budgets using marked sample data. The initial full-stack smoke timed out waiting for Spring health. It passed with a longer startup deadline. See `browser.log`, `browser-h2-verified.log` and the linked Spring log in that run's directory.
- MySQL: `node configuration/Test-IsolatedMySql.cjs --artifact <V16.jar> --previous-artifact <V15.jar> --mysql-bin <MySQL-8-bin> --output-dir <scratch>/mysql` passed on disposable MySQL 8.0.44. Verified Flyway through V16, Spring readiness, backup/restore, V15 rollback and roll-forward. See `mysql-rehearsal.json`. The preference API behavior was exercised separately on H2.
- FastAPI: `./.venv/Scripts/python.exe -B -m pytest -q -p no:cacheprovider` from the `ai-service` directory passed 24 tests with the existing Starlette TestClient deprecation warning. The system Python lacked pytest and the virtual environment supplied it.
- Evidence and local smoke commands are saved under `%USERPROFILE%/CartIQ-data/phase4-215353`. No live provider requests, model training, or price history writes occurred. The credit ledger held 92 settled credits, zero pending reservations and 12 experiment attempts left, with a protected floor of 77 and the same SHA-256 before and after. Shared MySQL and production data were untouched.

## Historical grocery price dataset acquisition phase 3B

- Backend `mvn.cmd -o -B package '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'` used the isolated source copy and passed 218 tests with 11 opt-in skips and zero failures. The acquisition fixtures exercised blocked intake without a registry entry, preview without writes, simulated and out-of-scope rows, original-evidence permission, exact-hash replay, CSV quoting, historical availability evidence, coverage gaps, audit retrieval and revocation. All test prices were synthetic. See `backend-verified.log` under `%USERPROFILE%/CartIQ-data/2026-11-15-price-acquisition`.
- `npm.cmd run typecheck`, `npm.cmd test` (54 passed), and offline `npm.cmd run build:web -- --output-dir <scratch-web>` passed. The initial type check caught two coverage panel identifier errors. The headless Chrome smoke checked separate simulated counts, source permissions and exact-series readiness and gaps through a fixture API. FastAPI `python -B -m pytest -q -p no:cacheprovider` passed 24 tests with one existing TestClient warning.
- The first disposable MySQL rehearsal rejected the V15 `row_number` identifier, which H2 accepted. Renamed it to `input_index`. The corrected `node configuration/Test-IsolatedMySql.cjs --artifact <V15.jar> --previous-artifact <V14.jar> --mysql-bin <MySQL-8-bin> --output-dir <private-scratch>` passed on MySQL 8.0.44, including Flyway V14→V15, backup/restore, readiness, and V14 rollback and roll-forward. The genuine rehearsal recorded zero approved observations, grants, source authorizations, batches and decisions. Evidence is in `mysql-verified.json` under `%USERPROFILE%/CartIQ-data/2026-11-15-price-acquisition`. [MySQL reserved words](https://dev.mysql.com/doc/refman/8.0/en/keywords.html)
- The source review found no accepted written licence or genuine historical file. Actual genuine imports and eligible series were both zero. The 12,420 legacy simulated observations remain excluded. No experiment ran on genuine data, no paid request was sent, and the local ledger digest stayed unchanged. Shared MySQL and production data were untouched. [Source and intake report](PRICE_DATASET_ACQUISITION_REPORT.md)

## Grocery price prediction phase 3

- Isolated Java 17 `mvn.cmd -o -B package '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'` passed 216 tests, with zero failures or errors and 11 opt-in skips. The three new tests exercised missing-data and authenticated coverage, separate train and serve grants, retailer/location isolation, mock FastAPI contract, revocation, and essential-purchase guardrails. The first run exposed a missing JWT setting for the non-sample test context and an outdated legacy forecast expectation. Both passed after correction.
- FastAPI `python -B -m pytest -q -p no:cacheprovider` passed 24 tests with one existing TestClient deprecation warning. Seven new synthetic cases exercised daily eligibility, gaps, temporal isolation, metrics, interval rejection, cohort pooling and internal authentication. Fixture scores are test evidence only.
- `npm.cmd run typecheck`, `npm.cmd test` (54 passed) and offline `npm.cmd run build:web -- --output-dir <scratch-web>` passed. A headless Chrome fixture smoke verified observed-price and forecast separation, interval and timing explanations, and that switching to a retailer with insufficient data clears the old forecast.
- Read-only H2 queries ran on a copy of the local database. The latest applied migration was V11. Applied V12–V14 SQL only to a second disposable copy using H2 `RunScript`, then used H2 `Shell` with `ACCESS_MODE_DATA=r` to verify zero approved observations, zero retention grants and zero training permissions. All 12,420 legacy history rows were marked `SIMULATED_HISTORY` / `SAMPLE`. `node configuration/Gemini-Recipe-Preflight.cjs` reported that the current shell lacked the service credential, Gemini key and model variable. The generation flag was disabled. The result applies only to the shell inspected. No request was sent or authorized.
- `node configuration/Test-IsolatedMySql.cjs --artifact <phase-3.jar> --previous-artifact <phase-2.jar> --mysql-bin <MySQL-8-bin> --output-dir <private-scratch>` passed on disposable MySQL 8.0.44. V13→V14, Flyway validation, backup restore, candidate and previous package readiness, and roll-forward all passed. The isolated schema had zero approved observations, zero retention grants and zero forecast permissions. See `mysql-rehearsal.json`. Forecast API behavior was tested separately on H2 with synthetic data.
- The QuickCommerce ledger remained unchanged. No retailer calls, Gemini dispatch, provider credit spend, shared MySQL connection or production migration occurred. Evidence is under `%USERPROFILE%/CartIQ-data/2026-11-15-price-intelligence`. [Forecasting coverage and contract](PRICE_PREDICTION_EXPERIMENTS.md)

## Recipe intelligence phase 2

- Isolated Java 17 `mvn.cmd -o -B package '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'` passed with 213 tests, zero failures or errors and 11 opt-in skips. Ten skips require the gated MySQL integration environment and one requires a captured Blinkit response. Four new H2/MockMvc cases covered filters, metric conversion, scaling, repeated needs, pantry and cart ownership, two pack sizes, retailer subtotals, budget warnings, substitution evidence and generated-draft isolation. Spring generation used a local mock AI service with token checking and invalid-draft fixtures.
- `npm.cmd run typecheck`, `npm.cmd test` (54 tests), and offline `npm.cmd run build:web -- --output-dir <scratch-web>` passed. Initial TypeScript verification caught an unsupported prop on the shared label and passed after it moved to the containing view. Headless Chrome drove curated browsing, a structured mock-generated draft, two meal days, pantry quantity editing, a budget warning, and the correctly scoped cart request. Browser fixtures contain synthetic products and prices.
- FastAPI `python -B -m pytest -q -p no:cacheprovider` passed 17 tests with one existing TestClient deprecation warning. Tests used mock Gemini responses and verified structured quantities, auth, disabled mode, timeout, and rejection of nutrition and price claims. No Gemini or retailer request was sent.
- `node configuration/Test-IsolatedMySql.cjs --artifact <recipe-candidate.jar> --previous-artifact <phase-1.jar> --mysql-bin <MySQL-8-bin> --output-dir <private-scratch>` passed with disposable MySQL 8.0.44. Both packages used V1–V13. Flyway validation, backup restore, candidate readiness, rollback and roll-forward succeeded. Recipe operations were exercised in H2 rather than the MySQL rehearsal.
- Runtime AI provider configuration, native device verification, reviewed ingredient-link coverage, and fresh licensed retailer offers remain separate acceptance requirements. QuickCommerce ledger unchanged at 92 settled credits and zero reservations. No shared database access or production migration occurred. Evidence is under `%USERPROFILE%/CartIQ-data/2026-11-15-recipe-intelligence`. [Recipe workflows and setup](RECIPE_INTELLIGENCE.md)

## Grocery data engine phase 1

- Isolated `mvn.cmd -o -B package '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'` passed with 209 tests, zero failures or errors, and 11 skips. Ten skips required the gated MySQL integration environment and one required a captured Blinkit body. Five new H2/MockMvc tests exercised approved intake, unknown discount and price, duplicate and conflicting records, GTIN and pack checks, location filtering, freshness, price changes, revocation, expiry and purge, chronological admin datasets, recipe components and pantry isolation. The first run found an error decoding a null decimal and a V12 receipt test that needed an explicit migration target. Both passed on the final run.
- `npm.cmd run typecheck`, `npm.cmd test` (54 passed), and offline `npm.cmd run build:web -- --output-dir <scratch-web>` passed. A headless Chrome smoke against the exported bundle and synthetic loopback API verified the new history panel, its variant/location scope, empty state, and removal of old prices when changing packs. Evidence under `%USERPROFILE%/CartIQ-data/2026-11-15-grocery-engine`.
- `node configuration/Test-IsolatedMySql.cjs --artifact <V13-candidate.jar> --previous-artifact <V12.jar> --mysql-bin <MySQL-8-bin> --output-dir <private-scratch>` passed on disposable MySQL 8.0.44. Applied and validated V1–V12 using the previous package, upgraded to V13, backed up and restored into a fresh schema, and checked candidate, previous and roll-forward startup and fixture readiness. This rehearsal verified migration and restore compatibility. The observation API tests used H2. Evidence in `%USERPROFILE%/CartIQ-data/2026-11-15-grocery-engine/mysql-rehearsal.json`.
- No new retailer requests, provider prices, genuine observations, historical coverage or trained models. The fixtures used synthetic observations on isolated databases only. QuickCommerce's persistent ledger remained unchanged. Blinkit and Zepto retain their prior live verification and Swiggy was not retested. Shared MySQL and production data were untouched.

## Release acceptance system and notification receipts

- `node configuration/Accept-Release.cjs` writes the JSON and Markdown acceptance reports. The command distinguishes missing external requirements from test failures and historical evidence. The default local run returned `INCOMPLETE` with exit `2`. Static Compose, the offline retailer safety and ledger digest check, and all 12 PIN tests passed. [Acceptance commands](DEPLOYMENT_GUIDE.md#release-acceptance-command)
- `node --test configuration/acceptance.test.cjs configuration/release-check.test.cjs` with `CARTIQ_SMOKE_TEST_TLS_DIR` pointed at the private loopback TLS fixtures passed 9 tests. Covered JSON and Markdown classification, redaction, nested isolation, staging HTTPS and FastAPI, rollback fixtures, and rejection of non-loopback targets.
- The isolated backend run `mvn.cmd -o -B test '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'` reported 204 passed, zero failures or errors, and 11 skips (ten gated MySQL tests and one optional operator-supplied Blinkit capture). The 19 receipt-related tests passed. This included device-level deduplication, rate-limit and retry bounds, uncertain outcome handling, opt-out, unregistered and replaced tokens, and V11 migration validation against a V12 schema. The initial queue tests exposed a fractional-second boundary that could postpone the first dispatch. The corrected worker uses database-compatible time precision. See `%USERPROFILE%/CartIQ-data/2026-11-15-release-acceptance/backend-full-tests.log`.
- The candidate packaged successfully with `mvn.cmd -o -B '-Dmaven.test.skip=true' package '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'`. JAR version 0.2.0 and SHA-256 `0D199535C5F49BAC4C802ECFF2F5011660FDD2B70BBD1ADCE1FC8E04B56A8980`. Build and backend test logs are under `%USERPROFILE%/CartIQ-data/2026-11-15-release-acceptance`.
- The configured acceptance run upgraded an isolated MySQL 8.0.44 database from V11 to V12. It validated Flyway, backed up and restored into a new schema, confirmed the in-app fallback for an old unresolved push and the persistence of a synthetic per-device receipt, and started the current, previous and current packages against the restored schema. The candidate passed real trusted HTTPS and API routing through portable Nginx. Runtime and backup evidence is in `mysql-evidence/isolated-mysql-xDRrYU/rehearsal.json` below `%USERPROFILE%/CartIQ-data/2026-11-15-release-acceptance/verified-39286c5c-714d-4d36-898e-4474e6ba772b`. The helper's initial calibration found unsupported client command flags and a Windows Java shim that could leave a child behind. The final run used supported client options and started Java and MySQL without those intermediary launchers.
- The configured run returned `INCOMPLETE` (exit `2`) with 12 `PASS` and 11 `BLOCKED_EXTERNAL` results. FastAPI's real loopback probes returned 200 health, 401 anonymous, and 200 authenticated. The service used Python 3.13.7, FastAPI 0.162.0 and Uvicorn 0.54.0 from the isolated virtual environment. Host `python --version` reported 3.14.5. `reports/release-acceptance.json` and `reports/release-acceptance.md` are in the same verification directory. Frontend `npm.cmd run typecheck` and `npm.cmd test` passed with 54 tests. All rehearsal processes stopped.

Firebase tests simulated responses and observed no physical-device delivery. No signed builds, shared MySQL, production migrations or paid retailer calls occurred. No QuickCommerce credits were consumed. The release rehearsal uses disposable database and synthetic data only.

## Final external deployment preparation

| Area | Status | Evidence |
|---|---|---|
| Compose base and Firebase override | **Verified statically.** | `node configuration/Check-Release.cjs` passed service, network, environment, health, mount and database isolation checks. Docker Compose runtime and native image entry points remain externally blocked. |
| Nginx | **Verified locally.** | `Render-LoopbackStagingProxy.ps1` ran `nginx.exe -t -c conf/cartiq-staging.conf` with portable Nginx 1.28.3. The new smoke script also passed against real portable Nginx and TLS with an isolated synthetic API. |
| Release smoke and rollback | **Verified with local fixtures.** | Set `CARTIQ_SMOKE_TEST_TLS_DIR` to the private loopback test certificate directory and run `node --test configuration/release-check.test.cjs`. Five passed. Checks covered HTTPS, request routing, readiness, forecast authentication, an immutable package digest, the disposable cart fixture and rejection of external targets. Docker image rollback remains externally blocked. |
| Backend notification lifecycle | **Verified with H2 and a mocked sender.** | Seven focused tests passed. Tested preferences, invalid token removal, expiry, token rotation, authentication and current-device logout. Native and Web delivery with authorized Firebase projects and devices remains externally blocked. Per-device delivery receipts are not implemented. |
| Production configuration guard | **Verified with local tests.** | Four focused tests passed. The packaged JAR rejected an invalid JWT fixture before HTTP or JDBC initialization, using a sanitized configuration error. |
| Current JAR and disposable MySQL | **Verified locally.** | Repeated the HTTPS restore and rollback rehearsal with the new package and Web export. Restore, registration, token rotation, cart isolation and payment-gated routes passed. Login and the same cart quantity passed on the previous package and on roll-forward. All 11 migration records were retained. The wrapper issued a second shutdown after the harness had stopped MySQL. A final socket check found no staging listeners. |
| PIN and GPS geocoder | **Verified with local fixtures.** | Twelve backend tests passed, including a stalled loopback provider response. Six frontend location tests passed as part of the frontend suite. Live PIN and reverse-address coverage remains externally blocked on an authorized endpoint. |
| Native configuration | **Verified at config and export level.** | `expo config --type introspect --json` checked the iOS APNs entitlement and foreground location. `expo prebuild --platform android --no-install` generated the Android project with location and notification permissions and the notification icon using synthetic Firebase configuration. iOS native compilation and physical-device tests remain externally blocked. |
| Release builds and frontend checks | **Verified locally.** | `npm.cmd run typecheck`, `npm.cmd test`, `npm.cmd run build:web -- --output-dir <scratch-web>` and `npx.cmd --no-install expo export --platform android --platform ios --output-dir <scratch-native>` ran locally. The frontend suite passed 54 tests. The outputs are Web assets and unsigned native JavaScript bundles. |

The isolated backend command `mvn.cmd -o -B test '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'` reported 192 passed, zero failures or errors and 11 skipped. The skips were ten gated real-MySQL tests and one optional captured Blinkit payload test. The geocoder socket regression failed during the initial transport experiments and passed with the final Apache HttpClient transport and disabled redirects/retries. Android generation exposed the missing explicit notification permission and status-bar icon. Both passed after configuration was corrected. See `%USERPROFILE%/CartIQ-data/2026-11-15-external-firebase/final-backend.log` and `%USERPROFILE%/CartIQ-data/2026-11-15-external-prep`.

No credentials were changed, public infrastructure deployed, signed builds submitted, notifications sent or paid retailer requests made. Shared MySQL was untouched. No production migrations ran. The QuickCommerce ledger hash remained unchanged with 92 settled credits and zero outstanding reservations. [Deployment commands](DEPLOYMENT_GUIDE.md) · [External launch requirements](PRODUCTION_READINESS.md#external-launch-requirements)

## Staging deployment verification

Staging ran on Windows loopback with portable Nginx 1.28.3, the existing release JAR and Web export, and disposable MySQL 8.0.44. The Nginx renderer used the same template with local bind ports and scratch TLS files. Docker Engine and a WSL distribution were absent. This run did not exercise Compose startup, the container DNS resolver or the Docker image entry point.

| Task | Status and evidence |
|---|---|
| Docker Compose staging | **Blocked.** Base and optional Firebase override YAML parsed. A Docker Engine with Compose v2 and an isolated host or network are required to run the stack. |
| Nginx, certificate, redirect, headers and routing | **Implemented and verified.** Passed `nginx.exe -t -c conf/cartiq-staging.conf`. The private CA and localhost certificate passed TLS chain and hostname verification. Untrusted CA and wrong hostname were rejected. HTTP returned 308 to the configured HTTPS origin with path and query. Changing the request Host did not change the redirect destination. HSTS, content-type, frame and geolocation headers, static Web and proxied API all passed. |
| Spring Boot production and MySQL | **Implemented and verified.** The `prod` JAR started against the restored scratch database using a DML-only account with startup Flyway off. Readiness returned 200. HTTPS checks covered registration, refresh rotation and replay rejection, non-cacheable secure cookies, catalogue search, comparison with no confirmed offer, cart ownership, assistant refusal to invent a price, and logout revocation. Paid search returned 403. |
| FastAPI health and authentication | **Implemented and verified.** Started Uvicorn on loopback port 18933 in production mode using a temporary internal secret. `/health` returned 200. `/docs` returned 404. `/models/status` returned 401 without the credential and 200 with it. |
| Live PIN geocoding | **Blocked.** No approved `CARTIQ_GEOCODER_URL` was configured. HTTPS probes confirmed invalid PIN 400 and unconfigured lookup 503. Three mocked geocoder tests passed, including locality selection and reverse lookup preserving device coordinates. No actual coordinates were inferred from the test PIN. |
| Firebase registration and delivery | **Blocked.** No development Firebase project values or ADC mapping were available. Added a development-only optional Compose secret mount. No token was registered and no push was sent. Native push remains unimplemented. |
| Signed Android and iOS artifacts | **Blocked.** No linked EAS project ID, authenticated EAS configuration, or approved signing setup was available. No cloud builds were submitted. Only the prior unsigned Expo Hermes bundles exist. |
| Actual Android and iOS devices | **Blocked.** No signed installables or provisioned test devices and private device-reachable test origin were available. Browser and HTTP checks do not constitute native verification. |
| Backup restore | **Implemented and verified.** Restored the existing disposable dump into a fresh MySQL data directory on loopback port 13320. All 11 migration records and the previously backed-up fixture account were present. The current JAR reached readiness on the restored database. |
| Deployment rollback | **Implemented and verified for the Java package rehearsal.** Switched from the release candidate to the previous package and then back. HTTPS login and the same persisted cart quantity passed after both switches. All 11 successful migration records were retained. Compose image rollback needs the target Docker host. |

The initial rollback probe timed out because it used the new readiness path against the previous release. The repeat used `/actuator/health` for the previous package and `/actuator/health/readiness` for the candidate. `rollback-initial-probe.log`, `candidate.log`, `rollback.log`, `roll-forward.log` and `mysql-rehearsal.err.log` are under `%USERPROFILE%/CartIQ-data/2026-11-15-staging`. The geocoder test used `mvn.cmd -o -B test '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' '-Dtest=ShoppingGeocoderTest'` from the isolated release source copy. [Exact commands](DEPLOYMENT_GUIDE.md#windows-loopback-proxy-verification)

The staging processes were stopped and their five loopback ports cleared. Zero live retailer requests were sent. The ledger hash matched before and after verification. The last settled balance was 92 with zero pending reservations, 12 experiment slots and a 77-credit floor. No shared MySQL connections, production migrations, provider storage or public infrastructure were used.

## Production release verification

| Check | Actual result |
|---|---|
| `npm.cmd run typecheck` and `npm.cmd test` | TypeScript and all 50 frontend tests passed. |
| `npm.cmd run build:web -- --output-dir <scratch-web>` with production mode, `EXPO_OFFLINE=1` and `EXPO_PUBLIC_API_URL=/api` | Web export passed. The exported bundle passed the loopback mocked Chrome shopping walkthrough, including location, search, comparison, details, and assistant behavior. |
| `expo export --platform android --platform ios --output-dir <scratch-native>` with production mode and an HTTPS API URL | Android and iOS Hermes bundle exports passed. Signed binaries and device tests remain pending on an EAS project and signing credentials. |
| `mvn.cmd -o -B package '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'` | Java 17 package built. 173 passed, zero failures, and ten MySQL-gated skips. Used an isolated source copy, H2, mocked provider requests and the existing captured Blinkit body in the opt-in parser test. |
| `python -B -m pytest -q -p no:cacheprovider` in `ai-service` with Python 3.13 and the locked requirements | Seven passed, one test-client deprecation warning. Added production secret and authenticated model status checks. Health passed through the FastAPI test client. |
| Production Java JAR and disposable MySQL 8.0.44 on loopback port 13318 | Explicit migration and validation passed V1–V11. The real `prod` profile started with a separate DML-only user. Readiness and liveness returned 200. Approved CORS origin passed and unlisted origin returned 403. Demo and OpenAPI returned 404. Secure non-cacheable cookie, JWT profile, logout revocation and production 429 passed. |
| Disposable backup and recovery | `mysqldump --single-transaction --no-tablespaces` produced a scratch backup. Replayed it with the MySQL client into a new schema on a separate, fresh MySQL 8.0.44 instance at loopback port 13319. Checked all 11 successful Flyway revisions and the restored synthetic account row. Both disposable listeners were stopped. |
| Deployment configuration | Compose YAML and EAS JSON parsed, and only the Web service publishes a port. Docker Engine and Nginx were unavailable on the host, so container build, proxy TLS and Compose orchestration remain unverified. |

The first isolated MySQL connection was rejected by Connector/J with TLS disabled. The corrected test used scratch JDBC TLS and a fresh data directory. An initial restore wrapper failed while converting the file path. The saved dump was restored successfully on the second disposable server. Deployment guidance requires remote certificate identity verification. Migrations were reviewed and kept byte-for-byte intact. No production database changes were authorized or made. No live retailer calls were made. The ledger stayed unchanged at 92 credits, zero pending reservations, 12 experiment slots and a 77-credit floor. The geocoder and Firebase development credentials were unavailable. Evidence is under `%USERPROFILE%/CartIQ-data/2026-11-15-production`.

## Shopping intelligence verification

Live retailer search still uses the existing local Spring adapter. The screen adds local filters, standard-unit prices, a matched-price breakdown with separate retailer timestamps, and review of similar excluded listings. Comparisons use the available known-price subset. The percentage difference uses the highest eligible equivalent item price as its denominator. It excludes fees. Matching uses normalized full title, brand and pack plus any supplied flavour, formulation and verifiable barcode. A barcode cannot override a pack conflict. The score is a heuristic, and reviewing a pair makes no durable catalogue mapping or approval. Additional optional upstream metadata was verified with fixtures only.

Cart planning still uses verified catalogue identities and fee evidence. Missing retailer variants are listed explicitly. The cart and assistant remain account scoped. The assistant's price-change intent compares the last two observations for each listing in the selected catalogue area and never uses transient retailer responses as history. Buy-now advice comes from existing cart rules. Forecasting defaults off and the opt-in path requires one listing and at least eight distinct observation days. No new production model was trained or validated.

GPS and PIN confirmation use the existing shopping location flow. Set `CARTIQ_GEOCODER_URL` to an authorized Nominatim-compatible service to resolve PINs and reverse addresses. The user-confirmed coordinates invalidate previous retailer results. Existing Firebase Web registration and backend delivery require `FIREBASE_ENABLED=true`, backend application credentials and Expo Web Firebase project settings. Price watches and replenishment read recorded data and make no QuickCommerceAPI requests. Both external services and native delivery were kept disabled or unconfigured for tests. [Location configuration](QUICKCOMMERCE_DRY_RUN.md#milestone-4-shopping-configuration)

Blinkit and Zepto were last verified live during the preceding milestone. Swiggy's upstream failures remain scoped to Swiggy. This milestone made zero live retailer requests and consumed zero credits, changed no migrations and wrote no generated provider price history. The ledger stayed byte-for-byte unchanged at 92 settled credits, zero pending reservations and 12 experiment slots above the 77-credit floor. Shared MySQL was untouched.

| Verification | Result |
|---|---|
| `npm.cmd run typecheck` and `npm.cmd test` | TypeScript and all 50 frontend tests passed. New coverage includes GTIN and pack conflicts, formulation and flavour differences, missing and unavailable prices, ranking, price differences, and fetch times. |
| `npm.cmd run build:web -- --output-dir <scratch-web>` with `EXPO_PUBLIC_API_URL=/api` and `EXPO_OFFLINE=1` | Expo Web export passed. |
| `node browser-smoke.cjs` from the local evidence directory | Loopback-only mocked Chrome workflow passed. Covered GPS and PIN confirmation, selection and partial failure, price comparison and savings, local filters, item detail, duplicate feedback, stale results, and the assistant history prompt. |
| `mvn.cmd -o -B test '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'` | 173 passed, zero failures, ten MySQL-only skips. Java 17, isolated source copy and H2, mocked provider traffic, and the existing Blinkit body fixture via `CARTIQ_CAPTURED_BLINKIT_BODY`. H2 checks covered account isolation, basket errors, recorded price increases and disabled forecasting. |
| `python -B -m pytest -q -p no:cacheprovider` from `ai-service`, Python 3.13 scratch environment with `requirements.lock.txt` | Six passed. Starlette reported one test-client dependency deprecation warning. Forecast and training checks used test fixtures. |

An intermediate backend run failed one forecast test because it used the old default. The final run exercises the opt-in failure path separately from default-disabled behavior. The first browser run stalled before the assistant because the mock dashboard response lacked the required data shape. Corrected the fixture and reran successfully. Evidence is under `%USERPROFILE%/CartIQ-data/2026-11-15-shopping-intelligence`.

## Live retailer verification and billing stop

The QuickCommerceAPI contract uses backend-only `X-API-Key`. CartIQ calls `GET /v1/search` with `q`, `platform`, `lat`, `lon` and an optional `pincode`. Item details use `GET /v1/item` and `item_id`. The platform values are `BlinkIt`, `Zepto` and `Swiggy`. Search maps `id`, `name`, `brand`, `quantity`, `offer_price`, `mrp`, `available`, `inventory` and `store_id`. Item details use `item_id`, `price`, and the documented item array. INR values convert to minor units and missing price, stock and availability remain unknown. Requests are serial and never automatically retried. The documented free `GET /v1/credits` reports `summary.total_available`. [QuickCommerceAPI reference](https://quickcommerceapi.com/docs)

| Live stage | Outcome |
|---|---|
| Account preflight | Called CartIQ's admin balance endpoint once. CartIQ and the provider returned 200. The account reported 96 available credits and matched the settled ledger. The check made no reservation and costs zero credits. |
| Blinkit `Toor dal` in Coimbatore at `10.942972, 76.953417`, no PIN | Made one ledger-guarded search. CartIQ returned 502 with “QuickCommerce balance verification failed (upstream HTTP 200).” The adapter received and decoded a success envelope, then stopped at credit-body validation before checking the header or releasing products. The exact credit field, provider request ID, and product count were unavailable from that failure. |
| Resumed account preflight | CartIQ and provider HTTP 200. The free check matched the reconciled ledger at 96. |
| Zepto independent `Toor dal` | Both HTTP 200. 24 products, all with price and stock reported. Request `d9f57c62-6222-4bdf-b3a4-09d02c719909`. Charged one, settled 95. |
| Swiggy independent `Toor dal` | Upstream HTTP 200 with a reported retailer failure. CartIQ returned a typed failure via 502. Request `e0750836-648e-446c-98a6-83b3366114d9` at 15:42:25 IST. Zero charged, settled 95. |
| Expo Web `Amul Taaza Milk` comparison | CartIQ HTTP 200. Blinkit returned 24 results, request `d58d5a7a-f981-4bae-9e04-e2881077153f`. Zepto returned 13, request `b3704f4d-1bb0-424b-aed9-dc735b555a5e`. Both upstream HTTP 200 and charged one each. Swiggy reported a second uncharged failure, request `73a80dba-45ae-4072-80d0-a3546b4a5e51` at 15:42:39 IST. The browser rendered a genuine listing and two matching-pack price comparisons. Settled 93. |
| Blinkit item detail from the browser comparison | CartIQ and provider HTTP 200. One item returned, request `0d6b7498-904f-4f31-a797-fd68bb9beb29`. Charged one, settled 92. |

The disposable Spring Boot instance used loopback and file-backed H2 with Flyway V11. The initial stage stopped at the pending reservation. The operator then supplied the response body for request `ef7354f2-a112-40bb-bc8b-955d9971e485`. It reported zero products, `failed_platforms: ["BlinkIt"]`, and `credits_remaining: 96`. This identified the root cause. CartIQ expected 95 before inspecting the reported platform failure. The original header remains unavailable. Exact no-charge reconciliation restored the held unit to the settled balance of 96, with zero pending reservations and 18 remaining experiment slots at that point. Both historical fingerprints and their provider request IDs remain recorded. No raw provider payload, products, or prices were stored. Shared MySQL was untouched.

The adapter now checks the echoed platform and coordinates, then checks `failed_platforms` before applying charge rules. It settles the uncharged attempt and reports a retailer failure only when the requested platform alone failed, the row list is empty, and the body and header confirm the prior balance. The failure retains its experiment slot and fingerprint. Conflicting or missing evidence leaves a pending reservation. Charge verification also accepts equivalent canonical integer text or an absent body balance backed by the exact expected header. Logs record only the correlation ID, upstream status, field kind and match flags.

Seven retailer requests and two free account requests ran live. The account started at 96 and settled at 92 after four confirmed charges. All eight experiment fingerprints, including the older Milk request, remain recorded. There are zero pending reservations, 12 experiment slots left, and a floor of 77. The latest browser flow used the real adapter for search, comparison, and item detail. Catalogue and cart integration were covered separately by isolated H2 tests. Verified the disposable port was closed and the normal adapter was disabled. Neither shared MySQL nor provider price storage was accessed.

| Offline verification | Result |
|---|---|
| `npm.cmd run typecheck` and `npm.cmd test` | Type check and all 47 frontend tests passed. |
| `npm.cmd run build:web -- --output-dir <scratch-web>` with `EXPO_PUBLIC_API_URL=/api` and `EXPO_OFFLINE=1` | Expo Web export passed. The mock browser walkthrough also passed for Coimbatore, retailer selection, location changes, duplicate feedback, and item detail. |
| `mvn.cmd -o -B package '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' '-Dtest=QuickCommerceProviderTest,QuickCommerceAdminTest,ShoppingGeocoderTest'` | 48 passed. Includes the 24-row historical Blinkit body replay over mock transport, the uncharged failure-envelope regression, protected blank-header behavior and balance preflight with no ledger writes. The captured historical status and credit headers were unavailable, so only fixture transport supplied them. |
| `mvn.cmd -o -B test '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'` | 168 passed, zero failures, ten MySQL-only skips. The test JVM used its isolated directory for `user.home`, and the historic-body test used `CARTIQ_CAPTURED_BLINKIT_BODY` pointing to the original attachment. |
| Local reconciliation | Verified wrong-balance refusal, preservation of both request records, unchanged attempt count, and refusal to reconcile the same reservation again. |

Evidence and sanitized startup logs are under `%USERPROFILE%/CartIQ-data/2026-11-15-live-retailer`. Two local restart preflights dispatched no provider requests. The harness was corrected to wait for admin seeding and use fresh isolated H2 files. The first no-charge test harness also miscounted fixture ledger lines and was corrected before the real reconciliation.

Swiggy failed for two distinct live queries at the chosen coordinates. The provider supplied no more specific error or coverage reason. The request IDs above are the evidence needed for provider diagnosis. PIN resolution still needs a configured geocoder. Native device verification and public display rights remain pending.

## Fresh grocery search verification

The frontend uses the saved shopping coordinates, including the selected Coimbatore location, and shows the search term, retailer selection and maximum credit cost before the user submits. One explicit submission can query one or more selected retailers. A later explicit submission can add still-unattempted retailers for the same search. Results, item details, and timestamps stay in memory. Query, location or account changes discard the previous view. A duplicate never refreshes an earlier provider request and prior provider rows are unavailable once the view is cleared.

Spring Boot validates the retailer list and reserves separately for each selected provider request under the existing ledger lock. Invalid lists produce HTTP 400 before transport. A duplicate produces a retailer-specific skip. An uncertain outcome stops the sequence and pauses the frontend for that search. Each retailer with a response displays its own fetch time. Empty results, provider errors and unknown attributes remain distinct.

| Check | Command or environment | Result |
|---|---|---|
| Frontend | `npm.cmd run typecheck` and `npm.cmd test` | Type check and all 47 tests passed, zero failures or skips. |
| Expo Web | With `EXPO_PUBLIC_API_URL=/api` and `EXPO_OFFLINE=1`, ran `npm.cmd run build:web -- --output-dir <scratch-web>` | Export passed. |
| Browser flow | `node browser-smoke.cjs` in the fresh-search evidence directory. Headless Chrome with a loopback mock API and external requests blocked. | Passed explicit one-retailer submission, exact Coimbatore coordinates, selection of remaining retailers, duplicate feedback, fetch timestamps, comparison and item details. Location change cleared the old results without issuing a lookup. |
| Targeted backend | `mvn.cmd -o -B test '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' '-Dtest=QuickCommerceProviderTest,QuickCommerceAdminTest,ShoppingGeocoderTest'` | All 39 passed with mocked upstream HTTP and isolated H2. Single-retailer dispatch, invalid list rejection and byte-identical fixture ledger on duplicate were verified. |
| Full backend | `mvn.cmd -o -B test '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'` | 159 passed, zero failures and 10 MySQL-only skips. Disposable source copy, Java 17, isolated H2 and MySQL gates off. |

The initial frontend test build found a fixture typing error, which was corrected before the passing run. Evidence is under `%USERPROFILE%/CartIQ-data/2026-11-15-fresh-search`.

The real ledger remained byte-identical at 96 settled credits, zero pending reservations, and 19 remaining experiment credits above the floor of 77. The adapter remains disabled. No live requests, provider data persistence or shared MySQL access occurred. Public retailer display rights and actual provider coverage remain pending.

The proposed next test awaits explicit approval. One Blinkit `GET /v1/search` for `Toor dal` at the selected Coimbatore coordinates `10.942972, 76.953417` with no PIN. The read-only ledger check found no matching fingerprint. Expected cost is one credit, leaving 95 settled credits if charged. Recheck the signed-in dashboard and ledger before dispatch. The previous Milk search with the same scope is recorded and remains blocked.

## Milestone 4 shopping location and retailer verification

The previous browser GPS callback printed coordinates and opened the saved-area list without storing the device point. Native foreground location was absent. The PIN field filtered the configured catalogue locations and never queried a postal source. Retailer views required a second, manually entered coordinate pair and a development admin. The new shared picker feeds a confirmed device point or approximate PIN point into retailer search and details. The selection stays on the device. The cart continues to use its configured catalogue area and verified identities.

| Check | Command or environment | Result |
|---|---|---|
| Expo types and unit tests | `npm.cmd run typecheck` and `npm.cmd test` | Type check passed. 45 tests passed, zero failures or skips. Added GPS permission, unavailable position, timeout, PIN confirmation, location scope and pricing cases. |
| Expo Web export | `EXPO_PUBLIC_API_URL=/api` and `EXPO_OFFLINE=1` in the launch shell, then `npm.cmd run build:web -- --output-dir <scratch-web>` | Export completed. |
| Browser smoke | `node browser-smoke.cjs` in the local milestone evidence directory. Headless Chrome with Playwright and a loopback-only mocked API. | Passed GPS confirmation, PIN validation, multiple localities, missing coordinates, match-based pricing, item detail, loading, empty and failure states, and stale result removal after a location change. No automatic search on location change. |
| Targeted backend | `mvn.cmd -o -B test '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' '-Dtest=QuickCommerceProviderTest,QuickCommerceAdminTest,ShoppingGeocoderTest'` | 37 passed, zero failures or skips. Mocked HTTP checks include no-charge retailer timeout with subsequent success, explicit outcomes, PIN address filtering, reverse geocoding and gated customer routes. |
| Full backend | `mvn.cmd -o -B test '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'` | 157 passed, zero failures, 10 MySQL-only skips. Java 17, disposable source and ledger paths, isolated in-memory H2, and MySQL gates disabled. |

Initial TypeScript runs identified union-narrowing errors in the new location and comparison tests. The errors were fixed before the passing type check and frontend suite. Browser smoke uses a local HTTP fixture. Backend tests separately exercise the actual Spring controller, adapter and geocoder with mocked upstream traffic. Native Android and iOS device execution, deployed geocoder coverage and live retailer verification remain pending. An operator-approved geocoder endpoint is required for PIN lookup and reverse address labels. No geocoder or retailer endpoint was called live.

The real ledger hash is unchanged. The balance is 96 settled, with zero pending reservations, 19 remaining experiment credits and a floor of 77. The app made zero live retailer requests and spent zero credits. The provider and customer shopping route were left disabled. Shared MySQL was untouched. Logs and browser harness are under `%USERPROFILE%/CartIQ-data/2026-11-15-shopping-milestone`. [Configuration and proposed one-call verification](QUICKCOMMERCE_DRY_RUN.md#milestone-4-shopping-configuration)

## Final QuickCommerceAPI offline verification

Inspected the supplied Blinkit JSON locally. It matches the settled request ID and search scope and reports successful completion, 24 results, zero failed platforms and 96 credits remaining. All 24 listing IDs are strings and all 24 top-level store IDs are integers. No provider rows, prices, precise location, or raw payload were copied into test code or logs. The original CartIQ request returned 502. The supplied JSON lacks the original HTTP status and headers, and the original CartIQ logs lack a diagnostic trace. The precise live failure remains unconfirmed.

Both synthetic 24-row variants returned 502 against the original adapter. One uses a numeric listing ID and nested numeric store ID. The other uses a string listing ID and numeric top-level store IDs, matching the field types found in the supplied body. The old parser required text for both ID fields. The fixed adapter returned HTTP 200 and serialized all 24 rows in each variant, preserving string IDs with leading zeros. The matrix also covers zero results, absent and null optional values, unexpected ID types, malformed JSON, missing envelope and collection fields, incorrect response structures, incomplete identity, duplicate IDs, provider errors and a simulated serialization failure. Optional product attributes remain unknown. Rating remains outside the existing API DTO. Invalid required identity and duplicate IDs reject the entire response with 502.

Reviewed the local provider and controller security path. Provider 502 responses contain only the stage and numeric HTTP status when available. The controller handles a pre-write converter failure with a generic HTTP 500, no exception details and `Cache-Control: no-store`. Captured logs and error bodies in the new tests omitted the injected credential, payload, location and PIN markers. The local startup diagnostic reports only API key presence when requested. The review covers default application logging and an injected failure before response bytes are committed. Avoid HTTP wire, header and MVC payload tracing when real retailer data is in use.

Ran the following in disposable Windows backend copies on Java 17. Maven stayed offline, QuickCommerce was disabled in Spring, the provider tests intercepted HTTP with `MockRestServiceServer`, and the test JVM used its scratch directory as `user.home`. Spring integration tests used isolated in-memory H2. The full suite includes loopback HTTP and skips all ten MySQL-gated tests. No test contacted an external service or shared MySQL.

| Command | Executed | Passed | Failed | Skipped |
|---|---:|---:|---:|---:|
| `mvn.cmd -o -B test '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' '-Dtest=QuickCommerceProviderTest#returnsTheBlinkitSuccessShapeThroughTheAdminController'` using the original smoke-test adapter | 2 | 0 | 2 expected 502 reproductions | 0 |
| `mvn.cmd -o -B test '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' '-Dtest=QuickCommerceProviderTest,QuickCommerceAdminTest'` using current source | 32 | 32 | 0 | 0 |
| `mvn.cmd -o -B test '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.'` using current source | 152 | 152 | 0 | 10 MySQL |

Verified the real ledger with `Get-FileHash` and `[IO.File]::ReadAllLines` before and after the tests. Its SHA-256 is unchanged. The original fingerprint and request ID `922db9a5-1a13-4a11-b1de-25c9d4d8b0b7` remain linked to `operator-confirmation-2026-11-15-blinkit-smoke`. One historical credit is consumed, the settled balance is 96, there are zero pending reservations and 19 experiment credits remain above the 77-credit floor. Verified the working loopback backend on port 8080 remained explicitly disabled. This verification made zero live requests, used zero additional credits and wrote no retailer or historical data. Test logs are under `%USERPROFILE%/CartIQ-data/2026-11-15-quickcommerce-final-offline`.

## Blinkit reservation reconciliation and offline 502 regression

The operator supplied provider evidence of a successful Blinkit Milk search, request ID `922db9a5-1a13-4a11-b1de-25c9d4d8b0b7`, 24 products, zero failed platforms and 96 remaining credits. Computed the original search fingerprint from its authorized query, retailer, coordinates and empty PIN. It matched the only pending ledger attempt. The reconciliation recorded one consumed credit at 96, moved the reservation from pending to settled, and preserved the original fingerprint and attempt. The ledger links the request ID to `operator-confirmation-2026-11-15-blinkit-smoke`. There are zero pending reservations, 19 experiment credits left and a protected floor of 77.

Checked the saved smoke startup log and empty stderr. The original CartIQ response was a generic 502. The error handler returned the adapter's sanitized failure without a parser stage or response body. The original upstream HTTP status, headers, and product fields were unavailable for replay at that point. A synthetic fixture used the reported result count and the public single-platform [search contract](https://quickcommerceapi.com/docs#search). It covers a concrete adapter failure in which a numeric listing ID was rejected before the controller could serialize the response. [Later review of the supplied body](#final-quickcommerceapi-offline-verification)

Offline ledger verification ran `./configuration/Initialize-QuickCommerceLedger.ps1` against a temporary ledger. It checked mismatched fingerprint and balance rejection, successful evidence attachment, unchanged attempt count and balance, and replay refusal. The same script settled the verified original ledger. No second attempt, refund or reservation was created.

Ran these commands in disposable backend copies with Maven offline:

| Command | Result |
|---|---|
| `mvn.cmd -o -B test '-Dspring.profiles.active=test' '-Dtest=QuickCommerceProviderTest#returnsTheBlinkitSuccessShapeThroughTheAdminController'` | Expected regression on the original smoke-test source. The controller returned 502 for the mocked upstream HTTP 200. |
| `mvn.cmd -o -B test '-Dspring.profiles.active=test' '-Dtest=QuickCommerceProviderTest,QuickCommerceAdminTest'` | Clean copy of the fixed source. All 17 provider/controller and two H2 admin boundary tests passed. Zero failures or skips. The same fixture now returns HTTP 200, 24 normalized rows, unknown optional values and `Cache-Control: no-store`. Separate tests check conflicting billing evidence, reported platform failure and journal survival across a restart and later mock reservation. |

The initial regression test build hit an ambiguous test import, which was corrected. An intermediate build reused pre-fix classes and was discarded. The final run compiled the fixed source in a fresh directory. Logs are under `%USERPROFILE%/CartIQ-data/2026-11-15-blinkit-reconciliation`.

Provider calls this session were zero, including Blinkit, Zepto, and Swiggy. No additional credits were consumed. The working backend on `127.0.0.1:8080` was confirmed to have QuickCommerce explicitly disabled. Tests use mocked HTTP transport, disposable ledger files and in-memory H2. No provider payloads, products, retailer prices, offers, or price history were written. Shared MySQL was untouched.

## Authorized one-credit Blinkit smoke test

The operator reconfirmed 97 available dashboard credits. The real Windows-protected key file was readable with its restricted ACL. The disposable Java 17 Spring Boot process on `127.0.0.1:18925` reported `QUICKCOMMERCE_API_KEY: PRESENT`, used the `local` profile and disposable file-backed H2, and successfully applied Flyway V1–V11. The normal backend on port 8080 was left running with QuickCommerce disabled.

Submitted one authenticated request to CartIQ's existing single-retailer search route for Blinkit Milk at the operator-authorized location. One reservation was persisted before transport. The adapter returned HTTP 502 and could not verify the upstream response or transport at the time. The request was not retried. No Zepto, Swiggy, balance or other provider request was made. No raw payload, product, price, offer or historical observation was saved. Shared MySQL was untouched.

Ledger before the attempt was settled at 97 with zero attempts and 20 experiment credits available. Immediately after the attempt it held a single pending reservation at a provisional balance of 96. The 77-credit floor remained configured. The isolated backend was stopped and the normal adapter stayed disabled. [Later reconciliation](#blinkit-reservation-reconciliation-and-offline-502-regression)

The smoke-test build ran `mvn.cmd -o -B '-Dspring.profiles.active=test' '-Dtest=QuickCommerceProviderTest,QuickCommerceAdminTest' package`. All 16 targeted tests passed, with zero failures or skips. Success responses now expose the numeric upstream status and HTTP error messages can report a sanitized upstream status when observed. No provider error body is exposed. Startup and test logs are under `%USERPROFILE%/CartIQ-data/2026-11-15-blinkit-smoke`.

## Project repair and isolated verification

| Check | Command or method | Result |
|---|---|---|
| Backend compile, unit, service, repository, security and HTTP | `mvn.cmd -o -B test '-Dspring.profiles.active=test'` from an isolated source copy | Java 17 compilation and 136 tests passed, ten MySQL-only skips, zero failures. Includes seven loopback HTTP integration tests, no-ledger fail-closed coverage, and a catalogue regression with 100 earlier rows. |
| Disposable MySQL 8.0.44 | After a V10 scratch bootstrap, `mvn.cmd -o -B test '-Dtest=MysqlCatalogueImportTest'` and `mvn.cmd -o -B test '-Dtest=MysqlMilestone2Test,MysqlMilestone3Test,MysqlMilestone4Test,ProviderMappingMigrationTest#preservesLegacyMappingOnMysqlUpgrade'` | Two V10 and eight V11 tests passed. SQL verified successful V1–V11 in both scratch schemas on `127.0.0.1:13316`. The upgrade preserved existing mapping and price-history fixtures. Shut down only the disposable instance. |
| Expo | `npm.cmd run typecheck`, `npm.cmd test`, and `expo.cmd export --platform web --output-dir <temporary-directory>` with `EXPO_OFFLINE=1` | Type check, all 38 tests, and Web export passed. Tests cover the API client and commerce logic. No signed-in browser component or navigation walkthrough and no native device test ran in this phase. |
| AI service | `./.venv/Scripts/python.exe -m pytest -q -p no:cacheprovider` from `ai-service` | Six passed. Starlette emitted a test-client dependency deprecation warning. No production model was trained or evaluated. Forecasts remain experimental and require observed history. |
| PowerShell secret and ledger checks | `./configuration/Start-CartIqBackend.ps1 -SaveQuickCommerceKey` and `-CheckOnly` with a synthetic key in a temporary workspace. `./configuration/Initialize-QuickCommerceLedger.ps1 -ReconcilePending` against temporary ledgers. | Confirmed encrypted save, current-user-only permissions, reload, existing-file preservation, both single-attempt reconciliation outcomes, preserved fingerprints and cap, and refusal of mismatched balances. |

Ran `Start-CartIqBackend.ps1 -Port 18923` against disposable file-backed H2 with a protected synthetic key, starter catalogue enabled, sample offers disabled, and QuickCommerce disabled. The JVM printed `QUICKCOMMERCE_API_KEY: PRESENT`, Flyway applied V1–V11, and the next start confirmed V11. A loopback HTTP smoke test verified registration, profile, search and category filtering, variant details, no winner without verified offers, cart creation and editing, account isolation, customer denial of admin access, refresh, logout revocation, persistence across sign-in, and removal. Logs contain neither the synthetic key nor the issued authentication tokens. The disposable application was stopped after the test. The pre-existing process on port 8080 was left running.

The first launcher attempt exposed a PowerShell Maven-flag binding issue, corrected with an explicit string array. The first reconciliation test exposed PowerShell's null-path conversion and passed after fixing the `File.Replace` argument. The first HTTP harness miscounted an empty JSON cart array, so the test was rerun with array shape preserved and passed. Maven reports a terminated process after the test harness stops its Spring Boot child. Startup and HTTP verification completed before shutdown.

**Live verification remains blocked.** The operator supplied a test location. The real workspace `-CheckOnly` still reports `MISSING`, and `backend/.env.local` does not exist there. No real key has been saved or validated. The credit ledger is byte-identical to preflight, settled at 97 with zero attempts, 20 available experiment credits, and a 77-credit floor. Zero provider requests or credits were used, no provider data was stored, and shared MySQL was untouched. Provider retention and public comparison rights remain unconfirmed. Payments and real retailer order placement were outside the verified scope. Evidence is under `%USERPROFILE%/CartIQ-data/2026-11-15-project-repair`. [Key setup and test prerequisites](QUICKCOMMERCE_DRY_RUN.md)

## One-credit Blinkit smoke test preflight

**Stopped before dispatch.** The authorized operation was one Blinkit `GET /v1/search` for `Milk`, through the CartIQ admin route only. No local HTTP request or provider request was sent, including to Zepto or Swiggy Instamart. No HTTP status, provider error, payload, or parsing result exists for this run.

| Check | Actual result |
|---|---|
| Backend listener | `Get-NetTCPConnection -LocalPort 8080 -State Listen` and a filtered `Get-CimInstance Win32_Process` inspection confirmed CartIQ on `127.0.0.1:8080` with `local`, `jdbc:h2:file:./.runtime/smartcart` for the datasource and Flyway, and QuickCommerce enabled with a configured starting checkpoint of 97. A local H2 file exists. |
| Applied migrations | Migration sources through V11 exist. Flyway V11 completion in the currently running H2 database was not independently verified. No database connection was made for this preflight. |
| Key presence | `./configuration/Start-CartIqBackend.ps1 -CheckOnly` returned `MISSING` for the inspecting shell. The running JVM is configured to report its key presence at startup, but its `PRESENT` status was not independently observed. No credential value was read into output. |
| Serviceable location | The handoff contains provider example coordinates. No configured, serviceable location for this local run was confirmed. The examples were not used. |
| Ledger before and after | The operator's last dashboard reading was 97. The ledger stayed settled at 97 with zero fingerprints or outstanding reservations, a cap of 20, a floor of 77, and all 20 experiment credits available. The ledger checksum stayed `CF3B9864B1FC15C6638B9E06F2349376DF9D8E439D72F83C47E90C32B532050A`. No reservation, settlement, or debit occurred. |

No catalogue, canonical variant, verified offer, price-history, or supplier record was changed or stored. Shared MySQL was untouched. The source register still records unconfirmed permissions for retention and publication. [Source rights](INDIA_RETAILER_DATA_SOURCES.md#quickcommerceapi-development-access)

## QuickCommerceAPI single-provider verification

QuickCommerceAPI is the only external grocery-price transport. Existing `GET /api/admin/providers/quickcommerce/search` and `/item` share the same adapter and key. The new `POST /api/admin/providers/quickcommerce/compare` accepts `q`, `lat`, `lon` and optional `pincode`. It uses at most three serial requests, one per retailer. It skips already-settled duplicate requests and preserves completed results when a later lookup fails. Remaining retailers are marked unattempted. Anonymous and non-admin callers cannot access these routes. Existing catalogue and verified comparison requests never spend credits. The legacy observation sync route now returns 503.

In a connected Expo development build, sign in as a CartIQ admin. Enter a query and apply coordinates in Discover, or choose a pack and apply coordinates on the product comparison screen. The QuickCommerce panel runs only on an explicit button press. It shows retailer listings and a separate, optional one-credit item detail action. Results are transient. No supplier rows, prices, labels or catalogue joins are persisted. [Provider contract and rights](INDIA_RETAILER_DATA_SOURCES.md#quickcommerceapi-development-access)

The key presence check returned `MISSING`. After live testing is explicitly authorized and the dashboard balance is verified, the existing key is needed only in the backend launch process. `./configuration/Start-CartIqBackend.ps1` stays disabled by default. The explicit `-EnableQuickCommerce -VerifiedDashboardBalance <balance>` launch option accepts only a balance above 77. The runtime ledger also checks the existing checkpoint and enforces 20 attempts across restarts. Use the single-retailer route for a one-call smoke test. The Expo comparison action can cost up to three credits and needs separate authorization from a one-call test.

No provider calls or credits were used. The local ledger stayed settled at the previously verified 97, with zero attempts, all 20 experiment credits remaining, and the 77-credit floor. No current dashboard lookup, MySQL connection, migration or live provider verification was attempted.

Executed `npm.cmd run typecheck`, `npm.cmd test`, and `mvn.cmd -o -B test '-Dspring.profiles.active=test'` from a temporary backend copy with the MySQL gates disabled. Spring initialized only isolated H2 test databases. Executed `./configuration/Start-CartIqBackend.ps1 -CheckOnly` and verified the launcher refuses `-EnableQuickCommerce -VerifiedDashboardBalance 77` before starting Maven. The comparison and item details tests use mocked Spring HTTP transport and temporary ledgers.

The final backend run discovered 144 tests. 134 passed, 10 MySQL-only tests were skipped, and there were zero failures or errors. It included 13 mocked QuickCommerce provider tests and two H2 admin boundary tests. Expo type checking and all 37 tests passed. `EXPO_OFFLINE=1` with `./node_modules/.bin/expo.cmd export --platform web --output-dir <temporary-directory>` completed successfully. The two development panels were type-checked and bundled. A signed-in Expo flow with live retailer data remains pending. Logs are under `%USERPROFILE%/CartIQ-data/2026-11-15-quickcommerce-single`.

## QuickCommerceAPI backend key inheritance

The current PowerShell process and Windows User and Machine scopes reported `MISSING`. No backend startup script previously loaded the key. The Maven plugin inherits the launch environment, VS Code has no launch or task configuration for the backend, and Spring Boot has no `.env` import. The existing key was neither read from chat nor changed.

`./configuration/Start-CartIqBackend.ps1 -CheckOnly` returned `MISSING` normally and `PRESENT` with a synthetic process variable. Ran the launcher offline with the synthetic variable in a temporary source copy using `-Port 18912`. The local Spring Boot child started on loopback with H2, printed one `PRESENT` line and omitted the synthetic value from its log. The child was stopped after verification. The launcher forces both retailer adapters off. No live API calls, credits, or shared MySQL were used. The existing ledger SHA-256 was identical before and after. [Local setup and presence check](QUICKCOMMERCE_DRY_RUN.md#pass-the-existing-key-to-the-local-backend)

## QuickCommerceAPI preflight checkpoint

Checked environment variable presence in PowerShell's Process, Windows User and Windows Machine scopes. `QUICKCOMMERCE_API_KEY` was absent in all three, and there was no running CartIQ JVM. The repository has no backend `.env` import. The Spring `local` adapter resolves the key from configuration inherited at launch. The existing credential was neither displayed nor changed. [Launch and preflight procedure](QUICKCOMMERCE_DRY_RUN.md)

The operator read 97 credits from the signed-in dashboard. Checked Java's `user.home` using `java -XshowSettings:properties -version`. Ran `./configuration/Initialize-QuickCommerceLedger.ps1 -VerifiedDashboardBalance 97` against that OS profile, then repeated it to verify the settled checkpoint. Both reads showed 97, zero attempts and an allowance of 20 above the 77-credit floor. This was local setup. The adapter stayed disabled and the single Blinkit Milk request stayed unsent. No provider API calls or credits were used. Shared MySQL was untouched.

The PowerShell initializer passed temporary-file checks for first setup, idempotent verification, existing attempt preservation, conflicting balance and pending reservation rejection, exclusive locking and the 77-credit floor. In a temporary backend copy, `mvn.cmd -o -B test '-Dspring.profiles.active=test' '-Dtest=QuickCommerceProviderTest,QuickCommerceAdminTest'` passed all 11 tests, zero failures or skips. The Java reader accepts a settled ledger with zero attempts and rejects a pending ledger without a reserved request. Transport was mocked and the admin test used in-memory H2.

## QuickCommerceAPI budget persistence and dry run

The dry run checked the backend launch environment for the presence of `QUICKCOMMERCE_API_KEY`, `QUICKCOMMERCE_ENABLED` and `QUICKCOMMERCE_VERIFIED_START_BALANCE` and used `Test-Path -LiteralPath` to check for the ledger. The key and checkpoint were absent, the adapter was disabled, and there was no ledger. No live API request, credential change, dashboard verification, or credit spend occurred. The user-reported 97 remains unverified. [Dry-run plan and expected cost](QUICKCOMMERCE_DRY_RUN.md)

The first authorized call initializes `%USERPROFILE%/.cartiq/quickcommerce-budget.txt` from the verified dashboard checkpoint above 77. Each distinct search or item uses a locked reservation that is forced to disk and atomically installed before transport. Settlement requires a valid response and agreement between the remaining-credit header, body and expected one-credit debit. A timeout, error, interrupted request or unexpected balance leaves a pending reservation and zero spendable credits. A missing checkpoint, mismatch, corrupt ledger or unsupported atomic write also fails closed. A restart must use the same OS account and a fresh dashboard checkpoint equal to the settled ledger balance. It does not restore attempted credits or clear duplicates. There is no reset or automatic release for pending reservations. Keep the state and lock on the same local filesystem and keep other callers off the account during the experiment. The ledger is local to that OS profile.

The ledger stores only a balance, pending or settled status, and SHA-256 fingerprints of the outbound request scope. No key, raw response, provider price, product data or matching label is stored. The Spring Boot adapter keeps response data in memory for the requesting admin. Existing catalogue, mapping and price history code paths were not used. Shared MySQL was untouched, and there were no migrations or Expo changes.

Commands executed from the workspace and a temporary Java 17 backend copy with MySQL verification gates off, external providers disabled, and no explicit `QUICKCOMMERCE_ENABLED` override:

```powershell
npm.cmd run typecheck
npm.cmd test
mvn.cmd -o -B test '-Dspring.profiles.active=test'
```

| Suite | Actual result |
|---|---|
| Backend unit and H2 | 145 discovered, 135 passed, 10 MySQL-only skips, zero failures or errors. Includes nine provider tests and one admin H2 boundary test. |
| Expo | Type check passed. 37 tests passed, zero failures. |

The provider tests use a mocked HTTP transport and real temporary files for the ledger. Restart checks construct a fresh adapter over the same file to verify the floor, cap, fingerprint replay, and pending-state hold. A separate check attempts overlapping locks on the same file. Tests also cover wrong location, unexpected credit balance, corrupt ledger, exact INR conversion, unknown prices, stock status and multipack normalization. No real credits or shared database were used. The backend log is under `%USERPROFILE%/CartIQ-data/2026-11-15-quickcommerce-persistence`.

## QuickCommerceAPI development verification

`QUICKCOMMERCE_API_KEY` was absent from the backend process environment. The credential supplied in chat was not used or copied into the workspace. No requests reached QuickCommerceAPI, including its free endpoints. No live retailer returned, no credits were consumed by this task, and no live data was retained. The reported balance of 97 is user-provided. The before and after dashboard balances were not verified because no live testing session started.

The local Spring Boot admin endpoints are `GET /api/admin/providers/quickcommerce/search` and `GET /api/admin/providers/quickcommerce/item`. The parameters are `retailer` (`blinkit`, `zepto`, or `instamart`), `q` for search or `itemId` for details, `lat`, `lon`, and optional `pincode`. The provider is available only under the `local` profile. The current authorization and checkpoint procedure is in the dry-run report above. [Dry-run plan](QUICKCOMMERCE_DRY_RUN.md)

The initial development adapter used an in-process credit budget. The persistent ledger described above supersedes that design. [Billing and response documentation](https://quickcommerceapi.com/docs)

Executed `npm.cmd run typecheck`, `npm.cmd test`, and from a temporary backend source copy `mvn.cmd -o -B test '-Dspring.profiles.active=test' '-Dtest=QuickCommerceProviderTest'` followed by `mvn.cmd -o -B test '-Dspring.profiles.active=test'` with MySQL-only gates and real retailer integrations disabled. Mocked transport tests cover search and details schemas, INR minor-unit conversion, missing prices and stock, exact location and multipack distinctions, key placement, duplicates, timeouts and budget boundaries. The admin boundary check uses in-memory H2 and a disabled provider. No MySQL or schema changes were needed. Shared MySQL was untouched. [Source rights](INDIA_RETAILER_DATA_SOURCES.md#quickcommerceapi-development-access)

| Suite | Actual result |
|---|---|
| Targeted mock transport | Six passed |
| Final backend unit and H2 | 132 passed, 10 MySQL-only skips, zero failures. Includes six mock provider tests and one H2 admin boundary test. |
| Expo | Type check passed, 37 tests passed, zero failures |

## Authorized sample receipt and annotation readiness

**Partially complete.** The workspace and the local CartIQ evidence archive were inventoried. No approved provider grant or corresponding sample was available. No permission was verified against a signed grant, no sample hashes or validation counts exist, and no row was accepted, rejected, or imported. No human reviewer signed off and no adjudication or evaluation ran. [Grant and receipt record](AUTHORIZED_SAMPLE_INTAKE_RECORD.md)

| Readiness check | Executed command or method | Actual result |
|---|---|---|
| Local receipt inventory | `Get-ChildItem -LiteralPath "$env:USERPROFILE/CartIQ-data" -Recurse -File` and `rg --files --hidden --no-ignore` in the workspace and archive, filtered for grant, permission, provider names and common sample extensions, excluding dependencies and build outputs | Historical catalogue evidence, source code and prior test artifacts. No authorized delivery identified. |
| Frozen benchmark and program integrity | `Get-FileHash docs/india-matching-annotation-benchmark.json,database/evaluate-catalogue-matching.py,backend/src/main/java/com/smartcart/catalogimport/ProductMatching.java -Algorithm SHA256` and a second Python hash check | Baseline digest matched the recorded digest `ee0f7d384b047c8a8ddc278e6b28be7e10e5842509670e9b6a2c8b62dc6f05b0`. Evaluator and Java matcher digests matched their pre-edit values. |
| Read-only benchmark and intake checks | `python -B -` with an inline script using `runpy.run_path('database/evaluate-catalogue-matching.py')['load_pairs']`, comparing counts and checking the intake report fields and local handoff links | Passed. Loaded 73 baseline cases, with train 20, validation 25 and test 28, and 57 observed plus 16 controlled cases. Both provider statuses, dispositions, and handoff links were present. No scoring ran. |

No application suite or sample validation ran in this documentation phase. No database environment was used, no migration ran and shared MySQL was untouched. No provider request was sent and no live adapter was used. Prior V11 test results appear below.

## Authorized data readiness and matching evidence

### Scope

The mapping ledger stores evidence references, SHA-256, permission reference, submitted and canonical identity, reviewer and server time, and the prior active mapping. Service tests cover replay, explicit supersession, blocked approval with previously rejected evidence, exact GTIN with incompatible pack, unconfirmed named variants and formulation, and the observation reassignment guard. The HTTP test exercises admin-only access, authenticated attribution, and a rejected approval replay for an unresolved decision. The V10-to-V11 test compares the full legacy listing, mapping, current-price and history rows before and after the schema-only migration and verifies Flyway validation. V11 creates the empty audit ledger and adds its index without backfilling unsupported evidence.

### Commands and environments

Frontend used the workspace. Backend commands ran from temporary copies using Java 17.0.12 and Maven 3.9.16. The H2 run used the test profile and in-memory schemas, with the real MySQL gates disabled. For MySQL verification, initialized a new MySQL 8.0.44 data directory with `mysqld --no-defaults --initialize-insecure`, started a loopback-only server on port `13316`, and verified the actual listener process and data directory before creating `cartiq_catalogue_verify_retry` and `cartiq_mapping_upgrade`. The first setup check confused the Windows launcher PID with the listening child. Process and data-directory verification resolved the startup check, then a temporary password was set before running tests. The application bootstrap for the legacy catalogue contract used `mysql-local`, `SPRING_FLYWAY_TARGET=10`, disabled sample and starter data, and the verified scratch JDBC URL. The mapping suite also exercised a separate, fresh V10-to-V11 upgrade schema. Shared MySQL was untouched.

```powershell
npm.cmd run typecheck
npm.cmd test
mvn.cmd -o -B test "-Dspring.profiles.active=test"
mvn.cmd -o -B spring-boot:run
mvn.cmd -o -B test "-Dtest=MysqlCatalogueImportTest"
mvn.cmd -o -B test "-Dtest=MysqlMilestone2Test,MysqlMilestone3Test,MysqlMilestone4Test,ProviderMappingMigrationTest#preservesLegacyMappingOnMysqlUpgrade"
mvn.cmd -o -B test "-Dtest=MysqlMilestone2Test,MysqlMilestone3Test,MysqlMilestone4Test"
```

The MySQL catalogue run used the existing `CARTIQ_CATALOGUE_MYSQL_*` gate and scratch credentials, with V10 explicitly validated before V11. The mapping run used the existing `CARTIQ_MYSQL_VERIFY*` settings pointing to the disposable listener and `CARTIQ_MAPPING_UPGRADE_MYSQL=true` for the dedicated upgrade schema. All tests used local fixtures and kept live adapters disabled. No provider request, sample, dataset import, matching replay against new retailer data, or human review was performed. The original benchmark SHA-256 remains `ee0f7d384b047c8a8ddc278e6b28be7e10e5842509670e9b6a2c8b62dc6f05b0`. The existing Python evaluator loaded all 73 baseline cases without scoring them. [Benchmark readiness](PRODUCT_MATCHING_BENCHMARK.md) · [Provider permissions](INDIA_RETAILER_DATA_SOURCES.md)

### Results

| Scope | Actual result |
|---|---|
| Expo frontend | TypeScript passed. 37 tests passed. |
| Backend unit and H2 | 135 discovered, 125 passed, 10 MySQL-only skips, zero failures or errors. Includes the admin HTTP boundary, durable mapping workflow, and V10-to-V11 preservation test. |
| Disposable MySQL, V10 | Two catalogue integration tests passed, with zero skips. |
| Disposable MySQL, V11 | Dedicated V10-to-V11 preservation test passed on the first run. Seven MySQL contract tests passed on rerun, with zero skips. |

The first combined V11 run reported one failing legacy catalogue/cart test out of eight. A fresh schema lacked the hardcoded `blr-indiranagar` sample location and product search returned 404. The test now creates its own location inside its rollback transaction. The seven-test contract rerun passed. The dedicated upgrade test passed before the rerun and its schema retained one legacy fixture mapping, one current observation and one historical observation, with an empty decision ledger. The main disposable schema had zero retained mappings, decision rows, current observations and price-history rows after rollback. SQL confirmed Flyway V1–V11 and the V11 foreign keys and unique keys. Test logs and verification TSVs are in `%USERPROFILE%/CartIQ-data/2026-11-15-authorized-data-readiness`.

## Licensed sample research and second annotation pass

### Permissions and mapping safeguards

Verified provider contact routes and drafted a request for a scoped sample with separate retention, evaluation, derivative-label, and public-comparison grants. No request was sent, no permission or sample was obtained, and live adapters stayed disabled. [Provider register and request draft](INDIA_RETAILER_DATA_SOURCES.md)

Rechecked manual canonical mapping, blocked barcode and pack conflicts, original reviewer attribution on replay, and the 409 reassignment guard. Added an integration fixture assertion that the guard still rejects reassignment when only historical observations remain. It verifies unchanged history counts, SKU, reviewer and approval timestamp. The fixture runs in H2 and the disposable MySQL suite and rolled back. The current schema keeps the current approval actor and time. Source evidence and a complete mapping revision history would require a separately reviewed schema change.

| Scope | Executed command | Result |
|---|---|---|
| Frontend | `npm.cmd run typecheck` and `npm.cmd test` | TypeScript passed, 37 tests passed. |
| Backend unit only | Maven `test` with the five unit test classes listed below | 57 passed, zero skips. |
| H2 integration only | Maven `test` with the 12 integration classes listed below | 66 passed, zero skips. |
| Full backend regression | `mvn.cmd -o -B test "-Dspring.profiles.active=test"` | 123 passed, nine MySQL-only skips, zero failures or errors. |
| Disposable MySQL 8.0.44 | `mvn.cmd -o -B test "-Dtest=MysqlCatalogueImportTest,MysqlMilestone2Test,MysqlMilestone3Test,MysqlMilestone4Test"` | Nine passed, zero skips. Used `cartiq_catalogue_verify_retry` on loopback port 13316 with existing Flyway V1–V10. |

Separate temporary source copies ran with Maven and Java 17. The genuine-data scratch schema stayed at 30 active products, 22 inactive products, 55 variants, four existing listing rows, and zero current or historical price observations. No new migrations ran on MySQL. Shared MySQL was untouched.

```powershell
mvn.cmd -o -B test "-Dspring.profiles.active=test" "-Dtest=CatalogueImportNormalizerTest,OpenFoodFactsAdapterSafetyTest,ProductMatchingTest,ProviderBoundaryTest,ProviderContractTest"
mvn.cmd -o -B test "-Dspring.profiles.active=test" "-Dtest=CatalogHttpTest,CatalogueImportFoundationTest,CatalogueImportSafetyTest,ComparisonHttpTest,ComparisonSecurityTest,EmailFlowTest,LocalStoreHttpAuditTest,LocalStoreTest,MobileApiHttpIntegrationTest,PriceObservationIntegrationTest,StarterCatalogueTest,WorkspaceIntegrationTest"
```

### New independently reviewed cohort

Two separate AI reviewers examined all 19 new observed pairs. Agreement was 17/19 (89.5%). Both disagreements remained unresolved. Final labels are five same, 11 distinct and three unresolved. The expanded data has 57 observed pairs and 16 historical controlled variants. The following results use only the eight new held-out observed pairs, with three confirmed matches and five confirmed distinct pairs. [Evidence, annotations and split checks](PRODUCT_MATCHING_BENCHMARK.md)

| Method | TP / FP / FN / TN | Precision | Recall | F1 | FPR | FNR | Review routing accuracy |
|---|---|---:|---:|---:|---:|---:|---:|
| Existing CartIQ | 0 / 0 / 3 / 5 | — | 0% | 0% | 0% | 100% | 75% |
| GTIN-only | 1 / 0 / 2 / 5 | 100% | 33.3% | 50% | 0% | 66.7% | 37.5% |
| Brand/title | 0 / 0 / 3 / 5 | — | 0% | 0% | 0% | 100% | 100% |
| Hybrid | 1 / 0 / 2 / 5 | 100% | 33.3% | 50% | 0% | 66.7% | 75% |
| Conservative rule | 1 / 0 / 2 / 5 | 100% | 33.3% | 50% | 0% | 66.7% | 75% |

Precision is undefined without a positive recommendation. Binary recall treats a deferred positive as missed. Review routing measures whether the method used review when the label was unresolved. There are zero unresolved cases in the fresh holdout, so brand/title's 100% review-routing accuracy reflects that it never deferred a case. The new validation split has two unresolved cases. The default, hybrid and conservative methods routed both to review. GTIN-only made positive recommendations for both. Brand/title classified both as distinct.

All five methods missed both confirmed positives in the five missing-GTIN test pairs. Default, hybrid, conservative and brand/title correctly classified all three distinct pairs in that slice, including the observed MTR single-pack versus two-pack case. GTIN-only deferred all five missing-GTIN pairs. In the separate controlled Bourbon example, `in-te-c4`, GTIN-only was the only method to recommend the wrong match. No matcher was fitted or calibrated, and these samples do not establish production performance.

There were 19 passing offline evaluator assertions covering rule decisions, slice classification and report behavior. The 47-pair legacy HTTP replay produced the same output hash, and the unextended 54-case output matched the archived result with the original flags. The extension ran through both authenticated admin matching methods over a Spring Boot API on loopback with in-memory H2. The eight-case test replay was byte-identical. Commands used a temporary admin JWT in `CARTIQ_ADMIN_TOKEN`.

```powershell
python -B database/evaluate-catalogue-matching.py docs/india-matching-annotation-benchmark.json --api http://127.0.0.1:18891/api --hybrid --conservative --split validation --origin extension --output "$env:USERPROFILE/CartIQ-data/2026-11-15-independent-annotation/extension-validation.json"
python -B database/evaluate-catalogue-matching.py docs/india-matching-annotation-benchmark.json --api http://127.0.0.1:18891/api --hybrid --conservative --split test --origin extension --output "$env:USERPROFILE/CartIQ-data/2026-11-15-independent-annotation/extension-test.json"
python -B database/evaluate-catalogue-matching.py docs/india-matching-annotation-benchmark.json --api http://127.0.0.1:18891/api --hybrid --conservative --output "$env:USERPROFILE/CartIQ-data/2026-11-15-independent-annotation/expanded-benchmark-results.json"
python -B database/evaluate-catalogue-matching.py docs/india-matching-annotation-benchmark.json --api http://127.0.0.1:18891/api --hybrid --conservative --split test --origin extension --output "$env:USERPROFILE/CartIQ-data/2026-11-15-independent-annotation/extension-test-replay.json"
```

The benchmark archive under `%USERPROFILE%/CartIQ-data/2026-11-15-independent-annotation` keeps reviewer inputs and outputs, distinct validation and test reports, full per-pair results, and database verification logs.

| File | SHA-256 |
|---|---|
| `docs/india-matching-annotation-benchmark.json` | `ee0f7d384b047c8a8ddc278e6b28be7e10e5842509670e9b6a2c8b62dc6f05b0` |
| `extension-validation.json` | `b27946294b178f48f9989a04e89805ceb7764ce019e92fa3dae8816cd57b45d8` |
| `extension-test.json` and `extension-test-replay.json` | `26aff393bbdd27812ba4ff06c394101d00e67778b81a77f0f3973c36f97a8fea` |
| `expanded-benchmark-results.json` | `3053fb2b71596cbf29c11cdee2ebb3398510326f8afa4a1a2ea0cda011c7735` |

## India-focused hybrid matching and licensed data review

### Source authorization and price readiness

Reviewed official retailer channels, Rewalto, GS1 DataKart, ONDC, Udaan, Open Food Facts and Open Prices. No CartIQ-approved retailer feed or licence covered this milestone's intended use. All adapters stayed disabled. Daily data delivery alone does not meet the existing 15-minute freshness rule. Production would need explicit rights, evaluation data, scoped credentials, and durable observation identity and evidence. [Source, fields, scope, cadence, and rights matrix](INDIA_RETAILER_DATA_SOURCES.md)

No retailer records or prices were collected or imported. SQL on the disposable genuine-data schema confirmed 30 active products, 22 inactive products, 55 variants, four pre-existing platform mapping rows, and zero current or historical price observations. The separate disposable MySQL test schema rolled back the new mappings and observation fixtures. No migrations were added or applied to MySQL. Shared MySQL was untouched.

### Admin mapping checks

The new read-only mapping preview returns the actual canonical variant's pack and GTINs. H2 tests exercise the authenticated route. Database tests cover incompatible single-versus-multipack declarations, conflicting GTINs, review rejection without writes, the valid manually approved path, reviewer attribution on replay, and the existing 409 reassignment guard once observations exist. Price tests reuse the same location-specific, stale and replay coverage in H2 and disposable MySQL. Current mapping audit stores actor/time, while immutable listing evidence and full revision history remain pending. [Mapping and price foundation](RETAILER_LISTING_FOUNDATION.md)

### India benchmark results

The 54 cases contain 38 observed source pairs and 16 controlled input changes. The observed held-out split has three confirmed matches, eight distinct pairs and three unresolved pairs. A deferral counts as no positive recommendation. Unresolved ground truth is excluded from binary matching metrics. A dash means the method made no positive recommendation on confirmed labels and precision is undefined. [Dataset, evidence and missed matches](PRODUCT_MATCHING_BENCHMARK.md)

| Method, 14 observed holdout cases | TP / FP / FN / TN | Precision | Recall | F1 | FPR | FNR | Review classification | Unresolved reviewed |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| Existing CartIQ | 0 / 0 / 3 / 8 | — | 0% | 0% | 0% | 100% | 71.4% | 2 / 3 |
| GTIN-only | 3 / 0 / 0 / 8 | 100% | 100% | 100% | 0% | 0% | 78.6% | 0 / 3 |
| Brand/title | 0 / 0 / 3 / 8 | — | 0% | 0% | 0% | 100% | 78.6% | 0 / 3 |
| Hybrid | 0 / 0 / 3 / 8 | — | 0% | 0% | 0% | 100% | 78.6% | 3 / 3 |

Brand/title also recommended a match on one unresolved pair. The existing and hybrid scorers deferred all three observed positives. This gives both a 72.7% decision accuracy on the 11 definite pairs with two usable GTINs, compared with 100% for GTIN-only and 72.7% for brand/title. Across the six controlled holdout cases, every method missed all four positives. GTIN-only made one false match on the two distinct items. The hybrid made none and deferred all four positive cases. Policy confidence is uncalibrated. No ML model was trained.

Ran validation, test and then all splits on the frozen matcher through the authenticated admin endpoints on loopback H2. Execute from the repository root after setting a short-lived admin token in `CARTIQ_ADMIN_TOKEN` on the disposable server.

```powershell
$evidence = Join-Path $env:USERPROFILE 'CartIQ-data/2026-11-14-india-hybrid-benchmark'
foreach ($split in 'validation','test','all') {
    python -B database/evaluate-catalogue-matching.py docs/india-matching-benchmark.json --api http://127.0.0.1:18891/api --output "$evidence/$split.json" --hybrid --split $split
}
```

The dataset SHA-256 was `ef495d8a22ea41c83dba4af86ccab00bdc97ea6adeafdbddf378470befa92af`. A second holdout run reproduced result SHA-256 `49b685032c37ba96ab2cfda4b597c151e9c8699b717a62a726e80e76d88025de`. The first evaluation request after restarting Spring returned 401 before the local admin was ready. The retry completed all three splits.

### Executed suites

| Scope | Command | Outcome |
|---|---|---|
| Frontend | `npm.cmd run typecheck` and `npm.cmd test` | TypeScript passed and 37 tests passed. |
| Targeted matcher and provider unit tests | `mvn.cmd -o -B test "-Dtest=ProductMatchingTest,ProviderBoundaryTest,ProviderContractTest"` | 18 passed. |
| Backend unit and isolated H2 | `mvn.cmd -o -B test "-Dspring.profiles.active=test"` | 132 detected, 123 passed, nine MySQL-gated skips, zero failures and errors in the final run. |
| Disposable MySQL 8.0.44 | `mvn.cmd -o -B test "-Dtest=MysqlCatalogueImportTest,MysqlMilestone2Test,MysqlMilestone3Test,MysqlMilestone4Test"` | Nine passed, zero skipped. The existing V1–V10 migrations validated in `cartiq_catalogue_verify_retry` on loopback port 13316. |

Maven 3.9.16 and Java 17 ran in temporary source copies. A first H2-suite run exposed asymmetric stripping of a brand which was part of the product name. The normalization was corrected before India dataset evaluation, and the full suite passed on rerun. The read-only historical benchmark replay still reproduced SHA-256 `51e50d1b9d9f9ce1a6b1e284e0af12d57a130f19da90ec76a2b7e5189047759b`.

## Product matching evaluation and retailer integration preparation

### Catalogue identity second pass

Re-reviewed the 33 held records. The approved rows were Bisleri 1 L (`8906017290040`), Kinley 1 L (`8901764082405`), Happy Happy Choco-Chip Cookies 31.5 g (`8901719129988`) and Cadbury Dairy Milk 11 g (`7622202334009`). Barcodes and retail pack evidence were linked to the correction audit. Exact identity was established for two out-of-scope medication labels, which were rejected. The remaining 27 candidates stayed on hold. The per-record log preserves sources and evidence gaps. [Identity evidence](INDIA_IDENTITY_EVIDENCE.md)

Spring Boot ran via `mvn.cmd -o -B spring-boot:run` from a temporary copy, on loopback HTTP port 18887 with `mysql-local`, explicit JDBC URL `jdbc:mysql://127.0.0.1:13316/cartiq_india_pilot?connectionTimeZone=UTC`, `SPRING_FLYWAY_ENABLED=false` and `REWALTO_ENABLED=false`. The operation used the existing admin review, correction, approval and rejection routes. The local evidence directory `%USERPROFILE%/CartIQ-data/2026-11-14-retailer-preparation` contains the image bytes, checksums, signed-operation outcomes and SQL result summary. The replay of all four approvals returned `DUPLICATE`. Product detail and exact public barcode lookup returned 404 for all four drafts. The existing Parle-G lookup remained public with zero offers. No new products were published.

Final SQL counts on disposable MySQL 8.0.44 were 30 active products, 22 inactive products, 55 variants, 28 distinct canonical barcodes, 28 provenance rows, 11 correction audit rows, 58 retained raw batch rows, four existing retailer mapping rows, zero current prices and zero price history. The real-data catalogue remained isolated from the price test fixtures. Shared MySQL received no writes or migrations.

### Matching benchmark

The 47-pair benchmark contains 14 same-variant, 25 distinct-variant and eight unresolved labels. Cases cover eight pack size differences, two different brands with similar names, five flavours, five formulations, three multipack versus single-pack comparisons, two similar non-identical items, six ambiguous listings and two incomplete listings. The 14 exact same pairs use distinct published source records and a single manual annotation pass done before scoring. Source records include Indian Open Food Facts and independently published product/pack evidence, plus US manufacturer SmartLabel and USDA branded-food examples. A record ID, normalized GTIN or declared product family may appear in only one split. [Dataset and evidence](product-matching-benchmark.json)

| Holdout method | TP / FP / FN / TN | Precision | Recall | F1 | FPR | FNR | Valid-GTIN decision accuracy | Review classification | Unresolved sent to review |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Existing scorer | 1 / 0 / 3 / 9 | 100% | 25% | 40% | 0% | 75% | 76.9% | 75% | 2 / 3 |
| GTIN-only | 4 / 0 / 0 / 9 | 100% | 100% | 100% | 0% | 0% | 100% | 81.25% | 0 / 3 |
| Brand/title Jaccard ≥ 0.70 | 3 / 7 / 1 / 2 | 30% | 75% | 42.9% | 77.8% | 25% | 38.5% | 81.25% | 0 / 3 |

The holdout contains four matching, nine distinct and three unresolved pairs. Binary metrics exclude unresolved truth and treat review as no automatic match. Valid-GTIN decision accuracy uses the 13 definite holdout pairs with two usable GTINs. Review classification tests whether each pair's unresolved label was correctly predicted. The resolved majority favours a system that never reviews, so the unresolved recall column is reported separately. Across all 47, the scorer found one exact match, deferred 13 confirmed positives, made zero false matches, and routed six of eight unresolved cases to review. GTIN-only benefited from selecting independently corroborated positives with the same printed code. High/medium/low are policy labels. Confidence calibration and production accuracy were not estimated. No model was trained.

Evaluated the unchanged scorer through the admin batch endpoint on disposable H2 at `http://127.0.0.1:18889/api`. After setting a short-lived admin token in `CARTIQ_ADMIN_TOKEN`, the replay command from the repository root was:

```powershell
$results = Join-Path $env:USERPROFILE 'CartIQ-data/2026-11-14-matching-benchmark/results.json'
python -B database/evaluate-catalogue-matching.py docs/product-matching-benchmark.json --api http://127.0.0.1:18889/api --output $results
```

A second replay against the same H2 backend reproduced the output SHA-256 `51e50d1b9d9f9ce1a6b1e284e0af12d57a130f19da90ec76a2b7e5189047759b`. Reviewed 59 identity references against 43 archived source hashes. One identity uses a link-only catalogue reference. [Benchmark method and confidence counts](PRODUCT_MATCHING_BENCHMARK.md)

### Price observation checks

- The H2 and MySQL integration fixture uses separate PINs and rolls back its rows. It exercises initial history insertion, a fractional timestamp replay, newer out-of-stock and unknown availability, late history, source attribution, stale eligibility and the 409 listing reassignment guard.
- Replaying an observation at the current timestamp preserves current price, source timestamp and comparison evidence. A late record may contribute price history while the latest current row remains in effect. A null price from an availability update creates no history row.
- The adapter contract tests mock provider responses. No requests were sent to a live retailer or paid feed. Direct access, public-display rights, multiple stores within one PIN, and distinct upstream events within one second are pending.

### Executed suites

| Scope | Command | Outcome |
|---|---|---|
| Frontend static and unit | `npm.cmd run typecheck` and `npm.cmd test` | TypeScript passed. 37 tests passed. |
| Adapter unit/contract | `mvn.cmd -o -B test "-Dtest=ProviderBoundaryTest,ProviderContractTest"` | Seven tests passed. |
| Backend unit and isolated H2 | `mvn.cmd -o -B test "-Dspring.profiles.active=test"` | 128 detected, 119 passed, nine MySQL-gated skips, zero failures or errors. |
| Disposable MySQL 8.0.44 | `mvn.cmd -o -B test "-Dtest=MysqlCatalogueImportTest,MysqlMilestone2Test,MysqlMilestone3Test,MysqlMilestone4Test"` | Nine passed, including price-observation integration with rollback. Zero failures, errors or skips. |

Both backend suites used temporary source copies, Maven 3.9.16 and Java 17. The MySQL test schema was `cartiq_catalogue_verify_retry` on port 13316 with the verification gates documented below. Flyway validated the existing MySQL V1–V10 history. In-memory H2 used the existing migrations to initialize the test schemas.

## Verified catalogue expansion and product matching

### Evidence review and disposable MySQL

- Reviewed the 30 correction cases, six incomplete cases and one conflicting record. Three corrections were made from GTIN-linked packaging evidence and source provenance. One conflicted identity was rejected. Twenty-seven correction and six incomplete cases are held. Rostaa, the previous ready record, was also approved. [Identity decisions and sources](INDIA_IDENTITY_EVIDENCE.md)
- Staged six more genuine food records in three original-source batches from the 76-GTIN balanced extract. The accepted records cover ghee, dairy, three spice blends and atta. Each batch retained its own source URL, response hash and payload digest. Approved these six and the four earlier candidates as ten separate inactive products with one variant apiece. No new product was published. Earlier published items remained active.
- Every approval replay returned `DUPLICATE` with the same variant ID. All ten draft details and barcode requests returned 404, and the existing Parle-G barcode still returned its public product with zero offers. SQL verified links between products, variants, barcode rows and provenance, normalized quantities and units, brand labels, category IDs and inactive status. All six new stored raw rows matched their archived JSONL rows by SHA-256. There were 58 retained raw batch rows, 24 distinct registered GTINs, 24 provenance records, seven correction audit rows, 51 total variants, 30 active products and 18 inactive drafts. Four prior retailer mapping rows remained, with zero current and zero historical price observations.

The local evidence archive is `%USERPROFILE%/CartIQ-data/2026-11-14-verified-catalogue`. It holds the three manifest-verified JSONL subsets, image evidence and hashes, `approval-results.json`, the SQL summary and matching output. The original source response archive remains in `%USERPROFILE%/CartIQ-data/2026-11-14-catalogue-quality`.

Ran Spring Boot via `mvn.cmd -o -B spring-boot:run` on loopback HTTP port 18886 in a temporary backend copy. The `mysql-local` profile used `jdbc:mysql://127.0.0.1:13316/cartiq_india_pilot?connectionTimeZone=UTC`, `SPRING_FLYWAY_ENABLED=false` and `REWALTO_ENABLED=false`. Port 18884 was occupied, so the clean run moved to 18886. The disposable MySQL 8.0.44 data directory was identified before writes. The existing V1–V10 schema was reused. Shared MySQL was untouched.

Run from the repository root with a short-lived admin token in `CARTIQ_ADMIN_TOKEN` for the disposable Spring Boot endpoint.

```powershell
$data = Join-Path $env:USERPROFILE 'CartIQ-data/2026-11-14-verified-catalogue'
$api = 'http://127.0.0.1:18886/api'
python -B database/stage-off-snapshot.py "$data/dairy.jsonl" --api $api --manifest "$data/dairy.manifest.json" --chunk-size 1 --max-chunks 1
python -B database/stage-off-snapshot.py "$data/spices.jsonl" --api $api --manifest "$data/spices.manifest.json" --chunk-size 1 --max-chunks 1
foreach ($sample in 'dairy','spices','flour-alt') {
    python -B database/stage-off-snapshot.py "$data/$sample.jsonl" --api $api --manifest "$data/$sample.manifest.json" --chunk-size 1
}
```

The dairy and spice batches stopped after one committed row and resumed from offset 1. After all three completed, each command was replayed and reported its completed offset. The approval results are the captured outcomes of separate admin correction and approval requests. The reviewed allowlist and signed HTTP checks are preserved in the local archive.

### Matching evaluation

Executed all 21 source-linked pairs through authenticated `POST /api/admin/catalogue-matching/review` against isolated in-memory H2 using `python -B "$data/evaluate-matching.py"` from the repository root. The evaluator submitted the source GTIN, brand, name and pack fields. Flavour and formulation conflicts were exercised separately in Java tests. All archive response hashes matched. Family- and GTIN-grouped split counts are 8 training, 6 validation and 7 test candidates. These are reserved splits for future experimentation. The rule run used all splits and trained no model.

| Human label | Rule result |
|---|---|
| Seven same-variant pairs | Seven exact GTIN. These come from repeated entries on archive pages. |
| Eight distinct-variant pairs | Eight distinct. Includes different amounts and differently named spice formulations. |
| Six unresolved pairs | One review, three insufficient information and two distinct. The distinct outputs were the unitless Maggi rows with differing raw brand labels and the conflicting 600/740 mL Sprite rows. Both remain on hold in the evidence record. |

The endpoint is advisory and makes no catalogue or retailer writes. Source repeats cannot validate cross-retailer matching performance. Independent barcode-linked packaging and authorized feed pairs are needed. [Labelled pairs](india-product-matching-evaluation.json)

### Executed test suites

| Scope | Command | Result |
|---|---|---|
| Frontend static check | `npm.cmd run typecheck` | Passed. |
| Frontend suite | `npm.cmd test` | 37 passed. |
| Backend unit and H2 | `mvn.cmd -o -B test "-Dspring.profiles.active=test"` | 122 discovered, 114 passed, eight gated MySQL skips. Zero failures and errors. Includes eight matching unit tests, five catalogue foundation H2 tests and two comparison H2 tests. |
| Real disposable MySQL | `mvn.cmd -o -B test "-Dtest=MysqlCatalogueImportTest,MysqlMilestone2Test,MysqlMilestone3Test,MysqlMilestone4Test"` | Eight passed with no skips. Existing Flyway history validated. Recovery, duplicate protection and fixture publication passed. |

Maven used Java 17 and temporary copies of the backend source. The MySQL contract suite used schema `cartiq_catalogue_verify_retry` on port 13316 and the MySQL gate variables documented below. Early backend test compiles failed while the new remap regression was being wired to an isolated fixture. The corrected suite and MySQL rerun both passed. No provider sync was enabled. Native device verification remains pending.

## Catalogue quality review and category sample

### Previous pilot review

| Recommendation | Candidates |
|---|---:|
| Ready for approval | 9 |
| Requires correction or reconciliation | 30 |
| Insufficient information | 6 |
| Invalid product identity | 1 |
| Confirmed duplicate | 0 |

All 46 candidate GTINs passed checksum validation and were distinct in the pilot. A valid checksum only checks barcode structure. The report records reasons, including contradictory product labels, unclear pack sizes, mixed brand strings and potential overlap with starter items. The invalid identity and ambiguous rows were left in the existing queue. [46-candidate report](INDIA_CANDIDATE_REVIEW.md)

Signed admin requests approved eight existing candidates in the disposable genuine-data schema. They created eight products and eight variants as inactive drafts. Replaying all eight calls returned `DUPLICATE`. Public details and barcode routes returned 404 for each draft. The previously published Parle-G variant remained accessible. The API also returned the expected 90 g result when search, brand, category and normalized pack filters were combined, and two sequential category pages did not share products.

Final SQL counts for `cartiq_india_pilot` on disposable MySQL 8.0.44: 8 imported candidates, 6 published, 26 needing correction and 12 pending review. The schema contained 30 active and 8 inactive products, 41 variants, 14 provenance rows, 14 barcode rows, four existing retailer mappings and zero price observations. No new candidate was published. The process ran from a temporary backend copy using `mvn -o -B spring-boot:run`, profile `mysql-local`, explicit loopback JDBC port 13316, HTTP port 18884 and `SPRING_FLYWAY_ENABLED=false`. The V1–V10 migration history was already present. The disposable server was stopped after verification and shared MySQL was left untouched.

### Category-balanced sample

| Category | Page records examined | India market | Valid GTIN rows | Product groups | Unique variants | Accepted for review | Rejected | Needs correction | Missing brand | Missing pack | Over quota |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Rice and grains | 25 | 25 | 25 | 19 | 25 | 8 | 0 | 11 | 6 | 6 | 6 |
| Flour and atta | 20 | 20 | 20 | 18 | 20 | 8 | 0 | 6 | 0 | 3 | 6 |
| Pulses and lentils | 20 | 20 | 20 | 18 | 20 | 8 | 0 | 7 | 0 | 5 | 5 |
| Cooking oil and ghee | 24 | 24 | 1 | 1 | 1 | 1 | 0 | 0 | 0 | 0 | 0 |
| Spices and masalas | 25 | 25 | 25 | 18 | 25 | 8 | 0 | 16 | 4 | 8 | 1 |
| Dairy | 24 | 24 | 23 | 21 | 23 | 8 | 0 | 8 | 0 | 2 | 7 |
| Packaged snacks | 59 | 59 | 20 | 9 | 10 | 7 | 10 | 3 | 0 | 4 | 0 |
| Biscuits and bakery | 79 | 79 | 33 | 14 | 16 | 8 | 17 | 4 | 0 | 1 | 4 |
| Beverages | 59 | 59 | 32 | 17 | 23 | 8 | 9 | 10 | 1 | 1 | 5 |
| Sauces and condiments | 64 | 64 | 26 | 18 | 24 | 8 | 2 | 7 | 2 | 3 | 9 |
| Personal care | 20 | 20 | 9 | 5 | 9 | 4 | 0 | 5 | 2 | 2 | 0 |
| Household essentials | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

- Examined 258 returned objects across 11 distinct successful API pages dated 2026-11-14 UTC, including 238 Open Food Facts objects and 20 Open Beauty Facts objects. Pages can contain the same item or feed more than one category. In the table the first two columns count page occurrences before category classification. The remaining columns count category-matched occurrences or distinct groups. Product groups use normalized brand and name as a provisional estimate. Variants use distinct checksum-valid GTINs. The 38 rejected occurrences repeated a category GTIN.
- The extract selected 76 distinct GTINs across 11 categories against a target of eight per category. Of those, 21 already appear in the 52-candidate pilot and 55 are outside that pilot. This is a comparison against the pilot archives. The categories include 77 occurrences needing correction or more evidence, 43 eligible over quota, 15 missing brand and 35 missing pack text. Allowed cases may still need reconciliation. Neither this extract nor the four Open Beauty Facts rows was staged. The existing food importer needs a source-specific intake path before personal-care ingestion.
- Selection requires the exact `en:india` sales-market tag and recorded category labels. Manufacturing and ingredient-origin signals are preserved separately. The source uses scan-ranked pages, so this is a bounded convenience sample. Fifteen requests returned 503. The cooking oil and ghee group recovered one ghee from the dairy page. Open Products Facts returned no HTTP response for three API probes or a bounded bulk-prefix request, so household coverage remains unmeasured. Preserve the source responses for replay because upstream rankings and records can change. [Open Food Facts API](https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/) · [Product schema](https://openfoodfacts.github.io/documentation/docs/Product-Opener/schemas/schemas/product/)
- Attribute Open Food Facts contributors and retain ODbL 1.0 for the database, DbCL 1.0 for individual contents and CC BY-SA 3.0 for images. Preserve Open Beauty Facts attribution, its ODbL dataset licence and the image-level CC BY-SA terms. The Open Products Facts listing specifies ODbL. No images were downloaded during sampling. Confirm the share-alike and packaging-image obligations before distribution. [Open Food Facts reuse guidance](https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/tutorials/license-be-on-the-legal-side/) · [Open Beauty Facts licence record](https://www.data.gouv.fr/datasets/open-beauty-facts) · [Open Beauty Facts image terms](https://wiki.openfoodfacts.org/images/e/ed/OpenBeautyFacts.pdf) · [Open Products Facts licence record](https://www.data.gouv.fr/datasets/open-products-facts)

### Reproduction and verification

The Windows archive is `%USERPROFILE%/CartIQ-data/2026-11-14-catalogue-quality`. It contains original response bodies, headers and URLs in `raw-pages`, the earlier input JSONL and approval evidence in `previous-pilot`, and the category payloads with a SHA-256 manifest in `balanced-output`. The extractor records failed responses and resumes missing pages. To collect missing pages, use `--fetch` with a contact-bearing User-Agent. To retry failed pages, use `--retry-failed` with a fresh copy of the archive. For the verification below, replay ran offline with Python 3.14.5 in PowerShell from the project root.

```powershell
$archive = Join-Path $env:USERPROFILE 'CartIQ-data/2026-11-14-catalogue-quality'
$prior = Join-Path $archive 'previous-pilot'
$replay = Join-Path $env:TEMP ('cartiq-balanced-archive-replay-' + [guid]::NewGuid().ToString('N'))
python -B database/extract-india-category-sample.py "$archive/raw-pages" $replay --quota 8 --compare-jsonl "$prior/india.jsonl" --compare-jsonl "$prior/india-api.jsonl"
Get-FileHash -LiteralPath "$replay/manifest.json"
```

All 13 replay outputs matched the archived files by SHA-256. The manifest digest is `bea64e0c761bf2b41dacbfdf47c4caa22d11aaaa6eaf25b9cdbd0cacdc099115`.

| Test scope | Command | Observed result |
|---|---|---|
| Frontend static checks | `npm.cmd run typecheck` | Passed. |
| Frontend unit tests | `npm.cmd test` | 37 passed. |
| Backend unit and H2 integration suites | `mvn.cmd -o -B test "-Dspring.profiles.active=test"` | 119 discovered, 111 passed, eight gated MySQL skips, zero failures or errors. |
| Targeted matching unit and H2 integration | `mvn.cmd -o -B test "-Dtest=ProductMatchingTest,CatalogueImportFoundationTest"` | 11 passed. Endpoint authorization and read-only behavior verified. |
| Disposable MySQL integration | `mvn.cmd -o -B test "-Dtest=MysqlCatalogueImportTest,MysqlMilestone2Test,MysqlMilestone3Test,MysqlMilestone4Test"` | Eight passed, zero skipped. Verified existing V1–V10 history, checkpoint restart and recovery, lease/offset conflicts, raw retention, review and publication, duplicate prevention, search, category and pack filters, exact barcode lookup and pagination using test fixtures. |

Maven 3.9.16 and Java 17 ran in temporary backend copies, and Node 24 ran frontend checks. The MySQL test schema `cartiq_catalogue_verify_retry` was on loopback port 13316 with the existing Flyway V10 target. The catalogue test gate used `CARTIQ_CATALOGUE_MYSQL_VERIFY`, `CARTIQ_CATALOGUE_MYSQL_USERNAME` and `CARTIQ_CATALOGUE_MYSQL_PASSWORD`. The legacy gates used `CARTIQ_MYSQL_VERIFY`, `CARTIQ_MYSQL_VERIFY_URL`, `CARTIQ_MYSQL_VERIFY_USERNAME` and `CARTIQ_DB_PASSWORD`. Earlier in development two new matching assertions failed on separator variants and comma-separated brand labels. The normalization was corrected before the passing runs above.

The matching dataset contains one repeated observation of the same real source record, four distinct-item pairs and two unresolved pairs. Source references and archived hashes were checked. It is a starting point for annotation, with no trained model or quality estimate. [Labelled pairs](india-product-matching-labels.json)

## India catalogue expansion and MySQL verification

| Measure | 512 MiB bulk prefix | India search page | Pilot total |
|---|---:|---:|---:|
| Source records examined | 135,111 | 39 | 135,150 |
| India-relevant records staged | 13 | 39 | 52 |
| Valid GTINs | 13 | 39 | 52 |
| Duplicate source GTINs | 0 | 0 | 0 across both |
| Missing name / brand / pack text | 2 / 2 / 3 | 0 / 0 / 2 | 2 / 2 / 5 |
| Ambiguous brand labels | 6 | 7 | 13 |
| Invalid or rejected records | 0 | 0 | 0 |
| Pending review after publication | 1 | 19 | 20 |
| Needs correction | 11 | 15 | 26 |
| Products and variants published | 1 / 1 | 5 / 5 | 6 / 6 |

The pilot published Parle-G, Britannia bourbon, Britannia Marie Gold Biscuit, Balaji Mung Dal, PepsiCo Sting Energy, and Marks & Spencer Teriyaki Dip Sauce Marinade. The remaining 46 candidates await review or correction. Six repeated approval calls returned DUPLICATE and created no additional products or variants. SQL on the genuine-data schema confirmed 30 products and 33 variants including 24 starter products and 27 starter variants, 52 retained raw rows, six provenance rows, zero price rows, and the four existing starter retailer mappings. The separate contract test schema contains deliberately labelled fixtures and is excluded from these counts.

### Source and quality

- Bulk export dated 2026-11-14 05:33:45 UTC, ETag `363e9064076f288fd3a2b7f0e17f42af-811`, bytes `0-536870911/13604811365`. India selection used exact India tags in `countries_tags` or `manufacturing_places_tags`. It found nine market-tagged rows and four manufacturing-tagged rows. The 20 ingredient-origin tags in the examined bulk prefix were counted separately and could not qualify a record by themselves. [Open Food Facts product schema](https://openfoodfacts.github.io/documentation/docs/Product-Opener/schemas/schemas/product/)
- India API page dated 2026-11-14 12:05:12 UTC, sorted by `unique_scans_n`, requested size 40, 39 returned. The API reported 26,126 matching products. All 39 returned rows had the India market tag, three had India manufacturing tags and 13 had India ingredient-origin tags. The exact response is archived separately from its reserialized JSONL. Re-extraction reproduced the same SHA-256. [Open Food Facts India query](https://in.openfoodfacts.org/api/v2/search?countries_tags=en:india&page_size=40&page=1&sort_by=unique_scans_n)
- The API sample contained six reported Balaji labels, two Balaji Wafers labels, four Britannia, three Parle, and two Kissan. These are reported brand names, with no inference about where the products were made. Common source leaf categories included sweetened beverages (4), biscuits (3), and namkeen (3). CartIQ suggested snacks for 20 records, beverages for 12, pantry for nine, and left 11 unmapped or ambiguous. Published mix was four snacks, one beverage, and one pantry item. Neither sample estimates national coverage or current stock.
- Open Food Facts attribution is carried through the extractor manifest, batch source URL, product source page, and public metadata. The database uses ODbL 1.0, individual contents DbCL 1.0, and images CC BY-SA 3.0. Preserve the licences and source when reusing or distributing the data. Assess share-alike obligations and any additional packaging-image rights before public distribution. [Open Food Facts reuse guidance](https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/tutorials/license-be-on-the-legal-side/)

### Reproduction

Run from project root in PowerShell. Keep the downloaded bytes and response headers together. Downloaded bulk snapshot and both extracts are verification inputs outside the repository.

```powershell
$pilot = Join-Path $env:TEMP 'cartiq-off-india-expansion'
$bulk = 'https://openfoodfacts-ds.s3.eu-west-3.amazonaws.com/openfoodfacts-products.jsonl.gz'
$india = 'https://in.openfoodfacts.org/api/v2/search?countries_tags=en:india&page_size=40&page=1&sort_by=unique_scans_n'
New-Item -ItemType Directory -Force -Path $pilot | Out-Null
curl.exe -fSL --range 0-536870911 -H 'If-Match: "363e9064076f288fd3a2b7f0e17f42af-811"' -D "$pilot/source-headers.txt" $bulk -o "$pilot/off-prefix.gz"
curl.exe -fSL -D "$pilot/india-api-headers.txt" $india -o "$pilot/india-api-response.json"
python database/extract-off-india.py "$pilot/off-prefix.gz" "$pilot/india.jsonl" --headers "$pilot/source-headers.txt" --limit 100 --max-examined 250000
python database/extract-off-india.py "$pilot/india-api-response.json" "$pilot/india-api.jsonl" --headers "$pilot/india-api-headers.txt" --api-url $india --mode market --limit 100 --max-examined 250000
python database/stage-off-snapshot.py "$pilot/india.jsonl" --manifest "$pilot/india.manifest.json" --api http://127.0.0.1:18884/api --chunk-size 5 --max-chunks 1
python database/stage-off-snapshot.py "$pilot/india.jsonl" --manifest "$pilot/india.manifest.json" --api http://127.0.0.1:18884/api --chunk-size 5
python database/stage-off-snapshot.py "$pilot/india-api.jsonl" --manifest "$pilot/india-api.manifest.json" --api http://127.0.0.1:18884/api --chunk-size 5 --max-chunks 1
python database/stage-off-snapshot.py "$pilot/india-api.jsonl" --manifest "$pilot/india-api.manifest.json" --api http://127.0.0.1:18884/api --chunk-size 5
```

The stage script needs an admin JWT in `CARTIQ_ADMIN_TOKEN` and an isolated backend on port 18884. Both real batches stopped at row five, resumed from row five, and a third invocation saw the completed checkpoint. The observed validated payload digests were `7ceeb4b7232ae624910efc402da6572ed222db840b31a6c5d4f877553b1dfdde` for bulk and `1ef5bea85cb7e7e5d00564c6878b0361aaaf9bf4809ad5159c1df9d6aea08e01` for the later API response. Live API pages and the rolling bulk export change over time. Use the archived inputs to reproduce the precise run.

### Database and testing

- Initialized a new MySQL 8.0.44 data directory, started `mysqld --no-defaults` bound to loopback port 13316, and created scratch schemas. Ran `mvn -o -B spring-boot:run` from a temporary copy with `SPRING_PROFILES_ACTIVE=mysql-local`, explicit scratch `DB_URL`, `PORT=18884` and `SPRING_FLYWAY_TARGET=7`, then restarted with target 10. Confirmed V7 seed had 24 products and 27 variants, and Flyway applied V8, V9, V10 in order. V8 creates the import candidates, barcode and source tables, V9 adds leases, checkpoints and correction history, and V10 references those candidates for the batch audit. In the first scratch attempt the original V10 failed with MySQL 1064 at unquoted `row_number`. The corrected upgrade ran in a fresh schema. The final genuine-data pilot used a separate V10 schema on the same disposable service.
- Frontend `npm run typecheck` passed, and `npm test` passed 37 with zero failures. Expo Web Chrome mobile viewport confirmed exact brand, equivalent pack sizes, category filtering, exact published barcode, variant detail and attribution. Signed HTTP requests verified pending review, publication, replay and result pagination.
- H2 and backend unit/HTTP tests ran in a temporary source copy using `mvn -o -B test "-Dspring.profiles.active=test"`. Result was 104 passed, zero failures/errors, and eight gated MySQL skips.
- MySQL suite ran using `mvn -o -B test "-Dtest=MysqlCatalogueImportTest,MysqlMilestone2Test,MysqlMilestone3Test,MysqlMilestone4Test"`. Set `CARTIQ_CATALOGUE_MYSQL_VERIFY=true` and its scratch username/password, plus the legacy `CARTIQ_MYSQL_VERIFY=true`, `CARTIQ_MYSQL_VERIFY_URL` targeting `127.0.0.1:13316/cartiq_catalogue_verify_retry`, `CARTIQ_MYSQL_VERIFY_USERNAME`, `CARTIQ_DB_PASSWORD` and Flyway target 10. Eight passed, zero failures or skips. The new tests validated actual Flyway history, foreign key and unique constraints, offset conflict under concurrency, raw retention, publication and duplicate prevention.

The shared MySQL instance was untouched. Native mobile verification, wider category coverage, and public distribution review remain pending.

## Real catalogue pilot

| Check | Executed command or method | Result |
|---|---|---|
| Backend tests | `mvn -o -B test "-Dspring.profiles.active=test"` from an isolated source copy | 101 passed, zero failures or errors, six gated MySQL skips. Includes a new H2 regression for offset conflicts, resume, raw retention, malformed rows, additional pack sizes, publishing, API metadata and price isolation. |
| Frontend | `npm run typecheck` and `npm test` | TypeScript passed. 37 tests passed, zero failures or skips. |
| Real source import | `python database/stage-off-snapshot.py <off-india-pilot.jsonl> --api http://127.0.0.1:18882/api --chunk-size 1`, then repeated with the same file | 2 rows processed, 1 ready for review, 1 needing correction, 0 invalid, 0 duplicate rows. Re-run resumed at 2/2 and created no duplicate candidates. |
| Publication and catalogue | Signed admin and public HTTP requests against Spring Boot with disposable H2 and V1–V10 | 1 new product and 1 new variant. Draft returned 404, then appeared in search, detail, and variants after publish. The metadata returned the GTIN, ingredients, 9 nutrients per 100 g, source and licences. The product returned zero offers. |
| Product image | HTTP HEAD against the derived Open Food Facts image URL | 200, image/jpeg. |
| Expo Web | `npm run web -- --port 8090 --localhost --max-workers 2` with `EXPO_PUBLIC_API_URL=http://127.0.0.1:18882/api`, automated Chrome mobile viewport | Searched for the published product and verified the GTIN, ingredient section, 170 kcal per 100 g, and source link. Signed in as admin and verified batch progress, the flagged source row and the published candidate in the review screen. |

### Source coverage and licensing

- Open Food Facts published snapshot `openfoodfacts-products.jsonl.gz`, ETag `363e9064076f288fd3a2b7f0e17f42af-811`, dated 2026-11-14 05:33:45 UTC. The 128 MiB compressed prefix was requested with HTTP Range and returned 206. It contained 34,802 complete records. Two had the country tag `en:india`. This source-ordered prefix was selected to verify the workflow and does not measure national catalogue coverage or current retail availability. The search API probe returned HTTP 503. [Bulk format](https://openfoodfacts.github.io/openfoodfacts-python/usage/) · [Open Food Facts reuse guidance](https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/tutorials/license-be-on-the-legal-side/)
- The imported record was Marks & Spencer Teriyaki Dip Sauce Marinade, 305 g, GTIN `00000000024907`. The second record had an ambiguous brand and unsupported pack text and was held for review. No fabricated replacement was submitted. [Open Food Facts product record](https://world.openfoodfacts.org/product/00024907)
- Open Food Facts lists ODbL for the database, DbCL for individual contents, and CC BY-SA for product images. The app links to the source and carries the separate data and image licence fields. Public distribution still needs a plan for the applicable share-alike obligations. Do not assume the images clear additional rights in the packaging artwork. [Open Food Facts reuse guidance](https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/tutorials/license-be-on-the-legal-side/)
- USDA FoodData Central offers bulk CC0 data and a branded dataset with GTIN, ingredients, and market-country fields. India coverage was not measured. GS1 India DataKart is a promising source of brand-owner records, with API and subscription offerings. Obtain the relevant access and reuse terms before importing. Neither source was imported. [USDA downloads](https://fdc.nal.usda.gov/download-datasets/) · [USDA fields](https://fdc.nal.usda.gov/portal-data/external/dataDictionary) · [USDA licensing](https://fdc.nal.usda.gov/api-guide/) · [GS1 India DataKart](https://www.gs1india.org/services/datakart)

### Reproducing the pilot

Start Spring Boot with `mvn -o -B spring-boot:run`, profile `local`, port 18882, and `SPRING_DATASOURCE_URL=jdbc:h2:mem:cartiq-real-catalogue;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`. Use `SAMPLE_DATA=false`, `SMARTCART_IMPORT_STARTER_CATALOGUE=true`, an ephemeral server-only `JWT_SECRET`, and local `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`. Flyway needs V8–V10 for all bulk features. Set `CARTIQ_ADMIN_TOKEN` in the shell after admin login. The stage script accepts an operator-prepared, immutable OFF JSONL extract, optionally gzip-compressed. It fingerprints the decompressed lines, stages at most 20 records per request, and stops on transport or checkpoint errors. Rerun with the same file after resolving the error. The import UI can review, correct, reject, approve, and publish. Batch issue counts describe original ingestion, even after a correction.

- Admin API under `/api/admin/catalogue-imports` now includes `POST /batches`, `GET /batches`, `GET /batches/{id}`, `PUT /batches/{id}/rows`, `GET /batches/{id}/issues`, `GET /review`, and `POST /candidates/{id}/publish`. The existing approval route accepts an optional `existingProductId` for another pack of a matched inactive product.
- Public `GET /api/products/{id}/metadata?locationId=...` returns barcode and label provenance for an active variant. Barcode values are canonical 14-digit strings. Legacy products without source records return empty metadata.

V10 is additive and was reviewed before execution. It creates the batch progress table and raw-row audit table. No older SQL migration was edited. V8–V10 were exercised in temporary H2 only in this milestone. The shared MySQL database was untouched. The pilot database was in memory. The source extract can be staged again using the instructions above. Large-import performance, MySQL V8–V10 compatibility, broader India coverage and native mobile verification remain pending.

## Native mobile verification

| Check | Command or environment | Result |
|---|---|---|
| TypeScript | `npm run typecheck` | Passed |
| Frontend | `npm test` | 37 passed, zero failures or skips |
| Backend | `mvn -o -B test "-Dspring.profiles.active=test"` from an isolated copy | 100 passed, zero failures or errors and six gated MySQL skips. The passing count includes seven real HTTP tests with H2. |
| Android and iOS bundle generation | `npx expo export --platform android --platform ios --max-workers 2 --output-dir <temp>` | Both Hermes bundles exported. |
| Android emulator | Android 14 (API 34), x86_64, Expo Go 54.0.8 | ADB confirmed Android booted and Expo Go was installed. System UI reported an application-not-responding dialog. CartIQ launch and native device-to-API connectivity remain unverified. |
| Physical phones | Android and iOS | Deferred. |

The emulator took longer than the first boot wait, and an initial Expo Go install during boot failed. Expo Go was installed by the subsequent attempt. At the last check, the test backend was UP from Windows and the test Metro process had stopped. Resume by bringing up a responsive emulator and restarting Metro before testing the app. The disposable backend copy shortened access-token lifetime from 30 minutes to 45 seconds for the planned expiry check. The workspace source retains the normal lifetime.

The native bundle checks did not compile or sign an APK or IPA. The existing desktop API returned UP over loopback. The same API port refused connections at the desktop Wi-Fi address. For the emulator, Spring Boot ran a separate API on host loopback port 18880 and used an in-memory H2 database seeded with the starter catalogue, Flyway target V7, and sample prices disabled. No shared database changes or MySQL migrations were made.

## Frontend and backend integration audit

| Environment | Executed command or method | Result |
|---|---|---|
| Frontend TypeScript | `npm run typecheck` | Passed |
| Frontend unit, fixture, and mocked-port tests | `npm test` | 36 passed, including URL policy |
| Frontend socket transport integration | Included in `npm test` | 1 passed. Real Node HTTP server exercised headers, JSON, error handling, timeout and cancellation. |
| Backend test profile | `mvn -o test "-Dspring.profiles.active=test"` | 99 passed, 0 failures or errors, 6 gated MySQL skips. |
| Backend final socket HTTP and H2 | `mvn -o test "-Dtest=MobileApiHttpIntegrationTest" "-Dspring.profiles.active=test"` | 7 passed, 0 failures or skips. The final rerun covers the subsequent forecast fallback change and its added regression. |
| Real MySQL with Spring MockMvc | `mvn -o -B surefire:test "-Dtest=MysqlMilestone2Test,MysqlMilestone3Test,MysqlMilestone4Test"` against compiled test classes | 6 passed, 0 failures, 0 skips. The MySQL connection and SQL were real. Protected requests in these tests use injected principals. |
| AI service | `./.venv/Scripts/python.exe -m pytest -q` from `ai-service` | 6 passed, with a Starlette HTTPX deprecation warning. |

The MySQL run used `CARTIQ_MYSQL_VERIFY=true`, `CARTIQ_MYSQL_VERIFY_URL`, `CARTIQ_MYSQL_VERIFY_USERNAME`, `CARTIQ_DB_PASSWORD`, and `SPRING_FLYWAY_TARGET=7`. The database was a new loopback-only MySQL 8.0.44 instance on port 13306. SQL confirmed V1–V7, 27 variants, 7 categories, and no retailer price observations. The pre-existing instance on port 3306 accepted TCP but rejected an unauthenticated read-only login with error 1045. Its schema and data were left untouched.

Across the two backend test-profile runs, 100 distinct cases passed. They comprise 28 pure unit tests, 14 mocked outbound HTTP tests, 51 existing H2-backed tests (some using MockMvc or Mockito), and 7 H2-backed tests using real socket HTTP. The new suite verifies persisted BCrypt account data, the 72-byte UTF-8 limit, signed JWT validation and expiry, refresh rotation and logout revocation, catalogue queries and unpriced comparison, cart persistence and authorization, CORS, HTTP validation, and the unavailable-model response.

Expo Web ran on port 18893 via `npm run web -- --port 18893 --localhost` with `EXPO_PUBLIC_API_URL=http://127.0.0.1:18880/api`. Spring Boot ran from a temporary copy with `mvn -B spring-boot:run`, profile `mysql-local`, the scratch JDBC URL, Flyway target V7, and the exact web origin in `ALLOWED_ORIGINS`. Health returned UP. The intended origin received credentialed CORS headers and an unlisted origin returned 403. Direct requests confirmed JSON 400 for missing parameters, 401 with a Bearer challenge for unauthenticated cart retrieval, 405 with an Allow header, and 415 for an unsupported request media type.

### Browser to Spring Boot to MySQL

**All 13 requested operations passed** in headless Chrome using Playwright's Pixel 7 browser emulation, the actual Expo Web bundle and Spring HTTP routes. Requests were captured in the browser and checked against committed MySQL data with read-only SQL. The browser runtime was compiled before the final forecast fallback edit, which was exercised by the final H2 socket suite. Native Android/iOS, the shared MySQL schema, and production were outside the executed verification.

| Operation | Observed result |
|---|---|
| Registration | `POST /api/auth/register` returned 201, a JWT, and the new profile. MySQL contained the account, BCrypt hash, and refresh-token row. |
| Login | `POST /api/auth/login` returned 200 for the credentials after logout and matched the persisted account ID. |
| Profile | `GET /api/me` ran on page restoration and when opening Profile. The account email rendered correctly. Reload restored the browser session via the rotating HttpOnly, SameSite=Strict cookie. |
| Product listing | `GET /api/products` and `GET /api/products/page` returned catalogue rows used in the app. SQL confirmed 27 variants. |
| Product search | The search text reached `GET /api/products/page?q=milk`. The result rendered the Amul Taaza variant, matched by ID and display name in SQL. |
| Category listing | `GET /api/categories` returned seven database categories. Tapping Dairy & eggs fetched its filtered catalogue page. |
| Product details | `GET /api/products/milk` returned the selected variant and description. The app rendered the description and requested pack variants. |
| Comparison | `GET /api/products/milk/comparison` returned ineligible options and no winner when the price table had zero rows. The UI rendered the explanation and unknown prices. |
| Cart add | `PUT /api/me/cart` returned quantity 1. SQL confirmed the row belonged to the signed-in shopper. |
| Cart update | A second `PUT /api/me/cart` returned quantity 2. SQL and the UI count agreed. |
| Cart removal | `DELETE /api/me/cart/{id}` returned 204 and showed the empty state. SQL confirmed cart item and purchase preferences were removed. |
| Cart retrieval | `GET /api/me/cart` after reload returned the same item ID and quantity 2 before removal. |
| Account isolation | A second user in the same browser had an empty cart via API and UI. The first user's row remained intact until the first user signed in and removed it. The automated API tests also exercise a non-owner removal attempt. |

Initial workspace runs hit transient class-file access and watcher failures while Maven compilation overlapped with Metro startup. Verification resumed from an isolated backend copy with a warmed Metro bundle. Initial browser attempts hit cold-bundle or reload timeouts, a locator that assumed the display name included its search term, and intermittent blank output from the temporary SQL subprocess. The successful run waited for network responses after navigation and validated the exact fixture ID and SQL results.

Earlier setup and test records follow for historical context.

---

## Test Execution Summary

| Suite / Environment | Command | Tests Run | Passed | Failed | Skipped | Status |
|---------------------|---------|-----------|--------|--------|---------|--------|
| **Backend (H2 In-Memory)** | `mvn test "-Dspring.profiles.active=test"` | 99 | 93 | 0 | 6 (MySQL tests) | ✅ **PASS** |
| **Backend (MySQL 8.0)** | `mvn test "-Dtest=MysqlMilestone2Test,MysqlMilestone3Test,MysqlMilestone4Test" "-Dspring.flyway.target=7"` | 6 | 6 | 0 | 0 | ✅ **PASS** |
| **Frontend (TypeScript)** | `npm run typecheck` (`tsc --noEmit`) | N/A | 0 errors | 0 | 0 | ✅ **PASS** |
| **Frontend (Unit/Integration)** | `npm run test` (`node tests/run.cjs`) | 35 | 35 | 0 | 0 | ✅ **PASS** |
| **AI Service (pytest)** | `.\ai-service\.venv\Scripts\pytest` | 6 | 6 | 0 | 0 | ✅ **PASS** |
| **Total Test Assertions** | | **140** | **140** | **0** | **0** | ✅ **100% PASS** |

---

## Backend Tests

### 1. H2 In-Memory Suite (V1–V9 Full Schema)
- **Environment:** H2 in-memory (`jdbc:h2:mem:smartcarttest;MODE=MySQL`)
- **Flyway:** All 9 migrations (V1–V9) applied to H2 memory schema
- **Command:** `mvn test "-Dspring.profiles.active=test"`

| Test Class | Tests | Pass | Fail | Skip | Time | Environment / Notes |
|-----------|-------|------|------|------|------|---------------------|
| `CatalogueImportNormalizerTest` | 25 | 25 | 0 | 0 | 0.62s | Unit (barcode, GTIN check-digits) |
| `CatalogueImportSafetyTest` | 17 | 17 | 0 | 0 | 10.4s | H2 + Mockito (rollback, lease, idempotency) |
| `WorkspaceIntegrationTest` | 17 | 17 | 0 | 0 | 15.5s | H2 + Spring Boot MockMvc |
| `OpenFoodFactsAdapterSafetyTest` | 14 | 14 | 0 | 0 | 0.26s | Mocked HTTP (timeouts, error codes) |
| `LocalStoreHttpAuditTest` | 4 | 4 | 0 | 0 | 4.0s | H2 + MockMvc |
| `CatalogHttpTest` | 3 | 3 | 0 | 0 | 5.9s | H2 + MockMvc |
| `CatalogueImportFoundationTest` | 3 | 3 | 0 | 0 | 39.0s | H2 + MockMvc |
| `ComparisonSecurityTest` | 2 | 2 | 0 | 0 | 3.8s | H2 + MockMvc |
| `EmailFlowTest` | 2 | 2 | 0 | 0 | 6.9s | H2 + MockMvc |
| `ProviderContractTest` | 2 | 2 | 0 | 0 | 0.04s | Unit |
| `ComparisonHttpTest` | 1 | 1 | 0 | 0 | 1.7s | H2 + MockMvc |
| `LocalStoreTest` | 1 | 1 | 0 | 0 | 0.4s | H2 |
| `ProviderBoundaryTest` | 1 | 1 | 0 | 0 | 0.01s | Unit |
| `StarterCatalogueTest` | 1 | 1 | 0 | 0 | 0.68s | H2 (verifies 24 products, 27 variants) |
| `MysqlMilestone2Test` | 1 | 0 | 0 | 1 | 0s | Skipped in H2 suite (runs in MySQL suite) |
| `MysqlMilestone3Test` | 4 | 0 | 0 | 4 | 0s | Skipped in H2 suite (runs in MySQL suite) |
| `MysqlMilestone4Test` | 1 | 0 | 0 | 1 | 0s | Skipped in H2 suite (runs in MySQL suite) |
| **Subtotal** | **99** | **93** | **0** | **6** | **~1m 52s** | |

### 2. MySQL 8.0 Suite (V1–V7 Target Schema)
- **Database:** MySQL 8.0.44 Community Server (`cartiq_db` on `127.0.0.1:3306`)
- **Credentials:** Authenticated as `cartiq_user`
- **Flyway:** Target set to version 7 (`spring.flyway.target=7`) per user approval
- **Command:** `mvn test "-Dtest=MysqlMilestone2Test,MysqlMilestone3Test,MysqlMilestone4Test" "-Dspring.flyway.target=7"`

| Test Class | Tests | Pass | Fail | Skip | Time | Notes |
|-----------|-------|------|------|------|------|-------|
| `MysqlMilestone2Test` | 1 | 1 | 0 | 0 | 27.3s | Verified MySQL catalogue pagination and contract |
| `MysqlMilestone3Test` | 4 | 4 | 0 | 0 | 42.9s | Verified local store audit contract on live MySQL |
| `MysqlMilestone4Test` | 1 | 1 | 0 | 0 | 1.0s | Verified migrations V1–V7 and comparison evidence |
| **Subtotal** | **6** | **6** | **0** | **0** | **~1m 12s** | **100% Passed** |

---

## AI Service Tests

- **Command:** `cd ai-service; .\.venv\Scripts\pytest`
- **Environment:** Python 3.12.10 virtual environment (`ai-service\.venv`)
- **Framework:** pytest 9.2.0, FastAPI 0.162.7, scikit-learn 1.9.1, pandas 3.1.1

| Test Suite | Total | Pass | Fail | Skip | Duration |
|------------|-------|------|------|------|----------|
| `test_app.py` | 4 | 4 | 0 | 0 | ~6.7s |
| `test_training.py` | 2 | 2 | 0 | 0 | ~3.4s |
| **Total** | **6** | **6** | **0** | **0** | **10.17s** |

---

## Frontend Tests

- **TypeScript check:**
  - **Command:** `npm run typecheck` (`tsc --noEmit`)
  - **Result:** PASSED (0 errors, clean compile)
- **Unit & Integration tests:**
  - **Command:** `npm run test` (`node tests/run.cjs`)
  - **Framework:** Node.js native test runner + compiled TypeScript test suite (`tests/comparison.test.ts`)
  - **Results:**
    - Total tests: 35
    - Passed: 35
    - Failed: 0
    - Skipped: 0
    - Duration: ~324 ms

---

## Local Runtime Verification

| Component | Port / Host | Command | Health Status | Verification Outcome |
|-----------|-------------|---------|---------------|----------------------|
| **Spring Boot Backend** | `http://127.0.0.1:8080` | `mvn spring-boot:run` (profile `local`) | `UP` (`/actuator/health`) | ✅ Operational. Locations API (`/api/locations`) returned Mumbai Bandra, Bengaluru Indiranagar & Koramangala. |
| **FastAPI AI Service** | `http://127.0.0.1:8001` | `uvicorn app:app --host 127.0.0.1 --port 8001` | `status: ok` (`/health`) | ✅ Operational. Experimental mode active, model endpoints live. |
| **Expo Web Frontend** | `http://localhost:8092` | `expo start --web --port 8092` | `HTTP 200 OK` | ✅ Operational. Metro Bundler running and serving web client bundle. |

---

## Database Verification

### MySQL Server
- **MySQL version:** 8.0.44 Community Server (`MYSQL80` Windows service on port 3306).
- **Database provisioned:** `cartiq_db` (created with `utf8mb4` charset).
- **User provisioned:** `cartiq_user` with full privileges on `cartiq_db.*`.
- **Flyway schema history:** Table `flyway_schema_history` active in `cartiq_db`.

### Flyway Migration Status on MySQL

| Rank | Version | Description | Script | Success | Execution Time |
|------|---------|-------------|--------|---------|----------------|
| 1 | 1 | normalized schema | `V1__normalized_schema.sql` | 1 | 1667 ms |
| 2 | 2 | provider mappings | `V2__provider_mappings.sql` | 1 | 70 ms |
| 3 | 3 | email verification | `V3__email_verification.sql` | 1 | 29 ms |
| 4 | 4 | catalogue lifecycle | `V4__catalogue_lifecycle.sql` | 1 | 73 ms |
| 5 | 5 | local stores | `V5__local_stores.sql` | 1 | 110 ms |
| 6 | 6 | local comparison terms | `V6__local_comparison_terms.sql` | 1 | 34 ms |
| 7 | 7 | online comparison evidence | `V7__online_comparison_evidence.sql` | 1 | 45 ms |

*Note: Migrations V8 and V9 are pending review and approval per project rules, and are not applied to MySQL.*

### Actual MySQL Database Counts

| Entity | Verified Count | Source |
|--------|----------------|--------|
| **Products** | 24 | Verified via `SELECT COUNT(*) FROM products` |
| **Product Variants** | 27 | Verified via `SELECT COUNT(*) FROM product_variants` |
| **Product Categories** | 7 | Verified via `SELECT COUNT(*) FROM product_categories` |
| **Grocery Platforms** | 3 | Verified via `SELECT COUNT(*) FROM grocery_platforms` (Blinkit, Instamart, Zepto) |
| **Delivery Locations** | 1 | Verified via `SELECT COUNT(*) FROM delivery_locations` (`blr-indiranagar`) |

---

## Environment Distinction

| Environment | Status | Notes |
|------------|--------|-------|
| **Unit tests** | ✅ 93 backend + 6 AI + 35 frontend passed | Pure logic and isolated component suites |
| **H2 tests** | ✅ 93 passed | In-memory and file H2 with `MODE=MySQL` (V1–V9) |
| **Mocked tests** | ✅ Included in test counts | Mockito-based adapter and MockMvc tests |
| **MySQL tests** | ✅ 6 passed | `MysqlMilestone2Test` (1), `MysqlMilestone3Test` (4), `MysqlMilestone4Test` (1) against live MySQL 8 |
| **Local runtime verification** | ✅ Active and verified | Backend (8080), AI service (8001), Frontend (8092) all live |
| **Production verification** | ⬜ Not performed | Staging/production deployment not yet configured |

---

*Agents must update this file after any test execution. Never claim a pass without evidence.*
