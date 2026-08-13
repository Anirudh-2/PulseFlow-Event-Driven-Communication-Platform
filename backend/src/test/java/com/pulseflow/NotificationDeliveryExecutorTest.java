package com.pulseflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.pulseflow.config.DeliveryProperties;
import com.pulseflow.domain.entity.Notification;
import com.pulseflow.domain.entity.NotificationRecipient;
import com.pulseflow.domain.enums.DeliveryChannel;
import com.pulseflow.integration.ChannelDeliveryService;
import com.pulseflow.messaging.DeliveryJobPublisher;
import com.pulseflow.messaging.DeliveryJobMessage;
import com.pulseflow.service.TemplateRenderService;
import com.pulseflow.service.NotificationDeliveryExecutor;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;

class NotificationDeliveryExecutorTest {
    @Test
    void dispatchDeliveriesShouldNotRenderTemplatesAndOnlyPublishJobs() {
        TemplateRenderService templateRenderService = Mockito.mock(TemplateRenderService.class);
        ChannelDeliveryService channelDeliveryService = Mockito.mock(ChannelDeliveryService.class);
        DeliveryJobPublisher publisher = Mockito.mock(DeliveryJobPublisher.class);

        NotificationDeliveryExecutor executor = new NotificationDeliveryExecutor(
                channelDeliveryService,
                publisher,
                new DeliveryProperties());

        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setTenantId("default");
        notification.setEventType("ORDER_CREATED");
        notification.setSourceService("orders");
        notification.setMetadata(Map.of("orderId", "o1"));

        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setId(UUID.randomUUID());
        recipient.setTenantId("default");
        recipient.setUserId("u1");

        executor.dispatchDeliveries(
                notification,
                recipient,
                List.of(DeliveryChannel.TEAMS),
                "en",
                null);

        ArgumentCaptor<DeliveryJobMessage> jobCaptor = ArgumentCaptor.forClass(DeliveryJobMessage.class);
        verify(publisher).publish(jobCaptor.capture());

        DeliveryJobMessage job = jobCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(DeliveryChannel.TEAMS.name(), job.deliveryChannel());
        org.junit.jupiter.api.Assertions.assertEquals(notification.getId(), job.notificationId());
        org.junit.jupiter.api.Assertions.assertEquals(recipient.getId(), job.recipientId());
        org.junit.jupiter.api.Assertions.assertEquals(0, job.retryAttempt());

        verify(templateRenderService, never()).render(any(), any(), any(), any(), any(), any());
    }
}

