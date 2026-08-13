package com.pulseflow.service;

import com.pulseflow.domain.entity.TenantIntegrationConfig;
import com.pulseflow.repository.TenantIntegrationConfigRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminConfigService {
    private final TenantIntegrationConfigRepository repository;

    public AdminConfigService(TenantIntegrationConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getIntegrationConfig(String tenantId) {
        var config = repository.findById(tenantId).orElseGet(() -> defaultConfig(tenantId));
        return toIntegrationResponse(config);
    }

    @Transactional
    public Map<String, Object> saveIntegrationConfig(String tenantId, Map<String, Object> payload, String actorUserId) {
        var config = repository.findById(tenantId).orElseGet(() -> defaultConfig(tenantId));
        config.setTeams(mergeOrCurrent(payload.get("teams"), config.getTeams()));
        config.setSmtp(mergeOrCurrent(payload.get("smtp"), config.getSmtp()));
        config.setWebhookSecurity(mergeOrCurrent(payload.get("webhookSecurity"), config.getWebhookSecurity()));
        config.setHrmsMapping(mergeOrCurrent(payload.get("hrmsMapping"), config.getHrmsMapping()));
        config.setTemplates(mergeOrCurrent(payload.get("templates"), config.getTemplates()));
        config.setTelegram(mergeOrCurrent(payload.get("telegram"), config.getTelegram()));
        config.setUpdatedAt(OffsetDateTime.now());
        config.setUpdatedBy(actorUserId);
        var saved = repository.save(config);
        return toIntegrationResponse(saved);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getWebhookSecurity(String tenantId) {
        var config = repository.findById(tenantId).orElseGet(() -> defaultConfig(tenantId));
        return config.getWebhookSecurity();
    }

    @Transactional
    public Map<String, Object> saveWebhookSecurity(String tenantId, Map<String, Object> payload, String actorUserId) {
        var config = repository.findById(tenantId).orElseGet(() -> defaultConfig(tenantId));
        config.setWebhookSecurity(new LinkedHashMap<>(payload));
        config.setUpdatedAt(OffsetDateTime.now());
        config.setUpdatedBy(actorUserId);
        repository.save(config);
        return config.getWebhookSecurity();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getHrmsMapping(String tenantId) {
        var config = repository.findById(tenantId).orElseGet(() -> defaultConfig(tenantId));
        return config.getHrmsMapping();
    }

    @Transactional
    public Map<String, Object> saveHrmsMapping(String tenantId, Map<String, Object> payload, String actorUserId) {
        var config = repository.findById(tenantId).orElseGet(() -> defaultConfig(tenantId));
        config.setHrmsMapping(new LinkedHashMap<>(payload));
        config.setUpdatedAt(OffsetDateTime.now());
        config.setUpdatedBy(actorUserId);
        repository.save(config);
        return config.getHrmsMapping();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTemplates(String tenantId) {
        var config = repository.findById(tenantId).orElseGet(() -> defaultConfig(tenantId));
        return config.getTemplates();
    }

    @Transactional
    public Map<String, Object> saveTemplates(String tenantId, Map<String, Object> payload, String actorUserId) {
        var config = repository.findById(tenantId).orElseGet(() -> defaultConfig(tenantId));
        config.setTemplates(new LinkedHashMap<>(payload));
        config.setUpdatedAt(OffsetDateTime.now());
        config.setUpdatedBy(actorUserId);
        repository.save(config);
        return config.getTemplates();
    }

    private TenantIntegrationConfig defaultConfig(String tenantId) {
        var config = new TenantIntegrationConfig();
        config.setTenantId(tenantId);
        config.setTeams(new LinkedHashMap<>(Map.of("enabled", false, "webhookUrl", "")));
        config.setSmtp(new LinkedHashMap<>(Map.of("enabled", false, "host", "smtp.office365.com", "port", 587)));
        config.setWebhookSecurity(new LinkedHashMap<>(Map.of("mode", "API_KEY", "apiKeyHeader", "X-Webhook-Api-Key")));
        config.setHrmsMapping(new LinkedHashMap<>(
                Map.of("defaultTenantId", tenantId, "sourceServiceName", "HRMS", "userIdentifierStrategy", "AAD_ID_FIRST", "eventTypeMap", Map.of())));
        config.setTemplates(new LinkedHashMap<>(
                Map.of(
                        "teams", Map.of("titleTemplate", "{{eventType}}", "bodyTemplate", "{{body}}"),
                        "email", Map.of("subjectTemplate", "{{eventType}}", "bodyTemplate", "{{body}}"))));
        config.setTelegram(new LinkedHashMap<>(
                Map.of(
                        "enabled", false,
                        "apiBase", "https://api.telegram.org",
                        "parseMode", "Markdown",
                        "botToken", "",
                        "chatId", "")));
        config.setUpdatedAt(OffsetDateTime.now());
        config.setUpdatedBy("system");
        return config;
    }

    private Map<String, Object> toIntegrationResponse(TenantIntegrationConfig config) {
        var response = new LinkedHashMap<String, Object>();
        response.put("teams", config.getTeams());
        response.put("smtp", config.getSmtp());
        response.put("webhookSecurity", config.getWebhookSecurity());
        response.put("hrmsMapping", config.getHrmsMapping());
        response.put("templates", config.getTemplates());
        response.put("telegram", config.getTelegram());
        response.put("updatedAt", config.getUpdatedAt());
        response.put("updatedBy", config.getUpdatedBy());
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    private Map<String, Object> mergeOrCurrent(Object value, Map<String, Object> current) {
        if (value instanceof Map<?, ?>) {
            return asMap(value);
        }
        return current == null ? new LinkedHashMap<>() : current;
    }
}
