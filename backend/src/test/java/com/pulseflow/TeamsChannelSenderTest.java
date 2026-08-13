package com.pulseflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.pulseflow.adapter.channel.TeamsChannelSender;
import com.pulseflow.domain.entity.Notification;
import com.pulseflow.domain.entity.NotificationRecipient;
import com.pulseflow.domain.port.ChannelException;
import com.pulseflow.domain.port.ChannelSender;
import com.pulseflow.integration.TeamsDeliveryService;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TeamsChannelSenderTest {

    @Test
    void shouldDelegateAdaptiveCardDelivery() throws ChannelException {
        TeamsDeliveryService teamsDeliveryService = Mockito.mock(TeamsDeliveryService.class);
        TeamsChannelSender sender = new TeamsChannelSender(teamsDeliveryService);
        Notification notification = new Notification();
        notification.setTitle("fallback");
        notification.setBody("fallback-body");
        NotificationRecipient recipient = new NotificationRecipient();
        var context = new ChannelSender.DeliveryContext(
                "default", notification, recipient, Map.of("webhook_url", "https://example.test"), null, "subject", "body");

        sender.send(context);
        verify(teamsDeliveryService).sendAdaptiveCard(eq("subject"), eq("body"), any());
    }

    @Test
    void shouldWrapTeamsServiceFailure() {
        TeamsDeliveryService teamsDeliveryService = Mockito.mock(TeamsDeliveryService.class);
        doThrow(new IllegalStateException("boom"))
                .when(teamsDeliveryService)
                .sendAdaptiveCard(any(), any(), any());
        TeamsChannelSender sender = new TeamsChannelSender(teamsDeliveryService);
        var context = new ChannelSender.DeliveryContext(
                "default", new Notification(), new NotificationRecipient(), Map.of(), null, "subject", "body");

        Assertions.assertThrows(ChannelException.class, () -> sender.send(context));
    }
}
