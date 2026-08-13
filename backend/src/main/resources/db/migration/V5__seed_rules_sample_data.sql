INSERT INTO notif.notification_rules
(tenant_id, name, role_name, notification_type, priority_override, channels, eval_order)
VALUES
('default','admin-all-notifications','ADMIN',NULL,NULL,ARRAY['WEBSOCKET','EMAIL','PUSH']::notif.delivery_channel[],10),
('default','employee-order-created','EMPLOYEE','WORKFLOW','HIGH',ARRAY['WEBSOCKET','EMAIL']::notif.delivery_channel[],20)
ON CONFLICT (tenant_id, name) DO NOTHING;

INSERT INTO notif.notifications
(tenant_id, title, body, type, priority, source_service, source_event_id, metadata)
VALUES
('default','Welcome Notification','Notification module initialized','SYSTEM','LOW','boot','seed-001','{}')
ON CONFLICT (tenant_id, source_service, source_event_id) DO NOTHING;
