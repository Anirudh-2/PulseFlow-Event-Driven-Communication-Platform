package com.pulseflow.service;

import com.pulseflow.domain.entity.NotificationRule;
import com.pulseflow.domain.enums.DeliveryChannel;
import com.pulseflow.domain.enums.NotificationType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface RuleEngineService {
    List<NotificationRule> matchedRules(String tenantId, String roleName, NotificationType type, Map<String, Object> payload);

    List<NotificationRule> matchedRules(
            String tenantId,
            String roleName,
            NotificationType type,
            Map<String, Object> payload,
            String eventType,
            UUID integrationSourceId);

    List<DeliveryChannel> matchedRoutingChannels(
            String tenantId,
            String roleName,
            String eventType,
            UUID integrationSourceId,
            Map<String, Object> payload);
}
