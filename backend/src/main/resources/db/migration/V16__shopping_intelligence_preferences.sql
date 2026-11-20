CREATE TABLE shopping_intelligence_preferences (
    user_id VARCHAR(36) PRIMARY KEY,
    household_size INT NOT NULL CHECK (household_size BETWEEN 1 AND 30),
    dietary_preference VARCHAR(20) NOT NULL CHECK (dietary_preference IN ('ANY','VEGETARIAN','NON_VEGETARIAN')),
    preferred_cuisine VARCHAR(100), shopping_budget_minor BIGINT CHECK (shopping_budget_minor>=0),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE TABLE bulk_buy_preferences (
    user_id VARCHAR(36) NOT NULL, product_id VARCHAR(60) NOT NULL, base_unit VARCHAR(10) NOT NULL,
    per_person_daily DECIMAL(12,3) NOT NULL CHECK (per_person_daily>0),
    storage_capacity DECIMAL(12,3) NOT NULL CHECK (storage_capacity>=0),
    planning_days INT NOT NULL CHECK (planning_days BETWEEN 1 AND 90),
    budget_minor BIGINT CHECK (budget_minor>=0), updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(user_id,product_id,base_unit), FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY(product_id) REFERENCES products(id)
);
