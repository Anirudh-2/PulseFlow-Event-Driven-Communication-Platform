package com.pulseflow.integration;

import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class TeamsDeliveryService {
    private final RestClient restClient;

    public TeamsDeliveryService() {
        this.restClient = RestClient.create();
    }

    public void sendAdaptiveCard(String subject, String body, Map<String, Object> config) {
        String webhookUrl = firstNonBlank(config, "webhook_url", "webhookUrl", "url");
        if (webhookUrl == null) {
            throw new IllegalArgumentException("Missing required Teams config key: webhook_url");
        }
        Map<String, Object> payload = Map.of(
                "type", "message",
                "attachments", List.of(Map.of(
                        "contentType", "application/vnd.microsoft.card.adaptive",
                        "contentUrl", null,
                        "content", Map.of(
                                "$schema", "http://adaptivecards.io/schemas/adaptive-card.json",
                                "type", "AdaptiveCard",
                                "version", "1.4",
                                "body", List.of(
                                        Map.of("type", "TextBlock", "size", "Medium", "weight", "Bolder", "text", safe(subject)),
                                        Map.of("type", "TextBlock", "wrap", true, "text", safe(body)))))));
        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    private static String firstNonBlank(Map<String, Object> config, String... keys) {
        if (config == null) {
            throw new IllegalArgumentException("Missing channel configuration");
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
