-- Seed one integration source per tenant from legacy JSON config (HRMS / source service name).
INSERT INTO notif.integration_sources (id, tenant_id, source_key, display_name, is_active, metadata, created_at, updated_at)
SELECT gen_random_uuid(),
       tic.tenant_id,
       UPPER(
           NULLIF(
               BTRIM(COALESCE(tic.hrms_mapping ->> 'sourceServiceName', 'HRMS')),
               ''
           )
       ),
       COALESCE(NULLIF(BTRIM(tic.hrms_mapping ->> 'sourceServiceName'), ''), 'HRMS') || ' (migrated)',
       TRUE,
       jsonb_build_object('migratedFrom', 'tenant_integration_config'),
       NOW(),
       NOW()
FROM notif.tenant_integration_config tic
ON CONFLICT (tenant_id, source_key) DO NOTHING;

-- Optional: link default SMTP / Teams / Telegram JSON into tenant_channel_configs when enabled (best-effort, idempotent).
INSERT INTO notif.tenant_channel_configs (id, tenant_id, channel_type_code, name, config, priority, is_default, is_enabled, created_at, updated_at)
SELECT gen_random_uuid(),
       tic.tenant_id,
       'EMAIL',
       'Migrated SMTP',
       tic.smtp,
       10,
       TRUE,
       COALESCE((tic.smtp ->> 'enabled')::boolean, FALSE),
       NOW(),
       NOW()
FROM notif.tenant_integration_config tic
WHERE COALESCE((tic.smtp ->> 'enabled')::boolean, FALSE) = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM notif.tenant_channel_configs x
      WHERE x.tenant_id = tic.tenant_id
        AND x.channel_type_code = 'EMAIL'
        AND x.name = 'Migrated SMTP'
  );

INSERT INTO notif.tenant_channel_configs (id, tenant_id, channel_type_code, name, config, priority, is_default, is_enabled, created_at, updated_at)
SELECT gen_random_uuid(),
       tic.tenant_id,
       'TEAMS',
       'Migrated Teams',
       tic.teams,
       20,
       TRUE,
       COALESCE((tic.teams ->> 'enabled')::boolean, FALSE),
       NOW(),
       NOW()
FROM notif.tenant_integration_config tic
WHERE COALESCE((tic.teams ->> 'enabled')::boolean, FALSE) = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM notif.tenant_channel_configs x
      WHERE x.tenant_id = tic.tenant_id
        AND x.channel_type_code = 'TEAMS'
        AND x.name = 'Migrated Teams'
  );

INSERT INTO notif.tenant_channel_configs (id, tenant_id, channel_type_code, name, config, priority, is_default, is_enabled, created_at, updated_at)
SELECT gen_random_uuid(),
       tic.tenant_id,
       'TELEGRAM',
       'Migrated Telegram',
       tic.telegram,
       30,
       TRUE,
       COALESCE((tic.telegram ->> 'enabled')::boolean, FALSE),
       NOW(),
       NOW()
FROM notif.tenant_integration_config tic
WHERE COALESCE((tic.telegram ->> 'enabled')::boolean, FALSE) = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM notif.tenant_channel_configs x
      WHERE x.tenant_id = tic.tenant_id
        AND x.channel_type_code = 'TELEGRAM'
        AND x.name = 'Migrated Telegram'
  );
