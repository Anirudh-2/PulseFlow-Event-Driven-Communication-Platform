-- flyway:executeInTransaction=false
ALTER TYPE notif.delivery_status
    ADD VALUE IF NOT EXISTS 'SKIPPED' AFTER 'FAILED';
