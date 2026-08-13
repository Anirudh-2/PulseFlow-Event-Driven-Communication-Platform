package com.pulseflow.adapter.channel;

import com.pulseflow.domain.port.ChannelException;
import com.pulseflow.domain.port.ChannelSender;
import com.pulseflow.integration.TelegramDeliveryService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TelegramChannelSender implements ChannelSender {
    private final TelegramDeliveryService telegramDeliveryService;

    public TelegramChannelSender(TelegramDeliveryService telegramDeliveryService) {
        this.telegramDeliveryService = telegramDeliveryService;
    }

    @Override
    public String channelTypeCode() {
        return "TELEGRAM";
    }

    @Override
    public void send(DeliveryContext context) throws ChannelException {
        try {
            Map<String, Object> config = new HashMap<>();
            if (context.channelConfig() != null) {
                config.putAll(context.channelConfig());
            }
            String recipientChatId = context.recipient().getTelegramChatId();
            if (recipientChatId != null && !recipientChatId.isBlank()) {
                config.putIfAbsent("chat_id", recipientChatId);
                config.putIfAbsent("chatId", recipientChatId);
            }
            telegramDeliveryService.sendMessage(
                    context.renderedSubject() != null ? context.renderedSubject() : context.notification().getTitle(),
                    context.renderedBody() != null ? context.renderedBody() : context.notification().getBody(),
                    config);
        } catch (RuntimeException e) {
            throw new ChannelException("Telegram delivery failed", e);
        }
    }
}
