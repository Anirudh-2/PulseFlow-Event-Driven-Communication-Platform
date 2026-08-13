DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_enum e
        JOIN pg_type t ON t.oid = e.enumtypid
        JOIN pg_namespace n ON n.oid = t.typnamespace
        WHERE n.nspname = 'notif'
          AND t.typname = 'delivery_channel'
          AND e.enumlabel = 'TELEGRAM'
    ) THEN
        ALTER TYPE notif.delivery_channel ADD VALUE 'TELEGRAM';
    END IF;
END $$;

ALTER TABLE notif.notification_recipients
    ADD COLUMN IF NOT EXISTS telegram_chat_id VARCHAR(255);
