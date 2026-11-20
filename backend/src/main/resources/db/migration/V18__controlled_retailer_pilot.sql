CREATE TABLE retailer_pilots (
    id VARCHAR(36) PRIMARY KEY,
    location_id VARCHAR(60) NOT NULL,
    retailer VARCHAR(20) NOT NULL,
    starts_at TIMESTAMP NOT NULL,
    ends_at TIMESTAMP NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    attempt_limit INT NOT NULL CHECK (attempt_limit BETWEEN 1 AND 12),
    credit_limit INT NOT NULL CHECK (credit_limit BETWEEN 1 AND 12),
    permission_reference VARCHAR(200) NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CHECK (starts_at < ends_at),
    FOREIGN KEY(location_id) REFERENCES delivery_locations(id),
    FOREIGN KEY(retailer) REFERENCES grocery_platforms(id),
    FOREIGN KEY(created_by) REFERENCES users(id)
);

CREATE TABLE retailer_pilot_variants (
    pilot_id VARCHAR(36) NOT NULL,
    variant_id VARCHAR(60) NOT NULL,
    mapping_decision_id VARCHAR(36) NOT NULL,
    grocery_group VARCHAR(20) NOT NULL CHECK (grocery_group IN ('MILK','RICE','DAL','COOKING_OIL','SNACKS')),
    PRIMARY KEY(pilot_id,variant_id),
    FOREIGN KEY(pilot_id) REFERENCES retailer_pilots(id),
    FOREIGN KEY(variant_id) REFERENCES product_variants(id),
    FOREIGN KEY(mapping_decision_id) REFERENCES provider_mapping_decisions(decision_id)
);

CREATE TABLE retailer_pilot_attempts (
    id VARCHAR(36) PRIMARY KEY,
    pilot_id VARCHAR(36) NOT NULL,
    variant_id VARCHAR(60) NOT NULL,
    account_id VARCHAR(36) NOT NULL,
    authorized_by VARCHAR(36) NOT NULL,
    authorization_reference VARCHAR(200) NOT NULL,
    operation VARCHAR(10) NOT NULL CHECK (operation IN ('SEARCH','ITEM')),
    expires_at TIMESTAMP NOT NULL,
    authorized_at TIMESTAMP NOT NULL,
    claimed_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    result_code VARCHAR(30),
    billing VARCHAR(24),
    provider_request_id VARCHAR(36),
    upstream_status INT,
    confirmed_debit INT CHECK (confirmed_debit IN (0,1)),
    credits_remaining INT,
    candidate_count INT CHECK (candidate_count>=0),
    verified_count INT CHECK (verified_count>=0),
    UNIQUE(pilot_id,authorization_reference),
    FOREIGN KEY(pilot_id,variant_id) REFERENCES retailer_pilot_variants(pilot_id,variant_id),
    FOREIGN KEY(account_id) REFERENCES users(id),
    FOREIGN KEY(authorized_by) REFERENCES users(id)
);
CREATE INDEX idx_pilot_attempt_status ON retailer_pilot_attempts(pilot_id,claimed_at,expires_at);
