CREATE TABLE grocery_variant_details (
    variant_id VARCHAR(60) PRIMARY KEY, subcategory VARCHAR(100), flavour VARCHAR(100), formulation VARCHAR(150),
    ingredients VARCHAR(4000), dietary_attributes VARCHAR(500), evidence_reference VARCHAR(500) NOT NULL,
    FOREIGN KEY (variant_id) REFERENCES product_variants(id)
);

CREATE TABLE grocery_ingredients (
    id VARCHAR(60) PRIMARY KEY, name VARCHAR(120) NOT NULL UNIQUE, evidence_reference VARCHAR(500) NOT NULL
);
CREATE TABLE grocery_ingredient_products (
    ingredient_id VARCHAR(60) NOT NULL, product_id VARCHAR(60) NOT NULL, evidence_reference VARCHAR(500) NOT NULL,
    PRIMARY KEY (ingredient_id, product_id), FOREIGN KEY (ingredient_id) REFERENCES grocery_ingredients(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
);
CREATE TABLE grocery_ingredient_substitutions (
    ingredient_id VARCHAR(60) NOT NULL, substitute_id VARCHAR(60) NOT NULL, quantity_ratio DECIMAL(12,4) NOT NULL,
    base_unit VARCHAR(10) NOT NULL, evidence_reference VARCHAR(500) NOT NULL,
    PRIMARY KEY (ingredient_id, substitute_id), CHECK (ingredient_id<>substitute_id), CHECK (quantity_ratio>0),
    FOREIGN KEY (ingredient_id) REFERENCES grocery_ingredients(id), FOREIGN KEY (substitute_id) REFERENCES grocery_ingredients(id)
);
ALTER TABLE recipe_ingredients ADD COLUMN grocery_ingredient_id VARCHAR(60);
ALTER TABLE recipe_ingredients ADD CONSTRAINT fk_recipe_grocery_ingredient FOREIGN KEY (grocery_ingredient_id) REFERENCES grocery_ingredients(id);

CREATE TABLE price_retention_grants (
    id VARCHAR(36) PRIMARY KEY, listing_id VARCHAR(80) NOT NULL, location_id VARCHAR(60) NOT NULL,
    mapping_decision_id VARCHAR(36) NOT NULL, provider VARCHAR(80) NOT NULL, permission_reference VARCHAR(200) NOT NULL,
    evidence_reference VARCHAR(500) NOT NULL, evidence_sha256 VARCHAR(64) NOT NULL,
    first_observation_at TIMESTAMP NOT NULL, last_observation_at TIMESTAMP NOT NULL, retain_until TIMESTAMP NOT NULL,
    history_allowed BOOLEAN NOT NULL, dataset_allowed BOOLEAN NOT NULL, approved_by VARCHAR(36) NOT NULL,
    approved_at TIMESTAMP NOT NULL, revoked_at TIMESTAMP NULL,
    FOREIGN KEY (listing_id) REFERENCES platform_products(id), FOREIGN KEY (location_id) REFERENCES delivery_locations(id),
    FOREIGN KEY (mapping_decision_id) REFERENCES provider_mapping_decisions(decision_id), FOREIGN KEY (approved_by) REFERENCES users(id)
);
CREATE INDEX idx_price_grant_expiry ON price_retention_grants(retain_until);

CREATE TABLE approved_price_observations (
    id VARCHAR(36) PRIMARY KEY, grant_id VARCHAR(36) NOT NULL, listing_id VARCHAR(80) NOT NULL,
    location_id VARCHAR(60) NOT NULL, variant_id VARCHAR(60) NOT NULL, retailer_sku VARCHAR(150) NOT NULL,
    observed_at TIMESTAMP NOT NULL, recorded_at TIMESTAMP NOT NULL, source_provider VARCHAR(80) NOT NULL, source_record_id VARCHAR(150) NOT NULL,
    source_evidence VARCHAR(500) NOT NULL, price_minor BIGINT, mrp_minor BIGINT, discount_percent DECIMAL(5,2),
    stock_status VARCHAR(20) NOT NULL, pack_label VARCHAR(60) NOT NULL, pack_count INT NOT NULL,
    each_amount DECIMAL(12,3) NOT NULL, base_unit VARCHAR(10) NOT NULL,
    UNIQUE (listing_id, location_id, observed_at), UNIQUE (listing_id, location_id, source_provider, source_record_id),
    CHECK (price_minor>=0), CHECK (mrp_minor>=0), CHECK (discount_percent BETWEEN 0 AND 100),
    CHECK (pack_count>0), CHECK (each_amount>0), CHECK (stock_status IN ('AVAILABLE','OUT_OF_STOCK','UNKNOWN')),
    FOREIGN KEY (grant_id) REFERENCES price_retention_grants(id), FOREIGN KEY (listing_id) REFERENCES platform_products(id),
    FOREIGN KEY (variant_id) REFERENCES product_variants(id), FOREIGN KEY (location_id) REFERENCES delivery_locations(id)
);
CREATE INDEX idx_approved_price_variant_location ON approved_price_observations(variant_id,location_id,observed_at);
