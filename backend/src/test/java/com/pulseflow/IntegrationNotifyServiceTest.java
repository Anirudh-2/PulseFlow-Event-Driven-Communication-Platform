package com.pulseflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulseflow.config.DeliveryProperties;
import com.pulseflow.domain.entity.IntegrationSource;
import com.pulseflow.domain.entity.RoutingRule;
import com.pulseflow.domain.enums.DeliveryChannel;
import com.pulseflow.dto.notify.NotifyDryRunResponse;
import com.pulseflow.dto.notify.NotifyQueuedResponse;
import com.pulseflow.dto.notify.NotifyRequest;
import com.pulseflow.dto.notify.RecipientsInput;
import com.pulseflow.dto.notify.UserRecipientInput;
import com.pulseflow.messaging.NotificationEventPublisher;
import com.pulseflow.repository.IntegrationSourceRepository;
import com.pulseflow.repository.NotificationRecipientRepository;
import com.pulseflow.repository.NotificationRepository;
import com.pulseflow.repository.RoutingRuleRepository;
import com.pulseflow.service.IntegrationNotifyService;
import com.pulseflow.service.JsonConditionEvaluator;
import com.pulseflow.service.NotificationDeliveryExecutor;
import com.pulseflow.service.RuleEngineService;
import com.pulseflow.service.TemplateRenderService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IntegrationNotifyServiceTest {
    @Test
    void dryRunShouldReturnMatchedRulesAndRenderedPerRecipientPerChannel() {
        IntegrationSourceRepository sourceRepository = Mockito.mock(IntegrationSourceRepository.class);
        RoutingRuleRepository routingRuleRepository = Mockito.mock(RoutingRuleRepository.class);
        JsonConditionEvaluator jsonConditionEvaluator = Mockito.mock(JsonConditionEvaluator.class);
        NotificationRepository notificationRepository = Mockito.mock(NotificationRepository.class);
        NotificationRecipientRepository recipientRepository = Mockito.mock(NotificationRecipientRepository.class);
        NotificationDeliveryExecutor deliveryExecutor = Mockito.mock(NotificationDeliveryExecutor.class);
        TemplateRenderService templateRenderService = Mockito.mock(TemplateRenderService.class);
        RuleEngineService ruleEngineService = Mockito.mock(RuleEngineService.class);
        NotificationEventPublisher eventPublisher = Mockito.mock(NotificationEventPublisher.class);
        DeliveryProperties deliveryProperties = new DeliveryProperties();

        IntegrationNotifyService service = new IntegrationNotifyService(
                sourceRepository,
                routingRuleRepository,
                jsonConditionEvaluator,
                notificationRepository,
                recipientRepository,
                deliveryExecutor,
                templateRenderService,
                ruleEngineService,
                eventPublisher,
                deliveryProperties,
                "default");

        UUID sourceId = UUID.randomUUID();
        IntegrationSource source = new IntegrationSource();
        source.setId(sourceId);
        source.setTenantId("default");
        source.setSourceKey("HRMS");
        source.setIsActive(true);
        when(sourceRepository.findByTenantIdAndSourceKeyIgnoreCase("default", "HRMS")).thenReturn(Optional.of(source));

        RoutingRule rule = new RoutingRule();
        rule.setId(UUID.randomUUID());
        rule.setName("r1");
        rule.setRoleName("EMPLOYEE");
        rule.setEventType("ORDER_CREATED");
        rule.setEvalOrder((short) 10);
        rule.setChannelTypeCodes(new String[] {"EMAIL", "TEAMS"});
        rule.setIsActive(true);
        when(routingRuleRepository.findByTenantIdAndIsActiveTrueOrderByEvalOrderAsc("default")).thenReturn(List.of(rule));
        when(jsonConditionEvaluator.matches(any(), any())).thenReturn(true);
        when(notificationRepository.findByTenantIdAndSourceServiceAndSourceEventId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(ruleEngineService.matchedRoutingChannels(any(), any(), any(), any(), any()))
                .thenReturn(List.of(DeliveryChannel.EMAIL, DeliveryChannel.TEAMS));
        when(templateRenderService.render(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> new TemplateRenderService.RenderedMessage(
                        "s-" + inv.getArgument(2), "b-" + inv.getArgument(2)));

        NotifyRequest req = new NotifyRequest(
                "ORDER_CREATED",
                "evt-1",
                new RecipientsInput(
                        "ROLE_BASED",
                        null,
                        "EMPLOYEE",
                        null,
                        null,
                        List.of(
                                new UserRecipientInput("u1", "u1@x.com", "aad-1"),
                                new UserRecipientInput("u2", "u2@x.com", "aad-2"))),
                Map.of("amount", 10));

        Object result = service.notify("HRMS", null, true, req);
        NotifyDryRunResponse dry = (NotifyDryRunResponse) result;
        assertEquals("dry_run", dry.status());
        assertEquals(1, dry.matchedRules().size());
        assertTrue(dry.rendered().containsKey("u1"));
        assertTrue(dry.rendered().get("u1").containsKey("EMAIL"));
        assertTrue(dry.rendered().get("u1").containsKey("TEAMS"));
        verify(deliveryExecutor, never()).dispatchDeliveries(any(), any(), any(), any(), any());
    }

    @Test
    void queuedModeShouldPersistAndDispatchPerRecipient() {
        IntegrationSourceRepository sourceRepository = Mockito.mock(IntegrationSourceRepository.class);
        RoutingRuleRepository routingRuleRepository = Mockito.mock(RoutingRuleRepository.class);
        JsonConditionEvaluator jsonConditionEvaluator = Mockito.mock(JsonConditionEvaluator.class);
        NotificationRepository notificationRepository = Mockito.mock(NotificationRepository.class);
        NotificationRecipientRepository recipientRepository = Mockito.mock(NotificationRecipientRepository.class);
        NotificationDeliveryExecutor deliveryExecutor = Mockito.mock(NotificationDeliveryExecutor.class);
        TemplateRenderService templateRenderService = Mockito.mock(TemplateRenderService.class);
        RuleEngineService ruleEngineService = Mockito.mock(RuleEngineService.class);
        NotificationEventPublisher eventPublisher = Mockito.mock(NotificationEventPublisher.class);
        DeliveryProperties deliveryProperties = new DeliveryProperties();

        IntegrationNotifyService service = new IntegrationNotifyService(
                sourceRepository,
                routingRuleRepository,
                jsonConditionEvaluator,
                notificationRepository,
                recipientRepository,
                deliveryExecutor,
                templateRenderService,
                ruleEngineService,
                eventPublisher,
                deliveryProperties,
                "default");

        UUID sourceId = UUID.randomUUID();
        IntegrationSource source = new IntegrationSource();
        source.setId(sourceId);
        source.setTenantId("default");
        source.setSourceKey("HRMS");
        source.setIsActive(true);
        when(sourceRepository.findByTenantIdAndSourceKeyIgnoreCase("default", "HRMS")).thenReturn(Optional.of(source));

        RoutingRule rule = new RoutingRule();
        rule.setRoleName("EMPLOYEE");
        rule.setEventType("ORDER_CREATED");
        rule.setChannelTypeCodes(new String[] {"EMAIL"});
        rule.setIsActive(true);
        when(routingRuleRepository.findByTenantIdAndIsActiveTrueOrderByEvalOrderAsc("default")).thenReturn(List.of(rule));
        when(jsonConditionEvaluator.matches(any(), any())).thenReturn(true);
        when(notificationRepository.findByTenantIdAndSourceServiceAndSourceEventId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(ruleEngineService.matchedRoutingChannels(any(), any(), any(), any(), any()))
                .thenReturn(List.of(DeliveryChannel.EMAIL));
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(recipientRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NotifyRequest req = new NotifyRequest(
                "ORDER_CREATED",
                "evt-2",
                new RecipientsInput(
                        "ROLE_BASED",
                        null,
                        "EMPLOYEE",
                        null,
                        null,
                        List.of(
                                new UserRecipientInput("u1", "u1@x.com", null),
                                new UserRecipientInput("u2", "u2@x.com", null))),
                Map.of("amount", 10));

        Object out = service.notify("HRMS", null, false, req);
        NotifyQueuedResponse queued = (NotifyQueuedResponse) out;
        assertEquals("queued", queued.status());
        verify(deliveryExecutor, times(2)).dispatchDeliveries(any(), any(), any(), any(), any());
    }
}
