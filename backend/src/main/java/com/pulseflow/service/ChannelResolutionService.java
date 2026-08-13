package com.pulseflow.service;

import com.pulseflow.config.DeliveryProperties;
import com.pulseflow.domain.enums.DeliveryChannel;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ChannelResolutionService {
    private final RuleEngineService ruleEngineService;
    private final DeliveryProperties deliveryProperties;

    public ChannelResolutionService(RuleEngineService ruleEngineService, DeliveryProperties deliveryProperties) {
        this.ruleEngineService = ruleEngineService;
        this.deliveryProperties = deliveryProperties;
    }

    /**
     * Resolves distinct delivery channels from routing_rules for the event context.
     */
    public List<DeliveryChannel> resolveChannels(
            String tenantId,
            String roleName,
            Map<String, Object> payload,
            String eventType,
            UUID integrationSourceId) {
        Set<DeliveryChannel> ordered = new LinkedHashSet<>();
        ordered.addAll(ruleEngineService.matchedRoutingChannels(tenantId, roleName, eventType, integrationSourceId, payload));
        if (ordered.isEmpty()) {
            ordered.add(DeliveryChannel.valueOf(deliveryProperties.getDefaultChannel().toUpperCase()));
        }
        return List.copyOf(ordered);
    }
}
