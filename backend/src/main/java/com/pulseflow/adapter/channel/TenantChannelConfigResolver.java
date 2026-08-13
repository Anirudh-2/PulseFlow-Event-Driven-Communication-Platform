package com.pulseflow.adapter.channel;

import com.pulseflow.repository.ChannelConfigurationRepository;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TenantChannelConfigResolver {
    private final ChannelConfigurationRepository repository;

    public TenantChannelConfigResolver(ChannelConfigurationRepository repository) {
        this.repository = repository;
    }

    public Map<String, Object> resolve(String tenantId, UUID appId, String channelTypeCode, UUID ignoredTenantChannelConfigId) {
        if (appId == null) {
            return Collections.emptyMap();
        }
        String channelType = mapChannelType(channelTypeCode);
        return repository
                .findFirstByTenantIdAndApp_IdAndChannelTypeAndIsActiveTrueOrderByCreatedAtDesc(
                        tenantId, appId, channelType)
                .map(c -> c.getConfigJson() == null ? Collections.<String, Object>emptyMap() : c.getConfigJson())
                .orElse(Collections.emptyMap());
    }

    private String mapChannelType(String channelTypeCode) {
        if (channelTypeCode == null) {
            return "";
        }
        return switch (channelTypeCode.trim().toUpperCase()) {
            case "EMAIL" -> "smtp";
            case "TEAMS" -> "teams";
            case "WHATSAPP" -> "whatsapp";
            case "TELEGRAM" -> "telegram";
            case "WEBHOOK" -> "webhook";
            default -> channelTypeCode.toLowerCase();
        };
    }
}
