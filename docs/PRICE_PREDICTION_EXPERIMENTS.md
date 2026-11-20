# Grocery price prediction experiments

## Genuine dataset coverage

The local H2 file was copied and inspected read-only. Its Flyway history stopped at V11. Applying V12–V14 SQL to a second disposable copy and querying it read-only found zero approved observations, zero retention grants and zero forecast grants. Its 12,420 legacy history rows were all `SIMULATED_HISTORY` / `SAMPLE` and were excluded. No authorized historical sample, export, or training grant was supplied for this milestone. Shared MySQL was outside the audit scope.

| Coverage measure | Genuine data available for this milestone |
|---|---|
| Retained observations and distinct priced products/variants | 0 available |
| Retailers and delivery locations with training data | 0 |
| Date range and observation frequency | No dated training records |
| Missing-price, missing-MRP and unknown-stock rates | No denominator |
| Duplicate observations | None in available training data. Intake enforces both listing/location/time and source record uniqueness. |
| Verified identity and permissions | No genuine training-qualified series. Each row needs its latest approved mapping, compatible canonical pack, title and brand, non-revoked retention grant and explicit training evidence. |

`GET /api/admin/price-intelligence/coverage` queries the current database. It reports totals, missing fields, distinct observed days, current reviewed identity count, retailer and location coverage, retention eligibility and explicit train/serve eligibility. It also provides up to 200 coverage groups by category, retailer and delivery area. Groups describe approved observed rows. They are not model performance. A `trainingAllowed` count reflects permission, before numeric/stock, date and sample-size cleaning. Scope is the database behind the authorized backend that ran the query.

## Training and evaluation contract

For authorized historical backfills, V15 permits a reviewed source availability timestamp when the written source authorization permits this evidence. Temporal evaluation uses that timestamp instead of the later ingestion timestamp. CartIQ preserves both. Rows without evidence retain the original same-day-ingestion requirement. The 60-day, consecutive-day holdout, naive and interval thresholds are unchanged. [Source authorization and receipt](PRICE_DATASET_ACQUISITION_REPORT.md)

1. Approve evidence for training and internal evaluation via `POST /api/admin/price-intelligence/forecast-permissions/{grantId}`. This records reviewer identity, a permission reference, its evidence digest, expiration and whether publishing derived forecasts to authenticated users is permitted. Training also requires `datasetAllowed` on the active original grant. Shopper forecasts require `historyAllowed` and `serveAllowed` on every contributing grant. Revoke with `DELETE` on the same route. Never approve synthetic fixtures or incomplete legal terms.
2. With `CARTIQ_APPROVED_FORECAST_ENABLED=true` on Spring and `AI_SERVICE_SECRET` configured for both services, an authorized admin calls `POST /api/admin/price-intelligence/experiment?variantId=...&retailer=...&locationId=...`. The service uses one canonical variant, reviewed listing, retailer and location. It fingerprints the exact evidence-qualified rows and grant digests. FastAPI receives only observed and intake timestamps, prices and the dataset version. It receives no provider identifiers, user data or legal files. Keep any returned experiment report only if the grant permits retaining derived metrics for that duration.
3. Use the last 180 days, restricted to completed UTC days, eligible positive INR prices and confirmed in-stock observations. Aggregate to the last price ingested on the same UTC day. Report late rows separately. Missing dates remain gaps. Large absolute price changes are flagged using an eight-times-median threshold learned on training changes. The prices remain in the analysis until evidence confirms a data error.
4. The first 60% of observed days initializes the chronological history. Select between last-price naive, trailing seven-observation mean and fixed-alpha exponential smoothing using the next 20%. Evaluate once on the final 20%. Both holdouts must contain at least 10 consecutive-day targets. Later predictions in rolling evaluation can use earlier realized observations. They cannot use their own targets, future prices or backfilled measurements. Seasonal and tree models are deferred pending evidence of incremental value.
5. Return MAE, RMSE, WAPE and signed bias in paise, with target counts for all methods on both holdouts. The forecast range uses absolute validation residuals. A candidate can serve only if the untouched test comparison is at least as accurate as naive on MAE, test interval coverage is at least 80%, and the latest eligible day is yesterday in UTC. Coverage describes the holdout and is not a probability guarantee. To pool the results of distinct authorized series by product category, retailer and delivery area, pass their admin experiment objects as a JSON array to `python ai-service/price_prediction.py < approved-experiments.json`. Pooled WAPE uses summed absolute error and actual prices. Freeze comparisons before opening the test set to avoid test-driven tuning.

## Shopper API and recommendation

`GET /api/me/price-intelligence/{variant}?retailer=blinkit&locationId=...` returns the latest permission-cleared observed value, stock, observation time, freshness, previous price change, a one-UTC-day model forecast when eligible, and an explanation. The forecast includes estimate, empirical interval, measured test coverage, algorithm version and training time. `INSUFFICIENT_DATA` has no prediction values. The existing legacy analytics endpoint displays legacy history separately, and its old estimator is limited to sample mode.

Observed stock and price freshness use a 15-minute window for timing advice. A saved maximum or target comes from the shopper's own cart. An essential or unclassified line is never called a wait candidate. Waiting requires an explicitly non-essential line and a valid interval whose upper bound is at least 5% below the current eligible price. Advice is informational and never changes the cart.

The service keeps up to 128 serving-qualified results in process memory. It rechecks row fingerprint, active permits and target date when responding. Expiration and revocation retire the cached result. A restart discards it. No durable model artifact is written. V14 stores approval evidence only. Training is disabled by default. No genuine model version, holdout score or real-world accuracy exists yet. Synthetic tests exercise format, selection, scoring and insufficient-data behavior only.

## Separate Gemini preflight

Run `node configuration/Gemini-Recipe-Preflight.cjs` in the protected backend/AI environment. It only reports configuration presence. The proposed test is one signed-in recipe request for “Suggest a quick South Indian breakfast for two.” with `usePantry=false`, using the existing key and required service authentication. Check a validated recipe, ingredient quantities, any unsupported claims and actual provider outcome. Stop and report after one dispatch, including on timeout. The preflight has no network client. Live authorization is pending. No request was sent during this milestone.
