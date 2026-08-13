package com.pulseflow.service;

import com.pulseflow.domain.entity.Notification;
import com.pulseflow.domain.entity.NotificationRecipient;
import com.pulseflow.domain.entity.RoutingRule;
import com.pulseflow.config.DeliveryProperties;
import com.pulseflow.domain.enums.DeliveryChannel;
import com.pulseflow.domain.enums.NotificationStatus;
import com.pulseflow.domain.enums.NotificationType;
import com.pulseflow.domain.enums.PriorityLevel;
import com.pulseflow.dto.CreateEventRequest;
import com.pulseflow.dto.notify.NotifyDryRunResponse;
import com.pulseflow.dto.notify.NotifyQueuedResponse;
import com.pulseflow.dto.notify.NotifyRequest;
import com.pulseflow.dto.notify.UserRecipientInput;
import com.pulseflow.messaging.NotificationEventPublisher;
import com.pulseflow.repository.IntegrationSourceRepository;
import com.pulseflow.repository.NotificationRecipientRepository;
import com.pulseflow.repository.NotificationRepository;
import com.pulseflow.repository.RoutingRuleRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class IntegrationNotifyService {
    private final IntegrationSourceRepository integrationSourceRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final JsonConditionEvaluator jsonConditionEvaluator;
    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final NotificationDeliveryExecutor deliveryExecutor;
    private final TemplateRenderService templateRenderService;
    private final RuleEngineService ruleEngineService;
    private final NotificationEventPublisher eventPublisher;
    private final DeliveryProperties deliveryProperties;
    private final String defaultTenantId;

    public IntegrationNotifyService(
            IntegrationSourceRepository integrationSourceRepository,
            RoutingRuleRepository routingRuleRepository,
            JsonConditionEvaluator jsonConditionEvaluator,
            NotificationRepository notificationRepository,
            NotificationRecipientRepository recipientRepository,
            NotificationDeliveryExecutor deliveryExecutor,
            TemplateRenderService templateRenderService,
            RuleEngineService ruleEngineService,
            NotificationEventPublisher eventPublisher,
            DeliveryProperties deliveryProperties,
            @Value("${app.integrations.hrmsDefaultTenantId:default}") String defaultTenantId) {
        this.integrationSourceRepository = integrationSourceRepository;
        this.routingRuleRepository = routingRuleRepository;
        this.jsonConditionEvaluator = jsonConditionEvaluator;
        this.notificationRepository = notificationRepository;
        this.recipientRepository = recipientRepository;
        this.deliveryExecutor = deliveryExecutor;
        this.templateRenderService = templateRenderService;
        this.ruleEngineService = ruleEngineService;
        this.eventPublisher = eventPublisher;
        this.deliveryProperties = deliveryProperties;
        this.defaultTenantId = defaultTenantId;
    }

    @Transactional
    public Object notify(String sourceKey, String tenantIdHeader, boolean dryRun, NotifyRequest request) {
        String tenantId = tenantIdHeader == null || tenantIdHeader.isBlank() ? defaultTenantId : tenantIdHeader.trim();
        var source = integrationSourceRepository
                .findByTenantIdAndSourceKeyIgnoreCase(tenantId, sourceKey)
                .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive integration source"));

        var recipients = resolveRecipients(request);
        String normalizedEventType = request.eventType().toUpperCase();
        String dedupeUserId =
                request.recipients() != null && request.recipients().userId() != null
                        ? request.recipients().userId()
                        : (recipients.isEmpty() ? null : recipients.get(0).userId());
        String resolvedSourceEventId = resolveSourceEventId(
                request.sourceEventId(),
                tenantId,
                source.getSourceKey(),
                normalizedEventType,
                dedupeUserId);
        var existing = notificationRepository.findByTenantIdAndSourceServiceAndSourceEventId(
                tenantId, source.getSourceKey(), resolvedSourceEventId);
        if (existing.isPresent()) {
            return new NotifyQueuedResponse(existing.get().getId(), "queued");
        }
        String roleName = request.recipients().roleName();
        var matched = matchRoutingRules(tenantId, normalizedEventType, roleName, source.getId(), request.payload());
        var channels = ruleEngineService.matchedRoutingChannels(
                tenantId, roleName, normalizedEventType, source.getId(), request.payload());

        if (dryRun) {
            Map<String, Map<String, NotifyDryRunResponse.TemplatePreview>> previews = new LinkedHashMap<>();
            for (var r : recipients) {
                Map<String, NotifyDryRunResponse.TemplatePreview> byChannel = new LinkedHashMap<>();
                for (var channel : channels) {
                    Map<String, Object> ctx = new LinkedHashMap<>(request.payload());
                    ctx.put("userId", r.userId());
                    ctx.put("userEmail", r.email());
                    ctx.put("roleName", roleName);
                    ctx.put("sourceService", source.getSourceKey());
                    ctx.put("eventType", normalizedEventType);
                    var rendered = templateRenderService.render(
                            tenantId,
                            normalizedEventType,
                            channel.name(),
                            deliveryProperties.getDefaultLocale(),
                            source.getId(),
                            ctx);
                    byChannel.put(channel.name(), new NotifyDryRunResponse.TemplatePreview(rendered.subject(), rendered.body()));
                }
                previews.put(r.userId(), byChannel);
            }
            List<Map<String, Object>> matchedRules = matched.stream()
                    .map(this::toRulePreview)
                    .toList();
            return new NotifyDryRunResponse("dry_run", matchedRules, previews);
        }

        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setTenantId(tenantId);
        notification.setTitle("Notification: " + normalizedEventType);
        notification.setBody("Event " + normalizedEventType + " queued from " + source.getSourceKey() + ".");
        notification.setType(resolveType(normalizedEventType));
        notification.setPriority(PriorityLevel.MEDIUM);
        notification.setStatus(NotificationStatus.ACTIVE);
        notification.setSourceService(source.getSourceKey());
        notification.setSourceEventId(resolvedSourceEventId);
        notification.setEventType(normalizedEventType);
        notification.setIntegrationSource(source);
        notification.setMetadata(request.payload());
        notification.setCreatedAt(OffsetDateTime.now());
        notification.setUpdatedAt(OffsetDateTime.now());
        notification.setIsDeleted(false);
        notification.setVersion(1L);
        Notification saved = notificationRepository.save(notification);

        for (var r : recipients) {
            NotificationRecipient recipient = new NotificationRecipient();
            recipient.setId(UUID.randomUUID());
            recipient.setTenantId(tenantId);
            recipient.setNotificationId(saved.getId());
            recipient.setUserId(r.userId());
            recipient.setUserEmail(r.email());
            recipient.setAadObjectId(r.aadObjectId());
            recipient.setRoleName(roleName);
            recipient.setIsRead(false);
            recipient.setIsAcknowledged(false);
            recipient.setCreatedAt(OffsetDateTime.now());
            recipientRepository.save(recipient);
            runAfterCommitOrNow(
                    () -> deliveryExecutor.dispatchDeliveries(
                            saved, recipient, channels, deliveryProperties.getDefaultLocale(), source.getId()));
            CreateEventRequest event = new CreateEventRequest(
                    tenantId,
                    normalizedEventType,
                    source.getSourceKey(),
                    resolvedSourceEventId,
                    recipient.getUserId(),
                    recipient.getUserEmail(),
                    recipient.getAadObjectId(),
                    recipient.getTelegramChatId(),
                    recipient.getRoleName(),
                    request.payload(),
                    source.getId(),
                    deliveryProperties.getDefaultLocale(),
                    resolvedSourceEventId,
                    null,
                    null);
            runAfterCommitOrNow(() -> eventPublisher.publish(event));
        }

        return new NotifyQueuedResponse(saved.getId(), "queued");
    }

    private List<RecipientResolved> resolveRecipients(NotifyRequest request) {
        String mode = request.recipients().mode() == null ? "" : request.recipients().mode().trim().toUpperCase();
        String roleName = request.recipients().roleName();
        if (roleName == null || roleName.isBlank()) {
            throw new IllegalArgumentException("recipients.roleName is required");
        }
        if ("DIRECT".equals(mode)) {
            if (isBlank(request.recipients().userId()) || isBlank(request.recipients().email())) {
                throw new IllegalArgumentException("DIRECT mode requires recipients.userId and recipients.email");
            }
            return List.of(new RecipientResolved(
                    request.recipients().userId(),
                    request.recipients().email(),
                    request.recipients().aadObjectId()));
        }
        if ("ROLE_BASED".equals(mode) || "BROADCAST".equals(mode)) {
            if (request.recipients().users() == null || request.recipients().users().isEmpty()) {
                throw new IllegalArgumentException(mode + " mode requires recipients.users[]");
            }
            return request.recipients().users().stream()
                    .map(this::mapUser)
                    .toList();
        }
        throw new IllegalArgumentException("Unsupported recipients.mode: " + mode);
    }

    private RecipientResolved mapUser(UserRecipientInput u) {
        if (isBlank(u.userId()) || isBlank(u.email())) {
            throw new IllegalArgumentException("Each recipients.users[] entry must contain userId and email");
        }
        return new RecipientResolved(u.userId(), u.email(), u.aadObjectId());
    }

    private List<RoutingRule> matchRoutingRules(
            String tenantId,
            String eventType,
            String roleName,
            UUID integrationSourceId,
            Map<String, Object> payload) {
        List<RoutingRule> out = new ArrayList<>();
        for (var rule : routingRuleRepository.findByTenantIdAndIsActiveTrueOrderByEvalOrderAsc(tenantId)) {
            if (rule.getRoleName() != null
                    && !rule.getRoleName().isBlank()
                    && !rule.getRoleName().equalsIgnoreCase(roleName)) {
                continue;
            }
            if (rule.getEventType() != null
                    && !rule.getEventType().isBlank()
                    && !rule.getEventType().equalsIgnoreCase(eventType)) {
                continue;
            }
            if (rule.getIntegrationSource() != null
                    && !integrationSourceId.equals(rule.getIntegrationSource().getId())) {
                continue;
            }
            if (!jsonConditionEvaluator.matches(rule.getConditionsJsonlogic(), payload)) {
                continue;
            }
            out.add(rule);
        }
        return out;
    }

    private List<DeliveryChannel> collectChannels(List<RoutingRule> rules) {
        Set<DeliveryChannel> channels = new LinkedHashSet<>();
        for (var rule : rules) {
            if (rule.getChannelTypeCodes() == null) {
                continue;
            }
            for (String c : rule.getChannelTypeCodes()) {
                if (c == null || c.isBlank()) {
                    continue;
                }
                try {
                    channels.add(DeliveryChannel.valueOf(c.trim().toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    // ignore unknown channel types configured in DB
                }
            }
        }
        if (channels.isEmpty()) {
            channels.add(DeliveryChannel.valueOf(deliveryProperties.getDefaultChannel().toUpperCase()));
        }
        return List.copyOf(channels);
    }

    private Map<String, Object> toRulePreview(RoutingRule rule) {
        var m = new LinkedHashMap<String, Object>();
        m.put("id", rule.getId());
        m.put("name", rule.getName());
        m.put("eventType", rule.getEventType());
        m.put("roleName", rule.getRoleName());
        m.put("channelTypeCodes", rule.getChannelTypeCodes());
        m.put("evalOrder", rule.getEvalOrder());
        return m;
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
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

    private record RecipientResolved(String userId, String email, String aadObjectId) {}
}
