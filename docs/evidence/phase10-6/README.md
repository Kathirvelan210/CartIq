# Phase 10.6 · Investigation of the Gemini HTTP 503

**Finding.** On 19 November 2026 at 11:57:35.426 IST (06:27:35.426 UTC), CartIQ submitted one recipe-generation request. FastAPI received HTTP 503 from the upstream service and classified it as `GEMINI_SERVER`. Spring preserved the classification and returned HTTP 502 to Chrome. The retained evidence establishes where the failure occurred. Its underlying provider cause remains unknown.

## Verified facts

| Evidence for CartIQ correlation ID `81ec13ad-2ec2-4d9d-8ee1-317f9ac0b14f` | Observation |
|---|---|
| Live receipt and local browser relay receipt | One submission and no retries. The HTTP response completed in 11.271 seconds. Chrome displayed the error after 11.312 seconds. No recipe draft was returned. |
| FastAPI console, `gemini_result` | Requested model `gemini-3.5-flash`, outcome `GEMINI_SERVER`, upstream HTTP 503. The logged provider request ID, response ID and total tokens were all unset. |
| Spring console, `ai_failure` and `http_request` | The same correlation ID, upstream HTTP 503, no upstream request ID, and public HTTP 502 for `POST /api/me/recipe-intelligence/generate`. |
| Displayed error | “Gemini reported a server error.” Spring supplies this text from its error classification. It is not a quotation from Google. |

The two console records were inspected by matching allowlisted fields in the existing operator console. The Phase 10.5 browser relay receipt also exists under the local `CartIQ/phase10-5-authorized-session` directory. Its regression log belongs to offline tests. The SHA-256 of the preserved project receipt was `9C9DFFDA1E750E9AC544DABC7F9328529694B17D5826244692604B4ADF8D2E1B`. [Original live receipt](../phase10-5/live-receipt.json) · [Original verification report](../phase10-5/README.md)

## What the adapter could observe

The launcher supplies the model through `GEMINI_MODEL`. The adapter validates the name's format and inserts it into a fixed HTTPS URL. For this logged model, the constructed operation is `POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent`. This URL was reconstructed from the code and correlated model field. No wire capture of the destination or provider-side routing was retained. The method and path agree with Google's REST reference. [Adapter](../../../ai-service/recipe_generation.py) · [Local launcher](../../../configuration/Start-CartIqGemini.ps1) · [Google REST reference](https://aistudio.google.com/api-reference/generate-content)

| Requested diagnostic | What can be established |
|---|---|
| Provider error body | No sanitized body or `error.status` was retained. FastAPI reads a bounded response and checks a small set of structured credential-error reasons. Empty, oversized and unrecognized error bodies can all produce the same classification for a 503. The original body content and even the presence of a nonempty body are unknown. |
| `Retry-After` | The adapter does not capture this header. Its presence and value on the original response are unknown. |
| Provider identifiers | The adapter accepts bounded IDs from `x-goog-request-id` or `x-request-id`. None survived into the trace. Missing or rejected header values cannot be distinguished retrospectively. The provider response ID and usage fields are parsed only on the success path. |
| Tokens, quota, billing and serving model | No per-request values were recovered. The configured model name identifies the requested alias. It does not identify a serving instance. |

## Hypotheses and public incident check

- **Temporary upstream unavailability is consistent with the 503.** Google documents temporary overload or service unavailability for this status. The trace provides no narrower cause such as regional demand, maintenance or a particular backend failure. [Google error reference](https://aistudio.google.com/docs/api-errors)
- **An unsupported endpoint or misspelled model is unsupported by this evidence.** Google documents the exact model name as deprecated and says API requests using it route automatically to `gemini-3.6-flash`. The captured upstream status was 503. There was no captured model-not-found response. The documentation does not establish whether this particular request reached a serving model. [Google model documentation](https://aistudio.google.com/docs/models/gemini-3.5-flash) · [Google deprecation schedule](https://aistudio.google.com/docs/deprecations)
- **No matching public incident was verified.** The official AI Studio/Gemini API status page exposed no readable incident timeline in the retrieved page, and a search of public Google material did not produce an official incident matching 06:27 UTC. This leaves an outage at that time unresolved. [Google AI Studio and Gemini API status](https://aistudio.google.com/status)

## Safe diagnostic improvement

For a future change, extend the private FastAPI trace with an allowlisted provider status enum, a bounded numeric retry delay when `Retry-After` parses safely, and an error-body state such as empty, parsed, malformed or oversized. Associate them with the existing CartIQ correlation ID. Free-form messages, arbitrary headers and raw payloads should stay out of logs. Treat retry hints as evidence only. Offline HTTP fixtures can cover absent or malformed hints, unrecognized enums, redaction and exactly one dispatch. This proposal was not applied and cannot recover missing information from Phase 10.5.

This investigation read existing local evidence and public documentation. It made zero Gemini, retailer or geocoding API requests, issued no retries and changed no credentials, database records, credit ledgers, endpoint, model or production configuration. Only this investigation report was added.
