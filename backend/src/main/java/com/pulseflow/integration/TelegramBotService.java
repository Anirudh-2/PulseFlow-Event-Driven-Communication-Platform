package com.pulseflow.integration;

import com.pulseflow.repository.TenantIntegrationConfigRepository;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class TelegramBotService {
    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    private final RestClient restClient;
    private final TenantIntegrationConfigRepository configRepository;

    public TelegramBotService(TenantIntegrationConfigRepository configRepository) {
        this.configRepository = configRepository;
        this.restClient = RestClient.create();
    }

    public void sendMessage(String tenantId, String chatId, String text) {
        sendMessage(tenantId, chatId, text, null);
    }

    /**
     * @param telegramConfigOverride optional per-delivery config from {@code tenant_channel_configs} (same shape as legacy
     *     {@code telegram} JSON: enabled, botToken, apiBase, parseMode).
     */
    public void sendMessage(String tenantId, String chatId, String text, Map<String, Object> telegramConfigOverride) {
        Map<String, Object> telegram;
        if (telegramConfigOverride != null && !telegramConfigOverride.isEmpty()) {
            telegram = telegramConfigOverride;
        } else {
            var config = configRepository.findById(tenantId)
                    .orElseThrow(() -> new IllegalStateException("No integration config found for tenant: " + tenantId));
            telegram = config.getTelegram();
        }
        boolean enabled = booleanValue(telegram.get("enabled"), false);
        if (!enabled) {
            log.info("Telegram integration is disabled; skipping message delivery");
            return;
        }
        String botToken = stringValue(telegram.get("botToken"));
        if (botToken == null || botToken.isBlank()) {
            throw new IllegalStateException("Telegram integration enabled but botToken is missing in tenant config");
        }
        if (chatId == null || chatId.isBlank()) {
            throw new IllegalArgumentException("Telegram chatId is required for TELEGRAM delivery");
        }
        String apiBase = stringValueOrDefault(telegram.get("apiBase"), "https://api.telegram.org");
        String parseMode = stringValueOrDefault(telegram.get("parseMode"), "Markdown");

        String endpoint = apiBase + "/bot" + botToken + "/sendMessage";
        var payload = Map.of(
                "chat_id", chatId,
                "text", text,
                "parse_mode", parseMode
        );

        restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static String stringValueOrDefault(Object value, String fallback) {
        String text = stringValue(value);
        return text == null || text.isBlank() ? fallback : text;
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
