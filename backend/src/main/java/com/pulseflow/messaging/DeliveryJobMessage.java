package com.pulseflow.messaging;

import java.util.UUID;

public record DeliveryJobMessage(
        UUID jobId,
        String tenantId,
        UUID notificationId,
        UUID recipientId,
        String deliveryChannel,
        UUID tenantChannelConfigId,
        String locale,
        UUID integrationSourceId,
        int retryAttempt,
        String correlationId) {

    /** Backward-compatible constructor used by older call sites / tests. */
    public DeliveryJobMessage(
            UUID jobId,
            String tenantId,
            UUID notificationId,
            UUID recipientId,
            String deliveryChannel,
            UUID tenantChannelConfigId,
            String locale,
            UUID integrationSourceId,
            int retryAttempt) {
        this(
                jobId,
                tenantId,
                notificationId,
                recipientId,
                deliveryChannel,
                tenantChannelConfigId,
                locale,
                integrationSourceId,
                retryAttempt,
                null);
    }
}
