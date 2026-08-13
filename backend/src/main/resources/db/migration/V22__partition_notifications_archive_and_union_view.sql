-- Partition archive storage by month on archived_at and provide a unified active+archive view.
-- Strategy: recreate archive as a partitioned table, migrate existing rows, then expose vw_notifications_all.

CREATE TABLE IF NOT EXISTS notif.notifications_archive_new (
    id UUID NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    type notif.notification_type NOT NULL,
    priority notif.priority_level NOT NULL,
    source_service VARCHAR(100) NOT NULL,
    source_event_id VARCHAR(255),
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, archived_at)
) PARTITION BY RANGE (archived_at);

-- Default catch-all partition for historical/out-of-range rows.
CREATE TABLE IF NOT EXISTS notif.notifications_archive_default
    PARTITION OF notif.notifications_archive_new DEFAULT;

-- Create a few monthly partitions around current year for demo/prod bootstrap.
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2025_01
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2025_02
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2025_03
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2025-03-01') TO ('2025-04-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2025_04
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2025-04-01') TO ('2025-05-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2025_05
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2025-05-01') TO ('2025-06-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2025_06
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2025-06-01') TO ('2025-07-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2025_07
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2025-07-01') TO ('2025-08-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2025_08
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2025-08-01') TO ('2025-09-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2025_09
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2025-09-01') TO ('2025-10-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2025_10
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2025_11
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2025_12
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2026_01
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2026_02
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2026_03
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2026_04
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2026_05
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2026_06
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2026_07
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2026_08
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2026_09
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2026_10
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2026_11
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE IF NOT EXISTS notif.notifications_archive_2026_12
    PARTITION OF notif.notifications_archive_new
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');

INSERT INTO notif.notifications_archive_new (
    id, tenant_id, title, body, type, priority, source_service, source_event_id, metadata, created_at, archived_at
)
SELECT
    id, tenant_id, title, body, type, priority, source_service, source_event_id, metadata, created_at, archived_at
FROM notif.notifications_archive
ON CONFLICT DO NOTHING;

DROP TABLE IF EXISTS notif.notifications_archive;
ALTER TABLE notif.notifications_archive_new RENAME TO notifications_archive;

CREATE INDEX IF NOT EXISTS idx_notifications_archive_tenant_created
    ON notif.notifications_archive (tenant_id, created_at DESC);

CREATE OR REPLACE VIEW notif.vw_notifications_all AS
SELECT
    n.id,
    n.tenant_id,
    n.title,
    n.body,
    n.type::text AS type,
    n.priority::text AS priority,
    n.status::text AS status,
    n.source_service,
    n.source_event_id,
    n.metadata,
    n.created_at,
    NULL::timestamptz AS archived_at,
    FALSE AS is_archived
FROM notif.notifications n
WHERE COALESCE(n.is_deleted, FALSE) = FALSE
UNION ALL
SELECT
    a.id,
    a.tenant_id,
    a.title,
    a.body,
    a.type::text AS type,
    a.priority::text AS priority,
    'ARCHIVED'::text AS status,
    a.source_service,
    a.source_event_id,
    a.metadata,
    a.created_at,
    a.archived_at,
    TRUE AS is_archived
FROM notif.notifications_archive a;
