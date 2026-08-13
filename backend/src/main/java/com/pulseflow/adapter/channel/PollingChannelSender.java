package com.pulseflow.adapter.channel;

import com.pulseflow.domain.port.ChannelSender;
import org.springframework.stereotype.Component;

@Component
public class PollingChannelSender implements ChannelSender {
    @Override
    public String channelTypeCode() {
        return "POLLING";
    }

    @Override
    public void send(DeliveryContext context) {
        throw new IllegalStateException("Channel POLLING is not yet implemented");
    }
}
