CREATE TABLE IF NOT EXISTS notif.tenant_integration_config (
    tenant_id VARCHAR(64) PRIMARY KEY,
    teams JSONB NOT NULL DEFAULT '{}'::jsonb,
    smtp JSONB NOT NULL DEFAULT '{}'::jsonb,
    webhook_security JSONB NOT NULL DEFAULT '{}'::jsonb,
    hrms_mapping JSONB NOT NULL DEFAULT '{}'::jsonb,
    templates JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_by VARCHAR(255),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
