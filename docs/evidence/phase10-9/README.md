# Phase 10.9 · Gemini 3.8 Flash visual test

**19 November 2026 · Stopped at compatibility review.** The current request builder explicitly sends `generationConfig.candidateCount: 1`. Google's 3.8 Flash migration checklist directs callers to omit that field. Changing only the local model setting would leave the unsupported parameter on the wire. [Google migration checklist](https://aistudio.google.com/docs/whats-new-gemini-3.8#migration-checklist) · [CartIQ request builder](../../../ai-service/recipe_generation.py)

| Check | Result |
|---|---|
| Model and endpoint | Google lists `gemini-3.8-flash` and documents `generateContent`. CartIQ's launcher and adapter accept that identifier and construct `POST /v1beta/models/{model}:generateContent`. [Model specification](https://aistudio.google.com/docs/models/gemini-3.8-flash) · [REST reference](https://aistudio.google.com/api-reference/generate-content) |
| Recipe output | The model supports structured text output and the 2,800-token limit fits its documented output window. CartIQ requires a completed candidate, skips thought parts, and validates the returned JSON against its recipe schema. No 3.8 response was obtained or validated. |
| Required adapter change | Omit the explicit candidate count for 3.8 and update the offline payload test that currently requires it. The REST reference documents one candidate as the default when omitted. Keep `maxOutputTokens: 2800`, the recipe schema and the single-dispatch guard. No adapter change was made in this configuration-only review. [Payload test](../../../ai-service/test_recipe_generation.py) · [Generation configuration reference](https://aistudio.google.com/api-reference/generate-content) |
| Final local model | Unchanged. The launcher default and the last verified request on the still-running FastAPI process were `gemini-3.5-flash`. No request was sent to that model as a substitute. [Previous model evidence](../phase10-5/live-receipt.json) |
| Live and visual outcome | Zero submissions, provider requests and retries. No recipe or error screen, screenshot, correlation ID, provider request ID, HTTP status, duration or usage receipt exists for Phase 10.9. |

Verification consisted of static inspection of the launcher, request builder, validator and existing test assertion, plus a read of Google's public documentation. No test request, generation, retailer or geocoding call was made. No credentials, runtime or production configuration, shared MySQL or retailer ledger were changed. A future visual run requires a compatible adapter and fresh live authorization.
