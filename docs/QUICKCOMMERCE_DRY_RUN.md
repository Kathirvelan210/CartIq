# QuickCommerceAPI dry run

## Milestone 4 shopping configuration

The Expo picker shares a confirmed location with Discover and retailer item details. For address and PIN lookup, set `CARTIQ_GEOCODER_URL` in the backend launch shell to an authorized HTTPS Nominatim-compatible endpoint or a self-hosted loopback endpoint. The value is the base URL before `/search` and `/reverse`. With the setting absent, PIN lookup reports that the service is unconfigured. Device GPS still works and can be given a local label. The app sends coordinates for reverse lookup only after the user requests GPS. Source coverage varies, and PIN points remain approximate. [Nominatim search](https://nominatim.org/release-docs/latest/api/Search/) · [Reverse geocoding](https://nominatim.org/release-docs/latest/api/Reverse/) · [Public instance usage rules](https://operations.osmfoundation.org/policies/nominatim/)

Use localhost or HTTPS for Expo Web geolocation. A physical device needs a reachable CartIQ backend URL through `EXPO_PUBLIC_API_URL` and foreground location permission. Native development builds require the Expo Location plugin configuration included in this milestone.

QuickCommerce remains disabled. The existing admin endpoints retain the local profile and credit ledger. The additional `/api/shopping/retailers` routes require an authenticated account and explicit `QUICKCOMMERCE_SHOPPING_ENABLED=true` alongside the provider's own enablement. Confirm provider display rights and an approved rollout before enabling customer access. The UI search button can attempt up to three serial lookups. Never use it for a one-request smoke test. An error with unverified billing leaves the reservation pending and stops the batch. Changing location clears the displayed results and makes no paid request.

**Proposed next test, awaiting explicit approval.** One Blinkit `GET /v1/search` for `Milk`, at the provider's example test coordinates `12.9021, 77.6639`, with no PIN. Use the existing admin single-search route and persistent ledger. Maximum expected cost is one credit. A confirmed charge would move the balance from 96 to 95 and leave 18 experiment credits. Recheck dashboard balance and fingerprint before dispatch. The example coordinates describe a test location. No live call is authorized by this plan. [QuickCommerceAPI search reference](https://quickcommerceapi.com/docs#search)

## Latest smoke-test state

The one authorized Blinkit Milk attempt returned CartIQ HTTP 502. The operator subsequently supplied provider evidence of success, 24 products, zero failed platforms and 96 credits remaining. The original reservation is settled as one consumed credit. The persistent ledger records request ID `922db9a5-1a13-4a11-b1de-25c9d4d8b0b7` and reference `operator-confirmation-2026-11-15-blinkit-smoke` alongside the original fingerprint. There are zero pending reservations and 19 experiment credits available above the protected floor of 77.

The disposable backend is stopped and the normal provider remains disabled. Only the accounting checkpoint and evidence reference were written. No provider requests were made during reconciliation. Any future live request requires separate authorization. [Smoke-test result and reconciliation](TESTING_STATUS.md#blinkit-reservation-reconciliation-and-offline-502-regression)

## Original HTTP diagnostics search

The original PowerShell execution record confirms CartIQ HTTP 502 for the single Blinkit Milk dispatch. Its completion was recorded on 2026-11-15 at 14:11:27.076 IST. The captured diagnostic reads “The adapter could not verify the provider response or transport.” The upstream status field is null and the parsing field reads `unavailable`. The command did not collect response headers and cleared the response and body after producing the sanitized report. The original upstream HTTP status, `X-Credits-Remaining` and other response headers, exception class, stack trace, and adapter failure stage remain unavailable.

| Local source checked | Finding |
|---|---|
| `%USERPROFILE%/.codex/sessions/2026/11/14/rollout-2026-11-14T16-13-26-01a1d9a7-a904-7553-ae75-92a10a1d3836.jsonl`, line 7356 | Original command completion and sanitized output. Confirms the application 502, one dispatch and a pending reservation at that time. |
| `%USERPROFILE%/CartIQ-data/2026-11-15-blinkit-smoke/` | `local-smoke-startup.log` records DispatcherServlet initialization at 14:11:22.610–14:11:22.617 IST and has no matching HTTP trace. `local-smoke-stderr.log` is empty. `offline-build.log` contains build output. `scratch-path.txt` points to the isolated backend copy, which contained no matching response capture. |
| `ConsoleHost_history.txt` under PowerShell PSReadLine, VS Code logs, Windows Terminal LocalState, and Windows Application and PowerShell event logs | No matching request evidence. The event window checked was 14:10–14:13 IST. |
| Workspace, CartIQ-data, Documents, OneDrive Documents, Downloads, Desktop and temporary-file directories | Checked for HAR, proxy, packet captures and PowerShell transcripts. No matching capture was found. Later reconciliation test logs are synthetic. |
| Supplied provider-body `Pasted text.txt` under attachment `5f51df75-889a-4699-98ca-5c28570a4352` and the existing local credit ledger | The attachment saved at 14:30:59 IST matches request ID `922db9a5-1a13-4a11-b1de-25c9d4d8b0b7`. It reports JSON `status: success` and `credits_remaining: 96`. Neither field establishes the HTTP status or header value. The ledger correlates the same ID through the recorded evidence reference. |

The runbook records only redacted diagnostics. This search issued zero live requests, used zero credits, and left the adapter disabled and the ledger byte-for-byte unchanged. It touched no application code, credentials, or shared MySQL.

## Final offline verification

The supplied body confirms provider-level success for the original request and contains 24 string listing IDs and 24 numeric top-level store IDs. CartIQ returned an application-level 502 during that attempt. Both numeric listing IDs and numeric store IDs triggered 502 in synthetic replays against the original adapter. Both synthetic 24-row replays pass on the fixed adapter. The original live trigger remains unconfirmed because the original HTTP status, headers and diagnostic trace are unavailable. The supplied body was inspected locally and was not copied into the workspace or logs.

This verification sent zero live requests and used zero additional credits. The provider stays disabled and the settled ledger stays at 96 with zero pending reservations, 19 experiment credits left and a floor of 77. [Test results and security review](TESTING_STATUS.md#final-quickcommerceapi-offline-verification)

For the next offline check, leave the provider disabled and use a disposable backend copy. Set `CARTIQ_MYSQL_VERIFY`, `CARTIQ_CATALOGUE_MYSQL_VERIFY` and `CARTIQ_MAPPING_UPGRADE_MYSQL` to `false` in the test shell. Run `mvn.cmd -o -B test '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' '-Dtest=QuickCommerceProviderTest,QuickCommerceAdminTest'` from that copy. If the original HTTP metadata is recovered, use a synthetic fixture with that status and credit-header shape. Another live call requires separate authorization.

## Start CartIQ after the repair

From the workspace root, save the existing QuickCommerce key once through the masked prompt. Skip the save command for ordinary development with the provider disabled.

```powershell
.\configuration\Start-CartIqBackend.ps1 -SaveQuickCommerceKey
.\configuration\Start-CartIqBackend.ps1 -CheckOnly
.\configuration\Start-CartIqBackend.ps1
```

`-SaveQuickCommerceKey` uses the current process variable if configured and otherwise prompts locally. The launcher saves only a Windows DPAPI-protected value in ignored `backend/.env.local`, with access restricted to the current Windows account. It never overwrites an existing key file. Normal startup loads that file if the launch environment lacks the key and reports `QUICKCOMMERCE_API_KEY: PRESENT` or `MISSING` from the child JVM. Maven runs offline by default. Use `-OnlineMaven` if the local dependency cache is incomplete. Stop your existing backend with Ctrl+C in its terminal before launching the updated code on port 8080. In a second terminal, run `npm.cmd run web -- --port 8092` with `EXPO_PUBLIC_API_URL` pointing to the local Spring Boot API.

The launcher verified the protected key in the original disposable Spring Boot process. Future authorized calls require the updated JVM's `PRESENT` status, Flyway V11 on local H2, and a fresh confirmed account checkpoint. On an uncertain outcome, stop and check the signed-in dashboard. The reconciliation script supports `-ReconcilePending -VerifiedDashboardBalance <observed-balance>`. To attach evidence of a consumed reservation, supply `-RequestFingerprint`, `-ProviderRequestId`, and `-EvidenceReference` together. The script checks the unchanged reserved balance and the unambiguous fingerprint, then preserves the existing experiment slot. [Verification and test history](TESTING_STATUS.md#project-repair-and-isolated-verification)

## Historical first live call preflight

The CartIQ operator confirmed that the signed-in dashboard showed 97 credits on 2026-11-15 IST. No provider API was queried to check the balance. The initial preflight used the provider's Blinkit example coordinates on paper only. Use the operator-authorized location for the single `GET /v1/search` request with `q=Milk` and `platform=BlinkIt`. At one credit, success would leave 96 account credits and 19 experiment credits. Keep the 77-credit floor. [Provider search reference](https://quickcommerceapi.com/docs#search)

At that preflight checkpoint the local ledger was initialized at 97 with zero attempts and verified by running the initializer again. The launch key was absent, the adapter was disabled, and no QuickCommerce API calls had been made.

### Pass the existing key to the local backend

`QuickCommerceProvider` receives `QUICKCOMMERCE_API_KEY` from Spring configuration under the `local` profile. Maven and the backend JVM inherit it from the launch shell. A variable set in another terminal does not reach this shell or an already-running JVM. The Maven plugin and VS Code settings define no credential loader, and the root Expo `.env` is outside Spring Boot configuration. The preflight found no key in Process, Windows User or Windows Machine scope.

The launcher passes the key only in the process environment and restores the shell's prior setting on exit. It forces Spring Boot's `local` profile, loopback address, and local H2 datasource and Flyway connection. A `PRESENT` line confirms configuration inheritance without checking the credential against the provider. The launcher neither reads nor modifies the credit ledger. [Original ledger and test details](TESTING_STATUS.md#quickcommerceapi-preflight-checkpoint)

## Earlier dry run

Local configuration checked on 2026-11-15 IST. This report required zero network requests. The checks read configuration presence and checked whether the ledger file existed. No credential value was displayed, and no ledger was initialized or changed.

| Check | Observed state |
|---|---|
| `QUICKCOMMERCE_API_KEY` | Absent from the backend launch environment |
| `QUICKCOMMERCE_ENABLED` | Disabled |
| Verified balance checkpoint and local ledger | Neither present |
| Dashboard balance | Unverified. Last user-reported figure was 97. |
| Maximum experiment budget unused | 20 credits. Current spendable allowance is zero until a checkpoint is verified and a live test is authorized. |
| Actual provider requests and credits consumed | 0 requests, 0 credits |

## Historical serial smoke test plan

The balances below were illustrative. Only the first row was authorized and attempted. Further tests require separate authorization. [Documented endpoint and billing behavior](https://quickcommerceapi.com/docs)

| Order | Platform | Provider endpoint | Expected cost | Experiment budget left if charged | Illustrative account balance |
|---|---|---|---:|---:|---:|
| 1 | Blinkit (`BlinkIt`) | `GET /v1/search` | 1 | 19 | 96 |
| 2 | Zepto (`Zepto`) | `GET /v1/search` | 1 | 18 | 95 |
| 3 | Swiggy Instamart (`Swiggy`) | `GET /v1/search` | 1 | 17 | 94 |
| 4 | Platform that returned the most useful variant | `GET /v1/item`, only if needed, using its native item ID | 1 | 16 | 93 |

Use one known serviceable location for all three searches. Leave `QUICKCOMMERCE_ENABLED` off while live authorization is pending. For an authorized run, use the existing environment key and supply the verified dashboard balance in `QUICKCOMMERCE_VERIFIED_START_BALANCE`. After a restart, the configured checkpoint must match the last settled ledger balance. Preserve the ledger and lock in the same OS user profile. A pending or mismatched ledger holds further testing until the account and reservation can be reconciled. [Implementation and tests](TESTING_STATUS.md#quickcommerceapi-budget-persistence-and-dry-run)
## Phase 6 offline verification

The exact-pack refresh uses the existing adapter and ledger. The relay test simulated a successful Blinkit response and a failed Swiggy platform. The separate mocked adapter tests checked the provider's request ID and balance evidence. No provider request was sent or reservation created in the real ledger. [Test evidence](TESTING_STATUS.md#live-grocery-offers-phase-6) · [Operator gates](OPERATIONS_RUNBOOK.md#quickcommerce-transient-offer-pilot)

