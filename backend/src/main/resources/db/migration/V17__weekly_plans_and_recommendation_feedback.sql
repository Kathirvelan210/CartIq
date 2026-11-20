CREATE TABLE weekly_shopping_plans (
    user_id VARCHAR(36) NOT NULL,
    location_id VARCHAR(60) NOT NULL,
    plan_input TEXT NOT NULL,
    revision INT NOT NULL CHECK (revision>0),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id,location_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (location_id) REFERENCES delivery_locations(id)
);

CREATE TABLE recommendation_feedback (
    user_id VARCHAR(36) NOT NULL,
    reference_id VARCHAR(36) NOT NULL,
    category VARCHAR(20) NOT NULL CHECK (category IN ('PLAN','BASKET','BULK_BUY','PERSONALIZED','ASSISTANT')),
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('ACCEPTED','REJECTED','MODIFIED','NOT_RELEVANT')),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id,reference_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_recommendation_feedback_aggregate ON recommendation_feedback(updated_at,category);
