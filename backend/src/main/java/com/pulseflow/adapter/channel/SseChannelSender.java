package com.pulseflow.adapter.channel;

import com.pulseflow.domain.port.ChannelSender;
import org.springframework.stereotype.Component;

@Component
public class SseChannelSender implements ChannelSender {
    @Override
    public String channelTypeCode() {
        return "SSE";
    }

    @Override
    public void send(DeliveryContext context) {
        throw new IllegalStateException("Channel SSE is not yet implemented");
    }
}
