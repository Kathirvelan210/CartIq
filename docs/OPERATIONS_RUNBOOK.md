# CartIQ operations runbook

## Marketplace and PIN checks

Set `CARTIQ_GEOCODER_URL` on the backend to the authorized Nominatim-compatible base described in the [deployment contract](DEPLOYMENT_GUIDE.md#geocoder-contract). The local configuration inspected during Phase 9 lacked this setting, so a valid PIN could not resolve real coordinates. The app displays the lookup error and offers GPS or a more specific locality. It accepts approximate PIN coordinates only after confirmation. Confirmed locations are saved by account on the current device. Previously shared device-wide location data requires reconfirmation.

Enable marketplace dispatch only for a separately authorized pilot in the approved area. Query selection and checking for an authorization make read-only calls. A distinct action issues one paid provider request. Always leave legacy dispatch off. The permanent experiment ledger still suppresses an identical expired fingerprint, even with a new ticket. Confirm the request scope and review the operator report instead of changing a query to defeat that guard.

V19 adds the optional query scope to the existing V18 attempt record. A database constraint rejects a V18-style attempt to claim a marketplace ticket without the explicit discovery claim flag. Before rolling back, disable retailer and legacy dispatch in process configuration and disable active pilots. Retain the schema and evidence.

## Phase 8 controlled grocery pilot

Use **Controlled retailer pilot** on the local admin dashboard. The pilot starts disabled and saves operational metadata under written permission. An approved user action uses one ticket tied to the exact pack, date window, retailer, operation and account. Enable controlled dispatch only for separately authorized work. Preserve the ledger floor, sandbox database and the existing key. Turn off the runtime gates after the authorized test. [Pilot setup, stop rules and evidence](CONTROLLED_GROCERY_PILOT.md)

On a package rollback to V17, leave the additive V18 tables and receipts in place. Keep QuickCommerce disabled during rollback to avoid reopening legacy dispatch. Revalidate the ledger and any pending receipt before any separately authorized roll-forward. No shared schema migration has been applied.

## Retailer data quality triage

Use **Admin dashboard → Retailer coverage and data quality** to select a delivery area, retailer and category. Compare approved mappings with usable recorded offers. Filter for stale timestamps, missing prices, uncertain stock, missing fees or pack conflicts. Review provenance and mapping history before requesting new source evidence. For a shopper's transient quotes, use the shopper workflow or the current administrator account's own view. Operator totals never disclose another account's live shopping history.

Set `CARTIQ_OFFER_FRESHNESS_SECONDS` to a whole number from 60 to 900 to tighten the current-offer window. The default is 900. The process rejects invalid settings. Require a new valid observation to restore expired eligibility. A missing delivery or handling fee or minimum-order value prevents a complete priced recommendation.

The process keeps the latest 120 sanitized retailer events and a last-success timestamp per retailer. Restart resets this in-memory view. The existing structured log carries CartIQ and provider correlations, upstream status, platform failure flag, category, time and settlement outcome. Events omit request queries, keys, tokens, user IDs, provider payloads, products, coordinates and balances. Location and category filters apply to offer coverage. Retailer filtering also applies to diagnostics.

Treat provider 401/402/403 as authentication or account, 404 as area or item unavailable, 422 as unsupported, 429 as rate-limited, 500 as provider failure, 502 as retailer upstream, and 504 as timeout. HTTP 200 with the retailer listed as failed remains a provider-reported platform failure with an unspecified cause. Malformed data or unverifiable scope are adapter validation events. Billing is independent of upstream status. Only verified evidence settles a charged or uncharged event. Uncertain outcomes require the existing ledger reconciliation procedure. The previous Swiggy evidence supports an unspecified platform failure. A more specific classification needs provider evidence.

## QuickCommerce transient offer pilot

Keep both `QUICKCOMMERCE_ENABLED` and `QUICKCOMMERCE_SHOPPING_ENABLED` off until a separately authorized live test. The exact-pack routes run only on the local backend with sample mode off. To prepare an approved run, set `QUICKCOMMERCE_OFFER_PERMISSION_REFERENCE` to the internal record for written temporary display and catalogue-join rights, `QUICKCOMMERCE_OFFER_LOCATION_ID` to the approved catalogue area, and `QUICKCOMMERCE_OFFER_LAT` and `QUICKCOMMERCE_OFFER_LON` to its approved exact coordinates. The matching journal must contain active approvals for the same QuickCommerceAPI reference. Leave the existing key in the backend environment and use the existing verified ledger checkpoint. The configuration establishes operator attestation. It does not verify the underlying agreement.

Use one explicit refresh for the allowed platform, exact variant and confirmed area. A search or item detail can reserve one credit. The default lifetime allowance is 20 attempts. An operator-approved extension is stored as an absolute lifetime ceiling in the sibling file `<ledger filename>.attempt-limit`. Keep it with the ledger on backup and restore. Restarting or reopening a search never renews the allowance. The balance floor of zero, cross-process lock and duplicate fingerprints remain enforced. Verified billing records store the provider UUID for settlement without body contents. Reserve no follow-up attempt after an uncertain outcome. Reconcile with actual provider evidence via the existing manual procedure. Disable the pilot after testing and discard its in-memory data by stopping the local backend. Snapshot expiry is enforced on reads and local memory cleanup makes no network requests.

Historical Swiggy responses from the controlled Coimbatore session reported HTTP 200 with Swiggy in the failure list and no charge. The adapter reproduced the outcome classification offline. This places the observed failure at the provider-to-retailer boundary, where the response lacks a cause. Provider account configuration, location coverage and upstream Instamart behavior require provider evidence to distinguish. Use the earlier request ID and sanitized status for an authorized escalation. The mock alias check alone does not establish a live fix.

## Health and diagnostics

Check the backend's private `/actuator/health/liveness` for process liveness and `/actuator/health/readiness` for MySQL and readiness. Check `/health` on the private forecast service. Forecasting is optional and its outage should leave recorded history available. Use a periodic internal probe and alert on failure. Public ingress should only expose approved Web and API routes.

On isolated staging, probe the HTTP port for a 308 to `CARTIQ_HTTPS_ORIGIN`. Then connect with the configured CA, verify HSTS and the expected API response, and confirm the API supplies its own `X-Request-Id`. Probe the forecast service separately. `GET /models/status` should return 401 without the internal credential and 200 with it. Keep these probes off public ingress.

Use `node configuration/Check-Release.cjs` for the offline topology check. Use `node configuration/Test-StagingRelease.cjs --origin https://localhost:8443 --http-port 8081 --ca <staging-ca.pem>` for an isolated HTTPS smoke test. For the optional private forecast probe, supply `--forecast-port <local-port>` and inject `AI_SERVICE_SECRET` locally. The runner never prints response bodies or credentials.

Run `node configuration/Accept-Release.cjs` before release review and inspect both reports. `PASS` means the named check ran with the evidence recorded. `FAIL` means an executed check failed. `BLOCKED_EXTERNAL` means a service, credential, or authorized reviewer is needed. `NOT_TESTED` means the check was not executed during the run. `NOT_IMPLEMENTED` records a capability gap. Historical evidence can be attached by digest and remains separate from current results. [Acceptance commands](DEPLOYMENT_GUIDE.md#release-acceptance-command)

Collect the Spring `http_request` and `http_exception` event lines and edge status and latency fields. Group by route and status to monitor authentication errors, 429s, slow requests and 5xx responses. Use `X-Request-Id` to correlate a user report. In a controlled retailer environment, group `retailer_lookup` events by retailer and outcome. The events omit query, account, location and listing data. Do not request a JWT or raw provider payload. Track Firebase delivery-deferred warnings and inspect the development project's delivery report before enabling production delivery. No notification check or health probe dispatches paid retailer calls.

## Incident response

| Symptom | Action |
|---|---|
| API readiness fails | Check private database connectivity and Flyway version. Keep traffic off until the approved revision is ready. |
| Forecast is unavailable | Keep the service private. Check secret mapping and the version of the trusted artifact. Leave forecasting off when evidence is insufficient. |
| 401, 403 or 429 | Use the route status and request ID. Verify HTTPS origin, clock, session status and proxy-trust configuration. Avoid logging auth headers. |
| PIN lookup fails | Confirm `CARTIQ_GEOCODER_URL`, provider rights and coverage. Let the shopper use device GPS or select a supported locality. |
| Retailer error or pending billing | Preserve successful retailer rows and the existing ledger. Use the provider correlation record and verified balance. Do not retry an uncertain request or bypass duplicate checks. |
| Firebase delivery deferred | Check ADC, project ID, device permission and a development device token privately. Keep in-app notifications available. |

Geocoding distinguishes local validation (400), missing address (404), rate limits (429), malformed upstream responses (502), disabled or unavailable service (503), and timeout (504). Reverse lookups outside India return 422. Respect the cooldown and let the user choose a GPS or PIN retry. Do not replace unresolved coordinates.

Firebase records each notice/device attempt individually. `SENT` records an FCM acceptance and its receipt ID. An unregistered token is removed. A definite quota rejection can retry with backoff and a provider-directed pause. The worker makes at most three dispatches per receipt. Ambiguous outcomes are terminal `UNCERTAIN` and must not be replayed. Review failure counts by status and reason without exporting tokens or receipt IDs. Confirm actual device delivery in the authorized Firebase project and through device acceptance. [FCM error codes and retry guidance](https://firebase.google.com/docs/cloud-messaging/error-codes)

For an approved database incident review, use an aggregate query through the read-only operations identity. `last_error` is a sanitized code. Watch `SENDING` claims that exceed ten minutes and sustained `RATE_LIMITED` counts. The claim reaper resolves abandoned claims to `UNCERTAIN`. Keep only one delivery worker active. Stop it before a package switch or restore and leave delivery disabled on restored staging data.

```sql
SELECT status, last_error, COUNT(*) AS receipt_count
FROM notification_deliveries
GROUP BY status, last_error;
```
