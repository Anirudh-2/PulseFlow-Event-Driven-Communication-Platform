package com.pulseflow.controller;

import com.pulseflow.dto.notify.NotifyRequest;
import com.pulseflow.service.IntegrationNotifyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations/{sourceKey}/notify")
public class IntegrationNotifyController {
    private final IntegrationNotifyService integrationNotifyService;

    public IntegrationNotifyController(IntegrationNotifyService integrationNotifyService) {
        this.integrationNotifyService = integrationNotifyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Object notifyIntegration(
            @PathVariable String sourceKey,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(defaultValue = "false") boolean dryRun,
            @Valid @RequestBody NotifyRequest request) {
        return integrationNotifyService.notify(sourceKey, tenantId, dryRun, request);
    }
}
