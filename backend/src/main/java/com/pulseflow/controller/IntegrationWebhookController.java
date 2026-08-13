package com.pulseflow.controller;

import com.pulseflow.dto.NotificationResponse;
import com.pulseflow.service.IntegrationIngestService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Generic integration ingress. Secured by {@link com.pulseflow.config.WebhookApiKeyFilter}
 * ({@code X-Webhook-Api-Key}; per-source SHA-256 hash when configured on the integration source, else global key).
 * Send {@code X-Tenant-Id} when it differs from {@code app.integrations.hrmsDefaultTenantId}.
 */
@RestController
@RequestMapping("/api/v1/integrations/{sourceKey}/webhook")
public class IntegrationWebhookController {
    private static final long MAX_REQUEST_BYTES = 1 * 1024 * 1024; // 1 MB guardrail for demo/safety

    private final IntegrationIngestService integrationIngestService;
    private final String defaultTenantId;

    public IntegrationWebhookController(
            IntegrationIngestService integrationIngestService,
            @Value("${app.integrations.hrmsDefaultTenantId:default}") String defaultTenantId) {
        this.integrationIngestService = integrationIngestService;
        this.defaultTenantId = defaultTenantId;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse ingest(
            @PathVariable String sourceKey,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        long contentLength = request.getContentLengthLong();
        if (contentLength > 0 && contentLength > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Request body too large (max 1 MB)");
        }
        String tenantId =
                body.get("tenantId") != null && !body.get("tenantId").toString().isBlank()
                        ? body.get("tenantId").toString()
                        : defaultTenantId;
        return integrationIngestService.ingest(tenantId, sourceKey, body);
    }
}
