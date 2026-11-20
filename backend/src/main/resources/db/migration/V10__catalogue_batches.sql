CREATE TABLE catalogue_import_batches (
 id VARCHAR(36) PRIMARY KEY,
 snapshot_sha256 VARCHAR(64) NOT NULL UNIQUE,
 snapshot_url VARCHAR(1000) NOT NULL,
 expected_rows INT NOT NULL,
 committed_rows INT NOT NULL DEFAULT 0,
 staged_rows INT NOT NULL DEFAULT 0,
 correction_rows INT NOT NULL DEFAULT 0,
 invalid_rows INT NOT NULL DEFAULT 0,
 requested_by VARCHAR(36) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY (requested_by) REFERENCES users(id)
);

CREATE TABLE catalogue_batch_rows (
 batch_id VARCHAR(36) NOT NULL,
 `row_number` INT NOT NULL,
 raw_record MEDIUMTEXT NOT NULL,
 outcome VARCHAR(24) NOT NULL,
 issue VARCHAR(1000),
 candidate_id VARCHAR(36),
 PRIMARY KEY (batch_id, `row_number`),
 FOREIGN KEY (batch_id) REFERENCES catalogue_import_batches(id),
 FOREIGN KEY (candidate_id) REFERENCES catalogue_import_candidates(id)
);
