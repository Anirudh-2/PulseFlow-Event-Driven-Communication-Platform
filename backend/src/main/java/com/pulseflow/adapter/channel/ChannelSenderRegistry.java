package com.pulseflow.adapter.channel;

import com.pulseflow.domain.port.ChannelSender;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ChannelSenderRegistry {
    private final Map<String, ChannelSender> byCode;

    public ChannelSenderRegistry(List<ChannelSender> senders) {
        this.byCode = senders.stream()
                .collect(Collectors.toMap(s -> s.channelTypeCode().toUpperCase(Locale.ROOT), Function.identity(), (a, b) -> a));
    }

    public ChannelSender require(String channelTypeCode) {
        if (channelTypeCode == null) {
            throw new IllegalArgumentException("channelTypeCode is required");
        }
        ChannelSender sender = byCode.get(channelTypeCode.toUpperCase(Locale.ROOT));
        if (sender == null) {
            throw new IllegalStateException("No channel adapter registered for: " + channelTypeCode);
        }
        return sender;
    }
}
