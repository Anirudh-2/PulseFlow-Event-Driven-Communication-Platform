ALTER TABLE IF EXISTS notif.notifications
    ADD COLUMN IF NOT EXISTS sequence_number BIGINT,
    ADD COLUMN IF NOT EXISTS event_timestamp TIMESTAMPTZ;

-- Speeds up "latest sequence" lookups for ordering/dropOld behavior.
CREATE INDEX IF NOT EXISTS idx_notifications_latest_sequence
    ON notif.notifications(tenant_id, source_service, event_type, sequence_number DESC, created_at DESC)
    WHERE is_deleted = FALSE;

