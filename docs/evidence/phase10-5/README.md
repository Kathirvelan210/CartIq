# Phase 10.5 Gemini recipe verification

## Authorized live submission at 11:57:35 IST on 19 November 2026

The operator signed in to the Expo Web Recipes screen in Chrome using an existing account. Spring Boot on port 8080 reported `UP`; FastAPI on port 8001 reported `ok`. The running Spring JVM pointed both datasource and Flyway at local H2. Both services stayed running throughout the check.

The browser submitted the authorized breakfast recipe request once. The loopback relay checked the exact request and `usePantry=false`, acquired an exclusive single-use dispatch marker and forwarded the authenticated POST to Spring. The test blocked retailer, location and other generation routes. The unchanged adapter requested one candidate with a limit of 2,800 output tokens. The running FastAPI console confirmed the model and upstream error on the same CartIQ correlation ID.

| Live result | Observed value |
|---|---|
| Model | `gemini-3.5-flash` |
| CartIQ submissions / provider requests / retries | 1 / 1 / 0 |
| CartIQ correlation ID | `81ec13ad-2ec2-4d9d-8ee1-317f9ac0b14f` |
| Upstream HTTP / CartIQ HTTP | 503 / 502 |
| Shopper-visible result | `GEMINI_SERVER`. Chrome displayed “Gemini reported a server error.” No valid draft was returned or rendered. |
| End-to-end timing | 11.271 seconds to the complete HTTP response and 11.312 seconds to the verified error on screen, measured from the generation click |
| Upstream request ID, token usage, quota and billing | Unavailable in the sanitized receipt and correlated application diagnostics |

The upstream response confirms that the request reached the provider. It gives no evidence of successful model execution or a charge. No retailer or geocoding API was called, no QuickCommerce credits were consumed and shared MySQL was untouched. Any further generation requires fresh authorization. [Sanitized live receipt](live-receipt.json)

### Offline regressions after the live response

| Scope | Command | Result |
|---|---|---|
| FastAPI, from `ai-service` | `.\.venv\Scripts\python.exe -B -m pytest -q -p no:cacheprovider test_recipe_generation.py` | 35 passed. One existing Starlette TestClient deprecation warning. |
| Frontend, from the project root | `npm.cmd test` | 68 passed |
| Spring, from a disposable copy of the current backend source with the test profile and H2 | `mvn.cmd -o -B -Dtest=GeminiFailureTest,RecipeIntelligenceTest -Dspring.profiles.active=test -DargLine=-Duser.home=. test` | 8 passed |

All 111 offline tests passed with zero failures or skips. The tests made zero additional live requests.

## Earlier preflight

**Stopped before dispatch, 19 November 2026 at 11:37 IST.** Windows showed no listening socket on the configured service ports. Both direct loopback health checks failed before any HTTP response arrived.

| Check | Observed result |
|---|---|
| Spring Boot, `GET http://127.0.0.1:8080/actuator/health` | TCP connection refused |
| FastAPI, `GET http://127.0.0.1:8001/health` | TCP connection refused |
| Current authenticated FastAPI readiness, generation enablement and model | Unverified. The configured service was unreachable. The earlier Phase 10.4 receipt belongs to a previous run. |
| Expo submissions, provider dispatches, retries | 0, 0, 0 |
| Draft, end-to-end generation time, CartIQ correlation ID, provider HTTP status and provider request ID | No generation transaction or receipt |
| Tokens, quota and billing | No provider usage or billing information returned |

The local checks used `Get-NetTCPConnection -State Listen` and PowerShell `Invoke-RestMethod -NoProxy -TimeoutSec 3 -MaximumRedirection 0`. The browser generation path was never entered. No retailer, geocoding or Gemini requests were sent. No credential, configuration, permission, authorization record, retailer ledger or shared database was changed.

## Offline regression checks

| Working directory | Command | Result |
|---|---|---|
| `ai-service` | `.\.venv\Scripts\python.exe -B -m pytest -q -p no:cacheprovider test_recipe_generation.py` | 35 passed, 0 failed, 0 skipped. One Starlette TestClient deprecation warning. |
| Project root | `npm.cmd test` | 68 passed, 0 failed, 0 skipped. |

The stop condition ended this run. For another attempt, start the existing local pair with `.\configuration\Start-CartIqGemini.ps1 -PromptForExistingKey`, enter the existing credential only in the masked local prompt, and authorize a fresh one-request run after the services are ready.
