-- PulseFlow demo seed (manual re-apply)
-- Apply:
--   docker exec -i pulseflow-postgres psql -U postgres -d pulseflow < scripts/seed-demo.sql
-- Or from PowerShell:
--   Get-Content .\scripts\seed-demo.sql | docker exec -i pulseflow-postgres psql -U postgres -d pulseflow
-- Prefer Flyway V23 on fresh volumes: docker compose down -v && docker compose up -d --build
-- Topbar: tenantId=default, userId=demo-user
-- Demo seed for a populated UI (tenant default, inbox user demo-user).
-- Idempotent: safe to re-run via scripts/seed-demo.sql with WHERE NOT EXISTS / ON CONFLICT.

-- Fixed UUIDs for FK wiring
-- Sources: a111... HRMS, a222... ORDERS, a333... PAYMENTS
-- Templates: b111... b444...
-- Notifications: c111... c888...
-- Recipients: d111... d888... (+ d901 admin)
-- Delivery: e111...
-- Channel configs / tenant channels / routing / mappings: f111...

-- ---------------------------------------------------------------------------
-- 1) Tenant integration config (Configuration → Integrations tab)
-- ---------------------------------------------------------------------------
INSERT INTO notif.tenant_integration_config (
    tenant_id, teams, smtp, webhook_security, hrms_mapping, templates, telegram, updated_by, updated_at
)
SELECT
    'default',
    '{"enabled":true,"webhookUrl":"https://example.webhook.office.com/webhookb2/demo"}'::jsonb,
    '{"enabled":true,"host":"smtp.example.com","port":587,"username":"demo@pulseflow.local","password":"demo","from":"no-reply@pulseflow.local","useTls":true}'::jsonb,
    '{"mode":"API_KEY","apiKeyHeader":"X-Webhook-Api-Key"}'::jsonb,
    '{"defaultTenantId":"default","sourceServiceName":"HRMS","userIdentifierStrategy":"AAD_ID_FIRST","eventTypeMap":{}}'::jsonb,
    '{"teams":{"titleTemplate":"{{eventType}}","bodyTemplate":"{{body}}"},"email":{"subjectTemplate":"{{eventType}}","bodyTemplate":"{{body}}"}}'::jsonb,
    '{"enabled":true,"apiBase":"https://api.telegram.org","parseMode":"Markdown","botToken":"demo-bot-token","chatId":"123456789"}'::jsonb,
    'demo-seed',
    NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM notif.tenant_integration_config WHERE tenant_id = 'default'
);

-- ---------------------------------------------------------------------------
-- 2) Integration sources (Applications)
-- ---------------------------------------------------------------------------
INSERT INTO notif.integration_sources (id, tenant_id, source_key, display_name, is_active, metadata, created_at, updated_at)
VALUES
    ('a1111111-1111-1111-1111-111111111111', 'default', 'HRMS', 'HRMS System', TRUE,
     '{"seeded":true}'::jsonb, NOW() - INTERVAL '10 days', NOW()),
    ('a2222222-2222-2222-2222-222222222222', 'default', 'ORDERS', 'Orders Service', TRUE,
     '{"seeded":true}'::jsonb, NOW() - INTERVAL '9 days', NOW()),
    ('a3333333-3333-3333-3333-333333333333', 'default', 'PAYMENTS', 'Payments Gateway', TRUE,
     '{"seeded":true}'::jsonb, NOW() - INTERVAL '8 days', NOW())
ON CONFLICT (tenant_id, source_key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3) Channel configurations (Channels page — per-app)
-- ---------------------------------------------------------------------------
INSERT INTO notif.channel_configurations (id, tenant_id, app_id, channel_type, config_json, is_active, created_at)
SELECT v.id, 'default', v.app_id, v.channel_type, v.config_json, TRUE, NOW() - INTERVAL '7 days'
FROM (VALUES
    ('f1111111-1111-1111-1111-111111111101'::uuid,
     'a1111111-1111-1111-1111-111111111111'::uuid,
     'teams',
     '{"webhook_url":"https://example.webhook.office.com/webhookb2/demo"}'::jsonb),
    ('f1111111-1111-1111-1111-111111111102'::uuid,
     'a1111111-1111-1111-1111-111111111111'::uuid,
     'smtp',
     '{"host":"smtp.example.com","port":587,"username":"demo","password":"demo","from":"hr@pulseflow.local","useTls":true}'::jsonb),
    ('f1111111-1111-1111-1111-111111111103'::uuid,
     'a2222222-2222-2222-2222-222222222222'::uuid,
     'telegram',
     '{"bot_token":"demo-bot-token","chat_id":"123456789","parseMode":"Markdown"}'::jsonb),
    ('f1111111-1111-1111-1111-111111111104'::uuid,
     'a3333333-3333-3333-3333-333333333333'::uuid,
     'webhook',
     '{"url":"https://httpbin.org/post","authType":"API_KEY","apiKeyHeader":"X-Api-Key","apiKey":"demo-key"}'::jsonb)
) AS v(id, app_id, channel_type, config_json)
WHERE EXISTS (SELECT 1 FROM notif.integration_sources s WHERE s.id = v.app_id)
  AND NOT EXISTS (SELECT 1 FROM notif.channel_configurations c WHERE c.id = v.id);

-- ---------------------------------------------------------------------------
-- 4) Tenant channel configs (Applications / Platform)
-- ---------------------------------------------------------------------------
INSERT INTO notif.tenant_channel_configs (
    id, tenant_id, channel_type_code, name, config, priority, is_default, is_enabled, created_at, updated_at
)
SELECT v.id, 'default', v.code, v.name, v.config, v.priority, TRUE, TRUE, NOW() - INTERVAL '7 days', NOW()
FROM (VALUES
    ('f2111111-1111-1111-1111-111111111201'::uuid, 'EMAIL', 'Demo SMTP',
     '{"host":"smtp.example.com","port":587,"username":"demo","password":"demo","from":"no-reply@pulseflow.local","useTls":true}'::jsonb, 10::smallint),
    ('f2111111-1111-1111-1111-111111111202'::uuid, 'TEAMS', 'Demo Teams Webhook',
     '{"webhook_url":"https://example.webhook.office.com/webhookb2/demo","enabled":true}'::jsonb, 20::smallint),
    ('f2111111-1111-1111-1111-111111111203'::uuid, 'TELEGRAM', 'Demo Telegram Bot',
     '{"botToken":"demo-bot-token","chatId":"123456789","parseMode":"Markdown","enabled":true}'::jsonb, 30::smallint),
    ('f2111111-1111-1111-1111-111111111204'::uuid, 'WEBHOOK', 'Demo Outbound Webhook',
     '{"url":"https://httpbin.org/post"}'::jsonb, 40::smallint)
) AS v(id, code, name, config, priority)
WHERE EXISTS (SELECT 1 FROM notif.channel_types t WHERE t.code = v.code)
  AND NOT EXISTS (SELECT 1 FROM notif.tenant_channel_configs c WHERE c.id = v.id);

-- ---------------------------------------------------------------------------
-- 5) Field mapping (Applications)
-- ---------------------------------------------------------------------------
INSERT INTO notif.integration_field_mappings (id, integration_source_id, version, mapping, is_active, created_at)
SELECT
    'f3111111-1111-1111-1111-111111111301',
    'a1111111-1111-1111-1111-111111111111',
    1,
    '{"eventType":"eventType","sourceEventId":"sourceEventId","userId":"userId","userEmail":"userEmail","roleName":"roleName","payload":"$payload"}'::jsonb,
    TRUE,
    NOW() - INTERVAL '6 days'
WHERE EXISTS (SELECT 1 FROM notif.integration_sources WHERE id = 'a1111111-1111-1111-1111-111111111111')
  AND NOT EXISTS (SELECT 1 FROM notif.integration_field_mappings WHERE id = 'f3111111-1111-1111-1111-111111111301');

-- ---------------------------------------------------------------------------
-- 6) Notification templates (Template Library)
-- ---------------------------------------------------------------------------
INSERT INTO notif.notification_templates (
    id, tenant_id, integration_source_id, event_type, channel_type_code, locale,
    subject_template, body_template, content_type, template_version, is_active, created_at, updated_at
)
SELECT v.id, 'default', v.source_id, v.event_type, v.channel, 'en',
       v.subject, v.body, 'text', 1, TRUE, NOW() - INTERVAL '5 days', NOW()
FROM (VALUES
    ('b1111111-1111-1111-1111-111111111111'::uuid, NULL::uuid, 'ORDER_CREATED', 'WEBSOCKET',
     'Order {{orderId}}', 'Hi {{userId}}, order {{orderId}} for amount {{amount}} was created.'),
    ('b2222222-2222-2222-2222-222222222222'::uuid, NULL::uuid, 'ORDER_CREATED', 'EMAIL',
     'Order {{orderId}} created', '<p>Order <b>{{orderId}}</b> amount {{amount}} is ready.</p>'),
    ('b3333333-3333-3333-3333-333333333333'::uuid, 'a1111111-1111-1111-1111-111111111111'::uuid, 'LEAVE_APPROVED', 'WEBSOCKET',
     'Leave approved', 'Your leave {{leaveId}} ({{days}} days) was approved.'),
    ('b4444444-4444-4444-4444-444444444444'::uuid, 'a1111111-1111-1111-1111-111111111111'::uuid, 'LEAVE_APPROVED', 'EMAIL',
     'Leave {{leaveId}} approved', '<p>Leave <b>{{leaveId}}</b> for {{days}} day(s) is approved.</p>')
) AS v(id, source_id, event_type, channel, subject, body)
WHERE NOT EXISTS (SELECT 1 FROM notif.notification_templates t WHERE t.id = v.id);

-- ---------------------------------------------------------------------------
-- 7) Routing rules (Applications / Platform)
-- ---------------------------------------------------------------------------
INSERT INTO notif.routing_rules (
    id, tenant_id, integration_source_id, name, event_type, role_name,
    conditions_jsonlogic, channel_type_codes, eval_order, is_active, created_at, updated_at
)
VALUES
    ('f4111111-1111-1111-1111-111111111401', 'default', 'a2222222-2222-2222-2222-222222222222',
     'demo-high-value-orders', 'ORDER_CREATED', 'EMPLOYEE',
     '{">":[{"var":"amount"},1000]}'::jsonb,
     ARRAY['WEBSOCKET','EMAIL'], 10, TRUE, NOW() - INTERVAL '5 days', NOW()),
    ('f4111111-1111-1111-1111-111111111402', 'default', 'a1111111-1111-1111-1111-111111111111',
     'demo-leave-approved', 'LEAVE_APPROVED', 'EMPLOYEE',
     '{}'::jsonb,
     ARRAY['WEBSOCKET','EMAIL','TEAMS'], 20, TRUE, NOW() - INTERVAL '5 days', NOW()),
    ('f4111111-1111-1111-1111-111111111403', 'default', 'a3333333-3333-3333-3333-333333333333',
     'demo-payment-received', 'PAYMENT_RECEIVED', 'EMPLOYEE',
     '{}'::jsonb,
     ARRAY['WEBSOCKET','WEBHOOK'], 30, TRUE, NOW() - INTERVAL '5 days', NOW())
ON CONFLICT (tenant_id, name) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 8) Extra notification_rules (Rules page)
-- ---------------------------------------------------------------------------
INSERT INTO notif.notification_rules (
    id, tenant_id, name, role_name, notification_type, event_type, priority_override,
    conditions, channels, is_active, eval_order, created_at, updated_at, integration_source_id
)
VALUES
    ('f5111111-1111-1111-1111-111111111501', 'default', 'demo-leave-to-employee', 'EMPLOYEE',
     'HR_ACTION', 'LEAVE_APPROVED', 'MEDIUM',
     '{}'::jsonb, ARRAY['WEBSOCKET','EMAIL']::notif.delivery_channel[], TRUE, 30,
     NOW() - INTERVAL '4 days', NOW(), 'a1111111-1111-1111-1111-111111111111'),
    ('f5111111-1111-1111-1111-111111111502', 'default', 'demo-payment-alert', 'EMPLOYEE',
     'SYSTEM', 'PAYMENT_RECEIVED', 'HIGH',
     '{"min_amount":100}'::jsonb, ARRAY['WEBSOCKET','EMAIL']::notif.delivery_channel[], TRUE, 40,
     NOW() - INTERVAL '4 days', NOW(), 'a3333333-3333-3333-3333-333333333333')
ON CONFLICT (tenant_id, name) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 9) Notifications (Dashboard list + stats)
-- ---------------------------------------------------------------------------
INSERT INTO notif.notifications (
    id, tenant_id, title, body, type, priority, status, source_service, source_event_id,
    integration_source_id, event_type, correlation_id, metadata, created_at, updated_at,
    is_deleted, version, sequence_number
)
SELECT v.id, v.tenant_id, v.title, v.body, v.type, v.priority, v.status, v.source_service, v.source_event_id,
       v.integration_source_id, v.event_type, v.correlation_id, v.metadata, v.created_at, v.updated_at,
       v.is_deleted, v.version, v.sequence_number
FROM (VALUES
    ('c1111111-1111-1111-1111-111111111111'::uuid, 'default',
     'Order ORD-1001 created', 'High-value order ORD-1001 for $1,500 is ready for fulfillment.',
     'WORKFLOW'::notif.notification_type, 'CRITICAL'::notif.priority_level, 'ACTIVE'::notif.notification_status,
     'ORDERS', 'demo-evt-order-1001',
     'a2222222-2222-2222-2222-222222222222'::uuid, 'ORDER_CREATED', 'corr-demo-001',
     '{"orderId":"ORD-1001","amount":1500}'::jsonb,
     NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours', FALSE, 1::bigint, 1::bigint),
    ('c2222222-2222-2222-2222-222222222222'::uuid, 'default',
     'Leave L-200 approved', 'Your annual leave request L-200 (3 days) was approved.',
     'HR_ACTION'::notif.notification_type, 'MEDIUM'::notif.priority_level, 'ACTIVE'::notif.notification_status,
     'HRMS', 'demo-evt-leave-200',
     'a1111111-1111-1111-1111-111111111111'::uuid, 'LEAVE_APPROVED', 'corr-demo-002',
     '{"leaveId":"L-200","leaveType":"annual","days":3}'::jsonb,
     NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day', FALSE, 1::bigint, 1::bigint),
    ('c3333333-3333-3333-3333-333333333333'::uuid, 'default',
     'Payment PAY-300 received', 'Payment of $500 was received for invoice INV-300.',
     'SYSTEM'::notif.notification_type, 'HIGH'::notif.priority_level, 'DELIVERED'::notif.notification_status,
     'PAYMENTS', 'demo-evt-pay-300',
     'a3333333-3333-3333-3333-333333333333'::uuid, 'PAYMENT_RECEIVED', 'corr-demo-003',
     '{"invoiceId":"INV-300","amount":500}'::jsonb,
     NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days', FALSE, 1::bigint, 1::bigint),
    ('c4444444-4444-4444-4444-444444444444'::uuid, 'default',
     'Order ORD-1002 created', 'Order ORD-1002 for $220 was created.',
     'WORKFLOW'::notif.notification_type, 'MEDIUM'::notif.priority_level, 'ACTIVE'::notif.notification_status,
     'ORDERS', 'demo-evt-order-1002',
     'a2222222-2222-2222-2222-222222222222'::uuid, 'ORDER_CREATED', 'corr-demo-004',
     '{"orderId":"ORD-1002","amount":220}'::jsonb,
     NOW() - INTERVAL '5 hours', NOW() - INTERVAL '5 hours', FALSE, 1::bigint, 2::bigint),
    ('c5555555-5555-5555-5555-555555555555'::uuid, 'default',
     'Security alert: new device', 'A new device signed in to your PulseFlow account.',
     'SECURITY'::notif.notification_type, 'CRITICAL'::notif.priority_level, 'ACTIVE'::notif.notification_status,
     'SYSTEM', 'demo-evt-sec-001',
     NULL::uuid, 'SECURITY_ALERT', 'corr-demo-005',
     '{"device":"Chrome/Windows","ip":"203.0.113.10"}'::jsonb,
     NOW() - INTERVAL '30 minutes', NOW() - INTERVAL '30 minutes', FALSE, 1::bigint, 1::bigint),
    ('c6666666-6666-6666-6666-666666666666'::uuid, 'default',
     'Reminder: training due', 'Complete mandatory compliance training by Friday.',
     'REMINDER'::notif.notification_type, 'LOW'::notif.priority_level, 'ACTIVE'::notif.notification_status,
     'HRMS', 'demo-evt-train-001',
     'a1111111-1111-1111-1111-111111111111'::uuid, 'TRAINING_ASSIGNED', 'corr-demo-006',
     '{"course":"Compliance 101"}'::jsonb,
     NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days', FALSE, 1::bigint, 1::bigint),
    ('c7777777-7777-7777-7777-777777777777'::uuid, 'default',
     'Announcement: maintenance window', 'Scheduled maintenance Sunday 02:00-04:00 UTC.',
     'ANNOUNCEMENT'::notif.notification_type, 'LOW'::notif.priority_level, 'ACTIVE'::notif.notification_status,
     'SYSTEM', 'demo-evt-announce-001',
     NULL::uuid, 'MAINTENANCE', 'corr-demo-007',
     '{"window":"Sun 02:00-04:00 UTC"}'::jsonb,
     NOW() - INTERVAL '6 hours', NOW() - INTERVAL '6 hours', FALSE, 1::bigint, 1::bigint),
    ('c8888888-8888-8888-8888-888888888888'::uuid, 'default',
     'Leave L-201 rejected', 'Leave request L-201 was rejected: insufficient balance.',
     'HR_ACTION'::notif.notification_type, 'HIGH'::notif.priority_level, 'ACTIVE'::notif.notification_status,
     'HRMS', 'demo-evt-leave-201',
     'a1111111-1111-1111-1111-111111111111'::uuid, 'LEAVE_REJECTED', 'corr-demo-008',
     '{"leaveId":"L-201","reason":"insufficient balance"}'::jsonb,
     NOW() - INTERVAL '12 hours', NOW() - INTERVAL '12 hours', FALSE, 1::bigint, 1::bigint)
) AS v(
    id, tenant_id, title, body, type, priority, status, source_service, source_event_id,
    integration_source_id, event_type, correlation_id, metadata, created_at, updated_at,
    is_deleted, version, sequence_number
)
WHERE NOT EXISTS (SELECT 1 FROM notif.notifications n WHERE n.id = v.id);

-- ---------------------------------------------------------------------------
-- 10) Recipients (inbox for demo-user + one admin row)
-- ---------------------------------------------------------------------------
INSERT INTO notif.notification_recipients (
    id, tenant_id, notification_id, user_id, user_email, role_name,
    is_read, is_acknowledged, created_at, read_at, acknowledged_at
)
SELECT v.id, 'default', v.notification_id, v.user_id, v.email, v.role_name,
       v.is_read, v.is_ack, v.created_at, v.read_at, v.ack_at
FROM (VALUES
    -- UNREAD critical order
    ('d1111111-1111-1111-1111-111111111111'::uuid, 'c1111111-1111-1111-1111-111111111111'::uuid,
     'demo-user', 'demo-user@example.com', 'EMPLOYEE', FALSE, FALSE,
     NOW() - INTERVAL '2 hours', NULL::timestamptz, NULL::timestamptz),
    -- READ leave
    ('d2222222-2222-2222-2222-222222222222'::uuid, 'c2222222-2222-2222-2222-222222222222'::uuid,
     'demo-user', 'demo-user@example.com', 'EMPLOYEE', TRUE, FALSE,
     NOW() - INTERVAL '1 day', NOW() - INTERVAL '20 hours', NULL::timestamptz),
    -- ACKNOWLEDGED payment
    ('d3333333-3333-3333-3333-333333333333'::uuid, 'c3333333-3333-3333-3333-333333333333'::uuid,
     'demo-user', 'demo-user@example.com', 'EMPLOYEE', TRUE, TRUE,
     NOW() - INTERVAL '3 days', NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days'),
    -- UNREAD medium order
    ('d4444444-4444-4444-4444-444444444444'::uuid, 'c4444444-4444-4444-4444-444444444444'::uuid,
     'demo-user', 'demo-user@example.com', 'EMPLOYEE', FALSE, FALSE,
     NOW() - INTERVAL '5 hours', NULL::timestamptz, NULL::timestamptz),
    -- UNREAD critical security
    ('d5555555-5555-5555-5555-555555555555'::uuid, 'c5555555-5555-5555-5555-555555555555'::uuid,
     'demo-user', 'demo-user@example.com', 'EMPLOYEE', FALSE, FALSE,
     NOW() - INTERVAL '30 minutes', NULL::timestamptz, NULL::timestamptz),
    -- READ reminder
    ('d6666666-6666-6666-6666-666666666666'::uuid, 'c6666666-6666-6666-6666-666666666666'::uuid,
     'demo-user', 'demo-user@example.com', 'EMPLOYEE', TRUE, FALSE,
     NOW() - INTERVAL '2 days', NOW() - INTERVAL '1 day', NULL::timestamptz),
    -- UNREAD announcement
    ('d7777777-7777-7777-7777-777777777777'::uuid, 'c7777777-7777-7777-7777-777777777777'::uuid,
     'demo-user', 'demo-user@example.com', 'EMPLOYEE', FALSE, FALSE,
     NOW() - INTERVAL '6 hours', NULL::timestamptz, NULL::timestamptz),
    -- READ leave rejected
    ('d8888888-8888-8888-8888-888888888888'::uuid, 'c8888888-8888-8888-8888-888888888888'::uuid,
     'demo-user', 'demo-user@example.com', 'EMPLOYEE', TRUE, FALSE,
     NOW() - INTERVAL '12 hours', NOW() - INTERVAL '10 hours', NULL::timestamptz),
    -- Admin also sees critical order
    ('d9011111-1111-1111-1111-111111111901'::uuid, 'c1111111-1111-1111-1111-111111111111'::uuid,
     'pulseflow-admin', 'pulseflow-admin@local.dev', 'ADMIN', FALSE, FALSE,
     NOW() - INTERVAL '2 hours', NULL::timestamptz, NULL::timestamptz)
) AS v(id, notification_id, user_id, email, role_name, is_read, is_ack, created_at, read_at, ack_at)
WHERE EXISTS (SELECT 1 FROM notif.notifications n WHERE n.id = v.notification_id)
  AND NOT EXISTS (SELECT 1 FROM notif.notification_recipients r WHERE r.id = v.id);

-- ---------------------------------------------------------------------------
-- 11) Delivery logs (Delivery Logs page)
-- ---------------------------------------------------------------------------
INSERT INTO notif.notification_delivery_log (
    id, tenant_id, notification_id, recipient_id, template_id, channel, status,
    attempt_count, max_attempts, error_message, created_at, delivered_at
)
SELECT v.id, 'default', v.notification_id, v.recipient_id, v.template_id, v.channel, v.status,
       v.attempt_count, v.max_attempts, v.error_message, v.created_at, v.delivered_at
FROM (VALUES
    ('e1111111-1111-1111-1111-111111111101'::uuid,
     'c1111111-1111-1111-1111-111111111111'::uuid, 'd1111111-1111-1111-1111-111111111111'::uuid,
     'b1111111-1111-1111-1111-111111111111'::uuid,
     'WEBSOCKET'::notif.delivery_channel, 'DELIVERED'::notif.delivery_status,
     1::smallint, 4::smallint, NULL::text,
     NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours' + INTERVAL '2 seconds'),
    ('e1111111-1111-1111-1111-111111111102'::uuid,
     'c1111111-1111-1111-1111-111111111111'::uuid, 'd1111111-1111-1111-1111-111111111111'::uuid,
     'b2222222-2222-2222-2222-222222222222'::uuid,
     'EMAIL'::notif.delivery_channel, 'FAILED'::notif.delivery_status,
     2::smallint, 4::smallint, 'SMTP connection refused (demo)',
     NOW() - INTERVAL '2 hours', NULL::timestamptz),
    ('e1111111-1111-1111-1111-111111111103'::uuid,
     'c2222222-2222-2222-2222-222222222222'::uuid, 'd2222222-2222-2222-2222-222222222222'::uuid,
     'b3333333-3333-3333-3333-333333333333'::uuid,
     'WEBSOCKET'::notif.delivery_channel, 'DELIVERED'::notif.delivery_status,
     1::smallint, 4::smallint, NULL::text,
     NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day' + INTERVAL '1 second'),
    ('e1111111-1111-1111-1111-111111111104'::uuid,
     'c2222222-2222-2222-2222-222222222222'::uuid, 'd2222222-2222-2222-2222-222222222222'::uuid,
     'b4444444-4444-4444-4444-444444444444'::uuid,
     'EMAIL'::notif.delivery_channel, 'DELIVERED'::notif.delivery_status,
     1::smallint, 4::smallint, NULL::text,
     NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day' + INTERVAL '3 seconds'),
    ('e1111111-1111-1111-1111-111111111105'::uuid,
     'c3333333-3333-3333-3333-333333333333'::uuid, 'd3333333-3333-3333-3333-333333333333'::uuid,
     NULL::uuid,
     'WEBHOOK'::notif.delivery_channel, 'DELIVERED'::notif.delivery_status,
     1::smallint, 4::smallint, NULL::text,
     NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days' + INTERVAL '5 seconds'),
    ('e1111111-1111-1111-1111-111111111106'::uuid,
     'c4444444-4444-4444-4444-444444444444'::uuid, 'd4444444-4444-4444-4444-444444444444'::uuid,
     NULL::uuid,
     'TEAMS'::notif.delivery_channel, 'SKIPPED'::notif.delivery_status,
     0::smallint, 4::smallint, 'Skipped: Teams webhook not reachable (demo)',
     NOW() - INTERVAL '5 hours', NULL::timestamptz),
    ('e1111111-1111-1111-1111-111111111107'::uuid,
     'c5555555-5555-5555-5555-555555555555'::uuid, 'd5555555-5555-5555-5555-555555555555'::uuid,
     NULL::uuid,
     'WEBSOCKET'::notif.delivery_channel, 'PENDING'::notif.delivery_status,
     0::smallint, 4::smallint, NULL::text,
     NOW() - INTERVAL '30 minutes', NULL::timestamptz),
    ('e1111111-1111-1111-1111-111111111108'::uuid,
     'c8888888-8888-8888-8888-888888888888'::uuid, 'd8888888-8888-8888-8888-888888888888'::uuid,
     NULL::uuid,
     'EMAIL'::notif.delivery_channel, 'DEAD_LETTERED'::notif.delivery_status,
     4::smallint, 4::smallint, 'Max attempts exhausted (demo)',
     NOW() - INTERVAL '12 hours', NULL::timestamptz)
) AS v(id, notification_id, recipient_id, template_id, channel, status, attempt_count, max_attempts, error_message, created_at, delivered_at)
WHERE EXISTS (SELECT 1 FROM notif.notification_recipients r WHERE r.id = v.recipient_id)
  AND NOT EXISTS (SELECT 1 FROM notif.notification_delivery_log d WHERE d.id = v.id);

-- ---------------------------------------------------------------------------
-- 12) Audit logs (Audit page)
-- ---------------------------------------------------------------------------
INSERT INTO notif.notification_audit_log (
    tenant_id, notification_id, recipient_id, action, actor_user_id, metadata, occurred_at, correlation_id
)
SELECT v.tenant_id, v.notification_id, v.recipient_id, v.action, v.actor, v.metadata, v.occurred_at, v.correlation_id
FROM (VALUES
    ('default', 'c1111111-1111-1111-1111-111111111111'::uuid, 'd1111111-1111-1111-1111-111111111111'::uuid,
     'CREATED', 'orders-service', '{"source":"demo-seed"}'::jsonb, NOW() - INTERVAL '2 hours', 'corr-demo-001'),
    ('default', 'c1111111-1111-1111-1111-111111111111'::uuid, 'd1111111-1111-1111-1111-111111111111'::uuid,
     'DELIVERED', 'system', '{"channel":"WEBSOCKET"}'::jsonb, NOW() - INTERVAL '2 hours' + INTERVAL '2 seconds', 'corr-demo-001'),
    ('default', 'c2222222-2222-2222-2222-222222222222'::uuid, 'd2222222-2222-2222-2222-222222222222'::uuid,
     'CREATED', 'hrms-service', '{"source":"demo-seed"}'::jsonb, NOW() - INTERVAL '1 day', 'corr-demo-002'),
    ('default', 'c2222222-2222-2222-2222-222222222222'::uuid, 'd2222222-2222-2222-2222-222222222222'::uuid,
     'READ', 'demo-user', '{}'::jsonb, NOW() - INTERVAL '20 hours', 'corr-demo-002'),
    ('default', 'c3333333-3333-3333-3333-333333333333'::uuid, 'd3333333-3333-3333-3333-333333333333'::uuid,
     'CREATED', 'payments-service', '{"source":"demo-seed"}'::jsonb, NOW() - INTERVAL '3 days', 'corr-demo-003'),
    ('default', 'c3333333-3333-3333-3333-333333333333'::uuid, 'd3333333-3333-3333-3333-333333333333'::uuid,
     'ACKNOWLEDGED', 'demo-user', '{}'::jsonb, NOW() - INTERVAL '2 days', 'corr-demo-003'),
    ('default', 'c5555555-5555-5555-5555-555555555555'::uuid, 'd5555555-5555-5555-5555-555555555555'::uuid,
     'CREATED', 'system', '{"source":"demo-seed"}'::jsonb, NOW() - INTERVAL '30 minutes', 'corr-demo-005'),
    ('default', 'c8888888-8888-8888-8888-888888888888'::uuid, 'd8888888-8888-8888-8888-888888888888'::uuid,
     'CREATED', 'hrms-service', '{"source":"demo-seed"}'::jsonb, NOW() - INTERVAL '12 hours', 'corr-demo-008'),
    ('default', 'c8888888-8888-8888-8888-888888888888'::uuid, 'd8888888-8888-8888-8888-888888888888'::uuid,
     'DEAD_LETTERED', 'system', '{"channel":"EMAIL"}'::jsonb, NOW() - INTERVAL '11 hours', 'corr-demo-008')
) AS v(tenant_id, notification_id, recipient_id, action, actor, metadata, occurred_at, correlation_id)
WHERE EXISTS (SELECT 1 FROM notif.notifications n WHERE n.id = v.notification_id)
  AND NOT EXISTS (
      SELECT 1 FROM notif.notification_audit_log a
      WHERE a.tenant_id = v.tenant_id
        AND a.notification_id = v.notification_id
        AND a.action = v.action
        AND a.correlation_id = v.correlation_id
  );
