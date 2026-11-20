# First live Gemini attempt — offline diagnosis

| Original evidence | Value |
|---|---|
| Start | 19 Nov 2026, 02:30:53.174 IST (18 Nov, 21:00:53.174 UTC) |
| Client | One authenticated local HTTP test client → Spring Boot. Expo was not involved in this request. |
| Operation | POST /api/me/recipe-intelligence/generate, pantry sharing off |
| Spring request ID | `5f9a3771-6268-492e-b475-76fad2cbdc29` |
| Spring console | Matching request ID, method, route, HTTP 502 and 3,214 ms |
| Client receipt | HTTP 502, 3,225 ms, “Recipe generation is temporarily unavailable.” No valid draft returned. |
| Google receipt | Provider status, request ID, response ID, model acceptance, tokens and charges unknown |

## Where the trace ends

The message and status identify the original `RecipeLibrary.generate` catch for a `RestClientResponseException`. Spring received an HTTP error on its request to the configured private FastAPI service and mapped it to 502. In that version, private responses of 429, 503 and 504 were passed through with those statuses. Spring's own connection/deserialization and draft-validation catch used a different message. The trace therefore reaches the private HTTP boundary. It does not establish that this generation passed private authentication, reached Google, or received a particular Google response.

The Windows operator console still contained the Spring access record. Inspection returned only allowlisted fields. No correlated Google status, exception trace or response was found there. A request-ID search of project diagnostics and CartIQ local evidence files returned no original provider capture. The launcher's readiness receipt predates dispatch. The FastAPI process ran with Uvicorn access logging disabled. The original HTTPX adapter raised on an error status before reading the Google error body and discarded upstream identifiers. Malformed successful content and network errors could also surface as private HTTP 502.

## Configuration and request contract

The same running session had passed authenticated local readiness for `gemini-3.5-flash`. The adapter uses HTTPX REST directly, a backend-only `x-goog-api-key` header and the v1beta model-specific `generateContent` endpoint. Spring constructs an empty pantry list when sharing is off. The generation configuration requests JSON, one candidate and at most 2,800 output tokens. HTTPX uses a three-second connect timeout and a ten-second read timeout. Redirects and environment proxy inheritance are disabled. The incident's elapsed time alone cannot establish a DNS, TLS or connection failure.

Google documents the model identifier and a subset of JSON Schema for structured generation. Public documentation cannot establish account access, the serving model, or the response to this request. The adapter now omits string length keywords from the outbound schema and enforces them with Pydantic locally. No historical evidence identifies the schema or the credential as the cause. [Model documentation](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash) · [Generation API](https://ai.google.dev/api/generate-content) · [Structured output constraints](https://ai.google.dev/gemini-api/docs/structured-output)

## Future diagnostic behavior

Spring forwards its generated request ID to the authenticated private FastAPI route. FastAPI records bounded, validated identifiers and numeric metadata, including observed HTTP status, duration and total tokens when Google supplies them. Google request IDs and response IDs remain separate. Logs exclude prompts, pantry data, raw error messages, raw generations and credentials. Token usage alone is not billing evidence.

Provider 401/403 and recognized API-key errors return `GEMINI_AUTH` with a service-level 503 to the shopper. The original HTTP status travels in `X-Gemini-HTTP-Status`. A separate `AI_SERVICE_AUTH` identifies a private-service credential failure. Provider 404 identifies a missing model or endpoint, 400/422 an invalid request, and 429 quota or rate limiting. Provider 5xx, DNS, TLS, other transport errors, timeouts and invalid generated content have separate codes. A valid upstream request ID is carried in `X-Gemini-Request-Id` when available. Unrecognized private bodies and free-form provider messages are never echoed. Expo already displays CartIQ's safe error message.

The diagnosis used only mocked provider traffic and isolated Spring tests. The running session was neither restarted nor used to generate another recipe. Loading the changed code requires restarting Spring and FastAPI before a separately authorized live attempt.

## Offline verification on 19 November 2026

| Suite | Command | Result |
|---|---|---|
| FastAPI, including HTTPX mock transport | From ai-service: `.\.venv\Scripts\python.exe -B -m pytest -q -p no:cacheprovider` | 53 passed. One existing Starlette TestClient deprecation warning. |
| Spring diagnostics, recipe and shopping regressions | From an isolated backend copy: `mvn.cmd -o -B '-Dtest=GeminiFailureTest,RecipeIntelligenceTest,WeeklyShoppingTest' '-Dspring.profiles.active=test' '-DargLine=-Duser.home=.' test` | 13 passed, 0 failed, 0 errors, 0 skipped. H2 and loopback HTTP fixtures. |

The HTTPX cases asserted one attempt, the configured URL and model, header authentication, empty pantry, JSON schema, candidate count and token ceiling. They exercised successful generation, malformed output and simulated HTTP, DNS, TLS, timeout and connection failures. Spring checks covered safe status/ID forwarding, correlation, redaction, pantry isolation and private draft ownership. The existing retailer ledger digest matched the pre-incident digest.
