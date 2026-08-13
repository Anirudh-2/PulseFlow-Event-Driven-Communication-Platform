package com.pulseflow;

import static org.mockito.Mockito.verify;

import com.pulseflow.adapter.channel.EmailChannelSender;
import com.pulseflow.domain.entity.Notification;
import com.pulseflow.domain.entity.NotificationRecipient;
import com.pulseflow.domain.port.ChannelSender;
import com.pulseflow.integration.SmtpDeliveryService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EmailChannelSenderTest {
    @Test
    void shouldDelegateToSmtpDeliveryServiceWithHtmlBody() throws Exception {
        SmtpDeliveryService smtpDeliveryService = Mockito.mock(SmtpDeliveryService.class);
        EmailChannelSender sender = new EmailChannelSender(smtpDeliveryService);

        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setTitle("fallback-title");
        notification.setBody("fallback-body");

        NotificationRecipient recipient = new NotificationRecipient();
        recipient.setUserId("u1");
        recipient.setUserEmail("u1@example.com");

        var context = new ChannelSender.DeliveryContext(
                "default",
                notification,
                recipient,
                Map.of(
                        "host", "smtp.example.com",
                        "port", 587,
                        "username", "user",
                        "password", "pass",
                        "from_email", "no-reply@example.com"),
                null,
                "Rendered Subject",
                "<h1>Rendered HTML</h1>");

        sender.send(context);

        verify(smtpDeliveryService).sendHtml(
                context.channelConfig(),
                "u1@example.com",
                "Rendered Subject",
                "<h1>Rendered HTML</h1>");
    }
}
