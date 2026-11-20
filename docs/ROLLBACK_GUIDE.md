# CartIQ rollback and MySQL recovery

## Before the release

Record the backend, Web and forecast image digests, environment version, MySQL version and Flyway schema version. Use a managed encrypted snapshot or `mysqldump --single-transaction --no-tablespaces` under the approved backup procedure. Supply credentials through a protected client option file or secret injection. Freeze schema changes during capture. Validate the backup by restoring it into a new isolated database and verifying Flyway history, row counts, cart ownership and application readiness. Never test a restore over shared or production data.

## Roll back

1. Remove traffic from the new service and preserve sanitized failure evidence. Keep migration history and ledger intact.
2. If the previous binaries support the deployed schema, redeploy the previous immutable images and approved settings. Check liveness, database readiness, authentication and cart ownership before restoring traffic.
3. If schema compatibility prevents binary rollback, prefer an approved forward corrective migration. For a necessary database recovery, restore the snapshot to a new database, verify it, assess changes since the backup and obtain explicit approval before moving traffic. Preserve the old database for reconciliation.
4. Never edit published migration checksums, run Flyway clean, or reset the credit ledger. If a provider attempt is pending, reconcile the specific fingerprint with verified billing evidence before further dispatch.

## Staging rehearsal scope

The Windows rehearsal uses a restored, isolated MySQL database and two frozen Java packages. Stop the current runtime, start the previous package with startup Flyway disabled, check readiness, log in through the unchanged HTTPS proxy and verify the existing cart. Then switch back to the release candidate and verify the same cart and migration history again. This does not replace a Compose image and network rollback rehearsal on the target staging host. [Staging evidence](TESTING_STATUS.md#staging-deployment-verification)

After each operator-controlled package switch on loopback, supply the disposable shopper's bearer token through `CARTIQ_STAGING_SMOKE_TOKEN` and run the read-only probe. Use phases `candidate`, `previous` and `roll-forward` and the matching approved artifact and hash. Set `--backend-health legacy` only for a previous package that exposes `/actuator/health`. Confirm separately that the running process loaded the artifact you supplied.

```text
node configuration/Test-StagingRelease.cjs --origin https://localhost:18443 --http-port 18081 --ca <staging-ca.pem> --phase candidate --artifact <candidate.jar> --sha256 <approved-hash> --expected-product <fixture-product-id> --expected-location <fixture-location-id> --expected-quantity 2 --backend-port 18932
```

The probe connects only to loopback, checks the artifact digest, TLS and cart fixture, and makes no database connection or deployment changes. Validation with the new runner used a local HTTPS fixture. The earlier package switch used real Java packages with disposable MySQL.
