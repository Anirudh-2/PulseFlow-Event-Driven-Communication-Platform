package com.pulseflow.adapter.channel;

import com.pulseflow.domain.port.ChannelException;
import com.pulseflow.domain.port.ChannelSender;
import com.pulseflow.integration.SmtpDeliveryService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailChannelSender implements ChannelSender {
    private static final Logger log = LoggerFactory.getLogger(EmailChannelSender.class);

    private final SmtpDeliveryService smtpDeliveryService;

    public EmailChannelSender(SmtpDeliveryService smtpDeliveryService) {
        this.smtpDeliveryService = smtpDeliveryService;
    }

    @Override
    public String channelTypeCode() {
        return "EMAIL";
    }

    @Override
    public void send(DeliveryContext context) throws ChannelException {
        String to = context.recipient().getUserEmail();
        if (to == null || to.isBlank()) {
            throw new IllegalStateException("Skipped: no email address for recipient");
        }
        Map<String, Object> cfg = context.channelConfig();
        String subject = context.renderedSubject() != null && !context.renderedSubject().isBlank()
                ? context.renderedSubject()
                : context.notification().getTitle();
        String html = context.renderedBody() != null && !context.renderedBody().isBlank()
                ? context.renderedBody()
                : context.notification().getBody();
        try {
            smtpDeliveryService.sendHtml(cfg, to, subject, html);
            log.info("Email sent to '{}' for notification '{}'", to, context.notification().getId());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new ChannelException("Email delivery failed", e);
        }
    }
}
