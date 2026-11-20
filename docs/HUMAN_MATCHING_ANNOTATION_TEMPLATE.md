# Human product matching annotation forms

Use these blank forms for the separate authorized benchmark. Copy the intake record and manifests into the access-controlled workspace allowed by the source agreement. Give each reviewer a separate packet and decision form. Retain the review and adjudication records only within the documented rights and retention terms. [Human review protocol](PRODUCT_MATCHING_BENCHMARK.md) · [Source intake checklist](INDIA_RETAILER_DATA_SOURCES.md)

## Custodian intake record

| Field | Value |
|---|---|
| Intake ID and revision | |
| Authorized provider, covered source and sample ID | |
| Written permission reference and version | |
| Allowed fields, joins, evaluation and reviewer access | |
| Raw, derived and evidence retention deadlines | |
| Restrictions, attribution and evidence storage method | |
| Accepted scope and market or location | |
| Permission checked by and UTC timestamp | |
| Accepted or held, with reason | |

Repeat for each source when a pair joins records from different sources. Keep permissions and raw material separate from reviewer files. Share only the evidence and access the source allows.

## Reviewer packet

| Field | Left observation | Right observation |
|---|---|---|
| Provider and source record ID | | |
| Retailer and native listing ID, if applicable | | |
| Source version, observation or retrieval time, and permitted reference | | |
| Market or PIN when applicable and allowed | | |
| Raw product title and brand | | |
| Raw barcode and verification status | | |
| Raw pack label | | |
| Pack count, per-unit amount and unit with verification status | | |
| Flavour and formulation with verification status | | |
| Evidence IDs and exact item, field, or image area | | |

| Shared packet field | Value |
|---|---|
| Opaque case ID | |
| Packet revision and evidence bundle SHA-256 | |
| Permitted evidence access and expiry | |

Repeat the following row for each evidence assertion. Preserve raw values and identify which side, pack level and field the evidence covers. Hash the exact packet bytes used by both reviewers and record that separate hash in the manifest and decision forms.

| Evidence ID | Side and assertion | Source reference or allowed artifact hash | Field, page or image area | Observed or retrieved time | Status and basis |
|---|---|---|---|---|---|
| | | | | | |

Use `MISSING` if the source supplied no value, `UNVERIFIED` for a supplied value without corroboration, and `VERIFIED` for a value linked to evidence for this sellable variant. Record material contradictions separately with both references. Never use a verified value from a separate source to fill an absent matcher input.

## Independent human decision

Submit one copy per reviewer against the same packet hash. Keep this submission private until both human decisions are frozen.

| Field | Value |
|---|---|
| Opaque case ID and packet SHA-256 | |
| Human reviewer ID and sign-off reference | |
| Submitted at in UTC, ISO 8601 | |
| Decision (`SAME_VARIANT`, `DIFFERENT_VARIANT`, or `UNCERTAIN`) | |
| Evidence IDs supporting each decisive fact | |
| GTIN assessment, including conflicts and their evidence | |
| Pack count, per-unit amount and unit assessment | |
| Brand, title, flavour and formulation assessment | |
| Rationale and evidence still missing or contradictory | |
| AI assistance, if any, and human verification | |

## Adjudication record

| Field | Value |
|---|---|
| Opaque case ID and packet SHA-256 | |
| Both frozen review IDs or hashes | |
| Review decisions and agreement status | |
| Unresolved evidence or conflict, if any | |
| Adjudicator ID and sign-off reference when required | |
| Adjudicated at in UTC, ISO 8601 | |
| Evidence used and basis for final label | |
| Final label (`SAME_VARIANT`, `DIFFERENT_VARIANT`, or `UNCERTAIN`) | |
| Superseding packet or follow-up evidence, if any | |

## Snapshot manifest

| Field | Value |
|---|---|
| Snapshot ID and build time in UTC | |
| Input versions, SHA-256 values and permission references | |
| Frozen baseline and prior benchmark versions checked | |
| Sampling method and slice counts | |
| Grouping method, split assignment reference and seed, if used | |
| Review, adjudication and evidence artifact references and hashes | |
| Evaluator and matcher versions, build commands and reporting plan | |
| Allowed access, artifact expiry and reproducibility status | |
