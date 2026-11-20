CREATE TABLE price_forecast_permissions (
    grant_id VARCHAR(36) PRIMARY KEY, permission_reference VARCHAR(200) NOT NULL,
    evidence_sha256 VARCHAR(64) NOT NULL, approved_by VARCHAR(36) NOT NULL,
    approved_at TIMESTAMP NOT NULL, expires_at TIMESTAMP NOT NULL,
    serve_allowed BOOLEAN NOT NULL, revoked_at TIMESTAMP NULL,
    FOREIGN KEY (grant_id) REFERENCES price_retention_grants(id), FOREIGN KEY (approved_by) REFERENCES users(id)
);
