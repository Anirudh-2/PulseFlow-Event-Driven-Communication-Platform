package com.pulseflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulseflow.config.DeliveryProperties;
import com.pulseflow.domain.entity.Notification;
import com.pulseflow.domain.enums.DeliveryChannel;
import com.pulseflow.dto.CreateEventRequest;
import com.pulseflow.messaging.NotificationEventPublisher;
import com.pulseflow.repository.IntegrationSourceRepository;
import com.pulseflow.repository.NotificationAuditLogRepository;
import com.pulseflow.repository.NotificationRecipientRepository;
import com.pulseflow.repository.NotificationRepository;
import com.pulseflow.service.ChannelResolutionService;
import com.pulseflow.service.NotificationDeliveryExecutor;
import com.pulseflow.service.impl.NotificationServiceImpl;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NotificationServiceTest {
    @Test
    void shouldCreateNotificationWhenNoDuplicateExists() {
        NotificationRepository notificationRepository = Mockito.mock(NotificationRepository.class);
        NotificationRecipientRepository recipientRepository = Mockito.mock(NotificationRecipientRepository.class);
        NotificationAuditLogRepository auditRepository = Mockito.mock(NotificationAuditLogRepository.class);
        ChannelResolutionService channelResolutionService = Mockito.mock(ChannelResolutionService.class);
        NotificationDeliveryExecutor deliveryExecutor = Mockito.mock(NotificationDeliveryExecutor.class);
        NotificationEventPublisher publisher = Mockito.mock(NotificationEventPublisher.class);
        IntegrationSourceRepository integrationSourceRepository = Mockito.mock(IntegrationSourceRepository.class);

        NotificationServiceImpl service = new NotificationServiceImpl(
                notificationRepository,
                recipientRepository,
                auditRepository,
                channelResolutionService,
                deliveryExecutor,
                publisher,
                integrationSourceRepository,
                new DeliveryProperties());

        when(notificationRepository.findByTenantIdAndSourceServiceAndSourceEventId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(recipientRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(channelResolutionService.resolveChannels(any(), any(), any(), any(), any()))
                .thenReturn(List.of(DeliveryChannel.WEBSOCKET));
        var response = service.processEvent(new CreateEventRequest(
                "default", "order_created", "orders", "evt-1", "u123", null, null, null, "EMPLOYEE", Map.of("amount", 1500)));
        assertEquals("default", response.tenantId());
        verify(deliveryExecutor).dispatchDeliveries(any(), any(), any(), any(), any());
    }
}
