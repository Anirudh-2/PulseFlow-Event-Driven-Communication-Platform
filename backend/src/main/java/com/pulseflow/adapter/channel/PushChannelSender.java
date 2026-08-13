package com.pulseflow.adapter.channel;

import com.pulseflow.domain.port.ChannelSender;
import org.springframework.stereotype.Component;

@Component
public class PushChannelSender implements ChannelSender {
    @Override
    public String channelTypeCode() {
        return "PUSH";
    }

    @Override
    public void send(DeliveryContext context) {
        throw new IllegalStateException("Channel PUSH is not yet implemented");
    }
}
