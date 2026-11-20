CREATE TABLE catalogue_import_jobs (
 id VARCHAR(36) PRIMARY KEY, source VARCHAR(40) NOT NULL, status VARCHAR(24) NOT NULL,
 requested_by VARCHAR(36) NOT NULL, source_code VARCHAR(14) NOT NULL,
 error_message VARCHAR(1000), created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 finished_at TIMESTAMP NULL, FOREIGN KEY(requested_by) REFERENCES users(id)
);
CREATE TABLE catalogue_import_candidates (
 id VARCHAR(36) PRIMARY KEY, job_id VARCHAR(36) NOT NULL, source VARCHAR(40) NOT NULL,
 external_id VARCHAR(14) NOT NULL, source_url VARCHAR(1000) NOT NULL,
 payload_json MEDIUMTEXT NOT NULL, status VARCHAR(24) NOT NULL, validation_message VARCHAR(1000),
 collected_at TIMESTAMP NOT NULL, reviewed_by VARCHAR(36), reviewed_at TIMESTAMP NULL,
 FOREIGN KEY(job_id) REFERENCES catalogue_import_jobs(id), FOREIGN KEY(reviewed_by) REFERENCES users(id)
);
CREATE INDEX idx_import_candidates_status ON catalogue_import_candidates(status,collected_at);
CREATE TABLE variant_barcodes (
 gtin VARCHAR(14) PRIMARY KEY, variant_id VARCHAR(60) NOT NULL,
 FOREIGN KEY(variant_id) REFERENCES product_variants(id)
);
CREATE INDEX idx_barcode_variant ON variant_barcodes(variant_id);
CREATE TABLE external_product_sources (
 source VARCHAR(40) NOT NULL, external_id VARCHAR(14) NOT NULL, variant_id VARCHAR(60) NOT NULL,
 source_url VARCHAR(1000) NOT NULL, collected_at TIMESTAMP NOT NULL, verification_status VARCHAR(40) NOT NULL,
 attribution VARCHAR(500) NOT NULL, data_license VARCHAR(100) NOT NULL, image_license VARCHAR(100),
 payload_json MEDIUMTEXT NOT NULL, PRIMARY KEY(source,external_id),
 FOREIGN KEY(variant_id) REFERENCES product_variants(id)
);
CREATE TABLE catalogue_import_locks (source VARCHAR(40) PRIMARY KEY, next_request_at TIMESTAMP NOT NULL);
INSERT INTO catalogue_import_locks(source,next_request_at) VALUES ('OPEN_FOOD_FACTS','2000-01-01 00:00:00');
