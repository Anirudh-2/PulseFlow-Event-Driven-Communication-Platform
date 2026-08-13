-- V6: Production hardening constraints and indexes (safe rollout)
-- Goal: improve integrity and query performance without breaking existing APIs.

-- 1) Add composite uniqueness to support tenant-consistent foreign keys.
ALTER TABLE notif.notifications
    ADD CONSTRAINT uq_notifications_tenant_id_id UNIQUE (tenant_id, id);

ALTER TABLE notif.notification_recipients
    ADD CONSTRAINT uq_recipients_tenant_id_id UNIQUE (tenant_id, id);

-- 2) Delivery log correctness checks.
ALTER TABLE notif.notification_delivery_log
    ADD CONSTRAINT chk_delivery_attempt_nonnegative CHECK (attempt_count >= 0),
    ADD CONSTRAINT chk_delivery_max_attempts_positive CHECK (max_attempts > 0),
    ADD CONSTRAINT chk_delivery_attempt_lte_max CHECK (attempt_count <= max_attempts),
    ADD CONSTRAINT chk_delivery_delivered_after_created CHECK (delivered_at IS NULL OR delivered_at >= created_at);

-- 3) Tenant-consistent foreign keys on delivery log.
ALTER TABLE notif.notification_delivery_log
    ADD CONSTRAINT fk_delivery_tenant_notification
        FOREIGN KEY (tenant_id, notification_id)
        REFERENCES notif.notifications (tenant_id, id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
        NOT VALID;

ALTER TABLE notif.notification_delivery_log
    ADD CONSTRAINT fk_delivery_tenant_recipient
        FOREIGN KEY (tenant_id, recipient_id)
        REFERENCES notif.notification_recipients (tenant_id, id)
        ON UPDATE CASCADE
        ON DELETE CASCADE
        NOT VALID;

ALTER TABLE notif.notification_delivery_log VALIDATE CONSTRAINT fk_delivery_tenant_notification;
ALTER TABLE notif.notification_delivery_log VALIDATE CONSTRAINT fk_delivery_tenant_recipient;

-- 4) Optional references with tenant consistency for audit/failures (nullable IDs).
ALTER TABLE notif.notification_audit_log
    ADD CONSTRAINT fk_audit_tenant_notification
        FOREIGN KEY (tenant_id, notification_id)
        REFERENCES notif.notifications (tenant_id, id)
        ON UPDATE CASCADE
        ON DELETE SET NULL
        NOT VALID;

ALTER TABLE notif.notification_audit_log
    ADD CONSTRAINT fk_audit_tenant_recipient
        FOREIGN KEY (tenant_id, recipient_id)
        REFERENCES notif.notification_recipients (tenant_id, id)
        ON UPDATE CASCADE
        ON DELETE SET NULL
        NOT VALID;

ALTER TABLE notif.notification_failures
    ADD CONSTRAINT fk_failure_tenant_notification
        FOREIGN KEY (tenant_id, notification_id)
        REFERENCES notif.notifications (tenant_id, id)
        ON UPDATE CASCADE
        ON DELETE SET NULL
        NOT VALID;

ALTER TABLE notif.notification_failures
    ADD CONSTRAINT fk_failure_tenant_recipient
        FOREIGN KEY (tenant_id, recipient_id)
        REFERENCES notif.notification_recipients (tenant_id, id)
        ON UPDATE CASCADE
        ON DELETE SET NULL
        NOT VALID;

ALTER TABLE notif.notification_audit_log VALIDATE CONSTRAINT fk_audit_tenant_notification;
ALTER TABLE notif.notification_audit_log VALIDATE CONSTRAINT fk_audit_tenant_recipient;
ALTER TABLE notif.notification_failures VALIDATE CONSTRAINT fk_failure_tenant_notification;
ALTER TABLE notif.notification_failures VALIDATE CONSTRAINT fk_failure_tenant_recipient;

-- 5) Additional query-path indexes.
CREATE INDEX IF NOT EXISTS idx_audit_tenant_action_occurred
    ON notif.notification_audit_log (tenant_id, action, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_delivery_tenant_channel_created
    ON notif.notification_delivery_log (tenant_id, channel, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_tenant_priority_created
    ON notif.notifications (tenant_id, priority, created_at DESC);

-- 6) Selective JSONB GIN indexes for metadata/rule-condition filtering.
CREATE INDEX IF NOT EXISTS idx_notifications_metadata_gin
    ON notif.notifications USING GIN (metadata);

CREATE INDEX IF NOT EXISTS idx_rules_conditions_gin
    ON notif.notification_rules USING GIN (conditions);
