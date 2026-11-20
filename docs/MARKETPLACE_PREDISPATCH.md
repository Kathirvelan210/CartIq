# CartIQ marketplace pre-dispatch report

**Dispatch blocked.** No live request, reservation or authorization was created.

## Phase 9.5 Google PIN locality selection

The reported message, “The location could not be matched to one Indian delivery area,” comes from the Google **reverse** parser. The former PIN workflow took a forward result's approximate coordinates and ran the strict reverse parser. That parser required a complete area and rejected conflicting reverse localities. The failure was reproduced with fixtures for both missing locality evidence and conflicting reverse areas. The original live response was unavailable, so the specific branch reached for PIN `641008` remains unknown.

The shopper picker now presents each forward result that has a named locality, city or postal town, Indian country evidence, matching PIN, consistent state and usable approximate coordinates. Multiple compatible localities can be chosen explicitly. A state-only result or conflicting state evidence leaves verification incomplete and offers a manual search of existing catalogue areas. Browser GPS still uses the actual device point and reverse corroboration. Sanitized adapter logs record counts and decision categories without reporting PIN values, coordinates, place names, response bodies or credentials.

A shopper's approximate PIN selection supplies no new evidence for admin area registration or retailer serviceability. The approved-point verification and the existing admin and retailer gates below still apply. Phase 9.5 used offline fixtures only.

## Phase 9.4 Google Geocoding API v4 demo

Google is the selected provider for the next isolated pilot verification. This is an admin-only prototype. The Phase 9.3 public Nominatim setup below remains available as a separate option.

1. Review the [Maps Demo Key limits](https://developers.google.com/maps/demo-key) and [Google Geocoding usage and attribution policy](https://developers.google.com/maps/documentation/geocoding/policies). Confirm approval to send the operator-supplied PIN and point to Google for two explicitly authorized requests. Then run `powershell.exe -NoProfile -File .\configuration\Start-CartIqBackend.ps1 -GoogleGeocodingDemo -Port 18094` in a private terminal and enter the existing demo key at the masked prompt. The launcher uses loopback and local H2.
2. Using a local admin session, send `POST http://127.0.0.1:18094/api/admin/geocoder-demo/pin` with `{"postcode":"641008"}`. Include `X-CartIQ-Geocoding-Consent: delivery-geocoding-v1` after consent. CartIQ calls Google's `GET /v4/geocode/address` with `address.postalCode=641008`, `address.regionCode=IN` and `languageCode=en`.
3. At least one second after completion, send `POST http://127.0.0.1:18094/api/admin/geocoder-demo/reverse` with `{"latitude":10.942972,"longitude":76.953417}` and the same consent header. CartIQ calls Google's `GET /v4/geocode/location` with `location.latitude=10.942972`, `location.longitude=76.953417`, `languageCode=en` and `regionCode=IN`. Spring Boot supplies the Google key as a header.
4. Compare the exact Indian PIN, locality, Coimbatore city and Tamil Nadu state across both results. The reverse coordinates in the CartIQ response identify the query point. The normalized postcode and locality provide the corroborating evidence. Stop on ambiguous, incomplete, mismatched or failed responses. Review the normalized results in memory and record the decision and operational status only, subject to the demo terms. Attribute Google Maps wherever its results are displayed. Keep the registration draft unsubmitted until a human reviews the evidence and confirms that the intended registration and data retention are permitted.

With `$pinResult` and `$reverseResult` assigned to the normalized lookup results, run the existing offline review without saving Google results to disk.

```powershell
@{places=@($pinResult); reverse=$reverseResult} | ConvertTo-Json -Depth 8 -Compress |
    node configuration/Review-PilotLocation.cjs - 641008 10.942972 76.953417 Coimbatore 'Tamil Nadu'
```

Both proposed requests remain unsent. Location verification, listing mapping, retailer permissions and the retailer authorization remain separate.

## Phase 9.3 blocker decision and operator handoff

| Gate | Current evidence | Required operator action |
| --- | --- | --- |
| Coimbatore area and PIN | The operator supplied `641008` for Coimbatore, Tamil Nadu, India, at `10.942972, 76.953417`. The PIN passes syntax validation. The adapter supports the public Nominatim contract, and the checked Windows process, user and machine environments have neither a geocoder URL nor a contact. The last isolated V19 inspection found zero rows for this PIN. No provider result was obtained. | Supply a monitored operator email for the identifying User-Agent. Confirm the point is non-personal and non-confidential, and separately approve the two lookups. Review that both identify the same delivery locality, PIN, city and state before submitting the existing admin registration draft. |
| Exact product mapping | Historical Blinkit SKU `176` returned brand **Amul**, title **Taaza Homogenised Toned Milk**, and quantity **1 ltr**. The title carries the formulation wording. The row supplies no separate formulation, pack count, multipack flag or GTIN. SKU `113945` was the separate 200 ml pack. The catalogue anchor `milk` is **Amul Taaza**, 1 litre, variant **Standard**, with no reviewed GTIN or formulation. Read-only execution of the current mapping review returned `TITLE_UNCONFIRMED`, with `BRAND_SAME`, `PACK_SAME` and `TITLE_DIFFERENT`. | Obtain authoritative packaging or brand-owner evidence linking SKU `176` to the exact single 1-litre homogenised toned pack. Verify both names, brand, net quantity, number of packs and GTIN where supplied. Keep source fields as observed and attach the evidence reference and SHA-256. Have the reviewer reconcile the canonical identity and rerun the existing mapping preview. Proceed through the revision journal only when the blockers clear and the reviewer approves. Preserve the original capture and any prior decision. |
| Written permission | The project register has no accepted scoped grant. `QUICKCOMMERCE_OFFER_PERMISSION_REFERENCE` is unset. | An authorized CartIQ representative must accept and record a written grant covering the QuickCommerceAPI account, Blinkit, the approved PIN and query, and the pilot window. It must cover transient private display of all returned candidates, permitted fields and any image use, temporary catalogue matching, and retention of the query authorization, sanitized request and billing metadata, and derived counts. Record dates, retention or deletion terms and the approval reference. Use the same reference in the mapping evidence, pilot setup and backend setting. |
| Isolated database | Read-only H2 2.3.232 inspection confirmed all 19 Flyway migrations and the V19 claim guard. It found zero Coimbatore areas, zero approved SKU `176` mappings, and zero pilots or authorizations. | Point the reviewed pilot launch explicitly at the isolated V19 database. The default Windows launcher uses another H2 file. Preserve the original ledger under the existing OS account. |
| Ledger and duplication | The ledger digest still matches Phase 9.1. It contains 92 settled credits, zero pending reservations and eight of 20 attempts used. The exact duplicate verdict remains pending because the adapter always sends the approved PIN. | Obtain a current signed-in dashboard balance, reconcile any discrepancy and set the verified checkpoint. Check the exact fingerprint and eligible cache before dispatch. Stop on an existing fingerprint, unknown balance, pending settlement, or a floor or cap violation. Keep one attempt, one credit and the 77-credit floor. |

The geocoder adapter reads `CARTIQ_GEOCODER_URL` only on the backend. For this pilot, the operator selected the OpenStreetMap Foundation's public Nominatim endpoint. The two-lookups workflow uses one isolated dispatching backend, explicit requests, attribution and a local cache. The provider limits aggregate traffic to one request per second and has no availability guarantee. [Public Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/) · [Geocoder contract](DEPLOYMENT_GUIDE.md#geocoder-contract)

### Next operator action and lookup verification

**Next operator action.** Supply a valid monitored operator email for Nominatim. Confirm that the proposed point is non-personal and non-confidential, and authorize one PIN lookup and one reverse lookup for this pilot. After that approval, start the isolated backend with these settings in its launch environment. The commands below are staged instructions.

```powershell
$env:CARTIQ_GEOCODER_CONTACT = Read-Host 'Monitored CartIQ operator email for Nominatim'
$env:CARTIQ_GEOCODER_URL = 'https://nominatim.openstreetmap.org'
$env:CARTIQ_GEOCODER_PUBLIC_REVERSE_POINT = '10.942972,76.953417'
```

The two CartIQ requests below are prepared for use **only after separate operator approval for both outbound geocoder lookups**. Include `X-CartIQ-Geocoding-Consent: delivery-geocoding-v1` after consenting. Existing authorized evidence for both lookups may be reviewed offline instead. Keep retailer dispatch disabled throughout.

| Step | Request to the isolated CartIQ backend | Outbound contract and acceptance evidence |
| --- | --- | --- |
| 1. PIN lookup | `POST /api/location-lookup/pin` with `{"postcode":"641008"}` | CartIQ calls `GET /search?format=jsonv2&addressdetails=1&countrycodes=in&postalcode=641008&limit=25`. The provider returns a JSON array whose entries carry an `address` object and, where available, numeric or numeric-string `lat` and `lon`. CartIQ filters for `address.country_code=in` and `address.postcode=641008`. Record the returned locality, city and state for the reviewed delivery area. A returned coordinate pair is approximate. |
| 2. Exact-point reverse lookup | `POST /api/location-lookup/reverse` with `{"latitude":10.942972,"longitude":76.953417}` | CartIQ calls `GET /reverse?format=jsonv2&addressdetails=1&lat=10.942972&lon=76.953417`. The provider returns one JSON object with an `address` object and `address.country_code=in`. Verify the reverse result identifies PIN `641008` and the same reviewed Coimbatore delivery area as step 1. CartIQ preserves the input coordinates, so compare the returned address fields. |

Wait at least one second after the first lookup finishes before requesting the second, and stop on a failed or rate-limited lookup. Where authorized, keep the normalized CartIQ responses in protected local files with the provider, lookup timestamps and the human review decision. The offline checker accepts the PIN response array and the reverse response object and requires one corroborating locality. It checks India, exact PIN, Coimbatore, Tamil Nadu and the reverse point. Missing or generic locality labels, duplicate matching locality candidates, or conflicting districts require more evidence. A PIN centroid or repeated input coordinates cannot independently establish the delivery area.

```powershell
node configuration/Review-PilotLocation.cjs '<private-pin-response.json>' '<private-reverse-response.json>' 641008 10.942972 76.953417 Coimbatore 'Tamil Nadu'
```

`CONSISTENT_FOR_HUMAN_REVIEW` means the address fields agree. The operator must still establish that the results identify the intended delivery area and record approval before submitting the registration draft or running the read-only duplicate check. Neither lookup, registration nor the duplicate check ran during Phase 9.3.

### Draft location registration

After the operator verifies the evidence, use the existing admin-only `POST /api/admin/locations` route on the isolated V19 backend. The ID and locality name below are placeholders for reviewed values. Coordinates are configured separately from the location row.

```json
{
  "id": "<approved-area-id>",
  "name": "<verified-delivery-locality>",
  "city": "Coimbatore",
  "postcode": "641008"
}
```

Use that area ID for `QUICKCOMMERCE_OFFER_LOCATION_ID` and the verified point for `QUICKCOMMERCE_OFFER_LAT=10.942972` and `QUICKCOMMERCE_OFFER_LON=76.953417`. The draft was left unsubmitted, and dispatch remains blocked.

For the identity review, use the existing `POST /api/admin/provider/mapping/preview` workflow. An eventual approval records retailer `blinkit`, native SKU `176`, the reviewed variant, the listing identity as observed, and mapping evidence. Supply a decision UUID, `sourceProvider=QUICKCOMMERCEAPI`, a traceable `sourceRecordId`, `evidenceReference`, `evidenceSha256`, the accepted `permissionReference`, and review rationale. Use `supersedesDecisionId` when revising an existing decision. A historical title alone cannot verify a structured formulation or the number of packs.

### Fields for a later authorized operator

1. In **Profile → Admin dashboard → Controlled retailer pilot**, prepare the disabled pilot. Setup fields are `location` (approved area ID), `retailer=blinkit`, `startsAt` and `endsAt` (timezone-aware), `maxAttempts=1`, `maxCredits=1`, and `permissionReference` (accepted grant). `packs` contains one entry with `variant=milk`, `group=MILK` and the approved `mappingDecision` UUID. Check `displayAndTemporaryJoinApproved` and `operationalMetadataApproved` only after confirming the written grant.
2. Leave paid, shopping and pilot dispatch gates off pending the separate approval. For the later approved isolated launch, keep legacy lookups off, use the existing protected key and supply the verified balance checkpoint. Enable only the selected pilot.
3. Only after the operator has separate human approval, the marketplace ticket fields for `POST /api/admin/retailer-pilot/{id}/authorize-discovery` are `query=amul taaza 1 litre`, `discoveryDisplayApproved=true`, and `approval` containing `accountId` (named shopper), `variant=milk`, `operation=SEARCH`, `expiresAt` (proposed 15 minutes from issue and no later than the pilot end), `authorizationReference` (unique human approval reference), and `separatelyAuthorized=true`. The server also caps expiry at one hour. The shopper action carries the issued `authorizationId`, exact location and PIN, retailer, variant and query.
4. The controller checks account and scope, current mapping, expiry and the V19 one-use claim. The ledger rejects an exact duplicate before reserving, then uses its lock to reserve at most one credit before transport. A proven debit or proven no-charge result settles the attempt. An uncertain result keeps the reservation pending and blocks subsequent dispatch. Leave retries disabled and use only operator reconciliation after uncertainty.

For the later read-only duplicate check in PowerShell 7, set `$approvedPin` to `641008` only after the evidence supports that exact PIN and point. The command displays only the duplicate verdict. The check was deferred while the PIN remains unverified.

```powershell
if ($approvedPin -cnotmatch '^[1-9][0-9]{5}$') { throw 'An approved six-digit PIN is required.' }
$pilotLedger = Join-Path $env:USERPROFILE '.cartiq/quickcommerce-budget.txt'
$pilotLedgerLines = Get-Content -LiteralPath $pilotLedger
if ($pilotLedgerLines[2] -ne 'settled') { throw 'Reconcile ledger state before proceeding.' }
$pilotRequestScope = @('search','blinkit','amul taaza 1 litre','10.942972','76.953417',$approvedPin) -join "`n"
$pilotFingerprint = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData(
    [Text.Encoding]::UTF8.GetBytes($pilotRequestScope))).ToLowerInvariant()
if ($pilotLedgerLines -ccontains $pilotFingerprint) { 'DUPLICATE' } else { 'NO MATCH IN LOCAL LEDGER' }
```

**Ready for separate human authorization: no.** Supply the location evidence, resolve the mapping, accept and record the written scope, and verify the dashboard balance and exact fingerprint first. The API key, pilot database, mapping history and credit ledger were unchanged.

## Phase 9.1 proposed request

| Field | Value |
| --- | --- |
| CartIQ entry point | Authenticated `POST /api/shopping/retailers/marketplace/search` |
| Provider operation | One QuickCommerceAPI `GET /v1/search` |
| Query and retailer | `amul taaza 1 litre`, platform `BlinkIt` |
| Catalogue anchor | Product and variant `milk`, Amul Taaza toned milk, 1 litre |
| Delivery position | Coimbatore, Tamil Nadu, `10.942972, 76.953417`. Approved catalogue area ID and six-digit PIN are missing. |
| Authorization | One named account, exact query, retailer, catalogue anchor and area. Proposed expiry is 15 minutes after human issuance, capped at the pilot end. |
| Credit impact | Maximum one credit. A confirmed charge would leave 91 settled credits, 11 experiment attempts and zero further attempts in a one-attempt pilot. Account floor remains 77. |

The adapter builds the following request after the area and PIN are approved. It normalizes the query to lowercase and always supplies the approved catalogue area's PIN. The placeholder is unresolved, so the exact duplicate fingerprint remains unverified.

```http
GET /v1/search?q=amul%20taaza%201%20litre&platform=BlinkIt&lat=10.942972&lon=76.953417&pincode=<APPROVED_PIN>
```

## Phase 9.1 readiness checks

| Check | Finding |
| --- | --- |
| Isolated database | Started the exact Phase 9 V19 release JAR with the `local` profile, a new file-backed H2 2.3.232 database at `./.runtime/preflight` in the scratch directory, and sample data off. Loopback health returned UP. Flyway applied V1–V19 and read-only SQL verified the V19 discovery claim constraint. The backend was stopped after checking. The regular launcher points at a separate `./.runtime/smartcart` file. |
| PIN workflow and approval | `CARTIQ_GEOCODER_URL` is missing from the inspected launch environment. The local API rejected an invalid PIN with 400 and returned 503 “Location lookup is not configured” for a valid-format PIN. Twelve offline geocoder tests passed. The isolated database has zero Coimbatore areas. A historical response at the proposed coordinates supplied no PIN. The operator must verify the real PIN against the authorized geocoder and confirm the exact scoped coordinates. |
| Key and ledger | The regular shell lacks the key variable. `configuration/Start-CartIqBackend.ps1 -CheckOnly` successfully reported `PRESENT` from the existing protected configuration. The offline JVM started with dispatch disabled and without loading that key. The ledger hash was unchanged at 92 settled credits, zero pending reservations and eight recorded attempts, leaving 12 under the 20-attempt cap. A fresh dashboard balance check remains pending through the signed-in operator, with no paid endpoint call. |
| Historical Blinkit identity | The operator-supplied response `922db9a5-1a13-4a11-b1de-25c9d4d8b0b7` contained Blinkit SKU `176`, **Taaza Homogenised Toned Milk**, brand **Amul**, **1 ltr**, at the proposed coordinates. It supplied no GTIN for that listing. SKU `113945` is the separate 200 ml pack. The original capture SHA-256 is `DC61E807E461F0733470E97C12C8D79E9EAE6669871BF71DEB95C465AB4E6720`. The raw capture was read in place. |
| Mapping approval | Zero approved mappings for SKU `176` and variant `milk` were found in the isolated database. Running the existing hybrid matcher against the catalogue and historical listing returned `REVIEW`, `LOW`, 53/100, with `BRAND_SAME`, `PACK_SAME` and `TITLE_DIFFERENT`. The mapping review converts that title reason to an approval blocker, `TITLE_UNCONFIRMED`. Human review needs packaging or brand-owner evidence for the exact 1-litre single pack, homogenised toned formulation, the native SKU and GTIN when available. The catalogue identity must be reconciled through the approved review process before enrolment. |
| Permissions and pilot state | No reviewed written QuickCommerceAPI grant for private display of all listings returned by this query, temporary catalogue matching, or retention of query authorization and sanitized billing metadata was found in the project records. The permission reference is unset in the inspected environment. The isolated database has zero pilots and zero authorizations. Separate human authorization remains required after the prerequisites are satisfied. |
| Frontend flow | This pass replayed the historical provider body through the parser with mocked HTTP transport and normalized all 24 rows. The previous Phase 9 Chrome workflow used actual Spring Boot HTTP with mocked adapters to verify rendering, reuse, cart write and weekly basket. Inspection confirms the approved-pack link opens canonical Product Details and its exact-pack offers panel. That panel and weekly comparison share eligible transient offers. The separately labelled four-source comparison still uses its recorded-evidence path. Marketplace results and the canonical catalogue have separate render paths. A single live Blinkit result cannot establish cross-retailer savings or a fee-inclusive total when the required data is missing. |

## Phase 9.1 evidence

`mvn.cmd -o -B '-Dtest=ShoppingGeocoderTest,QuickCommerceProviderTest#normalizesTheOperatorSuppliedBlinkitBodyWithoutRetainingIt' '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' test` passed 13 tests with zero failures and skips. Read-only H2 inspection ran after the isolated JVM stopped. Scratch evidence is under `%USERPROFILE%/CartIQ-data/phase9-readiness-122407`. [Phase 9 browser evidence](evidence/marketplace-phase9/README.md)

The approved PIN is needed to verify the exact provider request fingerprint. Clear the geocoder, location, identity, permission, dashboard and separate human authorization gates before dispatch. Keep the key unchanged, check for an eligible cache entry and use the existing persistent ledger. If billing is uncertain, pause with the pending reservation and leave retries disabled.

Zero live retailer, geocoder or model requests, zero credit debits, zero authorization creations and zero shared MySQL access occurred.
