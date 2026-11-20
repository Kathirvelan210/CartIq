# Phase 10.2 · Local Gemini activation

On 19 November 2026 the operator supplied the existing key through a masked Windows prompt. The private FastAPI child reported a present key, enabled generation and model `gemini-3.5-flash` through its authenticated readiness endpoint. A separate request without the service token received HTTP 401. The paired Spring Boot local H2 service reported UP. Google key validity, account entitlement, pricing tier and quota remain unverified. [Local readiness receipt](local-readiness.json)

## Local startup

From the project root on Windows, run the following and leave the terminal open while using the services.

```powershell
.\configuration\Start-CartIqGemini.ps1 -PromptForExistingKey
```

The key stays in memory for this launch. For the zero-network configuration check, add `-CheckOnly`. An existing process-level `GEMINI_API_KEY` or a Windows current-user DPAPI file supplied with `-ProtectedKeyFile` can also be used. The default file is `%LOCALAPPDATA%\CartIQ\gemini-key.dpapi`. The setup found no previously saved CartIQ Gemini key. It did not create a credential file. The existing encrypted retailer entry belongs to the retailer launcher.

The launcher configures FastAPI on loopback port 8001 and Spring on 8080. Spring receives the private service token and AI URL. Only FastAPI receives the Gemini key. An existing `AI_SERVICE_SECRET` and Spring `JWT_SECRET` are respected. Missing local secrets are generated independently for this session. With a session-only JWT secret, sign in again after restarting. The existing Spring local launcher selects H2. This command does not deploy or edit production settings. A conflicting listener blocks startup.

## Offline verification

| Command or check | Result |
|---|---|
| `npm.cmd test` | 68 passed, 0 failed or skipped |
| `npm.cmd run typecheck` | Passed |
| From ai-service, `.\.venv\Scripts\python.exe -B -m pytest -q -p no:cacheprovider` | 38 passed, 0 failed or skipped, one existing Starlette TestClient deprecation warning |
| From a disposable backend copy, `mvn.cmd -o -B '-Dtest=RecipeIntelligenceTest,WeeklyShoppingTest' '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' test` | H2, 4 recipe and 5 weekly shopping tests passed, 0 failures, errors or skips |
| Launcher `-CheckOnly -ProtectedKeyFile <temporary DPAPI fixture>` | Actual Python child loaded a disposable encrypted fixture, model configuration and service credential. Fixture deleted. |
| `node node_modules/expo/bin/cli export --platform web --max-workers 2 --output-dir "$env:USERPROFILE/CartIQ-data/phase10-2-local/web"` | Passed with Expo offline, dotenv disabled and same-origin /api configuration |
| `node "$env:USERPROFILE/CartIQ-data/phase10-2-local/browser.cjs"` | Four Chrome assertions passed with localhost-only networking. [Browser observations](browser-offline.json) |

The browser used a separate Spring instance on 18201 with in-memory H2 and private FastAPI on 18202. Only the Gemini transport was replaced with a deterministic fixture. Three disposable authenticated shoppers exercised the draft, weekly and assistant actions. Chrome observed loading followed by a rendered substitution, a seventh day from the returned curated list, and a shopper-specific empty-pantry explanation. A second draft attempt received HTTP 429 and displayed the server's cooldown message. The test pair was stopped after verification.

FastAPI tests exercised the model envelope and validated structured data, including unknown recipe IDs, invalid output, commercial claims, private authentication, timeouts and disabled or missing configuration. Spring tests covered private draft ownership, ingredient substitution, pantry subtraction, pack compatibility, budget limits and missing-price handling. The live service uses the existing HTTPX generateContent integration. Shopping intent selection is restricted to known CartIQ tools; Spring computes factual responses.

## First authorized live attempt

After the offline activation phase the operator authorized one recipe draft with pantry sharing off. The test client submitted once and received CartIQ HTTP 502. Google acceptance, usage and billing are unknown. [Original evidence and offline diagnosis](GEMINI_502.md)

This phase sent zero live Gemini, geocoding or retailer requests. The retailer ledger's before/after SHA-256 matched. Shared MySQL and production configuration were untouched.
