package com.pulseflow.service.impl;

import com.pulseflow.domain.entity.NotificationRule;
import com.pulseflow.domain.enums.DeliveryChannel;
import com.pulseflow.domain.enums.NotificationType;
import com.pulseflow.repository.NotificationRuleRepository;
import com.pulseflow.repository.RoutingRuleRepository;
import com.pulseflow.service.JsonConditionEvaluator;
import com.pulseflow.service.RuleEngineService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class RuleEngineServiceImpl implements RuleEngineService {
    private final NotificationRuleRepository ruleRepository;
    private final RoutingRuleRepository routingRuleRepository;
    private final JsonConditionEvaluator jsonConditionEvaluator;

    public RuleEngineServiceImpl(
            NotificationRuleRepository ruleRepository,
            RoutingRuleRepository routingRuleRepository,
            JsonConditionEvaluator jsonConditionEvaluator) {
        this.ruleRepository = ruleRepository;
        this.routingRuleRepository = routingRuleRepository;
        this.jsonConditionEvaluator = jsonConditionEvaluator;
    }

    @Override
    public List<NotificationRule> matchedRules(String tenantId, String roleName, NotificationType type, Map<String, Object> payload) {
        return matchedRules(tenantId, roleName, type, payload, null, null);
    }

    @Override
    public List<NotificationRule> matchedRules(
            String tenantId,
            String roleName,
            NotificationType type,
            Map<String, Object> payload,
            String eventType,
            UUID integrationSourceId) {
        String normEvent = eventType == null ? null : eventType.toUpperCase();
        return ruleRepository.findByTenantIdAndIsActiveTrueOrderByEvalOrderAsc(tenantId).stream()
                .filter(rule -> rule.getRoleName().equalsIgnoreCase(roleName))
                .filter(rule -> rule.getNotificationType() == null || rule.getNotificationType() == type)
                .filter(rule -> rule.getEventType() == null
                        || rule.getEventType().isBlank()
                        || rule.getEventType().equalsIgnoreCase(normEvent))
                .filter(rule -> rule.getIntegrationSource() == null
                        || integrationSourceId == null
                        || integrationSourceId.equals(rule.getIntegrationSource().getId()))
                .filter(rule -> evaluateRuleConditions(rule, payload))
                .toList();
    }

    @Override
    @Cacheable(
            cacheNames = "rulesCache",
            key = "'rules:' + #tenantId + ':' + #eventType",
            unless = "#result == null || #result.isEmpty()")
    public List<DeliveryChannel> matchedRoutingChannels(
            String tenantId,
            String roleName,
            String eventType,
            UUID integrationSourceId,
            Map<String, Object> payload) {
        String normEvent = eventType == null ? null : eventType.toUpperCase();
        var channels = new LinkedHashSet<DeliveryChannel>();
        for (var rule : routingRuleRepository.findByTenantIdAndIsActiveTrueOrderByEvalOrderAsc(tenantId)) {
            if (rule.getRoleName() != null
                    && !rule.getRoleName().isBlank()
                    && !rule.getRoleName().equalsIgnoreCase(roleName)) {
                continue;
            }
            if (rule.getEventType() != null
                    && !rule.getEventType().isBlank()
                    && (normEvent == null || !rule.getEventType().equalsIgnoreCase(normEvent))) {
                continue;
            }
            if (rule.getIntegrationSource() != null
                    && integrationSourceId != null
                    && !integrationSourceId.equals(rule.getIntegrationSource().getId())) {
                continue;
            }
            if (rule.getIntegrationSource() != null && integrationSourceId == null) {
                continue;
            }
            if (!jsonConditionEvaluator.matches(rule.getConditionsJsonlogic(), payload)) {
                continue;
            }
            if (rule.getChannelTypeCodes() != null) {
                for (String code : rule.getChannelTypeCodes()) {
                    if (code == null || code.isBlank()) {
                        continue;
                    }
                    try {
                        channels.add(DeliveryChannel.valueOf(code.trim().toUpperCase()));
                    } catch (IllegalArgumentException ignored) {
                        // unknown channel code in DB — skip
                    }
                }
            }
        }
        return new ArrayList<>(channels);
    }

    private boolean evaluateRuleConditions(NotificationRule rule, Map<String, Object> payload) {
        if (rule.getConditionsJsonlogic() != null && !rule.getConditionsJsonlogic().isEmpty()) {
            return jsonConditionEvaluator.matches(rule.getConditionsJsonlogic(), payload);
        }
        return evaluateLegacyConditions(rule.getConditions(), payload);
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateLegacyConditions(Map<String, Object> conditions, Map<String, Object> payload) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        for (var entry : conditions.entrySet()) {
            if ("min_amount".equals(entry.getKey())) {
                var amount = ((Number) payload.getOrDefault("amount", 0)).doubleValue();
                if (amount < Double.parseDouble(entry.getValue().toString())) {
                    return false;
                }
            } else if (!entry.getValue().equals(payload.get(entry.getKey()))) {
                return false;
            }
        }
        return true;
    }
}
