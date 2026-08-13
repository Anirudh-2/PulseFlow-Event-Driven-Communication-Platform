package com.pulseflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulseflow.config.DeliveryProperties;
import com.pulseflow.domain.enums.DeliveryChannel;
import com.pulseflow.domain.enums.NotificationType;
import com.pulseflow.service.ChannelResolutionService;
import com.pulseflow.service.RuleEngineService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ChannelResolutionServiceTest {

    @Test
    void shouldResolveFromRoutingRulesOnly() {
        RuleEngineService ruleEngineService = Mockito.mock(RuleEngineService.class);
        ChannelResolutionService service = new ChannelResolutionService(ruleEngineService, new DeliveryProperties());
        UUID integrationSourceId = UUID.randomUUID();

        when(ruleEngineService.matchedRoutingChannels(
                        "default", "EMPLOYEE", "ORDER_CREATED", integrationSourceId, Map.of("amount", 1500)))
                .thenReturn(List.of(DeliveryChannel.TEAMS, DeliveryChannel.EMAIL));

        var result = service.resolveChannels(
                "default",
                "EMPLOYEE",
                Map.of("amount", 1500),
                "ORDER_CREATED",
                integrationSourceId);

        assertEquals(List.of(DeliveryChannel.TEAMS, DeliveryChannel.EMAIL), result);
        verify(ruleEngineService, never())
                .matchedRules(
                        "default",
                        "EMPLOYEE",
                        NotificationType.WORKFLOW,
                        Map.of("amount", 1500),
                        "ORDER_CREATED",
                        integrationSourceId);
    }

    @Test
    void shouldFallbackToWebsocketWhenNoRoutingRuleMatches() {
        RuleEngineService ruleEngineService = Mockito.mock(RuleEngineService.class);
        ChannelResolutionService service = new ChannelResolutionService(ruleEngineService, new DeliveryProperties());

        when(ruleEngineService.matchedRoutingChannels("default", "EMPLOYEE", "ORDER_CREATED", null, Map.of()))
                .thenReturn(List.of());

        var result = service.resolveChannels("default", "EMPLOYEE", Map.of(), "ORDER_CREATED", null);

        assertEquals(List.of(DeliveryChannel.WEBSOCKET), result);
    }
}
