# GPS verification results — 18 November 2026

| Check | Method | Result |
|---|---|---|
| Foreground permission, denial and recovery | Android permission sheet, CartIQ explanation, blocked prompt, app-settings button, while-in-use grant | PASS on physical Android 16 |
| Approximate permission | Android coarse granted, fine denied. The UI showed approximate permission and a 2,000 m radius before confirmation. | PASS on device. An earlier attempt timed out with a visible error. |
| Precise permission | Device previews showed 20 m initially, then 26 m and 25 m in the corrected experience. | PASS on device |
| Authenticated transfer | Four confirmed PUTs returned 200. Each request had in-range coordinates, source `gps`, no PIN, a recent millisecond timestamp and the expected authenticated subject. | 4 passed, 0 failed |
| Original sensor time and accuracy | Final timestamp in the HTTP body matches the device preview to its displayed second. The backend received the unrounded precision. | PASS |
| Restart and app resume | Saved selection survived force-stop and return from background before expiration. The relay saw no additional PUT. | PASS |
| Expiry, range checking and owner isolation | Spring tests reject invalid coordinates, missing accuracy, future or expired device time. Two real local JWTs exercise selection and deletion through HTTP. A different account cannot use or clear the owner's fix. | PASS in isolated tests |
| PIN fallback | Frontend selection and verification fixtures, source separation, Spring controller access checks. Physical lookup excluded. | PASS offline |
| Android export | Expo Android Hermes export | PASS |
| Standalone release APK, emulator, Chrome and live geocoding | No run in this milestone | NOT TESTED |

## Commands and counts

From the project root, `npm.cmd test` ran **68 tests, 68 passed, 0 failed, 0 skipped**. `npm.cmd run typecheck` exited 0. Node v24.18.1.

From an isolated copy of `backend/src` and `backend/pom.xml`, using Java 17.0.12:

```powershell
mvn.cmd -o -B '-Dtest=ShoppingPositionsTest,ShoppingPositionHttpTest,GoogleLocationPickerSecurityTest' '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' test
```

**4 tests, 4 passed, 0 failed, 0 errors, 0 skipped.** Two service tests used synthetic fixes. One H2/MockMvc test exercised consent, authentication and an invalid PIN without issuing a provider lookup. One H2/random-port HTTP test used real application-issued JWTs. It verified unauthenticated rejection (401), valid selection (200), rejection of an expired replacement (400), and account-scoped deletion (204). Flyway reached V20 in both in-memory Spring contexts. No new migration was introduced.

Android export ran offline with `EXPO_NO_DOTENV=1`, `EXPO_OFFLINE=1`, `EXPO_NO_TELEMETRY=1` and `EXPO_PUBLIC_API_URL=https://localhost/api`:

```powershell
node node_modules/expo/bin/cli export --platform android --max-workers 2 --output-dir "$env:USERPROFILE/CartIQ-data/gps101-1794997806932/android-export-final"
```

The export completed. Device execution used Expo Go. The developer runtime also emitted a React Native text-outside-Text warning on the home screen. It did not prevent sign-in, permission handling or the four transfers.

No retailer, model or geocoding requests were sent. Zero provider credits were consumed. A read-only before/after SHA-256 comparison of the existing retailer ledger matched. The shared MySQL database was untouched.
