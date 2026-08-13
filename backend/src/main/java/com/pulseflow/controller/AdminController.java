package com.pulseflow.controller;

import com.pulseflow.domain.entity.NotificationRule;
import com.pulseflow.domain.enums.DeliveryChannel;
import com.pulseflow.domain.enums.NotificationType;
import com.pulseflow.dto.admin.AdminRuleRequest;
import com.pulseflow.dto.admin.AdminRuleResponse;
import com.pulseflow.domain.entity.IntegrationFieldMapping;
import com.pulseflow.domain.entity.IntegrationSource;
import com.pulseflow.domain.entity.NotificationTemplateEntity;
import com.pulseflow.domain.entity.RoutingRule;
import com.pulseflow.domain.entity.TenantChannelConfig;
import com.pulseflow.repository.ChannelTypeRepository;
import com.pulseflow.repository.IntegrationFieldMappingRepository;
import com.pulseflow.repository.IntegrationSourceRepository;
import com.pulseflow.repository.NotificationAuditLogRepository;
import com.pulseflow.repository.NotificationDeliveryLogRepository;
import com.pulseflow.repository.NotificationRuleRepository;
import com.pulseflow.repository.NotificationTemplateEntityRepository;
import com.pulseflow.repository.RoutingRuleRepository;
import com.pulseflow.repository.TenantChannelConfigRepository;
import com.pulseflow.service.AdminConfigService;
import com.pulseflow.service.NotificationHistoryService;
import com.pulseflow.util.ApiKeyHasher;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final NotificationRuleRepository ruleRepository;
    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final NotificationAuditLogRepository auditLogRepository;
    private final AdminConfigService adminConfigService;
    private final IntegrationSourceRepository integrationSourceRepository;
    private final IntegrationFieldMappingRepository fieldMappingRepository;
    private final TenantChannelConfigRepository tenantChannelConfigRepository;
    private final NotificationTemplateEntityRepository notificationTemplateEntityRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final ChannelTypeRepository channelTypeRepository;
    private final NotificationHistoryService notificationHistoryService;

    public AdminController(
            NotificationRuleRepository ruleRepository,
            NotificationDeliveryLogRepository deliveryLogRepository,
            NotificationAuditLogRepository auditLogRepository,
            AdminConfigService adminConfigService,
            IntegrationSourceRepository integrationSourceRepository,
            IntegrationFieldMappingRepository fieldMappingRepository,
            TenantChannelConfigRepository tenantChannelConfigRepository,
            NotificationTemplateEntityRepository notificationTemplateEntityRepository,
            RoutingRuleRepository routingRuleRepository,
            ChannelTypeRepository channelTypeRepository,
            NotificationHistoryService notificationHistoryService) {
        this.ruleRepository = ruleRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.auditLogRepository = auditLogRepository;
        this.adminConfigService = adminConfigService;
        this.integrationSourceRepository = integrationSourceRepository;
        this.fieldMappingRepository = fieldMappingRepository;
        this.tenantChannelConfigRepository = tenantChannelConfigRepository;
        this.notificationTemplateEntityRepository = notificationTemplateEntityRepository;
        this.routingRuleRepository = routingRuleRepository;
        this.channelTypeRepository = channelTypeRepository;
        this.notificationHistoryService = notificationHistoryService;
    }

    @GetMapping("/rules")
    public List<AdminRuleResponse> rules(@RequestParam String tenantId) {
        return ruleRepository.findByTenantIdOrderByEvalOrderAsc(tenantId).stream().map(this::toRuleResponse).toList();
    }

    @PostMapping("/rules")
    public AdminRuleResponse createRule(@RequestParam String tenantId, @Valid @RequestBody AdminRuleRequest request) {
        var rule = new NotificationRule();
        rule.setId(UUID.randomUUID());
        rule.setTenantId(tenantId);
        applyRuleRequest(rule, request);
        rule.setCreatedAt(OffsetDateTime.now());
        rule.setUpdatedAt(OffsetDateTime.now());
        return toRuleResponse(ruleRepository.save(rule));
    }

    @PutMapping("/rules/{ruleId}")
    public AdminRuleResponse updateRule(
            @RequestParam String tenantId,
            @PathVariable UUID ruleId,
            @Valid @RequestBody AdminRuleRequest request) {
        var existing = ruleRepository.findByIdAndTenantId(ruleId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found for tenant"));
        applyRuleRequest(existing, request);
        existing.setUpdatedAt(OffsetDateTime.now());
        return toRuleResponse(ruleRepository.save(existing));
    }

    @DeleteMapping("/rules/{ruleId}")
    public Map<String, String> deleteRule(@RequestParam String tenantId, @PathVariable UUID ruleId) {
        var existing = ruleRepository.findByIdAndTenantId(ruleId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Rule not found for tenant"));
        ruleRepository.delete(existing);
        return Map.of("status", "deleted");
    }

    @GetMapping("/delivery")
    public ResponseEntity<Map<String, Object>> deliveryAll(
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("createdAt").descending());
        Page<?> result = deliveryLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        return ResponseEntity.ok(toPagedResponse(result));
    }

    @GetMapping("/delivery/{notificationId}")
    public Object delivery(@RequestParam String tenantId, @PathVariable UUID notificationId) {
        return deliveryLogRepository.findByTenantIdAndNotificationId(tenantId, notificationId);
    }

    @GetMapping("/audit")
    public ResponseEntity<Map<String, Object>> audit(
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable =
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("occurredAt").descending());
        Page<?> result = auditLogRepository.findByTenantIdOrderByOccurredAtDesc(tenantId, pageable);
        return ResponseEntity.ok(toPagedResponse(result));
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/notifications/history")
    public List<Map<String, Object>> notificationHistory(
            @RequestParam String tenantId, @RequestParam(defaultValue = "50") int limit) {
        return notificationHistoryService.listActiveAndArchived(tenantId, limit);
    }

    @GetMapping("/config/integrations")
    public Map<String, Object> getIntegrationConfig(@RequestParam String tenantId) {
        return adminConfigService.getIntegrationConfig(tenantId);
    }

    @PutMapping("/config/integrations")
    public Map<String, Object> updateIntegrationConfig(
            @RequestParam String tenantId,
            @RequestBody Map<String, Object> payload,
            Principal principal) {
        return adminConfigService.saveIntegrationConfig(tenantId, payload, actor(principal));
    }

    @GetMapping("/config/webhook-security")
    public Map<String, Object> getWebhookSecurity(@RequestParam String tenantId) {
        return adminConfigService.getWebhookSecurity(tenantId);
    }

    @PutMapping("/config/webhook-security")
    public Map<String, Object> updateWebhookSecurity(
            @RequestParam String tenantId,
            @RequestBody Map<String, Object> payload,
            Principal principal) {
        return adminConfigService.saveWebhookSecurity(tenantId, payload, actor(principal));
    }

    @GetMapping("/config/hrms-mapping")
    public Map<String, Object> getHrmsMapping(@RequestParam String tenantId) {
        return adminConfigService.getHrmsMapping(tenantId);
    }

    @PutMapping("/config/hrms-mapping")
    public Map<String, Object> updateHrmsMapping(
            @RequestParam String tenantId,
            @RequestBody Map<String, Object> payload,
            Principal principal) {
        return adminConfigService.saveHrmsMapping(tenantId, payload, actor(principal));
    }

    @GetMapping("/templates")
    public Map<String, Object> getTemplates(@RequestParam String tenantId) {
        return adminConfigService.getTemplates(tenantId);
    }

    @PutMapping("/templates")
    public Map<String, Object> updateTemplates(
            @RequestParam String tenantId,
            @RequestBody Map<String, Object> payload,
            Principal principal) {
        return adminConfigService.saveTemplates(tenantId, payload, actor(principal));
    }

    @GetMapping("/integrations/sources")
    public List<IntegrationSource> listIntegrationSources(@RequestParam String tenantId) {
        return integrationSourceRepository.findByTenantIdOrderBySourceKeyAsc(tenantId);
    }

    @PostMapping("/integrations/sources")
    public IntegrationSource createIntegrationSource(
            @RequestParam String tenantId, @RequestBody Map<String, Object> body, Principal principal) {
        var entity = new IntegrationSource();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setSourceKey(body.get("sourceKey").toString().toUpperCase());
        entity.setDisplayName(body.getOrDefault("displayName", entity.getSourceKey()).toString());
        entity.setIsActive(Boolean.TRUE.equals(body.get("isActive")) || !Boolean.FALSE.equals(body.get("isActive")));
        entity.setMetadata(body.get("metadata") instanceof Map<?, ?> m ? new java.util.LinkedHashMap<>((Map<String, Object>) m) : Map.of());
        if (body.get("webhookApiKey") instanceof String key && !key.isBlank()) {
            entity.setWebhookApiKeyHash(ApiKeyHasher.sha256Hex(key));
        }
        entity.setCreatedAt(OffsetDateTime.now());
        entity.setUpdatedAt(OffsetDateTime.now());
        return integrationSourceRepository.save(entity);
    }

    @PutMapping("/integrations/sources/{id}")
    public IntegrationSource updateIntegrationSource(
            @RequestParam String tenantId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            Principal principal) {
        var existing = integrationSourceRepository
                .findById(id)
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Integration source not found"));
        if (body.containsKey("displayName")) {
            existing.setDisplayName(body.get("displayName").toString());
        }
        if (body.containsKey("isActive")) {
            existing.setIsActive(Boolean.TRUE.equals(body.get("isActive")));
        }
        if (body.get("metadata") instanceof Map<?, ?> m) {
            existing.setMetadata(new java.util.LinkedHashMap<>((Map<String, Object>) m));
        }
        if (body.containsKey("webhookApiKey")) {
            Object v = body.get("webhookApiKey");
            if (v == null || (v instanceof String s && s.isBlank())) {
                existing.setWebhookApiKeyHash(null);
            } else if (v instanceof String s) {
                existing.setWebhookApiKeyHash(ApiKeyHasher.sha256Hex(s));
            }
        }
        existing.setUpdatedAt(OffsetDateTime.now());
        return integrationSourceRepository.save(existing);
    }

    @GetMapping("/integrations/field-mappings")
    public List<IntegrationFieldMapping> listFieldMappings(
            @RequestParam String tenantId, @RequestParam UUID integrationSourceId) {
        integrationSourceRepository
                .findById(integrationSourceId)
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Integration source not found"));
        return fieldMappingRepository.findByIntegrationSource_IdOrderByVersionDesc(integrationSourceId);
    }

    @PostMapping("/integrations/field-mappings")
    public IntegrationFieldMapping createFieldMapping(
            @RequestParam String tenantId,
            @RequestParam UUID integrationSourceId,
            @RequestBody Map<String, Object> body) {
        var source = integrationSourceRepository
                .findById(integrationSourceId)
                .filter(s -> s.getTenantId().equals(tenantId))
                .orElseThrow();
        var m = new IntegrationFieldMapping();
        m.setId(UUID.randomUUID());
        m.setIntegrationSource(source);
        m.setVersion(body.get("version") instanceof Number n ? n.intValue() : 1);
        m.setMapping(body.get("mapping") instanceof Map<?, ?> map ? new java.util.LinkedHashMap<>((Map<String, Object>) map) : Map.of());
        m.setIsActive(true);
        m.setCreatedAt(OffsetDateTime.now());
        fieldMappingRepository.save(m);
        return fieldMappingRepository.findById(m.getId()).orElse(m);
    }

    @GetMapping("/channel-configs")
    public List<TenantChannelConfig> listChannelConfigs(@RequestParam String tenantId) {
        return tenantChannelConfigRepository.findByTenantIdOrderByChannelType_CodeAscPriorityAsc(tenantId);
    }

    @PostMapping("/channel-configs")
    public TenantChannelConfig createChannelConfig(@RequestParam String tenantId, @RequestBody Map<String, Object> body) {
        String typeCode = body.get("channelTypeCode").toString().toUpperCase();
        channelTypeRepository
                .findById(typeCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown channel type: " + typeCode));
        var cfg = new TenantChannelConfig();
        cfg.setId(UUID.randomUUID());
        cfg.setTenantId(tenantId);
        cfg.setChannelType(channelTypeRepository.getReferenceById(typeCode));
        cfg.setName(body.getOrDefault("name", typeCode).toString());
        cfg.setConfig(body.get("config") instanceof Map<?, ?> m ? new java.util.LinkedHashMap<>((Map<String, Object>) m) : Map.of());
        cfg.setPriority(((Number) body.getOrDefault("priority", 100)).shortValue());
        cfg.setIsDefault(Boolean.TRUE.equals(body.get("isDefault")));
        cfg.setIsEnabled(!Boolean.FALSE.equals(body.get("isEnabled")));
        cfg.setCreatedAt(OffsetDateTime.now());
        cfg.setUpdatedAt(OffsetDateTime.now());
        tenantChannelConfigRepository.save(cfg);
        return tenantChannelConfigRepository
                .findByIdAndTenantId(cfg.getId(), tenantId)
                .orElse(cfg);
    }

    @PutMapping("/channel-configs/{id}")
    public TenantChannelConfig updateChannelConfig(
            @RequestParam String tenantId, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        var existing = tenantChannelConfigRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Channel config not found"));
        if (body.containsKey("name")) {
            existing.setName(body.get("name").toString());
        }
        if (body.get("config") instanceof Map<?, ?> m) {
            existing.setConfig(new java.util.LinkedHashMap<>((Map<String, Object>) m));
        }
        if (body.containsKey("priority")) {
            existing.setPriority(((Number) body.get("priority")).shortValue());
        }
        if (body.containsKey("isDefault")) {
            existing.setIsDefault(Boolean.TRUE.equals(body.get("isDefault")));
        }
        if (body.containsKey("isEnabled")) {
            existing.setIsEnabled(Boolean.TRUE.equals(body.get("isEnabled")));
        }
        existing.setUpdatedAt(OffsetDateTime.now());
        tenantChannelConfigRepository.save(existing);
        return tenantChannelConfigRepository.findByIdAndTenantId(id, tenantId).orElse(existing);
    }

    @GetMapping("/routing-rules")
    public ResponseEntity<Map<String, Object>> listRoutingRules(
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.asc("evalOrder"), Sort.Order.desc("createdAt")));
        Page<?> result = routingRuleRepository.findByTenantId(tenantId, pageable);
        return ResponseEntity.ok(toPagedResponse(result));
    }

    @PostMapping("/routing-rules")
    @CacheEvict(cacheNames = "rulesCache", allEntries = true, beforeInvocation = true)
    public RoutingRule createRoutingRule(@RequestParam String tenantId, @RequestBody Map<String, Object> body) {
        var rule = new RoutingRule();
        rule.setId(UUID.randomUUID());
        rule.setTenantId(tenantId);
        applyRoutingBody(rule, body);
        rule.setCreatedAt(OffsetDateTime.now());
        rule.setUpdatedAt(OffsetDateTime.now());
        return routingRuleRepository.save(rule);
    }

    @PutMapping("/routing-rules/{ruleId}")
    @CacheEvict(cacheNames = "rulesCache", allEntries = true, beforeInvocation = true)
    public RoutingRule updateRoutingRule(
            @RequestParam String tenantId, @PathVariable UUID ruleId, @RequestBody Map<String, Object> body) {
        var existing = routingRuleRepository
                .findByIdAndTenantId(ruleId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Routing rule not found"));
        applyRoutingBody(existing, body);
        existing.setUpdatedAt(OffsetDateTime.now());
        return routingRuleRepository.save(existing);
    }

    @DeleteMapping("/routing-rules/{ruleId}")
    @CacheEvict(cacheNames = "rulesCache", allEntries = true, beforeInvocation = true)
    public Map<String, String> deleteRoutingRule(@RequestParam String tenantId, @PathVariable UUID ruleId) {
        var existing = routingRuleRepository
                .findByIdAndTenantId(ruleId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Routing rule not found"));
        routingRuleRepository.delete(existing);
        return Map.of("status", "deleted");
    }

    @GetMapping("/db-templates")
    public ResponseEntity<Map<String, Object>> listDbTemplates(
            @RequestParam String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("createdAt").descending());
        Page<?> result = notificationTemplateEntityRepository.findByTenantIdAndIsActiveTrue(tenantId, pageable);
        return ResponseEntity.ok(toPagedResponse(result));
    }

    @PostMapping("/db-templates")
    @CacheEvict(cacheNames = "templateCache", allEntries = true, beforeInvocation = true)
    public NotificationTemplateEntity createDbTemplate(@RequestParam String tenantId, @RequestBody Map<String, Object> body) {
        var t = new NotificationTemplateEntity();
        t.setId(UUID.randomUUID());
        t.setTenantId(tenantId);
        t.setLocale("en");
        t.setContentType("text");
        t.setTemplateVersion(1);
        t.setIsActive(true);
        applyTemplateBody(t, body);
        t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());
        notificationTemplateEntityRepository.save(t);
        return notificationTemplateEntityRepository
                .findByIdAndTenantId(t.getId(), tenantId)
                .orElse(t);
    }

    @PutMapping("/db-templates/{id}")
    @CacheEvict(cacheNames = "templateCache", allEntries = true, beforeInvocation = true)
    public NotificationTemplateEntity updateDbTemplate(
            @RequestParam String tenantId, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        var existing = notificationTemplateEntityRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        applyTemplateBody(existing, body);
        existing.setUpdatedAt(OffsetDateTime.now());
        notificationTemplateEntityRepository.save(existing);
        return notificationTemplateEntityRepository.findByIdAndTenantId(id, tenantId).orElse(existing);
    }

    @DeleteMapping("/db-templates/{id}")
    @CacheEvict(cacheNames = "templateCache", allEntries = true, beforeInvocation = true)
    public ResponseEntity<Void> deleteDbTemplate(@RequestParam String tenantId, @PathVariable UUID id) {
        var existing = notificationTemplateEntityRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Template not found"));
        if (deliveryLogRepository.existsByTemplateId(id)) {
            existing.setIsActive(false);
            existing.setUpdatedAt(OffsetDateTime.now());
            notificationTemplateEntityRepository.save(existing);
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        notificationTemplateEntityRepository.delete(existing);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void applyRoutingBody(RoutingRule rule, Map<String, Object> body) {
        if (body.containsKey("name")) {
            rule.setName(body.get("name").toString());
        }
        if (body.containsKey("eventType")) {
            rule.setEventType(body.get("eventType") == null ? null : body.get("eventType").toString());
        }
        if (body.containsKey("roleName")) {
            rule.setRoleName(body.get("roleName") == null ? null : body.get("roleName").toString());
        }
        if (body.get("conditionsJsonlogic") instanceof Map<?, ?> m) {
            rule.setConditionsJsonlogic(new java.util.LinkedHashMap<>((Map<String, Object>) m));
        }
        if (body.get("channelTypeCodes") instanceof List<?> list) {
            rule.setChannelTypeCodes(list.stream().map(Object::toString).toArray(String[]::new));
        }
        if (body.containsKey("evalOrder")) {
            rule.setEvalOrder(((Number) body.get("evalOrder")).shortValue());
        }
        if (body.containsKey("isActive")) {
            rule.setIsActive(Boolean.TRUE.equals(body.get("isActive")));
        }
        if (body.get("integrationSourceId") != null) {
            UUID sid = UUID.fromString(body.get("integrationSourceId").toString());
            rule.setIntegrationSource(integrationSourceRepository.getReferenceById(sid));
        } else if (body.containsKey("integrationSourceId")) {
            rule.setIntegrationSource(null);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyTemplateBody(NotificationTemplateEntity t, Map<String, Object> body) {
        if (body.containsKey("eventType")) {
            t.setEventType(body.get("eventType").toString());
        }
        if (body.containsKey("channelTypeCode")) {
            String code = body.get("channelTypeCode").toString().toUpperCase();
            channelTypeRepository.findById(code).orElseThrow(() -> new IllegalArgumentException("Unknown channel type"));
            t.setChannelType(channelTypeRepository.getReferenceById(code));
        }
        if (body.containsKey("locale")) {
            t.setLocale(body.get("locale").toString());
        }
        if (body.containsKey("subjectTemplate")) {
            t.setSubjectTemplate(body.get("subjectTemplate") == null ? null : body.get("subjectTemplate").toString());
        }
        if (body.containsKey("bodyTemplate")) {
            t.setBodyTemplate(body.get("bodyTemplate").toString());
        }
        if (body.containsKey("contentType")) {
            t.setContentType(body.get("contentType").toString());
        }
        if (body.containsKey("isActive")) {
            t.setIsActive(Boolean.TRUE.equals(body.get("isActive")));
        }
        if (body.get("integrationSourceId") != null) {
            t.setIntegrationSource(integrationSourceRepository.getReferenceById(UUID.fromString(body.get("integrationSourceId").toString())));
        } else if (body.containsKey("integrationSourceId")) {
            t.setIntegrationSource(null);
        }
    }

    private void applyRuleRequest(NotificationRule rule, AdminRuleRequest request) {
        rule.setName(request.name());
        rule.setRoleName(request.roleName());
        rule.setNotificationType(request.notificationType() == null || request.notificationType().isBlank()
                ? null
                : NotificationType.valueOf(request.notificationType().toUpperCase()));
        rule.setEventType(request.eventType() == null || request.eventType().isBlank() ? null : request.eventType());
        if (request.integrationSourceId() != null && !request.integrationSourceId().isBlank()) {
            rule.setIntegrationSource(
                    integrationSourceRepository.getReferenceById(UUID.fromString(request.integrationSourceId())));
        } else {
            rule.setIntegrationSource(null);
        }
        rule.setChannels(request.channels().stream()
                .map(ch -> {
                    DeliveryChannel channel = DeliveryChannel.valueOf(ch.toUpperCase());
                    // Stub channels exist in the enum for roadmap compatibility, but must not be selectable.
                    if (channel == DeliveryChannel.SSE
                            || channel == DeliveryChannel.PUSH
                            || channel == DeliveryChannel.POLLING) {
                        throw new IllegalArgumentException(
                                "Channel " + channel.name() + " is not implemented and cannot be used in rules");
                    }
                    return channel;
                })
                .toArray(DeliveryChannel[]::new));
        rule.setIsActive(request.isActive());
        rule.setEvalOrder(request.evalOrder());
        rule.setConditions(request.conditions());
        rule.setConditionsJsonlogic(request.conditionsJsonlogic());
    }

    private AdminRuleResponse toRuleResponse(NotificationRule rule) {
        var channels = rule.getChannels() == null
                ? List.<String>of()
                : Arrays.stream(rule.getChannels()).map(Enum::name).collect(Collectors.toList());
        var evalOrder = rule.getEvalOrder() == null ? (short) 100 : rule.getEvalOrder();
        var active = Boolean.TRUE.equals(rule.getIsActive());
        UUID integrationId =
                rule.getIntegrationSource() != null ? rule.getIntegrationSource().getId() : null;
        return new AdminRuleResponse(
                rule.getId(),
                rule.getTenantId(),
                rule.getName(),
                rule.getRoleName(),
                rule.getRoleName(),
                rule.getNotificationType() == null ? null : rule.getNotificationType().name(),
                rule.getEventType(),
                integrationId,
                channels,
                rule.getPriorityOverride() == null ? null : rule.getPriorityOverride().name(),
                evalOrder,
                evalOrder,
                active,
                active,
                rule.getConditions(),
                rule.getConditionsJsonlogic(),
                rule.getCreatedAt());
    }

    private String actor(Principal principal) {
        return principal == null ? "admin" : principal.getName();
    }

    private Map<String, Object> toPagedResponse(Page<?> result) {
        return Map.of(
                "content", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "first", result.isFirst(),
                "last", result.isLast());
    }
}
