CREATE TABLE local_store_comparison_terms (
    store_id VARCHAR(36) PRIMARY KEY,
    pickup_charge_minor BIGINT CHECK(pickup_charge_minor>=0),
    delivery_charge_minor BIGINT CHECK(delivery_charge_minor>=0),
    pickup_minimum_minor BIGINT CHECK(pickup_minimum_minor>=0),
    delivery_minimum_minor BIGINT CHECK(delivery_minimum_minor>=0),
    observed_at TIMESTAMP NOT NULL,
    FOREIGN KEY(store_id) REFERENCES local_stores(id)
);
