ALTER TABLE cart_items ADD COLUMN mapping_decision_id VARCHAR(36);
ALTER TABLE cart_items ADD CONSTRAINT fk_cart_listing_decision
    FOREIGN KEY (mapping_decision_id) REFERENCES provider_mapping_decisions(decision_id);
