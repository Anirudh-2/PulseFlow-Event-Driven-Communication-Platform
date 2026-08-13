CREATE OR REPLACE FUNCTION notif.fn_set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := NOW();
    NEW.version := OLD.version + 1;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_notifications_updated_at ON notif.notifications;
CREATE TRIGGER trg_notifications_updated_at
BEFORE UPDATE ON notif.notifications
FOR EACH ROW EXECUTE FUNCTION notif.fn_set_updated_at();

DROP TRIGGER IF EXISTS trg_rules_updated_at ON notif.notification_rules;
CREATE TRIGGER trg_rules_updated_at
BEFORE UPDATE ON notif.notification_rules
FOR EACH ROW EXECUTE FUNCTION notif.fn_set_updated_at();

CREATE OR REPLACE VIEW notif.vw_delivery_health AS
SELECT tenant_id, channel, status, COUNT(*) AS count
FROM notif.notification_delivery_log
GROUP BY tenant_id, channel, status;
