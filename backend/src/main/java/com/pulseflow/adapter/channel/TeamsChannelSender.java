package com.pulseflow.adapter.channel;

import com.pulseflow.domain.port.ChannelException;
import com.pulseflow.domain.port.ChannelSender;
import com.pulseflow.integration.TeamsDeliveryService;
import org.springframework.stereotype.Component;

@Component
public class TeamsChannelSender implements ChannelSender {
    private final TeamsDeliveryService teamsDeliveryService;

    public TeamsChannelSender(TeamsDeliveryService teamsDeliveryService) {
        this.teamsDeliveryService = teamsDeliveryService;
    }

    @Override
    public String channelTypeCode() {
        return "TEAMS";
    }

    @Override
    public void send(DeliveryContext context) throws ChannelException {
        String subject = context.renderedSubject() != null ? context.renderedSubject() : context.notification().getTitle();
        String body = context.renderedBody() != null ? context.renderedBody() : context.notification().getBody();
        try {
            teamsDeliveryService.sendAdaptiveCard(subject, body, context.channelConfig());
        } catch (RuntimeException e) {
            throw new ChannelException("Teams delivery failed", e);
        }
    }
}
