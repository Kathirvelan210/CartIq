# Authorized sample grant and intake record

Recorded 2026-11-15 (IST). **Both sources are on hold.** The project workspace and local CartIQ evidence archive contain historical evidence and test outputs. No written provider grant accepted by CartIQ or corresponding provider sample was available for this milestone. Neither source has a verified permission, validated record, or reviewer sign-off.

## Provider grant verification

Copy this record into the restricted intake workspace when a grant is supplied. Use one signed record per provider and grant revision. Cite the exact clause or annex for each scope item. Set `MISSING` when no written provision was supplied, `AMBIGUOUS` when its meaning or scope cannot be confirmed, `PERMITTED` or `PROHIBITED` when stated explicitly, and `NOT_APPLICABLE` when the field is outside the proposed source scope. Record the reason for every restriction or inapplicable field. Any missing or ambiguous right required for the intended workflow holds the affected intake. Optional rights such as public display stay outside the approved purpose unless expressly granted.

| Verification item | Rewalto | GS1 India DataKart |
|---|---|---|
| Named legal entities, provider signatory and signing authority, authorized CartIQ representative, grant ID/version and effective dates | `MISSING` | `MISSING`, including applicable brand-owner authority |
| Allowed products, locations and dates | `MISSING`, including named upstream retailers, brand/product or SKU scope, PINs, and observation date range | `MISSING`, including program, brand owners, markets, GTINs, pack levels and record versions or dates. Retailer PIN pricing is outside the proposed identity scope. |
| Allowed fields and evidence | `MISSING`, including listing and observation IDs, identity, authorized prices or stock fields, and any image or evidence pointer rights | `MISSING`, including product-master fields, packaging identity and permitted image bytes or reference use |
| Access, raw-data retention and deletion | `MISSING`, including approved delivery, storage and reviewer access for each data or evidence class | `MISSING`, including program and brand-owner limits on storing or sharing evidence with reviewers |
| Catalogue joins and derived use | `MISSING`, including links to the canonical catalogue and permitted GS1 evidence, human annotation, derived labels and reproducible internal evaluation | `MISSING`, including links to the canonical catalogue or approved retailer rows, human annotation, derived labels and reproducible internal evaluation |
| Public comparison, redistribution, publication and model training | `MISSING` for each use. Keep all outside this intake. | `MISSING` for each use, including onward sharing of brand evidence. Keep all outside this intake. |
| Signed acceptance and dated scope decision | Pending authorized CartIQ representative. Intake held. | Pending authorized CartIQ representative. Intake held. |

Record the provider agreement and evidence references, CartIQ approval role, approver ID and signature reference, and UTC approval time. Identify allowed recipients, storage location, geography, purpose, expiry, attribution and deletion owner. Record separate deadlines and verification of deletion for originals, evidence, caches, exports, backups and derived records where covered. Preserve the signed grant and source materials only in the restricted location authorized for them. [Provider field specification](INDIA_RETAILER_DATA_SOURCES.md#sample-fields)

## Controlled receipt and validation report

Use this report after the CartIQ representative records acceptance of the written grant and the corresponding authorized file arrives. The approved transfer channel and access list must cover the operator and both reviewers before they handle provider evidence. A file received without verified permission remains outside the intake process.

| Report field | Current status |
|---|---|
| Intake ID, export ID, provider, grant revision and approved scope reference | Pending |
| Original file reference, sender and transfer provenance, receipt time UTC, byte count, SHA-256 | Pending authorized delivery |
| Provider schema/version, contract-supported field map and validation rules | Pending sample and agreed schema |
| Report version, operator, verification time, validator version and exact commands | Readiness checks only. No sample validation ran. |
| Source rows, accepted, invalid, duplicate, incomplete and out-of-scope totals | Not applicable. No sample was processed. |
| Per-row dispositions, reason codes and retained record locator | None |
| Original file and output retention, access check and deletion owner | Pending grant |

1. Preserve the original bytes in approved storage and keep the read-only original separate from analysis copies. Use `Get-FileHash -LiteralPath $approvedFile -Algorithm SHA256` and record the digest, size, receipt metadata, and originating sample ID. Recheck the SHA-256 before each validation replay. Stop on an unexpected digest or schema version. Log the versioned field map and validation command with the report.
2. Validate against the delivered schema and approved fields, identifiers, geography and dates. Keep identifiers and GTINs as text, including leading zeroes. Record source observation time and effective or revision date separately from receipt time. Where authorized observations include prices, check currency, integer minor units and location. Preserve missing optional fields and verification status. Missing GTIN alone is eligible for review when other identity evidence survives the intake checks.
3. Use one disposition per record and retain secondary reason codes. First exclude records known to be outside scope, then flag schema, type, or identifier failures as invalid. Keep conflicting payloads under the same provider record key together for investigation. Count repeated identical payloads as duplicates after choosing the first valid record deterministically. Distinguish listing IDs from observation IDs so repeated observations of the same listing do not become duplicates. Incomplete records within scope stay on hold when required evidence is missing or scope cannot be confirmed. Accept only valid, unique, in-scope records with the evidence required for the intended use. Reconcile source rows with the disposition totals and use restricted record locators, no raw supplier rows in routine reports.
4. Freeze the accepted evidence snapshot with the grant revision and hashes. Use the existing blank forms for two independent human reviews. Keep each decision private until both are frozen. Preserve both originals, record the adjudication outcome and keep insufficient evidence `UNCERTAIN`. Benchmark export and scoring follow signed reviews and adjudication. [Human review forms](HUMAN_MATCHING_ANNOTATION_TEMPLATE.md) · [Benchmark protocol](PRODUCT_MATCHING_BENCHMARK.md)
