ALTER TABLE notif.channel_configurations
    DROP CONSTRAINT IF EXISTS chk_channel_configurations_channel_type;

ALTER TABLE notif.channel_configurations
    ADD CONSTRAINT chk_channel_configurations_channel_type
        CHECK (channel_type IN ('teams', 'whatsapp', 'telegram', 'smtp', 'webhook', 'websocket'));
