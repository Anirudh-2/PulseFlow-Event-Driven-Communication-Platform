package com.pulseflow.service.impl;

import com.pulseflow.domain.entity.Notification;
import com.pulseflow.domain.entity.NotificationAuditLog;
import com.pulseflow.domain.entity.NotificationRecipient;
import com.pulseflow.domain.enums.NotificationStatus;
import com.pulseflow.domain.enums.NotificationType;
import com.pulseflow.domain.enums.PriorityLevel;
import com.pulseflow.config.DeliveryProperties;
import com.pulseflow.dto.CreateEventRequest;
import com.pulseflow.dto.NotificationResponse;
import com.pulseflow.messaging.NotificationEventPublisher;
import com.pulseflow.repository.IntegrationSourceRepository;
import com.pulseflow.repository.NotificationAuditLogRepository;
import com.pulseflow.repository.NotificationRecipientRepository;
import com.pulseflow.repository.NotificationRepository;
import com.pulseflow.service.ChannelResolutionService;
import com.pulseflow.service.NotificationDeliveryExecutor;
import com.pulseflow.service.NotificationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationAuditLogRepository auditRepository;
    private final ChannelResolutionService channelResolutionService;
    private final NotificationDeliveryExecutor notificationDeliveryExecutor;
    private final NotificationEventPublisher eventPublisher;
    private final IntegrationSourceRepository integrationSourceRepository;
    private final DeliveryProperties deliveryProperties;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            NotificationRecipientRepository recipientRepository,
            NotificationAuditLogRepository auditRepository,
            ChannelResolutionService channelResolutionService,
            NotificationDeliveryExecutor notificationDeliveryExecutor,
            NotificationEventPublisher eventPublisher,
            IntegrationSourceRepository integrationSourceRepository,
            DeliveryProperties deliveryProperties) {
        this.notificationRepository = notificationRepository;
        this.recipientRepository = recipientRepository;
        this.auditRepository = auditRepository;
        this.channelResolutionService = channelResolutionService;
        this.notificationDeliveryExecutor = notificationDeliveryExecutor;
        this.eventPublisher = eventPublisher;
        this.integrationSourceRepository = integrationSourceRepository;
        this.deliveryProperties = deliveryProperties;
    }

    @Override
    @Transactional
    public NotificationResponse processEvent(CreateEventRequest request) {
        String normalizedType = request.eventType().toUpperCase();
        String resolvedSourceEventId = resolveSourceEventId(
                request.sourceEventId(),
                request.tenantId(),
                request.sourceService(),
                normalizedType,
                request.userId());
        var existing = notificationRepository.findByTenantIdAndSourceServiceAndSourceEventId(
                request.tenantId(), request.sourceService(), resolvedSourceEventId);
        if (existing.isPresent()) {
            // Idempotency policy: first-write wins.
            // Do not create a new notification/recipient rows and do not dispatch deliveries again.
            // However, return the recipient (if it already exists) so the response is consistent.
            var notification = existing.get();
            var recipient =
                    recipientRepository.findByTenantIdAndNotificationId(request.tenantId(), notification.getId()).stream()
                            .filter(r -> request.userId().equals(r.getUserId()))
                            .findFirst()
                            .orElse(null);
            return toResponse(notification, recipient);
        }

        // Optional out-of-order handling:
        // If upstream provides a sequenceNumber, drop/ignore events with a lower sequenceNumber
        // than the latest stored notification for the same (tenantId, sourceService, eventType).
        if (request.sequenceNumber() != null) {
            var latest = notificationRepository
                    .findTopByTenantIdAndSourceServiceAndEventTypeAndStatusAndIsDeletedFalseOrderBySequenceNumberDesc(
                            request.tenantId(),
                            request.sourceService(),
                            normalizedType,
                            NotificationStatus.ACTIVE);

            if (latest.isPresent()
                    && latest.get().getSequenceNumber() != null
                    && request.sequenceNumber() < latest.get().getSequenceNumber()) {
                var notification = latest.get();
                var recipient =
                        recipientRepository.findByTenantIdAndNotificationId(
                                        request.tenantId(), notification.getId()).stream()
                                .filter(r -> request.userId().equals(r.getUserId()))
                                .findFirst()
                                .orElse(null);
                return toResponse(notification, recipient);
            }
        }

        String locale =
                request.locale() == null || request.locale().isBlank()
                        ? deliveryProperties.getDefaultLocale()
                        : request.locale();

        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setTenantId(request.tenantId());
        // Template rendering is deferred to channel delivery time.
        notification.setTitle("Notification: " + normalizedType);
        notification.setBody("Event " + normalizedType + " received from " + request.sourceService() + ".");
        notification.setType(resolveType(normalizedType));
        notification.setPriority(PriorityLevel.MEDIUM);
        notification.setStatus(NotificationStatus.ACTIVE);
        notification.setSourceService(request.sourceService());
        notification.setSourceEventId(resolvedSourceEventId);
        notification.setEventType(normalizedType);
        notification.setSequenceNumber(request.sequenceNumber());
        notification.setEventTimestamp(request.eventTimestamp());
        String correlationId = request.correlationId();
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = org.slf4j.MDC.get("correlationId");
        }
        notification.setCorrelationId(correlationId);
        if (request.integrationSourceId() != null) {
            integrationSourceRepository
                    .findById(request.integrationSourceId())
                    .filter(s -> s.getTenantId().equals(request.tenantId()))
                    .ifPresent(notification::setIntegrationSource);
        }
        notification.setMetadata(request.payload());
        notification.setCreatedAt(OffsetDateTime.now());
        notification.setUpdatedAt(OffsetDateTime.now());
        notification.setIsDeleted(false);
        notification.setVersion(1L);

        var saved = notificationRepository.save(notification);

        var recipient = new NotificationRecipient();
        recipient.setId(UUID.randomUUID());
        recipient.setTenantId(saved.getTenantId());
        recipient.setNotificationId(saved.getId());
        recipient.setUserId(request.userId());
        recipient.setUserEmail(request.userEmail());
        recipient.setAadObjectId(request.aadObjectId());
        recipient.setTelegramChatId(request.telegramChatId());
        recipient.setRoleName(request.roleName());
        recipient.setIsRead(false);
        recipient.setIsAcknowledged(false);
        recipient.setCreatedAt(OffsetDateTime.now());
        recipientRepository.save(recipient);

        UUID integrationId =
                request.integrationSourceId() != null
                        ? request.integrationSourceId()
                        : (saved.getIntegrationSource() != null
                                ? saved.getIntegrationSource().getId()
                                : null);

        var channels = channelResolutionService.resolveChannels(
                request.tenantId(),
                request.roleName(),
                request.payload(),
                normalizedType,
                integrationId);

        runAfterCommitOrNow(
                () -> notificationDeliveryExecutor.dispatchDeliveries(saved, recipient, channels, locale, integrationId));

        audit("CREATED", request.tenantId(), request.userId(), Map.of("notificationId", saved.getId().toString()));
        runAfterCommitOrNow(() -> eventPublisher.publish(request));
        return toResponse(saved, recipient);
    }

    @Override
    public List<NotificationResponse> getNotifications(String tenantId, String userId) {
        if (userId != null && !userId.isBlank()) {
            var recipients = recipientRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(tenantId, userId);
            if (recipients.isEmpty()) {
                return List.of();
            }
            var ids = recipients.stream().map(NotificationRecipient::getNotificationId).distinct().toList();
            var notifMap = notificationRepository.findAllById(ids).stream()
                    .filter(n -> n.getStatus() == NotificationStatus.ACTIVE
                            && !Boolean.TRUE.equals(n.getIsDeleted()))
                    .collect(Collectors.toMap(Notification::getId, n -> n));
            return recipients.stream()
                    .map(r -> {
                        Notification n = notifMap.get(r.getNotificationId());
                        return n == null ? null : toResponse(n, r);
                    })
                    .filter(Objects::nonNull)
                    .toList();
        }
        return notificationRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, NotificationStatus.ACTIVE)
                .stream()
                .map(n -> toResponse(n, null))
                .toList();
    }

    @Override
    @Transactional
    public void markRead(String tenantId, String userId, UUID notificationId) {
        recipientRepository.findByTenantIdAndNotificationId(tenantId, notificationId).stream()
                .filter(r -> r.getUserId().equals(userId))
                .findFirst()
                .ifPresent(r -> {
                    r.setIsRead(true);
                    r.setReadAt(OffsetDateTime.now());
                    recipientRepository.save(r);
                    audit("READ", tenantId, userId, Map.of("notificationId", notificationId.toString()));
                });
    }

    private NotificationResponse toResponse(Notification notification, NotificationRecipient recipient) {
        String inboxStatus = deriveInboxStatus(recipient);
        String lifecycle = notification.getStatus() == null ? null : notification.getStatus().name();
        return new NotificationResponse(
                notification.getId(),
                notification.getTenantId(),
                notification.getTitle(),
                notification.getBody(),
                notification.getType(),
                notification.getPriority(),
                notification.getSourceService(),
                notification.getSourceEventId(),
                notification.getMetadata(),
                notification.getCreatedAt(),
                notification.getEventType(),
                inboxStatus,
                lifecycle);
    }

    private static String deriveInboxStatus(NotificationRecipient recipient) {
        if (recipient == null) {
            return null;
        }
        if (Boolean.TRUE.equals(recipient.getIsAcknowledged())) {
            return "ACKNOWLEDGED";
        }
        if (Boolean.TRUE.equals(recipient.getIsRead())) {
            return "READ";
        }
        return "UNREAD";
    }

    private NotificationType resolveType(String normalizedEventType) {
        return switch (normalizedEventType) {
            case "LEAVE_APPROVED", "LEAVE_REJECTED", "LEAVE_SUBMITTED",
                    "PAYSLIP_GENERATED", "EMPLOYEE_ONBOARDED", "EMPLOYEE_OFFBOARDED",
                    "PERFORMANCE_REVIEW_DUE", "PROBATION_ENDING", "CONTRACT_EXPIRING",
                    "TRAINING_ASSIGNED" -> NotificationType.HR_ACTION;
            case "ORDER_CREATED" -> NotificationType.WORKFLOW;
            default -> NotificationType.SYSTEM;
        };
    }

    private void audit(String action, String tenantId, String actorUserId, Map<String, Object> metadata) {
        var auditLog = new NotificationAuditLog();
        auditLog.setTenantId(tenantId);
        auditLog.setAction(action);
        auditLog.setActorUserId(actorUserId);
        auditLog.setMetadata(metadata);
        auditLog.setOccurredAt(OffsetDateTime.now());
        auditRepository.save(auditLog);
    }

    private String resolveSourceEventId(
            String provided, String tenantId, String sourceService, String eventType, String userId) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        String minuteKey = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES).toString();
        String raw = tenantId + "|" + sourceService + "|" + eventType + "|" + (userId == null ? "" : userId) + "|" + minuteKey;
        try {
            String hash = java.util.HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
            return "auto-" + hash.substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void runAfterCommitOrNow(Runnable runnable) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
            return;
        }
        runnable.run();
    }
}
