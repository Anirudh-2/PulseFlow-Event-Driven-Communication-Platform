ALTER TABLE notif.notification_recipients
    ADD COLUMN IF NOT EXISTS user_email    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS aad_object_id VARCHAR(255);
