CREATE TABLE IF NOT EXISTS notif.channel_configurations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(64) NOT NULL,
    app_id UUID NOT NULL REFERENCES notif.integration_sources(id) ON DELETE CASCADE,
    channel_type TEXT NOT NULL,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_channel_configurations_channel_type
        CHECK (channel_type IN ('teams', 'whatsapp', 'telegram', 'smtp', 'webhook'))
);

CREATE INDEX IF NOT EXISTS idx_channel_configurations_lookup
    ON notif.channel_configurations (tenant_id, app_id, channel_type, is_active);

CREATE INDEX IF NOT EXISTS idx_channel_configurations_config_gin
    ON notif.channel_configurations USING GIN (config_json);
