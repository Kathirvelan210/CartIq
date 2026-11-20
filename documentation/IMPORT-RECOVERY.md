# Import recovery checkpoint

Backend only. V8 and V9 have NOT been applied to MySQL by this work.
V9 is additive; V1–V8 are left unchanged. Do not start an application against
MySQL with automatic Flyway enabled until migration approval is granted.

## Contract
- POST /api/admin/catalogue-imports/{jobId}/resume: resume a persisted job.
- PUT /api/admin/catalogue-imports/candidates/{candidateId}: JSON name, brand,
  quantity and reason. Admin scope required. Existing approve/reject endpoints remain.
- Corrections keep the original source payload, URL and collection timestamp;
  each correction is stored with admin identity, reason and timestamp.
- Publication still requires explicit admin category/flavour approval and validation.
  Imported products remain inactive and community-contributed, not manufacturer verified.

## Recovery
One database lease per source, 120 seconds, with a unique ownership token.
All checkpoint writes require the current unexpired token. Recovery marks abandoned
attempts interrupted. Old workers cannot commit or release another worker's lease.
The source request cooldown remains an additional rate guard, not the worker lock.
Recovery is explicitly triggered by the admin endpoint; there is no background scheduler.
A stalled external request may remain in flight after lease expiry, but is fenced
from database writes. This is not exactly-once external HTTP execution.

Checkpoints NEW → FETCHED → STAGED persist the raw fetched payload before staging.
Resume from FETCHED does not refetch; STAGED is idempotent. Jobs currently process
one barcode each, not bulk pages. Per-attempt outcomes and attempt counts are durable.
Temporary HTTP 429/5xx and transport IO failures use at most three total attempts
per job, with 5 and 10 second waits. Malformed JSON/validation failures are not
automatically retried. Manual resumption cannot reset the attempt budget.

## Local verification (H2 and mocked external HTTP only)
From the project root in PowerShell:

~~~powershell
mvn.cmd -f backend/pom.xml '-Dtest=CatalogueImportNormalizerTest,CatalogueImportFoundationTest,CatalogueImportSafetyTest,OpenFoodFactsAdapterSafetyTest' test
~~~

Do not run MySQL integration scripts or boot the MySQL profile in this phase.
The previous 50-test success is the user's local baseline, not verification of these
changes. New assertions must pass before approving deployment.
