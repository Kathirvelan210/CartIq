CREATE TABLE provider_mappings (platform_product_id VARCHAR(80) PRIMARY KEY, matched_by VARCHAR(36) NOT NULL, matched_at TIMESTAMP NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY(platform_product_id) REFERENCES platform_products(id), FOREIGN KEY(matched_by) REFERENCES users(id));

