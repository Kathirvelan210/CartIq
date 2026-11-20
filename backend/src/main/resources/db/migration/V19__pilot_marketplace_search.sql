ALTER TABLE retailer_pilot_attempts ADD COLUMN discovery_query VARCHAR(150);
ALTER TABLE retailer_pilot_attempts ADD COLUMN discovery_claimed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE retailer_pilot_attempts ADD CONSTRAINT chk_discovery_claim
    CHECK (discovery_query IS NULL OR claimed_at IS NULL OR discovery_claimed = TRUE);
