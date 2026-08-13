-- Backfill routing_rules from legacy notification_rules.
-- Keeps notification_rules intact and idempotently copies missing records by (tenant_id, name).
INSERT INTO notif.routing_rules (
    tenant_id,
    integration_source_id,
    name,
    event_type,
    role_name,
    conditions_jsonlogic,
    channel_type_codes,
    eval_order,
    is_active,
    created_at,
    updated_at
)
SELECT
    nr.tenant_id,
    nr.integration_source_id,
    nr.name,
    COALESCE(NULLIF(nr.event_type, ''), nr.notification_type::text) AS event_type,
    nr.role_name,
    COALESCE(nr.conditions_jsonlogic, nr.conditions) AS conditions_jsonlogic,
    COALESCE(nr.channels::text[], ARRAY[]::text[]) AS channel_type_codes,
    nr.eval_order,
    nr.is_active,
    nr.created_at,
    nr.updated_at
FROM notif.notification_rules nr
ON CONFLICT (tenant_id, name) DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_routing_rules_conditions_jsonlogic_gin
    ON notif.routing_rules USING GIN (conditions_jsonlogic);

CREATE INDEX IF NOT EXISTS idx_routing_rules_channel_codes_gin
    ON notif.routing_rules USING GIN (channel_type_codes);
