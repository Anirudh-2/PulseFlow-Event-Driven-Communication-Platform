DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_enum e
        JOIN pg_type t ON t.oid = e.enumtypid
        JOIN pg_namespace n ON n.oid = t.typnamespace
        WHERE n.nspname = 'notif'
          AND t.typname = 'notification_status'
          AND e.enumlabel = 'DEAD_LETTERED'
    ) THEN
        ALTER TYPE notif.notification_status ADD VALUE 'DEAD_LETTERED';
    END IF;
END $$;
