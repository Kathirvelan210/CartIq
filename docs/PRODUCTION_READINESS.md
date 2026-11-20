# CartIQ release readiness

## Phase 10 · 18 November 2026

Local implementation supports consented device GPS, authorized scoped retailer discovery and persistent exact-listing cart choices. Launch remains conditional on retailer display and matching rights, reviewed exact mappings, authorized geographic scope, provider availability, credentialed Gemini verification and real-device checks. The controlled retailer endpoints remain a local pilot. An ordinary user cannot create a dispatch ticket. Swiggy serviceability at the intended location is unconfirmed.

The verified release checks, including the actual disposable database version and the distinction between browser geolocation emulation and physical GPS, are in TESTING_STATUS.md. A signed app or production service was not deployed.


CartIQ has a local release candidate for Expo Web, the Java 17 API, MySQL 8 and an internal Python forecasting service. The Docker Compose stack is for isolated integration and a deployment reference. Production infrastructure, mobile signing, and Firebase delivery need separate verification. [Verification results](TESTING_STATUS.md#production-release-verification)

| Area | Release state |
|---|---|
| Sign-in, refresh, logout, ownership | Existing JWT, rotating HttpOnly cookies, account scoping and role boundaries retained. Production disables demo accounts and makes session responses non-cacheable. |
| Catalogue, comparison, cart and assistant | Existing paginated catalogue and verified-offer workflows retained. A provider search result does not become a stored canonical offer. |
| Retailers | Blinkit and Zepto previously worked on the controlled local adapter. Swiggy remains an independent upstream failure. Paid QuickCommerce routes are outside the production profile. Public display permission and a production credit-control design are outstanding. |
| Retailer data quality and mapping | Admin coverage, evidence preview, human mapping decisions and history are implemented. Transient live counts cover only the admin's own approved pilot position. Cross-process diagnostic aggregation, representative licensed coverage and live pilot authorization remain deployment requirements. |
| Location | Web on HTTPS and native foreground permissions exist. PIN lookup and reverse addresses require `CARTIQ_GEOCODER_URL`. No default coordinates are supplied. |
| History and forecasting | History uses recorded evidence. Forecasting defaults off. Only validated, trusted model artifacts should be considered for release. |
| Unified shopping planner | Implemented using recorded offers and deterministic Spring services. V17 stores owner-scoped drafts and optional feedback. Phase 5 used a local Web rehearsal and disposable databases. Native UI, representative retailer coverage and production deployment require separate verification. |
| Notifications | Web, Android and iOS registration and server-side token lifecycle are implemented. Local tests use fixtures and a mocked Firebase sender. Firebase project and device delivery remain externally blocked. |
| Scale | This configuration assumes one API instance. In-memory app limits and scheduled notification work require coordination before scaling horizontally. Review MySQL query plans against representative production volume before claiming capacity. |

The Windows loopback rehearsal verified HTTPS proxying, production startup with disposable MySQL, backup restoration, and Java package rollback. Deployment approval still requires a private Compose rehearsal on the target host, configured geocoder and Firebase checks, signed builds and device verification, and review of the deployment host and secret handling. [Launch steps](DEPLOYMENT_GUIDE.md) · [Staging evidence](TESTING_STATUS.md#staging-deployment-verification)

Apply V17 only through the existing approved migration procedure after backing up the target database. A package rollback to V16 hides the new endpoints. Keep the V17 tables and plan/feedback records in place for roll-forward. [Phase 5 evidence](TESTING_STATUS.md#unified-shopping-intelligence-phase-5)

## External launch requirements

| Gate | Status | Requirement |
|---|---|---|
| Compose configuration and Nginx template | Verified locally | Run the reviewed configuration on a private Docker Engine and Compose v2 host. Confirm port isolation, image health checks, and TLS with the target hostname and CA. |
| Container execution and image rollback | Externally blocked | Use immutable candidate and prior image digests, an isolated MySQL restore, and the rollback acceptance checks. |
| Geocoder | Verified with local tests | An approved HTTPS Nominatim-compatible endpoint and confirmation of real Indian PIN and GPS coverage are needed. |
| Firebase delivery | Externally blocked | Separate development and production projects, matching clients, least-privilege backend ADC, APNs setup for iOS, and authorized devices. |
| Native device registration and rotation | Implemented but unverified on devices | Config generation and backend tests passed. Verify foreground registration, token changes, revoke, and sign-out with signed builds. |
| Per-device delivery receipts | Implemented | The backend stores FCM acceptance, terminal and ambiguous outcomes, and bounded recovery for explicit quota rejection. Live device verification remains externally blocked. |
| Native signing and device acceptance | Externally blocked | Linked EAS project, approved build access, Android signing, Apple signing and provisioning, and reachable private HTTPS. |
| Production secrets, data and rollout approval | Externally blocked | Approve the host, TLS, runtime and migration identities, secret injection, backup/restore and monitoring before any production change. |

Use the release acceptance command and both reports for sign-off. The candidate includes additive Flyway V12. Review its isolated migration and rollback evidence before applying it to any approved deployment database. External gates include the target Compose and image rollback rehearsal, live PIN coverage, Firebase delivery, EAS signing and device walkthroughs. [Acceptance workflow](DEPLOYMENT_GUIDE.md#release-acceptance-command)
