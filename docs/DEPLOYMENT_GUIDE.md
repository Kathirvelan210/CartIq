# CartIQ deployment guide

## Phase 10 · 18 November 2026

Set `EXPO_PUBLIC_API_URL` to the reachable CartIQ backend. Use HTTPS on devices and HTTPS or localhost on web. A missing API URL now surfaces a configuration error. Start the backend with an isolated catalogue and sample mode off. Device GPS confirmation needs a signed-in user and foreground location permission. Reverse geocoding is optional. PIN lookup still requires the configured geocoder and separate disclosure. Google demo selections stay out of persistent address storage.

Apply V20 through the existing reviewed migration procedure on a disposable or explicitly authorized database. A valid active position expires after 15 minutes. Reacquire GPS or select a saved PIN to submit a new position. The authenticated `PUT /api/me/shopping-position` body contains latitude, longitude, accuracy in metres for GPS, observedAt in Unix milliseconds, source (gps or pin), and nullable postcode. DELETE clears the in-process selection. POST to the marketplace cart-quotes route reads current cart evidence without dispatching a provider lookup. Commercial serviceability is gated independently from device accuracy.

For AI, keep `GEMINI_API_KEY`, `GEMINI_MODEL` and `AI_SERVICE_SECRET` in the protected service environment. Enable `CARTIQ_RECIPE_GENERATION_ENABLED` in Spring and FastAPI only for authorized use. Example supported model identifier is `gemini-3.5-flash`. Run `node configuration/Gemini-Recipe-Preflight.cjs` in that environment to check configuration without making a request. AI Smart Shopping only sends the question on **Ask with Gemini**. Recipes exposes separate explicit actions for one generated recipe and a seven-day selection.

Proposed first retailer verification requires a separate one-use BlinkIt search ticket for the exact query, reviewed pack, account, coordinates, location and expiry. The previously proposed Coimbatore point and PIN must match the confirmed shopper selection and approved area. Maximum cost is one credit. No other retailer or item operation is part of that request.


## Release acceptance command

Run from the workspace with Node.js and installed project dependencies. The command writes both `release-acceptance.json` and `release-acceptance.md`. By default it runs available offline checks, including an isolated Maven PIN fixture suite. Environment validation omits secret values from both reports. A missing external dependency leaves a gate open. Exit code `0` means all gates passed, `1` means a check failed, and `2` means acceptance is incomplete.

```powershell
node configuration/Accept-Release.cjs
```

Copy the [input example](../configuration/acceptance.example.json) to a private file, keep only sections for available checks, and use the reviewed release artifact versions and hashes. Inject service secrets through a protected environment file or process environment. The input JSON contains file paths and public metadata only. If you have approved Firebase development configuration, add `--firebase`.

```powershell
$release = Join-Path $env:USERPROFILE 'CartIQ-release'
node configuration/Accept-Release.cjs --inputs "$release/acceptance.local.json" --env-file .env.production.local --output-dir "$release/acceptance-report"
```

For HTTPS and health checks, start the private staging services first and provide their loopback ports and CA. The MySQL rehearsal has its own fresh local data directory, encrypted loopback connection and disposable accounts. It accepts a pinned current Spring Boot JAR and an optional pinned previous JAR, validates Flyway, restores a synthetic backup into a separate schema, and checks the restored fixture through both packages. Keep its output under a private scratch directory. It never accepts a database URL or an existing data directory. An image rollback on the target Docker host remains a separate operational check. [MySQL initialization](https://dev.mysql.com/doc/refman/8.0/en/data-directory-initialization.html)

For package rollback, fill in `isolatedMysql.previousArtifact` and `isolatedMysql.previousSha256` together. For a portable Nginx proxy served by the disposable candidate, move the HTTPS `origin`, `httpPort`, `ca`, and `backendPort` into `isolatedMysql.edge` and start that proxy before acceptance. The fresh candidate binds the requested free backend port during the rehearsal. The `https.forecastPort` setting remains separate. Provide `AI_SERVICE_SECRET` to the forecast service and acceptance process through private environment injection.

## Local release stack

Run the offline gate from the workspace with `node configuration/Check-Release.cjs`. Once a private Docker host and staging settings are available, run `node configuration/Check-Release.cjs --env-file .env.production.local --docker-config`. For Firebase development, add `--firebase` and include `-f configuration/compose.firebase.example.yml` after `-f compose.yml` in each Compose command. `--docker-config` requires Docker Engine and Compose v2. The validator uses `config --quiet` so interpolated values stay out of command output. Use an isolated shell with staging-only environment variables.

1. Install Java 17, Node 22, Docker Engine with Compose v2 and an HTTPS certificate trusted by the test device. Copy `configuration/production.env.example` to a private `.env.production.local` outside the build contexts or to an ignored local path. Set independent JWT and AI service secrets with at least 32 random bytes and disposable database credentials. Set `CARTIQ_HTTPS_ORIGIN` to the exact canonical HTTPS origin, including the port if nonstandard, and include the same origin in `ALLOWED_ORIGINS`. Mount a certificate and private key named `fullchain.pem` and `privkey.pem` through `CARTIQ_TLS_DIR`.
2. Check `docker compose --env-file .env.production.local config --quiet`. Start the disposable database with `docker compose --env-file .env.production.local up -d --wait db`. Never point local integration at shared MySQL.
3. On the known disposable database run `docker compose --env-file .env.production.local --profile migration run --rm migrate migrate`. Then run the same command with the final argument `validate`. These operations use the migration entry point in the same backend image.
4. Run `docker compose --env-file .env.production.local up -d --build --wait backend forecast web`. The edge is the only published service. The default loopback HTTP port 8081 redirects with 308 to `https://localhost:8443`. Confirm the redirect preserves the path and query, then check the certificate and headers on HTTPS. Use the browser and API to verify health, registration, session renewal, sign-out, catalogue, cart and location state. Never bypass TLS verification for acceptance.
5. Use the same staging project name when tearing the stack down. This preserves the MySQL volume.

Use the fixed staging Compose context and project for the actual rehearsal. These examples target the Compose-owned `db` service. Do the migration step only for the approved disposable staging volume.

```powershell
docker --context default compose --project-name cartiq-staging --env-file .env.production.local -f compose.yml --profile migration config --quiet
docker --context default compose --project-name cartiq-staging --env-file .env.production.local -f compose.yml up -d --wait db
docker --context default compose --project-name cartiq-staging --env-file .env.production.local -f compose.yml --profile migration run --rm migrate migrate
docker --context default compose --project-name cartiq-staging --env-file .env.production.local -f compose.yml --profile migration run --rm migrate validate
docker --context default compose --project-name cartiq-staging --env-file .env.production.local -f compose.yml up -d --build --wait backend forecast web
node configuration/Test-StagingRelease.cjs --origin https://localhost:8443 --http-port 8081 --ca "$env:USERPROFILE/CartIQ-staging/ca.pem"
docker --context default compose --project-name cartiq-staging --env-file .env.production.local -f compose.yml down
```

## Deployment environment

- Assign hostname and HTTPS certificates, restrict API and forecasting ports to the private network, and publish only the TLS proxy. Assign the proxy an explicit trusted address. Rewrite forwarded headers at that proxy. Use same-origin web `/api` requests so the Strict refresh cookie stays usable.
- Use separate deployment MySQL and credentials. Require encrypted identity-verified JDBC transport on remote infrastructure. Grant the runtime user read/write application access without DDL. Assign schema changes to a separate migration identity. Use protected environment injection and omit credentials from URLs. Configure backups and verify a restore on a separate instance before authorizing Flyway. [Rollback guide](ROLLBACK_GUIDE.md)
- Build immutable backend, forecast and Web images, scan their dependencies, record image digests and deploy the same reviewed artifacts to staging and production. Compose fixes both JDBC targets to its disposable container. Configure a separate deployment using the same images for staging or a managed database. Run the migration launcher with the approved deployment JDBC URL and the migration identity only after the backup and migration approval.
- For PIN and address lookup, set `CARTIQ_GEOCODER_URL` on the backend to an authorized HTTPS Nominatim-compatible base URL. Verify one known serviceable PIN, multiple locality choices when supplied, and an unresolved address. Never turn a PIN into exact GPS coordinates. For Firebase staging, configure a development project, inject ADC into the backend from the deployment identity or a private read-only credential mount, and pass the development project's public Firebase values to the web build. Keep delivery disabled without credentials and project values.
- If using a local ADC file, set `FIREBASE_DEV_ADC_FILE` and `FIREBASE_PROJECT_ID` in the ignored environment file and select the Firebase development override. The mount is read-only on the backend. Confirm its non-root runtime identity can read it. Use an authorized staging account and devices to test registration and one real development-project notification.
- Generate an EAS production build using `eas build --platform android --profile production` and `eas build --platform ios --profile production` after linking the release checkout to the organization's EAS project, configuring signing and setting `EXPO_PUBLIC_API_URL=https://<approved-host>/api` in the EAS production environment. The native bundle IDs are in `app.json`. Preview uses internal distribution. Test foreground location and session refresh on both devices before submission. EAS and store build/signing operations can incur costs and were not run for this milestone. [EAS build profiles](https://docs.expo.dev/eas/json/)
- Paid QuickCommerce stays gated in the local profile. Keep the existing key backend-only. Do not copy the developer ledger to a container as a production data source.
- For a fresh schema, supply a unique `BOOTSTRAP_ADMIN_EMAIL` and a protected strong `BOOTSTRAP_ADMIN_PASSWORD` for initial startup. Remove both from the runtime environment after confirming the account. Add supported delivery areas through `POST /api/admin/locations` and publish an approved catalogue before onboarding shoppers. A fresh database supplies categories and retailer names, with no sample catalogue, verified retailer prices or fee records.
- After a verified migration and release approval, check both service health probes, confirm no sample catalogue is enabled, and watch sanitized error and latency logs. [Runbook](OPERATIONS_RUNBOOK.md)

For a deployment outside Compose, inject `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` for the approved migration account and use the reviewed executable JAR:

```text
java -Dloader.main=com.smartcart.config.DatabaseMigration -cp smartcart-api-0.2.0.jar org.springframework.boot.loader.launch.PropertiesLauncher migrate
java -Dloader.main=com.smartcart.config.DatabaseMigration -cp smartcart-api-0.2.0.jar org.springframework.boot.loader.launch.PropertiesLauncher validate
```

Start the API with the runtime database account, `SPRING_PROFILES_ACTIVE=prod`, the application secrets and `ALLOWED_ORIGINS` from the release environment. The server validates the schema before readiness and leaves migrations to the separate operation.

## Windows loopback proxy verification

With portable Nginx, a local Web export, and a private staging certificate, render and validate the same edge template through `./configuration/Render-LoopbackStagingProxy.ps1 -NginxDirectory <nginx> -WebDirectory <export> -TlsDirectory <tls>`. It changes only local ports, upstream address, certificate and log paths for the Windows test. Start `nginx.exe -c conf/cartiq-staging.conf` from the Nginx directory with the test API on loopback port 18932. Check `http://127.0.0.1:18081` and `https://localhost:18443` with a client scoped to the test CA. Stop that instance with `nginx.exe -s quit -c conf/cartiq-staging.conf`. This verifies proxy behavior without exercising Docker networking or entry-point templating. [Staging evidence](TESTING_STATUS.md#staging-deployment-verification)

The Windows verification used the following commands from the workspace. The replay harness expects the preserved candidate and prior JARs, the disposable SQL dump, a fresh initialized MySQL data directory recorded in the scratch `mysql-path.txt`, local certificates, and the portable Nginx template output above. Start the scratch MySQL instance before running the harness. The harness restores the dump and shuts down the isolated services when it exits.

```powershell
$stage = Join-Path $env:USERPROFILE 'CartIQ-data/2026-11-15-staging'
& (Join-Path $stage 'verify-staging.ps1')
$env:PYTHONPATH = (Resolve-Path ./ai-service).Path
& "$env:USERPROFILE/CartIQ-data/2026-11-15-shopping-intelligence/forecast-venv/Scripts/python.exe" -B (Join-Path $stage 'run-forecast.py')
```

The disposable MySQL server was initialized with `mysqld.exe --no-defaults --initialize-insecure --datadir=<new-scratch-directory>` and started with `mysqld.exe --no-defaults --console --bind-address=127.0.0.1 --port=13320 --datadir=<new-scratch-directory> --mysqlx=OFF`. The insecure bootstrap applies only to the disposable loopback database. The API runs under a separate DML-only identity.

## Firebase project setup

1. Create a development project and separately provision a production project before rollout. Enable the Firebase Cloud Messaging HTTP v1 API. Provide the backend workload with least-privilege ADC for sending FCM messages in the chosen project. Prefer managed workload identity on deployed hosts. `FIREBASE_ENABLED=true` requires valid ADC and `FIREBASE_PROJECT_ID`. Leave it off until a dev project is authorized.
2. Register the Web app if Web push is needed. Configure all five `EXPO_PUBLIC_FIREBASE_*` Web values listed in `configuration/production.env.example`, including the public VAPID key. The Web project ID must match `FIREBASE_PROJECT_ID`. Use HTTPS and grant browser notification permission during acceptance.
3. Register Android `com.raydonsolutions.cartiq` and iOS `com.raydonsolutions.cartiq` with Firebase. Supply the app-specific `google-services.json` and `GoogleService-Info.plist` as protected EAS file variables `CARTIQ_FIREBASE_ANDROID_FILE` and `CARTIQ_FIREBASE_IOS_FILE`. These are client app configurations. Keep Admin ADC, signing material and APNs private keys out of client build environments and artifacts. When the CLI evaluates app configuration locally, provide authorized local client file copies via the same variables.
4. Configure Apple's Push Notifications capability for the app ID and upload the authorized APNs credential through Firebase. Set `CARTIQ_NATIVE_PUSH_ENABLED=true` and `EXPO_PUBLIC_FIREBASE_PROJECT_ID` for the native build. EAS preview and production use the production APNs entitlement. A locally signed development build may use the development entitlement when its provisioning allows it.
5. On each authorized device, sign in, save notification preferences, and enable push for that device in Settings. Verify a dev-only notification with the app foregrounded, backgrounded and closed. Check token refresh, system permission revocation, opt-out, and sign-out. Confirm another signed-in device keeps its separate registration. Use the in-app notification list to verify the full event and mark it read.

The receipt worker requires Flyway V12. Apply it only through the reviewed migration procedure and with the prior worker stopped. Stop the current worker before switching packages during rollback. Pending notifications created before V12 remain in the in-app list. Do not enable Firebase during a backup or rollback rehearsal.

[Firebase HTTP v1 and ADC](https://firebase.google.com/docs/cloud-messaging/send/v1-api) · [React Native Firebase setup](https://rnfirebase.io/)

## Signed native builds

Use the existing EAS profiles and [native environment template](../configuration/native.env.example). Set `CARTIQ_EAS_PROJECT_ID` to the linked project UUID. Configure `EXPO_PUBLIC_API_URL` as a private, device-reachable HTTPS URL ending in `/api` for both preview and production. Preview builds are for internal distribution. The release Android profile emits an App Bundle. Run these only with approved project, signing, provisioning and build access.

An authorized EAS operator can review existing credentials with `eas credentials -p android` and `eas credentials -p ios`. Review the linked project, bundle IDs, Android keystore assignment, Apple signing certificate and provisioning, and push capability. This acceptance step stops after inspection. Keep signing credentials and their exports out of reports. [EAS credential management](https://docs.expo.dev/app-signing/managed-credentials/)

```text
eas build --platform android --profile preview
eas build --platform ios --profile preview
eas build --platform android --profile production
eas build --platform ios --profile production
```

## Geocoder contract

### Google Maps Demo Key for local geocoding

Select `CARTIQ_GEOCODER_PROVIDER=google-demo` on a loopback backend with the `local` profile and supply the existing key as `CARTIQ_GOOGLE_GEOCODING_API_KEY` through the backend process environment. On Windows use `powershell.exe -NoProfile -File .\configuration\Start-CartIqBackend.ps1 -GoogleGeocodingDemo -Port 18094`. The launcher accepts an already injected key or prompts without echo, passes it to Maven and Spring Boot through process inheritance, and restores the previous environment when it exits. It uses the separate `backend/.runtime/geocoding-demo` H2 database and disables retailer dispatch. Keep the Google key out of command-line arguments, frontend settings, dotenv files and HTTP traces.

Google's fixed HTTPS API origin receives the key in `X-Goog-Api-Key`. The local admin-only routes require the consent header and use the existing rate gate and timeout. Results are returned with `Cache-Control: no-store` and the demo bypasses application result caching. It adds no saved-location, evidence or area writes. The demo is unavailable under the production profile. Follow the demo terms and show Google Maps attribution if displaying its results. Review those terms before retaining or using any returned data outside this diagnostic. [Maps Demo Key and quota](https://developers.google.com/maps/demo-key) · [Forward v4 contract](https://developers.google.com/maps/documentation/geocoding/geocoding) · [Reverse v4 contract](https://developers.google.com/maps/documentation/geocoding/reverse-geocoding) · [Geocoding policies](https://developers.google.com/maps/documentation/geocoding/policies) · [Pre-dispatch workflow](MARKETPLACE_PREDISPATCH.md#phase-94-google-geocoding-api-v4-demo)

The local shopper location picker also supports the demo. Restart Spring Boot with the demo launcher after updating the code, then open Expo Web on `http://localhost:8092` with `EXPO_PUBLIC_API_URL=http://127.0.0.1:18094/api`. Sign in and allow delivery geocoding in the picker. An Indian PIN search makes one forward request and lets the user choose a compatible named locality with an approximate point. **Use current location** requests browser permission and uses reverse geocoding plus a PIN lookup to corroborate the actual device point. Conflicting evidence blocks verification. The picker attributes Google Maps and keeps the selected demo location in memory for the session. Shopper selection creates no catalogue or pilot area. The diagnostic admin routes remain separate.

### Nominatim configuration

Omit `CARTIQ_GEOCODER_PROVIDER` or set it to `nominatim` to use the existing `CARTIQ_GEOCODER_URL` configuration. Provider selection is explicit and the adapter makes no fallback calls. For ordinary device GPS, use a private Nominatim-compatible service authorized for device coordinates and set `CARTIQ_GEOCODER_ALLOW_DEVICE_GPS=true`. Public Nominatim keeps its precise device GPS restriction.

**Public Nominatim usage policy.** Use the public service only for a modest, explicitly approved pilot with one CartIQ backend instance issuing requests. The OpenStreetMap Foundation limits an application's combined traffic to one request per second, requires identification and attribution, and prohibits autocomplete, systematic queries and sending personal or confidential data. CartIQ sends an identifying User-Agent and uses a bounded 15-minute in-memory cache for validated results, including empty searches. One outbound lookup can be in flight and the next uncached lookup must wait at least one second after completion. The adapter honors provider rate limits, and disables retries and redirects. The public service offers no availability guarantee. Use a separately approved service if CartIQ needs higher volume, multiple dispatching replicas or private GPS geocoding. The backend can switch providers through the same setting without a mobile release. [Public Nominatim usage policy](https://operations.osmfoundation.org/policies/nominatim/) · [OpenStreetMap attribution](https://www.openstreetmap.org/copyright)

For the public pilot, configure the isolated Spring Boot launch environment or the private Compose environment file.

| Setting | Public pilot value |
| --- | --- |
| `CARTIQ_GEOCODER_URL` | `https://nominatim.openstreetmap.org`. Keep it blank until the operator is ready. Other authorized Nominatim-compatible HTTPS base URLs may include a base path. Credentials, query strings and fragments are rejected. Loopback HTTP is reserved for local fixtures. |
| `CARTIQ_GEOCODER_CONTACT` | A monitored email supplied by the CartIQ operator and intended for disclosure to Nominatim. The backend sends it in `User-Agent: CartIQ/1.0 (contact: …)`. The public configuration fails startup validation without a syntactically valid contact. |
| `CARTIQ_GEOCODER_PUBLIC_REVERSE_POINT` | Leave blank to disable reverse lookup against public Nominatim. Set `10.942972,76.953417` only if the operator confirms this is a non-personal, non-confidential delivery-area point and separately approves the lookup. Other GPS points stay off the public service. |

The mobile app sends explicitly submitted requests to Spring Boot. The backend alone calls `GET /search` with `format=jsonv2`, `addressdetails=1`, `countrycodes=in`, a validated Indian `postalcode`, and `limit=25`. Public Nominatim receives the PIN alone. Other compatible services may receive an optional structured `city`. Search returns a JSON array whose entries contain an `address` and may contain numeric or numeric-string `lat` and `lon`. CartIQ retains Indian results with the exact requested PIN. PIN coordinates are approximate when available. `GET /reverse` takes `format=jsonv2`, `addressdetails=1` and the approved WGS84 `lat` and `lon`. CartIQ requires an Indian `address.country_code` and preserves the input point. Nominatim identifies a nearby suitable mapped feature, so review its returned address and cross-check the PIN and locality before registering any area. [Nominatim Search](https://nominatim.org/release-docs/latest/api/Search/) · [Nominatim Reverse](https://nominatim.org/release-docs/latest/api/Reverse/) · [Pilot verification workflow](MARKETPLACE_PREDISPATCH.md#next-operator-action-and-lookup-verification)
