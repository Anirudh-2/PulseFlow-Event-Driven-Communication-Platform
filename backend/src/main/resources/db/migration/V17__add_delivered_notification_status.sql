-- disable flyway transaction for this migration
-- flyway:executeInTransaction=false

ALTER TYPE notif.notification_status
    ADD VALUE IF NOT EXISTS 'DELIVERED' AFTER 'ACTIVE';
