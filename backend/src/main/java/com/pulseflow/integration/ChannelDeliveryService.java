package com.pulseflow.integration;

import com.pulseflow.adapter.channel.ChannelSenderRegistry;
import com.pulseflow.adapter.channel.TenantChannelConfigResolver;
import com.pulseflow.domain.entity.Notification;
import com.pulseflow.domain.entity.NotificationDeliveryLog;
import com.pulseflow.domain.entity.NotificationRecipient;
import com.pulseflow.domain.enums.DeliveryChannel;
import com.pulseflow.domain.enums.DeliveryStatus;
import com.pulseflow.domain.port.ChannelSender;
import com.pulseflow.repository.NotificationDeliveryLogRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ChannelDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(ChannelDeliveryService.class);

    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final ChannelSenderRegistry channelSenderRegistry;
    private final TenantChannelConfigResolver tenantChannelConfigResolver;

    public ChannelDeliveryService(
            NotificationDeliveryLogRepository deliveryLogRepository,
            ChannelSenderRegistry channelSenderRegistry,
            TenantChannelConfigResolver tenantChannelConfigResolver) {
        this.deliveryLogRepository = deliveryLogRepository;
        this.channelSenderRegistry = channelSenderRegistry;
        this.tenantChannelConfigResolver = tenantChannelConfigResolver;
    }

    public void deliver(
            DeliveryChannel channel,
            Notification notification,
            NotificationRecipient recipient,
            DeliveryStatus initialStatus,
            short attemptCount,
            short maxAttempts,
            String renderedSubject,
            String renderedBody,
            UUID templateId,
            UUID appId,
            UUID tenantChannelConfigId) {
        var logRow = new NotificationDeliveryLog();
        logRow.setId(UUID.randomUUID());
        logRow.setTenantId(notification.getTenantId());
        logRow.setNotificationId(notification.getId());
        logRow.setRecipientId(recipient.getId());
        logRow.setTemplateId(templateId);
        logRow.setChannel(channel);
        logRow.setStatus(initialStatus);
        logRow.setAttemptCount(attemptCount);
        logRow.setMaxAttempts(maxAttempts);
        logRow.setCreatedAt(OffsetDateTime.now());

        var context = new ChannelSender.DeliveryContext(
                notification.getTenantId(),
                notification,
                recipient,
                Map.of(),
                tenantChannelConfigId,
                renderedSubject,
                renderedBody);

        try {
            if (channel == DeliveryChannel.WEBSOCKET) {
                channelSenderRegistry.require(channel.name()).send(context);
                logRow.setStatus(DeliveryStatus.DELIVERED);
                logRow.setDeliveredAt(OffsetDateTime.now());
                deliveryLogRepository.save(logRow);
                return;
            }
            if (channel == DeliveryChannel.PUSH
                    || channel == DeliveryChannel.SSE
                    || channel == DeliveryChannel.POLLING) {
                logRow.setStatus(DeliveryStatus.FAILED);
                logRow.setErrorMessage("Channel " + channel.name() + " is not yet implemented");
                deliveryLogRepository.save(logRow);
                return;
            }

            Map<String, Object> channelConfig = tenantChannelConfigResolver.resolve(
                    notification.getTenantId(), appId, channel.name(), tenantChannelConfigId);
            if (channelConfig.isEmpty()) {
                logRow.setStatus(DeliveryStatus.FAILED);
                logRow.setErrorMessage("No active channel configuration found for channel " + channel.name());
                deliveryLogRepository.save(logRow);
                throw new IllegalStateException(logRow.getErrorMessage());
            }

            context = new ChannelSender.DeliveryContext(
                    notification.getTenantId(),
                    notification,
                    recipient,
                    channelConfig,
                    tenantChannelConfigId,
                    renderedSubject,
                    renderedBody);
            channelSenderRegistry.require(channel.name()).send(context);
            logRow.setStatus(DeliveryStatus.DELIVERED);
            logRow.setDeliveredAt(OffsetDateTime.now());
        } catch (RuntimeException ex) {
            log.error("Delivery failed for channel {} / recipient '{}': {}", channel, recipient.getUserId(), ex.getMessage(), ex);
            logRow.setStatus(isSkipped(ex.getMessage()) ? DeliveryStatus.SKIPPED : DeliveryStatus.FAILED);
            logRow.setErrorMessage(ex.getMessage());
            deliveryLogRepository.save(logRow);
            // "Skipped:" indicates a non-retryable configuration/input issue.
            // Record the skip and stop so it doesn't keep retrying and eventually DLQ.
            if (isSkipped(ex.getMessage())) {
                return;
            }
            throw new IllegalStateException(ex.getMessage(), ex);
        } catch (Exception ex) {
            log.error("Delivery failed for channel {} / recipient '{}': {}", channel, recipient.getUserId(), ex.getMessage(), ex);
            logRow.setStatus(isSkipped(ex.getMessage()) ? DeliveryStatus.SKIPPED : DeliveryStatus.FAILED);
            logRow.setErrorMessage(ex.getMessage());
            deliveryLogRepository.save(logRow);
            if (isSkipped(ex.getMessage())) {
                return;
            }
            throw new IllegalStateException(ex.getMessage(), ex);
        }
        deliveryLogRepository.save(logRow);
    }

    private static boolean isSkipped(String message) {
        return message != null && message.startsWith("Skipped:");
    }
}
