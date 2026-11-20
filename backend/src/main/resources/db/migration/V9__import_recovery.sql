ALTER TABLE catalogue_import_locks ADD COLUMN owner_token VARCHAR(36);
ALTER TABLE catalogue_import_locks ADD COLUMN job_id VARCHAR(36);
ALTER TABLE catalogue_import_locks ADD COLUMN lease_until TIMESTAMP NULL;
ALTER TABLE catalogue_import_jobs ADD COLUMN checkpoint VARCHAR(24) NOT NULL DEFAULT 'NEW';
ALTER TABLE catalogue_import_jobs ADD COLUMN fetched_payload MEDIUMTEXT;
ALTER TABLE catalogue_import_jobs ADD COLUMN fetched_at TIMESTAMP NULL;
ALTER TABLE catalogue_import_jobs ADD COLUMN attempts INT NOT NULL DEFAULT 0;
CREATE TABLE catalogue_import_attempts (
 job_id VARCHAR(36) NOT NULL, attempt_number INT NOT NULL, outcome VARCHAR(30) NOT NULL,
 started_at TIMESTAMP NOT NULL, finished_at TIMESTAMP NULL,
 PRIMARY KEY(job_id,attempt_number), FOREIGN KEY(job_id) REFERENCES catalogue_import_jobs(id)
);
ALTER TABLE catalogue_import_candidates ADD COLUMN corrected_payload MEDIUMTEXT;
CREATE TABLE catalogue_candidate_corrections (
 id VARCHAR(36) PRIMARY KEY, candidate_id VARCHAR(36) NOT NULL, corrected_by VARCHAR(36) NOT NULL,
 corrected_at TIMESTAMP NOT NULL, payload_json MEDIUMTEXT NOT NULL, reason VARCHAR(1000) NOT NULL,
 FOREIGN KEY(candidate_id) REFERENCES catalogue_import_candidates(id), FOREIGN KEY(corrected_by) REFERENCES users(id)
);
