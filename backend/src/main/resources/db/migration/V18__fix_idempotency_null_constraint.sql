DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conname = 'uq_notifications_source_event'
      AND conrelid = 'notif.notifications'::regclass
  ) THEN
    ALTER TABLE notif.notifications
      DROP CONSTRAINT uq_notifications_source_event;
  END IF;
END $$;

DROP INDEX IF EXISTS notif.uq_notifications_source_event;

CREATE UNIQUE INDEX IF NOT EXISTS uq_notif_source_event_non_null
  ON notif.notifications(tenant_id, source_service, source_event_id)
  WHERE source_event_id IS NOT NULL;
