CREATE TABLE online_comparison_evidence (
    price_id VARCHAR(100) PRIMARY KEY,
    retailer_sku VARCHAR(150) NOT NULL,
    product_name VARCHAR(150) NOT NULL,
    brand VARCHAR(100) NOT NULL,
    variant_name VARCHAR(100) NOT NULL,
    amount_base DECIMAL(12,3) NOT NULL,
    base_unit VARCHAR(10) NOT NULL,
    price_observed_at TIMESTAMP NOT NULL,
    stock_quantity INT CHECK(stock_quantity>=0),
    delivery_charge_minor BIGINT CHECK(delivery_charge_minor>=0),
    handling_charge_minor BIGINT CHECK(handling_charge_minor>=0),
    minimum_minor BIGINT CHECK(minimum_minor>=0),
    evidence_source VARCHAR(150) NOT NULL,
    observed_at TIMESTAMP NOT NULL,
    verified_by VARCHAR(36) NOT NULL,
    FOREIGN KEY(price_id) REFERENCES location_prices(id),
    FOREIGN KEY(verified_by) REFERENCES users(id)
);
