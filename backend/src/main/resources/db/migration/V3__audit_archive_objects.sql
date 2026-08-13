CREATE TABLE IF NOT EXISTS notif.notification_audit_log (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    notification_id UUID,
    recipient_id UUID,
    action VARCHAR(64) NOT NULL,
    actor_user_id VARCHAR(255),
    metadata JSONB NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    correlation_id VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS notif.notifications_archive (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    type notif.notification_type NOT NULL,
    priority notif.priority_level NOT NULL,
    source_service VARCHAR(100) NOT NULL,
    source_event_id VARCHAR(255),
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL,
    archived_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_tenant_occurred ON notif.notification_audit_log(tenant_id, occurred_at DESC);
