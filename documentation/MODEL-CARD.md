# Model training evidence and limitations

Three saved models were trained on 7 November 2026 using the application’s simulated fixtures. These are functional baseline models. No live grocery observations, real user behaviour or independently labelled recipe dataset was supplied.

## Price model

- Estimator: scikit-learn RandomForestRegressor, 100 trees, maximum depth 12, minimum 3 examples per leaf, fixed seed 42.
- Features: canonical variant, platform, location, lagged prices, seven-observation mean, recent change and day of week.
- Every lag precedes the target date. The holdout uses later dates; feature preprocessing is fitted on the training partition.
- Training examples: **11,592**. Later holdout examples: **3,036**.
- Holdout period: **28 October–7 November 2026**.
- Evaluated groups: **276** product/platform/location combinations, including daily-lowest-price series.

| Model | Holdout mean absolute error on simulated data |
| --- | --- |
| Random forest | 45.953 paise, approximately ₹0.46 |
| Last observed price baseline | 168.844 paise, approximately ₹1.69 |
| Seven-day mean baseline | 484.207 paise, approximately ₹4.84 |

The random forest was selected. The final saved estimator was then refitted on all available simulated examples; the reported evaluation metrics remain those from the earlier holdout run. The dataset fingerprint, run timestamp and split counts are in `ai-service/models/metadata.json`.

These numbers describe predictable simulated price curves. They do not establish forecasting accuracy on real market prices. Collection changes, promotions, outages and location coverage can all change performance. The displayed fit-quality score is not a calibrated probability; the empirical range is not a guarantee, and no price drop is promised.

## Recipe ranking

The saved TF-IDF model learns term weights from 3 curated sample recipes and their linked ingredients. The application combines content similarity with actual pantry matches when ordering the recipe list. There is no measured personalization accuracy because no labelled user preference dataset was supplied.

## Ingredient retrieval

The saved character TF-IDF model learns a retrieval representation from 20 canonical catalogue products. Scores below the configured threshold produce no suggestion. Matches require user confirmation; retrieval scores never prove that brand, variant, pack and unit are equivalent.

The 16 curated recipe-to-product links all returned the linked product in a corpus sanity check. These names come from the same catalogue used for training, so **16/16 is not an independent accuracy result**. Real evaluation needs unseen ingredient wording, regional names, misspellings, quantities, ambiguous matches and negative examples.

## Retraining and serving

`training.py` accepts the versioned JSON export from the backend or `--sample-api` for an explicitly local sample server. It validates input shape, keeps sample provenance, removes invalid/future records, uses chronological evaluation and saves model artifacts atomically. No training job calls a paid provider API.

The API service loads the saved estimator and can reload an updated artifact. If no compatible saved price model exists, the service identifies its small history-based fallback. The application has been checked through Spring Boot to confirm that the forecast method reports **Saved random forest — trained on sample data**.

Recipe serving quantities, pack rounding and pantry subtraction remain exact rules. Monthly totals are ledger calculations. Replenishment currently uses the user’s configured cycle. Trained spending/replenishment models await suitable histories and an evaluation design; neither is claimed complete.

Before real-data use: obtain permitted datasets, check timestamp/pack/location quality, train with `sample:false`, compare against baselines, evaluate unseen periods/wording, review errors and uncertainty, then monitor prospective performance. Do not load untrusted joblib files.

