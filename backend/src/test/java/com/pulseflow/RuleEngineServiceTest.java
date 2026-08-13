package com.pulseflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseflow.domain.entity.NotificationRule;
import com.pulseflow.domain.enums.NotificationType;
import com.pulseflow.repository.NotificationRuleRepository;
import com.pulseflow.repository.RoutingRuleRepository;
import com.pulseflow.service.JsonConditionEvaluator;
import com.pulseflow.service.impl.RuleEngineServiceImpl;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RuleEngineServiceTest {
    @Test
    void shouldMatchRuleByRoleAndMinAmountCondition() {
        NotificationRuleRepository repository = Mockito.mock(NotificationRuleRepository.class);
        RoutingRuleRepository routingRuleRepository = Mockito.mock(RoutingRuleRepository.class);
        JsonConditionEvaluator jsonConditionEvaluator = new JsonConditionEvaluator(new ObjectMapper());
        RuleEngineServiceImpl service =
                new RuleEngineServiceImpl(repository, routingRuleRepository, jsonConditionEvaluator);

        NotificationRule rule = new NotificationRule();
        rule.setRoleName("EMPLOYEE");
        rule.setNotificationType(NotificationType.WORKFLOW);
        rule.setConditions(Map.of("min_amount", 1000));

        when(repository.findByTenantIdAndIsActiveTrueOrderByEvalOrderAsc("default")).thenReturn(List.of(rule));
        when(routingRuleRepository.findByTenantIdAndIsActiveTrueOrderByEvalOrderAsc("default")).thenReturn(List.of());

        var result = service.matchedRules("default", "EMPLOYEE", NotificationType.WORKFLOW, Map.of("amount", 1500));
        assertEquals(1, result.size());
    }
}
