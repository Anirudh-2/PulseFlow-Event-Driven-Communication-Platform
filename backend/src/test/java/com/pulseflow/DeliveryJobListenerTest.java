package com.pulseflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulseflow.config.DeliveryProperties;
import com.pulseflow.domain.entity.NotificationDeliveryLog;
import com.pulseflow.domain.entity.Notification;
import com.pulseflow.domain.entity.NotificationRecipient;
import com.pulseflow.domain.enums.DeliveryChannel;
import com.pulseflow.domain.enums.NotificationStatus;
import com.pulseflow.messaging.DeliveryJobListener;
import com.pulseflow.messaging.DeliveryJobMessage;
import com.pulseflow.messaging.DeliveryJobPublisher;
import com.pulseflow.repository.NotificationDeliveryLogRepository;
import com.pulseflow.repository.NotificationFailureRepository;
import com.pulseflow.repository.NotificationRecipientRepository;
import com.pulseflow.repository.NotificationRepository;
import com.pulseflow.service.NotificationDeliveryExecutor;
import com.pulseflow.service.TemplateRenderService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class DeliveryJobListenerTest {
    @Test
    void shouldRenderTemplateInConsumerForSpecificChannel() {
        NotificationRepository notificationRepository = Mockito.mock(NotificationRepository.class);
        NotificationRecipientRepository recipientRepository = Mockito.mock(NotificationRecipientRepository.class);
        NotificationDeliveryLogRepository deliveryLogRepository = Mockito.mock(NotificationDeliveryLogRepository.class);
        NotificationFailureRepository notificationFailureRepository = Mockito.mock(NotificationFailureRepository.class);
        NotificationDeliveryExecutor deliveryExecutor = Mockito.mock(NotificationDeliveryExecutor.class);
        DeliveryJobPublisher deliveryJobPublisher = Mockito.mock(DeliveryJobPublisher.class);
        TemplateRenderService templateRenderService = Mockito.mock(TemplateRenderService.class);
        DeliveryProperties deliveryProperties = new DeliveryProperties();

        DeliveryJobListener listener = new DeliveryJobListener(
                notificationRepository,
                recipientRepository,
                deliveryLogRepository,
                notificationFailureRepository,
                deliveryExecutor,
                deliveryJobPublisher,
                templateRenderService,
                deliveryProperties,
                Mockito.mock(com.pulseflow.metrics.DeliveryMetrics.class));

        UUID notificationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID integrationSourceId = UUID.randomUUID();
        UUID tenantChannelConfigId = UUID.randomUUID();

        Notification notification = new Notification();
        notification.setId(notificationId);
        notification.setTenantId("default");
        notification.setEventType("ORDER_CREATED");
        notification.setSourceService("orders");
        notification.setMetadata(Map.of("orderId", "o1"));

        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId(recipientId);
        recipient.setTenantId("default");
        recipient.setUserId("u1");
        recipient.setUserEmail("u1@example.com");
        recipient.setRoleName("EMPLOYEE");

        DeliveryJobMessage job = new DeliveryJobMessage(
                UUID.randomUUID(),
                "default",
                notificationId,
                recipientId,
                DeliveryChannel.TEAMS.name(),
                tenantChannelConfigId,
                "en",
                integrationSourceId,
                0);

        when(notificationRepository.findById(notificationId)).thenReturn(java.util.Optional.of(notification));
        when(recipientRepository.findById(recipientId)).thenReturn(java.util.Optional.of(recipient));

        when(templateRenderService.render(
                        any(), any(), any(), any(), any(), any()))
                .thenReturn(new TemplateRenderService.RenderedMessage("subj", "body"));

        listener.handleDelivery(job);

        ArgumentCaptor<Map<String, Object>> ctxCaptor = ArgumentCaptor.forClass(Map.class);
        verify(templateRenderService).render(
                eq("default"),
                eq("ORDER_CREATED"),
                eq("TEAMS"),
                eq("en"),
                eq(integrationSourceId),
                ctxCaptor.capture());

        var ctx = ctxCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("o1", ctx.get("orderId"));
        org.junit.jupiter.api.Assertions.assertEquals("u1", ctx.get("userId"));
        org.junit.jupiter.api.Assertions.assertEquals("orders", ctx.get("sourceService"));
        org.junit.jupiter.api.Assertions.assertEquals("ORDER_CREATED", ctx.get("eventType"));

        verify(deliveryExecutor).deliverRendered(
                eq(notification),
                eq(recipient),
                eq(DeliveryChannel.TEAMS),
                eq("subj"),
                eq("body"),
                isNull(),
                eq(integrationSourceId),
                eq(tenantChannelConfigId),
                eq(0));
    }

    @Test
    void shouldSkipEmailDeliveryWhenEmailTemplateMissing() {
        NotificationRepository notificationRepository = Mockito.mock(NotificationRepository.class);
        NotificationRecipientRepository recipientRepository = Mockito.mock(NotificationRecipientRepository.class);
        NotificationDeliveryLogRepository deliveryLogRepository = Mockito.mock(NotificationDeliveryLogRepository.class);
        NotificationFailureRepository notificationFailureRepository = Mockito.mock(NotificationFailureRepository.class);
        NotificationDeliveryExecutor deliveryExecutor = Mockito.mock(NotificationDeliveryExecutor.class);
        DeliveryJobPublisher deliveryJobPublisher = Mockito.mock(DeliveryJobPublisher.class);
        TemplateRenderService templateRenderService = Mockito.mock(TemplateRenderService.class);
        DeliveryProperties deliveryProperties = new DeliveryProperties();

        DeliveryJobListener listener = new DeliveryJobListener(
                notificationRepository,
                recipientRepository,
                deliveryLogRepository,
                notificationFailureRepository,
                deliveryExecutor,
                deliveryJobPublisher,
                templateRenderService,
                deliveryProperties,
                Mockito.mock(com.pulseflow.metrics.DeliveryMetrics.class));

        UUID notificationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        Notification notification = new Notification();
        notification.setId(notificationId);
        notification.setTenantId("default");
        notification.setEventType("ORDER_CREATED");
        notification.setSourceService("orders");

        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId(recipientId);
        recipient.setTenantId("default");
        recipient.setUserId("u1");
        recipient.setUserEmail("u1@example.com");
        recipient.setRoleName("EMPLOYEE");

        DeliveryJobMessage job = new DeliveryJobMessage(
                UUID.randomUUID(),
                "default",
                notificationId,
                recipientId,
                DeliveryChannel.EMAIL.name(),
                null,
                "en",
                null,
                0);

        when(notificationRepository.findById(notificationId)).thenReturn(java.util.Optional.of(notification));
        when(recipientRepository.findById(recipientId)).thenReturn(java.util.Optional.of(recipient));
        when(templateRenderService.resolveTemplate("default", "ORDER_CREATED", "EMAIL", "en", null)).thenReturn(null);

        listener.handleDelivery(job);

        verify(templateRenderService, never()).render(any(), any(), any(), any(), any(), any());
        verify(deliveryExecutor, never()).deliverRendered(any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void shouldPublishRetryOnFailure() {
        NotificationRepository notificationRepository = Mockito.mock(NotificationRepository.class);
        NotificationRecipientRepository recipientRepository = Mockito.mock(NotificationRecipientRepository.class);
        NotificationDeliveryLogRepository deliveryLogRepository = Mockito.mock(NotificationDeliveryLogRepository.class);
        NotificationFailureRepository notificationFailureRepository = Mockito.mock(NotificationFailureRepository.class);
        NotificationDeliveryExecutor deliveryExecutor = Mockito.mock(NotificationDeliveryExecutor.class);
        DeliveryJobPublisher deliveryJobPublisher = Mockito.mock(DeliveryJobPublisher.class);
        TemplateRenderService templateRenderService = Mockito.mock(TemplateRenderService.class);
        DeliveryProperties deliveryProperties = new DeliveryProperties();

        DeliveryJobListener listener = new DeliveryJobListener(
                notificationRepository,
                recipientRepository,
                deliveryLogRepository,
                notificationFailureRepository,
                deliveryExecutor,
                deliveryJobPublisher,
                templateRenderService,
                deliveryProperties,
                Mockito.mock(com.pulseflow.metrics.DeliveryMetrics.class));

        UUID notificationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(notificationId);
        notification.setTenantId("default");
        notification.setEventType("ORDER_CREATED");
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId(recipientId);
        recipient.setUserId("u1");

        DeliveryJobMessage job =
                new DeliveryJobMessage(UUID.randomUUID(), "default", notificationId, recipientId, "TELEGRAM", null, "en", null, 1);

        when(notificationRepository.findById(notificationId)).thenReturn(java.util.Optional.of(notification));
        when(recipientRepository.findById(recipientId)).thenReturn(java.util.Optional.of(recipient));
        when(templateRenderService.render(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TemplateRenderService.RenderedMessage("s", "b"));
        Mockito.doThrow(new IllegalStateException("fail"))
                .when(deliveryExecutor)
                .deliverRendered(any(), any(), any(), any(), any(), any(), any(), any(), anyInt());

        listener.handleDelivery(job);
        verify(deliveryJobPublisher).publishRetry30s(any(DeliveryJobMessage.class));
    }

    @Test
    void shouldMarkNotificationDeadLetteredAfterFinalFailure() {
        NotificationRepository notificationRepository = Mockito.mock(NotificationRepository.class);
        NotificationRecipientRepository recipientRepository = Mockito.mock(NotificationRecipientRepository.class);
        NotificationDeliveryLogRepository deliveryLogRepository = Mockito.mock(NotificationDeliveryLogRepository.class);
        NotificationFailureRepository notificationFailureRepository = Mockito.mock(NotificationFailureRepository.class);
        NotificationDeliveryExecutor deliveryExecutor = Mockito.mock(NotificationDeliveryExecutor.class);
        DeliveryJobPublisher deliveryJobPublisher = Mockito.mock(DeliveryJobPublisher.class);
        TemplateRenderService templateRenderService = Mockito.mock(TemplateRenderService.class);
        DeliveryProperties deliveryProperties = new DeliveryProperties();

        DeliveryJobListener listener = new DeliveryJobListener(
                notificationRepository,
                recipientRepository,
                deliveryLogRepository,
                notificationFailureRepository,
                deliveryExecutor,
                deliveryJobPublisher,
                templateRenderService,
                deliveryProperties,
                Mockito.mock(com.pulseflow.metrics.DeliveryMetrics.class));

        UUID notificationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(notificationId);
        notification.setTenantId("default");
        notification.setEventType("ORDER_CREATED");
        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId(recipientId);
        recipient.setUserId("u1");

        DeliveryJobMessage job =
                new DeliveryJobMessage(UUID.randomUUID(), "default", notificationId, recipientId, "TEAMS", null, "en", null, 3);

        when(notificationRepository.findById(notificationId)).thenReturn(java.util.Optional.of(notification));
        when(recipientRepository.findById(recipientId)).thenReturn(java.util.Optional.of(recipient));
        when(templateRenderService.render(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TemplateRenderService.RenderedMessage("s", "b"));
        Mockito.doThrow(new IllegalStateException("fail"))
                .when(deliveryExecutor)
                .deliverRendered(any(), any(), any(), any(), any(), any(), any(), any(), anyInt());

        listener.handleDelivery(job);

        ArgumentCaptor<NotificationDeliveryLog> logCaptor = ArgumentCaptor.forClass(NotificationDeliveryLog.class);
        verify(deliveryLogRepository).save(logCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                com.pulseflow.domain.enums.DeliveryStatus.DEAD_LETTERED, logCaptor.getValue().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(NotificationStatus.DEAD_LETTERED, notification.getStatus());
        verify(notificationRepository).save(notification);
    }
}

