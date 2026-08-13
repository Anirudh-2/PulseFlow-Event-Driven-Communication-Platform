package com.pulseflow.messaging;

import com.pulseflow.domain.enums.DeliveryChannel;
import com.pulseflow.domain.enums.DeliveryStatus;
import com.pulseflow.domain.enums.NotificationStatus;
import com.pulseflow.domain.entity.NotificationDeliveryLog;
import com.pulseflow.domain.entity.NotificationFailure;
import com.pulseflow.domain.entity.NotificationTemplateEntity;
import com.pulseflow.config.DeliveryProperties;
import com.pulseflow.metrics.DeliveryMetrics;
import com.pulseflow.repository.NotificationDeliveryLogRepository;
import com.pulseflow.repository.NotificationFailureRepository;
import com.pulseflow.repository.NotificationRecipientRepository;
import com.pulseflow.repository.NotificationRepository;
import com.pulseflow.service.NotificationDeliveryExecutor;
import com.pulseflow.service.TemplateRenderService;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DeliveryJobListener {
    private static final Logger log = LoggerFactory.getLogger(DeliveryJobListener.class);

    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final NotificationFailureRepository notificationFailureRepository;
    private final NotificationDeliveryExecutor deliveryExecutor;
    private final DeliveryJobPublisher deliveryJobPublisher;
    private final TemplateRenderService templateRenderService;
    private final DeliveryProperties deliveryProperties;
    private final DeliveryMetrics deliveryMetrics;

    public DeliveryJobListener(
            NotificationRepository notificationRepository,
            NotificationRecipientRepository recipientRepository,
            NotificationDeliveryLogRepository deliveryLogRepository,
            NotificationFailureRepository notificationFailureRepository,
            NotificationDeliveryExecutor deliveryExecutor,
            DeliveryJobPublisher deliveryJobPublisher,
            TemplateRenderService templateRenderService,
            DeliveryProperties deliveryProperties,
            DeliveryMetrics deliveryMetrics) {
        this.notificationRepository = notificationRepository;
        this.recipientRepository = recipientRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.notificationFailureRepository = notificationFailureRepository;
        this.deliveryExecutor = deliveryExecutor;
        this.deliveryJobPublisher = deliveryJobPublisher;
        this.templateRenderService = templateRenderService;
        this.deliveryProperties = deliveryProperties;
        this.deliveryMetrics = deliveryMetrics;
    }

    @Transactional
    @RabbitListener(queues = "${app.messaging.delivery-queue}")
    public void handleDelivery(DeliveryJobMessage job) {
        String correlationId = job.correlationId();
        if (correlationId != null && !correlationId.isBlank()) {
            MDC.put("correlationId", correlationId);
        }
        if (job.tenantId() != null) {
            MDC.put("tenantId", job.tenantId());
        }
        if (job.notificationId() != null) {
            MDC.put("notificationId", job.notificationId().toString());
        }
        try {
            handleDeliveryInternal(job);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("tenantId");
            MDC.remove("notificationId");
        }
    }

    private void handleDeliveryInternal(DeliveryJobMessage job) {
        var notification = notificationRepository.findById(job.notificationId()).orElse(null);
        if (notification == null) {
            log.warn("Delivery job references missing notification {}", job.notificationId());
            return;
        }
        var recipient = recipientRepository.findById(job.recipientId()).orElse(null);
        if (recipient == null) {
            log.warn("Delivery job references missing recipient {}", job.recipientId());
            return;
        }
        DeliveryChannel channel = DeliveryChannel.valueOf(job.deliveryChannel());
        NotificationTemplateEntity resolvedTemplate = templateRenderService.resolveTemplate(
                job.tenantId(),
                notification.getEventType(),
                channel.name(),
                job.locale(),
                job.integrationSourceId());
        if (channel == DeliveryChannel.EMAIL) {
            if (resolvedTemplate == null) {
                log.info(
                        "Skipping EMAIL delivery for notification {}: no EMAIL template for eventType {}",
                        notification.getId(),
                        notification.getEventType());
                NotificationDeliveryLog skipLog = new NotificationDeliveryLog();
                skipLog.setId(UUID.randomUUID());
                skipLog.setTenantId(notification.getTenantId());
                skipLog.setNotificationId(notification.getId());
                skipLog.setRecipientId(recipient.getId());
                skipLog.setChannel(channel);
                skipLog.setStatus(DeliveryStatus.SKIPPED);
                skipLog.setAttemptCount((short) 0);
                skipLog.setMaxAttempts((short) deliveryProperties.getMaxAttempts());
                skipLog.setErrorMessage(
                        "Skipped: no EMAIL template found for eventType: "
                                + notification.getEventType()
                                + ", channel: "
                                + channel.name());
                skipLog.setCreatedAt(OffsetDateTime.now());
                deliveryLogRepository.save(skipLog);
                deliveryMetrics.skipped(channel.name());
                return;
            }
        }
        Map<String, Object> ctx = NotificationDeliveryExecutor.buildTemplateContext(notification, recipient);
        var rendered = templateRenderService.render(
                job.tenantId(),
                notification.getEventType(),
                channel.name(),
                job.locale(),
                job.integrationSourceId(),
                ctx);
        try {
            deliveryExecutor.deliverRendered(
                    notification,
                    recipient,
                    channel,
                    rendered.subject(),
                    rendered.body(),
                    resolvedTemplate != null ? resolvedTemplate.getId() : null,
                    job.integrationSourceId(),
                    job.tenantChannelConfigId(),
                    job.retryAttempt());

            deliveryMetrics.success(channel.name());
            boolean allDelivered = deliveryLogRepository.findByNotificationId(notification.getId()).stream()
                    .allMatch(logRow -> logRow.getStatus() == DeliveryStatus.DELIVERED);
            if (allDelivered) {
                notification.setStatus(NotificationStatus.DELIVERED);
                notification.setUpdatedAt(OffsetDateTime.now());
                notificationRepository.save(notification);
                log.info("Notification {} fully delivered across all channels", notification.getId());
            }
        } catch (RuntimeException ex) {
            deliveryMetrics.failure(channel.name());
            int nextAttempt = job.retryAttempt() + 1;
            int maxAttempts = deliveryProperties.getMaxAttempts();
            DeliveryJobMessage retryJob = new DeliveryJobMessage(
                    job.jobId(),
                    job.tenantId(),
                    job.notificationId(),
                    job.recipientId(),
                    job.deliveryChannel(),
                    job.tenantChannelConfigId(),
                    job.locale(),
                    job.integrationSourceId(),
                    nextAttempt,
                    job.correlationId());
            if (nextAttempt >= maxAttempts) {
                NotificationDeliveryLog deadLetterLog = new NotificationDeliveryLog();
                deadLetterLog.setId(java.util.UUID.randomUUID());
                deadLetterLog.setTenantId(notification.getTenantId());
                deadLetterLog.setNotificationId(notification.getId());
                deadLetterLog.setRecipientId(recipient.getId());
                deadLetterLog.setChannel(channel);
                deadLetterLog.setStatus(DeliveryStatus.DEAD_LETTERED);
                deadLetterLog.setAttemptCount((short) (job.retryAttempt() + 1));
                deadLetterLog.setMaxAttempts((short) deliveryProperties.getMaxAttempts());
                deadLetterLog.setErrorMessage(ex.getMessage());
                deadLetterLog.setCreatedAt(java.time.OffsetDateTime.now());
                deliveryLogRepository.save(deadLetterLog);

                NotificationFailure failure = new NotificationFailure();
                failure.setId(java.util.UUID.randomUUID());
                failure.setTenantId(notification.getTenantId());
                failure.setNotificationId(notification.getId());
                failure.setRecipientId(recipient.getId());
                failure.setChannel(channel);
                failure.setFailureReason(ex.getMessage());
                failure.setRawEventPayload(Map.of());
                failure.setIsResolved(false);
                failure.setOccurredAt(java.time.OffsetDateTime.now());
                try {
                    notificationFailureRepository.save(failure);
                } catch (Exception failureRecordEx) {
                    log.error(
                            "Could not record notification failure for notificationId {}: {}",
                            notification.getId(),
                            failureRecordEx.getMessage());
                }

                notification.setStatus(NotificationStatus.DEAD_LETTERED);
                notification.setUpdatedAt(java.time.OffsetDateTime.now());
                notificationRepository.save(notification);
                deliveryMetrics.deadLettered(channel.name());
                return;
            }
            deliveryMetrics.retry(channel.name());
            if (nextAttempt == 1) {
                deliveryJobPublisher.publishRetry5s(retryJob);
                return;
            }
            if (nextAttempt == 2) {
                deliveryJobPublisher.publishRetry30s(retryJob);
                return;
            }
            if (nextAttempt == 3) {
                deliveryJobPublisher.publishRetry5m(retryJob);
                return;
            }
        }
        log.debug("Processed delivery job {} channel {}", job.jobId(), channel);
    }
}
