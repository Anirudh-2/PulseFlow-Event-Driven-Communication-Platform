-- Optional new channel value for outbound HTTP delivery
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_enum e
        JOIN pg_type t ON t.oid = e.enumtypid
        JOIN pg_namespace n ON n.oid = t.typnamespace
        WHERE n.nspname = 'notif'
          AND t.typname = 'delivery_channel'
          AND e.enumlabel = 'WEBHOOK'
    ) THEN
        ALTER TYPE notif.delivery_channel ADD VALUE 'WEBHOOK';
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS notif.integration_sources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL,
    source_key VARCHAR(128) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    webhook_api_key_hash VARCHAR(128),
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_integration_sources_tenant_key UNIQUE (tenant_id, source_key)
);

CREATE INDEX IF NOT EXISTS idx_integration_sources_tenant ON notif.integration_sources (tenant_id, is_active);

CREATE TABLE IF NOT EXISTS notif.integration_field_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    integration_source_id UUID NOT NULL REFERENCES notif.integration_sources (id) ON DELETE CASCADE,
    version INT NOT NULL DEFAULT 1,
    mapping JSONB NOT NULL DEFAULT '{}',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ifm_integration_version UNIQUE (integration_source_id, version)
);

CREATE TABLE IF NOT EXISTS notif.channel_types (
    code VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    handler_key VARCHAR(128) NOT NULL,
    capabilities JSONB NOT NULL DEFAULT '{}',
    is_enabled_globally BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO notif.channel_types (code, display_name, handler_key, capabilities) VALUES
    ('EMAIL', 'Email (SMTP)', 'email', '{}'),
    ('TEAMS', 'Microsoft Teams', 'teams', '{}'),
    ('TELEGRAM', 'Telegram', 'telegram', '{}'),
    ('WHATSAPP', 'WhatsApp (Twilio)', 'whatsapp', '{}'),
    ('WEBHOOK', 'Outbound HTTP webhook', 'webhook', '{}'),
    ('WEBSOCKET', 'In-app WebSocket', 'websocket', '{}'),
    ('SSE', 'Server-Sent Events', 'sse', '{}'),
    ('PUSH', 'Mobile push', 'push', '{}'),
    ('POLLING', 'Polling', 'polling', '{}')
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS notif.tenant_channel_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL,
    channel_type_code VARCHAR(64) NOT NULL REFERENCES notif.channel_types (code),
    name VARCHAR(255) NOT NULL,
    config JSONB NOT NULL DEFAULT '{}',
    priority SMALLINT NOT NULL DEFAULT 100,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tenant_channel_configs_lookup
    ON notif.tenant_channel_configs (tenant_id, channel_type_code, is_enabled, priority);

CREATE TABLE IF NOT EXISTS notif.notification_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL,
    integration_source_id UUID REFERENCES notif.integration_sources (id) ON DELETE SET NULL,
    event_type VARCHAR(255) NOT NULL,
    channel_type_code VARCHAR(64) NOT NULL REFERENCES notif.channel_types (code),
    locale VARCHAR(32) NOT NULL DEFAULT 'en',
    subject_template TEXT,
    body_template TEXT NOT NULL,
    content_type VARCHAR(32) NOT NULL DEFAULT 'text',
    template_version INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_templates_global_active
    ON notif.notification_templates (tenant_id, event_type, channel_type_code, locale)
    WHERE is_active = TRUE AND integration_source_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_templates_source_active
    ON notif.notification_templates (tenant_id, integration_source_id, event_type, channel_type_code, locale)
    WHERE is_active = TRUE AND integration_source_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS notif.routing_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL,
    integration_source_id UUID REFERENCES notif.integration_sources (id) ON DELETE SET NULL,
    name VARCHAR(255) NOT NULL,
    event_type VARCHAR(255),
    role_name VARCHAR(100),
    conditions_jsonlogic JSONB,
    channel_type_codes TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    eval_order SMALLINT NOT NULL DEFAULT 100,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_routing_rules_name UNIQUE (tenant_id, name)
);

CREATE INDEX IF NOT EXISTS idx_routing_rules_match
    ON notif.routing_rules (tenant_id, is_active, eval_order);

ALTER TABLE notif.notification_rules
    ADD COLUMN IF NOT EXISTS integration_source_id UUID REFERENCES notif.integration_sources (id);

ALTER TABLE notif.notification_rules
    ADD COLUMN IF NOT EXISTS event_type VARCHAR(255);

ALTER TABLE notif.notification_rules
    ADD COLUMN IF NOT EXISTS conditions_jsonlogic JSONB;

ALTER TABLE notif.notifications
    ADD COLUMN IF NOT EXISTS integration_source_id UUID REFERENCES notif.integration_sources (id);

ALTER TABLE notif.notifications
    ADD COLUMN IF NOT EXISTS event_type VARCHAR(255);

ALTER TABLE notif.notifications
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_notifications_tenant_event ON notif.notifications (tenant_id, event_type);
