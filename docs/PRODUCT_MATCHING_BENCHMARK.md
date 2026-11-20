# Product matching benchmark

## Phase 7 recommendation regression baseline

The versioned local regression dataset defines eight synthetic evaluation cases with expected outcomes, fixture input features and provenance. Run `node configuration/Report-RecommendationBaseline.cjs <surefire-reports>` after the Java suite. It reports executed, failed and skipped cases by capability without importing retailer data or computing a live accuracy claim. The executed fixture run passed 2 of 2 cases for each capability. [Regression dataset](recommendation-regression-dataset.json)

| Capability | Fixture failure modes and labels | Genuine held-out evidence and metrics |
| --- | --- | --- |
| Product matching | Shared barcode and differing multipack, or barcode registered to another canonical variant. Expected labels block approval and exclude planning. | Use two independent reviewers and adjudication for source pairs. Keep related product families and source records together. Report precision, recall, false-match rates and review routing. |
| Bulk buying | Larger usable value pack and missing exact stock. Expected labels are buy larger and insufficient information. | Independent review of stock, shelf life, pack, budget and household signals. Measure unsafe-extra rate, action agreement and missing-data abstention. |
| Personalization | Unknown dietary evidence, foreign account signal and budget-sensitive shopping. Expected labels exclude unsupported items and require a complete price for budget claims. | Permission-cleared account cohorts and observed exposure decisions. Measure ranking coverage, user outcomes and privacy, dietary and budget violations. |
| Recipe-to-product mapping | Mass versus volume and a same-unit substitution with an unknown price. Expected labels preserve compatible units, pantry-adjusted quantities and unknown prices. | Independent ingredient, preparation-state and exact-pack review. Measure eligible mapping precision, unknown-price handling, compatible substitutions and pantry deduction errors. |

The frozen 73-case product matching benchmark and its splits remain unchanged. The fixture baseline provides regression evidence only. No learned recommender was trained or evaluated against genuine outcomes.

## Live offer identity gate

The heuristic score is advisory. A transient offer can carry canonical IDs only when a human-approved QuickCommerceAPI mapping is current for the retailer SKU and exact variant under the configured grant. The live listing must agree with the reviewed brand, title, unit, multipack count and quantities, plus recorded flavour, formulation and barcode where required. The server rechecks mapping revisions and conflicting catalogue attributes on read. Missing or conflicting evidence leaves the listing unresolved or distinct. A shared barcode cannot override a pack conflict. Tests exercised a single 500 g pack versus 2 × 250 g. No labels, splits, benchmark scores or mappings were changed.

## Authorized sample execution status

The next benchmark is on hold at sample intake. No accepted grant or authorized sample was available. No human reviewers have been assigned, and no human decisions, sign-offs or adjudications were received. The custodian can use the existing forms once there is an accepted evidence snapshot. Preserve each independently signed review against the same packet hash and keep it hidden until both reviews are frozen. The adjudication record covers agreements and unresolved cases as well as disagreements, with human adjudicator sign-off on disagreements. Prior AI reviews do not count toward these sign-offs. [Grant and receipt record](AUTHORIZED_SAMPLE_INTAKE_RECORD.md) · [Human review forms](HUMAN_MATCHING_ANNOTATION_TEMPLATE.md)

After the review gate, create a separate benchmark version with the grouped split manifest described below. Freeze the evaluated matcher version before accessing holdout labels. Report precision, recall, F1, false-positive rate, false-negative rate and review routing with denominators, and break out missing-GTIN, pack-conflict, multipack and abbreviated-title cases. Keep unresolved cases in the review-routing analysis and omit them from binary match metrics. Report unavailable rates where denominators are absent. This milestone produced no new scored results. [Baseline integrity check](TESTING_STATUS.md#authorized-sample-receipt-and-annotation-readiness)

## Authorized data and human review readiness

The existing 73-case dataset stays frozen at SHA-256 `ee0f7d384b047c8a8ddc278e6b28be7e10e5842509670e9b6a2c8b62dc6f05b0`. The next benchmark will have its own version, source manifest, reviews and split manifest. The process and forms are ready. No authorized Rewalto sample or GS1 India DataKart evidence is available to annotate, and no human reviews have been completed for this milestone. [Frozen baseline](india-matching-annotation-benchmark.json) · [Authorized source intake](INDIA_RETAILER_DATA_SOURCES.md) · [Blank review forms](HUMAN_MATCHING_ANNOTATION_TEMPLATE.md)

### Select cases and prepare evidence

1. Log the written permission and validate its scope and expiry before accepting any item. It must cover the specific inputs, approved reviewer access, benchmark use and required retention. Check that joining retailer listings to a product master and retaining derived labels are within scope. Store only the approved evidence or an allowed source reference. Leave cases on hold when the needed evidence cannot be made available to both reviewers. [Source intake checklist](INDIA_RETAILER_DATA_SOURCES.md)
2. Build a separate candidate register before reviewing the results. Prioritize confirmed identical products with genuinely missing GTINs, shortened titles, spelling and transliteration differences, inconsistent pack descriptions, shared or conflicting GTINs, single packs versus multipacks, and similar products that differ by flavour or formulation. Record each applicable slice and the sampling method. Keep observed source values unchanged. Any deliberate masking or field change belongs in a separately reported controlled cohort, with the original pair and exact edit recorded.
3. Prepare evidence for the exact sellable variant on both sides. Capture product master record or listing IDs, the source versions and retrieval times, permitted image or document references, brand and title, GTIN status, units per sellable pack, per-unit amount and unit, flavour, formulation, and market or location when relevant. Cite the field or area of the packaging that demonstrates the assertion. A checksum establishes structural validity. Product identity needs corroboration from the packaging or a product master linked to the particular sellable configuration. Keep source values separate from verified claims. For example, an independently verified GTIN can support a label while remaining absent from the matcher input when the retailer listing never supplied it.

### Freeze the split and review independently

1. Group all pairs transitively by source record, native listing identity across snapshots, normalized GTIN, canonical variant and product family. Treat both members of each pair, near-duplicate listings and controlled derivatives as linked. Keep every group in one split. Exclude groups already exposed in the 73-case baseline, older scored benchmarks or rule development from the new holdout. Record grouping and split assignments in a separate manifest with the method and seed when randomized. Audit for duplicate or reversed pairs, cross-split overlap and accidental baseline reuse. The existing evaluator checks declared family, source-record and canonical GTIN ownership within an export. Compare with the earlier snapshots and audit listing and variant groups separately.
2. The benchmark custodian gives two distinct human reviewers independent copies of the same versioned evidence packet. Include an opaque case ID, two observations and permitted evidence. Hide split assignment, sampling target, existing labels, scorer output and the other reviewer's decision. Record each reviewer's approved identifier, decision, rationale, supporting evidence IDs and submitted time before revealing either review. AI can assist with transcription or locating a field. A human verifies the cited evidence and signs each decision.
3. Use `SAME_VARIANT` only when the evidence supports the same sellable configuration, including pack count and amount and material flavour or formulation. Use `DIFFERENT_VARIANT` for a documented identity difference. Use `UNCERTAIN` for missing, insufficient or conflicting evidence. A common GTIN cannot resolve conflicting pack structure. An omitted flavour or formulation cannot establish agreement or difference by itself.
4. Keep both original reviews and record agreement or disagreement explicitly. A human adjudicator records the final label, evidence, rationale, identity and time for disagreements. When new evidence is needed, issue a new packet version and obtain two independent reviews of that version. Publish a confirmed label only when the documented evidence resolves the conflict. Keep any remaining unresolved case as `UNCERTAIN`. A benchmark label never approves a live mapping.

### Freeze the next evaluation snapshot

Record the snapshot ID, build date, source and permission revisions, retention deadlines, evaluator version, grouping and split manifest, permitted evidence references, two signed reviews, adjudications, and SHA-256 values for the covered artifacts. Restrict access to the test labels until the evaluated matcher version and reporting plan are frozen. Audit the final dataset and log the commands used to build and evaluate it. Export separate observed and controlled results and report denominators by slice, disagreement and unresolved routing. Preserve rights-permitted versions and record later corrections as a new snapshot. If permissions expire, remove affected material under the retention requirements and flag the evaluation as no longer reproducible when its inputs are unavailable.

CartIQ retains the default scorer and advisory hybrid. The new benchmark tracks GTIN-only as a comparator. A matching recommendation cannot approve a mapping or override conflicting pack evidence. No rule fitting, model training, calibration or new accuracy claims are part of this milestone.

## India annotation expansion protocol

The expansion preserves the earlier benchmark. New source items, product families, and GTINs are separated from published parent cases, and related pairs stay in the same split. The additional conservative rule was fixed before scoring the extension. It recommends a match only with the same usable GTIN, exact normalized brand and explicit pack structure, compatible title tokens, and no explicit flavour, formulation or pack conflict. Without a usable shared code or positive evidence of distinction it routes the pair to review. It is an evaluator-only comparator.

The first AI reviewer collected and recorded case evidence and a decision. A separate AI reviewer received the two source records, evidence URLs and pair reference without first-pass labels, split assignments or scorer output. Adjudication kept both disagreements unresolved. There was no human reviewer sign-off. The [expanded dataset](india-matching-annotation-benchmark.json) embeds both decisions, evidence links, adjudication, and hashes for the frozen first pass, blind inputs, and second review.

### Annotation and coverage

| Review step | Same variant | Different variant | Unresolved |
|---|---:|---:|---:|
| First AI review | 5 | 11 | 3 |
| Blinded second AI review | 7 | 11 | 1 |
| Adjudicated | 5 | 11 | 3 |

The reviewers agreed on 17 of 19 pairs (89.5%). Both deferred `ann-10`. The Parle pack shows a 12.6 g bonus under the same GTIN, which can explain the difference between the 44.1 g base listing and 56.7 g archived pack. The listing leaves the delivered bonus configuration uncertain. For `ann-14`, both reviewers found the same barcode and 64 g item fields, while the FAQ describes 68 g. `ann-17` has a manufacturer 500 g Chitale mockup that visually corroborates the archived packaging, while the manufacturer storefront supplies no barcode for that option. Only the second reviewer accepted `ann-14` and `ann-17`. Both remain unresolved. [Parle listing](https://aapkabazar.co/822/pd/parle-krack-jack-biscuits-56-7-gm) · [Marie Gold listing](https://aapkabazar.co/3315/pd/undefined) · [Chitale product page](https://www.chitalebandhu.in/products/bakarwadi-1)

| Split | All observed same / distinct / unresolved | Historical controlled | Total | New observed same / distinct / unresolved |
|---|---:|---:|---:|---:|
| Training | 1 / 12 / 3 | 4 | 20 | 0 / 3 / 1 |
| Validation | 5 / 12 / 2 | 6 | 25 | 2 / 3 / 2 |
| Test | 6 / 13 / 3 | 6 | 28 | 3 / 5 / 0 |
| Total | 12 / 37 / 8 | 16 | 73 | 5 / 11 / 3 |

The 19 new observed pairs use 25 source records from eight product families. Nine records cover rice and grains, seven packaged snacks, six biscuits and bakery, and three sauces and condiments. Brands include Britannia, Chitale, Daawat, Fortune, Hellmann’s, MTR, Parle and Rajdhani, with the original source spellings preserved. Thirteen identities are archived Open Food Facts rows from the 2026-11-14 extract. The other 12 use public product references from Metro (2), the MTR product catalogue (2), Aap Ka Bazar (2), Chitale Bandhu (2), Fetch N Buy (1), Orkla India (1), Alfa Sangli (1), and Fresh N Green (1). The full data contains 74 source records across 31 product families and 50 archive references. Brand and shop citations support individual identification facts. The dataset includes no price or availability fields. [Open Food Facts licence and attribution](https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/tutorials/license-be-on-the-legal-side/)

Seven new pairs lack at least one barcode, two have a shared barcode with conflicting pack evidence, three have ambiguous pack evidence, and one is an observed single-pack versus multipack conflict. The corresponding counts for all 57 observed pairs are 12, three, 11 and four. These slices overlap. A different valid barcode is tracked separately and often correctly denotes a different sellable variant. The new corpus adds abbreviated and reordered titles, transliterated terms, a brand spelling variation, a different preparation, and competing brands. It contains no dedicated observed flavour-only conflict and no newly observed malformed barcode. Eight fresh holdout pairs have no family, source or GTIN overlap with either previously scored dataset. Two Krackjack pairs use a GTIN from the older benchmark, so they remain in validation.

### New holdout findings

Hybrid, GTIN-only and the conservative rule each identified `ann-01`, the corroborated Rajdhani 500 g match. The default scorer deferred it. All methods missed the confirmed Metro Rajdhani and MTR Khatta Meetha Poha matches with one missing barcode. The hybrid and conservative rules deferred both. The default scorer treated the MTR pair as distinct. Different abbreviated descriptions and missing explicit variant or formulation fields limit recall. None of the five methods made a false match on the five adjudicated distinct holdout pairs.

- For `ann-06`, the observed MTR single pack versus two-pack, the default, hybrid, conservative and brand/title methods returned distinct. GTIN-only deferred where the pack listing lacked a barcode.
- In validation, GTIN-only recommended matches on both unresolved barcode-and-pack conflicts, `ann-10` and `ann-14`. The default, hybrid and conservative methods routed both to review. Unresolved cases are excluded from binary false-positive counts.
- The earlier controlled `in-te-c4` replaces the Bourbon multipack code with the single pack’s code. GTIN-only falsely matched it. The default, hybrid and conservative methods deferred it and brand/title excluded it. The controlled excerpt is separate from the real observed inputs.

The sample is small and curated, with no licensed retailer feed or human sign-off. The new test split contains no adjudicated unresolved cases. Use validation to inspect unresolved routing. No thresholds were fitted, model trained, or confidence calibration performed. The default scorer stays in place, and the hybrid remains advisory. [Metrics and reproduction commands](TESTING_STATUS.md)

## India-focused hybrid protocol

The hybrid reuses checksum validation and metric pack parsing, including explicit multipack count and unit. It removes a title's pack suffix only when that suffix agrees with the separately declared pack. It accepts a redundant leading brand when the other title supports the shorter form. It otherwise compares Unicode-normalized titles with token overlap. It does not infer equivalence across scripts, translate a formulation, or use a semantic embedding.

- For a shared usable GTIN, the hybrid requires exact normalized brand and pack plus an equivalent title or strong title overlap corroborated by explicit matching flavour and formulation. Conflicting pack structure, flavour or formulation prevents an exact recommendation.
- With one usable GTIN, an exact normalized title, brand and pack and explicit matching flavour and formulation can produce `SUPPORTED_MATCH`. It is a review suggestion. Shared inconsistent codes, invalid or source-assigned codes, and incomplete evidence are routed to review or identified as insufficient. Strong known differences can support a distinct result.
- The original scorer remains the default. The evaluator compares its decisions with GTIN-only, normalized brand/title similarity, and the opt-in hybrid against the same labels. Controlled perturbations are reported separately from observed inputs. The hybrid rules were fixed before evaluation and were not optimized on the holdout.

### Dataset and splits

The [India benchmark](india-matching-benchmark.json) contains 49 India-market identities and 54 cases. Seven observed positives were labelled from packaging and separate published references. Distinct-item labels require a documented difference, and uncertain labels preserve cases where source evidence is missing or conflicts. One annotator labelled the corpus and a second reviewer spot-checked all three observed holdout positives and the Bourbon multipack. No full second annotation or agreement statistic is available.

| Split | Observed same / distinct / unresolved | Controlled same / distinct | Total |
|---|---:|---:|---:|
| Training | 1 / 9 / 2 | 2 / 2 | 16 |
| Validation | 3 / 9 / 0 | 4 / 2 | 18 |
| Test | 3 / 8 / 3 | 4 / 2 | 20 |
| Total | 7 / 26 / 5 | 10 / 6 | 54 |

The evaluator validates split ownership by source record, product family, and normalized GTIN, including pre-perturbation and overridden codes. No input GTIN overlaps the earlier 47-pair dataset. The observed records include actual missing and conflicting identifiers and unitless or incomplete descriptions. The controlled cohort separately masks, changes, or malforms GTINs, rewrites titles across scripts, and varies pack descriptions to test failure modes. A controlled case uses the real pair's ground truth and stays in the same split.

The corpus reuses archived India-market Open Food Facts rows, with product-label and catalogue pages as corroborating evidence. Verified 37 identity references by SHA-256 against six archived source pages. The remaining 12 identities cite link-only evidence. The archive paths are relative to `%USERPROFILE%/CartIQ-data`. Open Food Facts contributors are credited under ODbL 1.0 for the database and DbCL 1.0 for individual contents. Images carry their separate CC BY-SA terms. Manufacturer and shop links are evidence for pack, title and barcode facts. No retailer price, availability, or private feed data was included. [Open Food Facts reuse guide](https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/tutorials/license-be-on-the-legal-side/) · [Source rights audit](INDIA_RETAILER_DATA_SOURCES.md)

### Results

The hybrid deferred all three observed positives and did not produce an observed match recommendation. It classified all eight observed distinct pairs correctly and routed all three unresolved cases to review. The earlier scorer routed two unresolved cases to review. GTIN-only found all three observed positives, but returned distinct for the three unresolved cases and falsely matched a controlled case where a multipack had been given the single pack's GTIN. [Metrics and commands](TESTING_STATUS.md)

- `in-te-01` Happy Happy cookies and `in-te-02` Britannia Bourbon have corroborated barcodes and equal normalized pack sizes. Abbreviated and extended titles differed without enough matching named flavour and formulation fields for the hybrid to recommend a match.
- `in-te-06` Kinley has an independently confirmed 1 L pack, while the archived listing contains unitless quantity `6`. The hybrid deferred for missing compatible pack evidence.
- `in-te-c4` preserves the real 50 g single pack versus multipack label and substitutes the single pack's barcode in the multipack input. Only the GTIN-only baseline recommended a match. The hybrid sent the shared code and conflicting pack structure to review.

Across all observed pairs, the hybrid routed all five unresolved cases to review, with no match recommendations for the seven established positives. The eight observed holdout exclusions and six review decisions all carried high policy confidence. The high tier expresses rule confidence in actions including deferral. It is not a calibrated probability. No model was trained and the results support manual triage only.

## Earlier 47-pair benchmark

The [benchmark](product-matching-benchmark.json) contains 60 source identities and 47 manually labelled pairs. It was annotated in a single pass before running the scorer. Positive labels require corroboration from different published records. Some sources share upstream manufacturer data, and there was no second annotator. The sample combines India-market cases with US branded-food and packaging stress cases.

| Split | Same item | Distinct | Unresolved | Total |
|---|---:|---:|---:|---:|
| Training reserved | 3 | 5 | 3 | 11 |
| Validation reserved | 7 | 11 | 2 | 20 |
| Test | 4 | 9 | 3 | 16 |
| Total | 14 | 25 | 8 | 47 |

The evaluator rejects reversed duplicate pairs and crossing split assignments for a source record, valid canonical GTIN or declared product family. The training and validation labels are reserved. No model or thresholds were fitted. GTIN-only makes a match decision when both checksum-valid canonical codes agree. The title/brand baseline uses exact normalized brand tokens and title token Jaccard similarity of at least 0.70, without using pack, barcode or formulation. The CartIQ scorer can defer a result to review. In the binary metrics, a deferral on a true match counts as a false negative, and unresolved ground truth is reported separately.

| Scorer tier | Test decision counts | All decision counts |
|---|---|---|
| High | 9 distinct correctly excluded, 1 positive accepted, 3 positives deferred, 1 unresolved classified distinct, 1 unresolved deferred | 25 distinct correctly excluded, 1 positive accepted, 13 positives deferred, 2 unresolved classified distinct, 3 unresolved deferred |
| Medium | No results | No results |
| Low | 1 unresolved deferred | 3 unresolved deferred |

The high tier includes confidence that review is needed. Those labels are not probabilities. The sample is too small and uneven across confidence levels for calibration. One accepted holdout match supports the reported precision. The GTIN-only result benefits from a barcode-rich design, while title/brand alone incorrectly matched seven of nine distinct holdout pairs. [Metrics and replay command](TESTING_STATUS.md)

The evidence references point to Open Food Facts, original package images, manufacturer SmartLabel and public product sources, including USDA FoodData Central. Validated the hashes for 59 identities across 43 archived files. The independent Fetch N Buy Tata Salt reference is link-only and was checked for the exact displayed barcode and pack. The input source archives are under `%USERPROFILE%/CartIQ-data/2026-11-14-matching-benchmark` and `%USERPROFILE%/CartIQ-data/2026-11-14-catalogue-quality/raw-pages`. Product facts in the dataset have row-level provenance. Keep Open Food Facts attribution and its ODbL/DbCL obligations. USDA data uses CC0. Manufacturer page material remains with its publisher. No retailer prices or availability were collected. [Open Food Facts reuse](https://openfoodfacts.github.io/documentation/docs/Product-Opener/api/tutorials/license-be-on-the-legal-side/) · [USDA source and licence](https://fdc.nal.usda.gov/api-guide/) · [GS1 GTIN management](https://ref.gs1.org/standards/gtin-management/)
