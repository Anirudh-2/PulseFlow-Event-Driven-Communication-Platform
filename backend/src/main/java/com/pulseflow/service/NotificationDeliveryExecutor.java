package com.pulseflow.service;

import com.pulseflow.config.DeliveryProperties;
import com.pulseflow.domain.entity.Notification;
import com.pulseflow.domain.entity.NotificationRecipient;
import com.pulseflow.domain.enums.DeliveryChannel;
import com.pulseflow.domain.enums.DeliveryStatus;
import com.pulseflow.integration.ChannelDeliveryService;
import com.pulseflow.messaging.DeliveryJobPublisher;
import com.pulseflow.messaging.DeliveryJobMessage;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class NotificationDeliveryExecutor {

    private final ChannelDeliveryService channelDeliveryService;
    private final DeliveryJobPublisher deliveryJobPublisher;
    private final DeliveryProperties deliveryProperties;

    public NotificationDeliveryExecutor(
            ChannelDeliveryService channelDeliveryService,
            DeliveryJobPublisher deliveryJobPublisher,
            DeliveryProperties deliveryProperties) {
        this.channelDeliveryService = channelDeliveryService;
        this.deliveryJobPublisher = deliveryJobPublisher;
        this.deliveryProperties = deliveryProperties;
        // Delivery is always executed through the configured RabbitMQ delivery queue.
    }

    public void dispatchDeliveries(
            Notification notification,
            NotificationRecipient recipient,
            java.util.List<DeliveryChannel> channels,
            String locale,
            UUID integrationSourceId) {
        String correlationId = notification.getCorrelationId();
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get("correlationId");
        }
        for (DeliveryChannel channel : channels) {
            deliveryJobPublisher.publish(new DeliveryJobMessage(
                    UUID.randomUUID(),
                    notification.getTenantId(),
                    notification.getId(),
                    recipient.getId(),
                    channel.name(),
                    null,
                    locale != null ? locale : deliveryProperties.getDefaultLocale(),
                    integrationSourceId,
                    0,
                    correlationId));
        }
    }

    public void deliverRendered(
            Notification notification,
            NotificationRecipient recipient,
            DeliveryChannel channel,
            String renderedSubject,
            String renderedBody,
            UUID templateId,
            UUID integrationSourceId,
            UUID tenantChannelConfigId,
            int retryAttempt) {
        channelDeliveryService.deliver(
                channel,
                notification,
                recipient,
                DeliveryStatus.PENDING,
                (short) (retryAttempt + 1),
                (short) deliveryProperties.getMaxAttempts(),
                renderedSubject,
                renderedBody,
                templateId,
                integrationSourceId,
                tenantChannelConfigId);
    }

    public static Map<String, Object> buildTemplateContext(Notification notification, NotificationRecipient recipient) {
        Map<String, Object> ctx = new HashMap<>();
        if (notification.getMetadata() != null) {
            ctx.putAll(notification.getMetadata());
        }
        ctx.put("userId", recipient.getUserId());
        ctx.put("userEmail", recipient.getUserEmail());
        ctx.put("roleName", recipient.getRoleName());
        ctx.put("sourceService", notification.getSourceService());
        ctx.put("eventType", notification.getEventType());
        // title/body are intentionally not used for template lookup; templates should rely on event metadata.
        return ctx;
    }
}
