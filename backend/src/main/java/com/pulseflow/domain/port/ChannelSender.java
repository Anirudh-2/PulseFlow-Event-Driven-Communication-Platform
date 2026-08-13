package com.pulseflow.domain.port;

import com.pulseflow.domain.entity.Notification;
import com.pulseflow.domain.entity.NotificationRecipient;
import java.util.Map;
import java.util.UUID;

/**
 * Outbound channel adapter (strategy). Implementations are registered by {@link com.pulseflow.adapter.channel.ChannelSenderRegistry}.
 */
public interface ChannelSender {

    /** Uppercase channel code matching {@code channel_types.code} and {@link com.pulseflow.domain.enums.DeliveryChannel}. */
    String channelTypeCode();

    void send(DeliveryContext context) throws ChannelException;

    record DeliveryContext(
            String tenantId,
            Notification notification,
            NotificationRecipient recipient,
            Map<String, Object> channelConfig,
            UUID tenantChannelConfigId,
            String renderedSubject,
            String renderedBody) {}
}
