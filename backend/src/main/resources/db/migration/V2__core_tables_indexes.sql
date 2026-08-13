CREATE TABLE IF NOT EXISTS notif.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    type notif.notification_type NOT NULL,
    priority notif.priority_level NOT NULL,
    status notif.notification_status NOT NULL DEFAULT 'ACTIVE',
    source_service VARCHAR(100) NOT NULL,
    source_event_id VARCHAR(255),
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT uq_notifications_source_event UNIQUE (tenant_id, source_service, source_event_id)
);

CREATE TABLE IF NOT EXISTS notif.notification_recipients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL,
    notification_id UUID NOT NULL REFERENCES notif.notifications(id),
    user_id VARCHAR(255) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    is_acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    read_at TIMESTAMPTZ,
    acknowledged_at TIMESTAMPTZ,
    CONSTRAINT uq_recipient UNIQUE (tenant_id, notification_id, user_id)
);

CREATE TABLE IF NOT EXISTS notif.notification_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    notification_type notif.notification_type,
    priority_override notif.priority_level,
    conditions JSONB NOT NULL DEFAULT '{}',
    channels notif.delivery_channel[] NOT NULL DEFAULT ARRAY['WEBSOCKET']::notif.delivery_channel[],
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    eval_order SMALLINT NOT NULL DEFAULT 100,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rules_name UNIQUE (tenant_id, name)
);

CREATE TABLE IF NOT EXISTS notif.notification_delivery_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL,
    notification_id UUID NOT NULL REFERENCES notif.notifications(id),
    recipient_id UUID NOT NULL REFERENCES notif.notification_recipients(id),
    channel notif.delivery_channel NOT NULL,
    status notif.delivery_status NOT NULL DEFAULT 'PENDING',
    attempt_count SMALLINT NOT NULL DEFAULT 0,
    max_attempts SMALLINT NOT NULL DEFAULT 3,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivered_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS notif.notification_failures (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL,
    notification_id UUID,
    recipient_id UUID,
    channel notif.delivery_channel,
    failure_reason TEXT NOT NULL,
    raw_event_payload JSONB,
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_tenant_active ON notif.notifications(tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_recipients_tenant_user_unread ON notif.notification_recipients(tenant_id, user_id, created_at DESC) WHERE is_read = FALSE;
CREATE INDEX IF NOT EXISTS idx_rules_tenant_active ON notif.notification_rules(tenant_id, is_active, eval_order);
CREATE INDEX IF NOT EXISTS idx_delivery_tenant_status ON notif.notification_delivery_log(tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_failure_tenant_unresolved ON notif.notification_failures(tenant_id, occurred_at DESC) WHERE is_resolved = FALSE;
