# Gemini diagnostics after the local service restart

On 19 November 2026 the Windows local services restarted on their original loopback ports. The operator entered the existing credential in a masked local prompt before the old pair stopped. Only the dedicated launcher console and its identified Spring and FastAPI children received the shutdown signal. The replacement used `configuration/Start-CartIqGemini.ps1` and its child `Start-CartIqBackend.ps1 -LocalGemini`.

Spring PID 23348 returned `UP` on port 8080. Its datasource and Flyway configuration both pointed to local H2 at `./.runtime/smartcart`; retailer access was disabled. FastAPI PID 14020 returned `ok` on port 8001. The paired launcher confirmed private authentication, readiness and the expected `gemini-3.5-flash` model through `GET /recipes/readiness`. This checks local configuration only. [Restart receipt](service-restart.json)

## Browser results

Chrome used a fresh Expo Web export, nine disposable authenticated accounts, Spring on port 18201 with in-memory H2, and private FastAPI on port 18202. Each shopper clicked **Generate recipe draft** once with pantry sharing off. Only the outbound HTTP transport in this isolated FastAPI process was redirected. It verified the model URL and synthetic key, stripped the key and sent the request to a loopback fixture. The real-key services received health or startup-readiness checks only.

| Fixture | Provider HTTP | CartIQ HTTP | What the shopper saw |
|---|---:|---:|---|
| Valid complete draft | 200 | 200 | Returned recipe substitution |
| Authentication | 401 | 503 | Gemini rejected the service credential or its access permissions |
| Unknown model or endpoint | 404 | 503 | Configured model or endpoint was not found |
| Invalid payload | 400 | 502 | Gemini rejected the generation request |
| Rate limit | 429 | 429 | Gemini reported a quota or rate limit |
| Provider server failure | 503 | 502 | Gemini reported a server error |
| Connection refused | No response | 502 | Connection to Gemini failed |
| Read timeout before headers | No response | 504 | Gemini request timed out |
| Invalid generated JSON | 200 | 502 | Incomplete or invalid model output |

The browser receipt and FastAPI result shared a CartIQ correlation ID in all nine cases. All seven responses with provider headers retained the synthetic upstream request ID and status at the browser. Connection refusal and timeout produced neither field. Each action caused one browser submission and one HTTP transport dispatch. The loopback provider received eight HTTP requests, including the one it deliberately left unanswered past the real ten-second read timeout. The connection-refusal case reached a reserved, non-listening port. No raw provider error text was displayed. [Per-case browser and transport evidence](browser-results.json)

The first browser pass revealed that a successful draft omitted the upstream receipt headers. FastAPI and Spring now forward the same allowlisted status and request ID for successful drafts that they use for classified errors. Draft JSON and account ownership are unchanged. A second nine-case run verified the fix. The final nine-case run additionally verified the reproducible runner terminates the disposable JVM directly on Windows. These were deliberate offline suite executions.

## Reproduction

Executed on Windows with Java 17.0.12, Python 3.12.10 and Node 24.18.1. From the repository root, use a fresh scratch directory outside the checkout.

```powershell
$run = Join-Path $env:LOCALAPPDATA ('CartIQ/diagnostic-' + [guid]::NewGuid())
New-Item -ItemType Directory "$run/suite" | Out-Null
Copy-Item backend/src "$run/suite/src" -Recurse
Copy-Item backend/pom.xml "$run/suite"
Push-Location "$run/suite"
mvn.cmd -o -B '-Dtest=GeminiFailureTest,RecipeIntelligenceTest,WeeklyShoppingTest' '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' package
Pop-Location
Push-Location ai-service
.\.venv\Scripts\python.exe -B -m pytest -q -p no:cacheprovider
Pop-Location
npm.cmd test
$env:EXPO_OFFLINE = '1'
$env:EXPO_NO_DOTENV = '1'
$env:CI = '1'
$env:EXPO_PUBLIC_API_URL = '/api'
node node_modules/expo/bin/cli export --platform web --max-workers 2 --output-dir "$run/web"
.\ai-service\.venv\Scripts\python.exe -B docs/evidence/phase10-4/verify.py "$run/results" "$run/suite/target/smartcart-api-0.2.0.jar" "$run/web"
```

The runner requires free ports 18201 and 18202, the installed Chrome browser and the local Playwright dependency used by the CartIQ browser checks. It starts child services with an environment allowlist and isolated credentials. It records only synthetic case evidence in the published JSON. The raw local logs remain outside this evidence folder.

| Check | Passed | Failed | Skipped |
|---|---:|---:|---:|
| FastAPI offline suite | 53 | 0 | 0 |
| Focused Spring unit and H2 tests | 13 | 0 | 0 |
| Frontend tests | 68 | 0 | 0 |
| Final Chrome diagnostic scenarios | 9 | 0 | 0 |

Expo Web export succeeded. FastAPI emitted one existing Starlette TestClient deprecation warning. The run made zero live Gemini, retailer or geocoding requests. The QuickCommerce ledger checksum was unchanged and no shared MySQL was accessed. The original incident's Google acceptance, token usage and billing remain unknown. [Original incident](../phase10-2/GEMINI_502.md)
