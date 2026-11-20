CREATE TABLE notification_deliveries (
    notification_id VARCHAR(36) NOT NULL,
    device_id VARCHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NULL,
    last_attempt_at TIMESTAMP NULL,
    provider_retry_at TIMESTAMP NULL,
    last_error VARCHAR(32),
    provider_message_id VARCHAR(300),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (notification_id, device_id),
    FOREIGN KEY (notification_id) REFERENCES notifications(id) ON DELETE CASCADE
);

CREATE INDEX idx_notification_delivery_due ON notification_deliveries (status, next_attempt_at);
CREATE INDEX idx_notification_delivery_claim ON notification_deliveries (status, last_attempt_at);
CREATE INDEX idx_notification_delivery_throttle ON notification_deliveries (provider_retry_at);
CREATE INDEX idx_notifications_push_queue ON notifications (delivery_status, created_at, id);

-- Older workers kept no recipient receipts. Their unfinished events cannot safely be replayed.
UPDATE notifications SET delivery_status='IN_APP' WHERE delivery_status='PUSH_PENDING';
