ALTER TABLE notif.notification_delivery_log
    ADD COLUMN IF NOT EXISTS template_id UUID;

CREATE INDEX IF NOT EXISTS idx_delivery_log_template_id
    ON notif.notification_delivery_log(template_id);
