package com.pulseflow.controller;

import com.pulseflow.dto.CreateEventRequest;
import com.pulseflow.dto.HrmsWebhookPayload;
import com.pulseflow.dto.NotificationResponse;
import com.pulseflow.domain.entity.IntegrationSource;
import com.pulseflow.repository.IntegrationSourceRepository;
import com.pulseflow.service.NotificationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives HRMS system events via webhook and converts them to internal notifications.
 * The endpoint is secured by {@link com.pulseflow.config.WebhookApiKeyFilter}
 * using the X-Webhook-Api-Key header; JWT authentication is not required.
 */
@RestController
@RequestMapping("/api/v1/hrms")
public class HrmsWebhookController {

    private static final String SOURCE_SERVICE = "HRMS";

    private final NotificationService notificationService;
    private final IntegrationSourceRepository integrationSourceRepository;
    private final String defaultTenantId;

    public HrmsWebhookController(
            NotificationService notificationService,
            IntegrationSourceRepository integrationSourceRepository,
            @Value("${app.integrations.hrmsDefaultTenantId:default}") String defaultTenantId) {
        this.notificationService = notificationService;
        this.integrationSourceRepository = integrationSourceRepository;
        this.defaultTenantId = defaultTenantId;
    }

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse handleHrmsEvent(@Valid @RequestBody HrmsWebhookPayload payload) {
        UUID integrationId = integrationSourceRepository
                .findByTenantIdAndSourceKeyIgnoreCase(defaultTenantId, SOURCE_SERVICE)
                .map(IntegrationSource::getId)
                .orElse(null);
        var request = new CreateEventRequest(
                defaultTenantId,
                payload.eventType(),
                SOURCE_SERVICE,
                payload.sourceEventId(),
                payload.userId(),
                payload.userEmail(),
                payload.aadObjectId(),
                payload.telegramChatId(),
                payload.roleName(),
                payload.payload(),
                integrationId,
                null,
                null,
                null,
                null);
        return notificationService.processEvent(request);
    }
}
