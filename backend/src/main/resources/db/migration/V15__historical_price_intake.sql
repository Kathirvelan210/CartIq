CREATE TABLE price_source_authorizations (
  grant_id VARCHAR(36) PRIMARY KEY, source_identity VARCHAR(150) NOT NULL, document_reference VARCHAR(300) NOT NULL,
  document_sha256 VARCHAR(64) NOT NULL, approval_scope VARCHAR(600) NOT NULL, revocation_conditions VARCHAR(600) NOT NULL,
  analytics_allowed BOOLEAN NOT NULL, training_allowed BOOLEAN NOT NULL, serve_allowed BOOLEAN NOT NULL,
  display_allowed BOOLEAN NOT NULL, raw_evidence_allowed BOOLEAN NOT NULL, historical_as_of_allowed BOOLEAN NOT NULL,
  approved_by VARCHAR(36) NOT NULL, approved_at TIMESTAMP NOT NULL, revoked_at TIMESTAMP NULL,
  FOREIGN KEY (grant_id) REFERENCES price_retention_grants(id), FOREIGN KEY (approved_by) REFERENCES users(id)
);

CREATE TABLE price_intake_batches (
  id VARCHAR(36) PRIMARY KEY, grant_id VARCHAR(36) NOT NULL, payload_sha256 VARCHAR(64) NOT NULL,
  input_format VARCHAR(10) NOT NULL, row_count INT NOT NULL, original_evidence_reference VARCHAR(500),
  submitted_by VARCHAR(36) NOT NULL, submitted_at TIMESTAMP NOT NULL,
  UNIQUE(grant_id,payload_sha256), FOREIGN KEY(grant_id) REFERENCES price_source_authorizations(grant_id),
  FOREIGN KEY(submitted_by) REFERENCES users(id)
);

CREATE TABLE price_intake_decisions (
  batch_id VARCHAR(36) NOT NULL, input_index INT NOT NULL, disposition VARCHAR(24) NOT NULL,
  reason_code VARCHAR(60) NOT NULL, decided_at TIMESTAMP NOT NULL,
  PRIMARY KEY(batch_id,input_index), FOREIGN KEY(batch_id) REFERENCES price_intake_batches(id)
);

ALTER TABLE approved_price_observations ADD COLUMN source_available_at TIMESTAMP NULL;
ALTER TABLE approved_price_observations ADD COLUMN source_as_of_evidence VARCHAR(500);
