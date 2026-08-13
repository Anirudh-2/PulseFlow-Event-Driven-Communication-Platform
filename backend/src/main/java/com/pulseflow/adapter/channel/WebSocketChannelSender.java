package com.pulseflow.adapter.channel;

import com.pulseflow.domain.port.ChannelException;
import com.pulseflow.domain.port.ChannelSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebSocketChannelSender implements ChannelSender {
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketChannelSender(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public String channelTypeCode() {
        return "WEBSOCKET";
    }

    @Override
    public void send(DeliveryContext context) throws ChannelException {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/tenant/" + context.notification().getTenantId(), context.notification());
        } catch (Exception e) {
            throw new ChannelException("WebSocket delivery failed", e);
        }
    }
}
