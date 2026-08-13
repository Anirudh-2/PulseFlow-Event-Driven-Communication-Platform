package com.pulseflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.pulseflow.adapter.channel.TelegramChannelSender;
import com.pulseflow.domain.entity.Notification;
import com.pulseflow.domain.entity.NotificationRecipient;
import com.pulseflow.domain.port.ChannelException;
import com.pulseflow.domain.port.ChannelSender;
import com.pulseflow.integration.TelegramDeliveryService;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TelegramChannelSenderTest {

    @Test
    void shouldDelegateTelegramDelivery() throws ChannelException {
        TelegramDeliveryService telegramDeliveryService = Mockito.mock(TelegramDeliveryService.class);
        TelegramChannelSender sender = new TelegramChannelSender(telegramDeliveryService);
        Notification notification = new Notification();
        notification.setTitle("fallback");
        notification.setBody("fallback-body");
        NotificationRecipient recipient = new NotificationRecipient();
        var context = new ChannelSender.DeliveryContext(
                "default",
                notification,
                recipient,
                Map.of("bot_token", "token", "chat_id", "123"),
                null,
                "subject",
                "body");

        sender.send(context);
        verify(telegramDeliveryService).sendMessage(eq("subject"), eq("body"), any());
    }

    @Test
    void shouldWrapTelegramServiceFailure() {
        TelegramDeliveryService telegramDeliveryService = Mockito.mock(TelegramDeliveryService.class);
        doThrow(new IllegalStateException("boom"))
                .when(telegramDeliveryService)
                .sendMessage(any(), any(), any());
        TelegramChannelSender sender = new TelegramChannelSender(telegramDeliveryService);
        var context = new ChannelSender.DeliveryContext(
                "default", new Notification(), new NotificationRecipient(), Map.of(), null, "subject", "body");

        Assertions.assertThrows(ChannelException.class, () -> sender.send(context));
    }
}
