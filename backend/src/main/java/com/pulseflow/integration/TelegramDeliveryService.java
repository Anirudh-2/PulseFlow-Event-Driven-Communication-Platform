package com.pulseflow.integration;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class TelegramDeliveryService {
    private final RestClient restClient;

    public TelegramDeliveryService() {
        this.restClient = RestClient.create();
    }

    public void sendMessage(String renderedSubject, String renderedBody, Map<String, Object> config) {
        String token = firstNonBlank(config, "bot_token", "botToken");
        String chatId = firstNonBlank(config, "chat_id", "chatId");
        if (token == null) {
            throw new IllegalArgumentException("Missing required Telegram config key: bot_token");
        }
        if (chatId == null) {
            throw new IllegalArgumentException("Missing required Telegram config key: chat_id");
        }
        String text = (renderedSubject == null || renderedSubject.isBlank())
                ? safe(renderedBody)
                : renderedSubject + "\n\n" + safe(renderedBody);
        String apiBase = firstNonBlank(config, "api_base", "apiBase");
        if (apiBase == null || apiBase.isBlank()) {
            apiBase = "https://api.telegram.org";
        }
        String endpoint = apiBase.replaceAll("/$", "") + "/bot" + token + "/sendMessage";
        String parseMode = firstNonBlank(config, "parseMode", "parse_mode");
        Map<String, Object> payload =
                parseMode == null || parseMode.isBlank()
                        ? Map.of("chat_id", chatId, "text", text)
                        : Map.of("chat_id", chatId, "text", text, "parse_mode", parseMode);
        restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    private static String firstNonBlank(Map<String, Object> config, String... keys) {
        if (config == null) {
            return null;
        }
        for (String key : keys) {
            Object value = config.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
