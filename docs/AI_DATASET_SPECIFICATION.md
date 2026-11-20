# AI dataset specification

`GET /api/admin/grocery/dataset` creates an uncached, single-variant and single-location export. Supply `variantId`, `locationId`, `from`, `validationStart`, `testStart` and `until` as IDs and ISO-8601 timestamps. The response contains `schemaVersion`, a SHA-256 `version` tied to the query and ordered immutable observation IDs, `generatedAt`, split counts and row provenance. Identical eligible inputs and rows yield the same version. The endpoint does not publish a persistent dataset itself. Record the returned version and expiry constraints when an approved downstream system retains an export.

| Window | Split |
|---|---|
| `[from, validationStart)` | `TRAIN` |
| `[validationStart, testStart)` | `VALIDATION` |
| `[testStart, until)` | `TEST` |

Only non-revoked, unexpired grants with `datasetAllowed` contribute rows. Validation rejects conflicting source IDs, missing required identity/evidence, wrong pack, out-of-window timestamps, and invalid amounts before export. Price, MRP and discount can remain null. Priced targets and availability targets must be selected separately. Empty splits are reported as zero. A query exceeding 5,000 eligible observations requires a smaller window and is never silently truncated.

Lag features use only an earlier reading for that listing and location whose intake time was on or before the target observation. This prevents late historical imports from appearing as features known in the past. `pricePerBaseUnitMinor` derives from the row's concurrent price. Treat it as a derived label or diagnostic when predicting that price, never as an independent feature. Use train-only preprocessing, freeze policy before evaluating the test window, and use a product-family split across independent datasets if the evaluation question requires generalization to unseen products.

The pipeline is implemented. No genuine licensed retailer dataset was loaded for this phase, no model was trained, and no production forecast performance has been established. Retention and allowed training use must be explicitly confirmed before retaining any export or training model artifacts.

Phase 3B adds an authorization registry and hash-linked intake workflow for licensed files. Forecast experiments may use separately evidenced `sourceAvailableAt` for historical availability. That timestamp never overwrites CartIQ's `recordedAt`. The existing `price-v1` export remains conservatively gated by the CartIQ intake time for its lag features. [Historical source and intake specification](PRICE_DATASET_ACQUISITION_REPORT.md)
