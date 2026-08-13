ALTER TABLE notif.tenant_integration_config
    ADD COLUMN IF NOT EXISTS telegram JSONB NOT NULL DEFAULT '{}'::jsonb;
