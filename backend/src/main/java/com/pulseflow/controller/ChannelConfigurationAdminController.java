package com.pulseflow.controller;

import com.pulseflow.domain.entity.ChannelConfiguration;
import com.pulseflow.service.ChannelConfigurationService;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/channel-configurations")
public class ChannelConfigurationAdminController {
    private final ChannelConfigurationService service;

    public ChannelConfigurationAdminController(ChannelConfigurationService service) {
        this.service = service;
    }

    @GetMapping
    public Object list(@RequestParam String tenantId) {
        return service.list(tenantId);
    }

    @PostMapping
    public ChannelConfiguration create(@RequestParam String tenantId, @RequestBody Map<String, Object> body) {
        return service.create(
                tenantId,
                UUID.fromString(body.get("appId").toString()),
                body.get("channelType").toString(),
                body.get("configJson") instanceof Map<?, ?> m ? new java.util.LinkedHashMap<>((Map<String, Object>) m) : Map.of(),
                !Boolean.FALSE.equals(body.get("isActive")));
    }

    @PutMapping("/{id}")
    public ChannelConfiguration update(
            @RequestParam String tenantId,
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body) {
        UUID appId = body.get("appId") == null ? null : UUID.fromString(body.get("appId").toString());
        String channelType = body.get("channelType") == null ? null : body.get("channelType").toString();
        Map<String, Object> configJson =
                body.get("configJson") instanceof Map<?, ?> m ? new java.util.LinkedHashMap<>((Map<String, Object>) m) : null;
        Boolean isActive = body.containsKey("isActive") ? Boolean.TRUE.equals(body.get("isActive")) : null;
        return service.update(tenantId, id, appId, channelType, configJson, isActive);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@RequestParam String tenantId, @PathVariable UUID id) {
        service.delete(tenantId, id);
        return Map.of("status", "deleted");
    }

    @PostMapping("/{id}/test")
    public Map<String, Object> test(@RequestParam String tenantId, @PathVariable UUID id) {
        return service.testConnection(service.getById(tenantId, id));
    }
}
