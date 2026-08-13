package com.pulseflow.adapter.channel;

import com.pulseflow.domain.port.ChannelException;
import com.pulseflow.domain.port.ChannelSender;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.net.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WebhookChannelSender implements ChannelSender {
    private static final Logger log = LoggerFactory.getLogger(WebhookChannelSender.class);

    private final RestClient restClient;

    public WebhookChannelSender() {
        // Explicit connect/read timeouts for outbound webhook delivery.
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public String channelTypeCode() {
        return "WEBHOOK";
    }

    @Override
    public void send(DeliveryContext context) throws ChannelException {
        Map<String, Object> cfg = context.channelConfig() == null ? Map.of() : context.channelConfig();
        Object urlObj = cfg.get("url");
        if (urlObj == null || urlObj.toString().isBlank()) {
            urlObj = cfg.get("webhook_url");
        }
        String url = urlObj == null ? null : urlObj.toString();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Skipped: WEBHOOK channel has no url in configuration");
        }
        var body = new LinkedHashMap<String, Object>();
        body.put("tenantId", context.tenantId());
        body.put("notificationId", context.notification().getId().toString());
        body.put("recipientUserId", context.recipient().getUserId());
        body.put("title", context.renderedSubject());
        body.put("body", context.renderedBody());
        body.put("metadata", context.notification().getMetadata());

        try {
            RestClient.RequestBodySpec req =
                    restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON);

            // Optional auth headers from channel_configurations.config_json:
            // - authType: "API_KEY" | "BASIC" | "BEARER"
            // - apiKeyHeader / apiKey
            // - username / password
            // - bearerToken
            String authType = firstNonBlank(cfg, "authType", "auth_type");
            if (authType != null) {
                switch (authType.trim().toUpperCase()) {
                    case "API_KEY" -> {
                        String header = firstNonBlank(cfg, "apiKeyHeader", "api_key_header");
                        String apiKey = firstNonBlank(cfg, "apiKey", "api_key");
                        if (header == null || apiKey == null) {
                            throw new IllegalStateException(
                                    "Skipped: WEBHOOK API_KEY auth requires apiKeyHeader and apiKey");
                        }
                        req = req.header(header, apiKey);
                    }
                    case "BASIC" -> {
                        String username = firstNonBlank(cfg, "username", "user");
                        String password = firstNonBlank(cfg, "password", "pass");
                        if (username == null || password == null) {
                            throw new IllegalStateException(
                                    "Skipped: WEBHOOK BASIC auth requires username and password");
                        }
                        String token = Base64.getEncoder()
                                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
                        req = req.header("Authorization", "Basic " + token);
                    }
                    case "BEARER" -> {
                        String bearer = firstNonBlank(cfg, "bearerToken", "token", "accessToken");
                        if (bearer == null) {
                            throw new IllegalStateException(
                                    "Skipped: WEBHOOK BEARER auth requires bearerToken");
                        }
                        req = req.header("Authorization", "Bearer " + bearer);
                    }
                    default -> throw new IllegalStateException(
                            "Skipped: unsupported WEBHOOK authType: " + authType);
                }
            }

            // Allow arbitrary custom headers via configJson.headers map.
            Object headersObj = cfg.get("headers");
            if (headersObj instanceof Map<?, ?> headers) {
                for (var e : headers.entrySet()) {
                    if (e.getKey() == null || e.getValue() == null) {
                        continue;
                    }
                    req = req.header(e.getKey().toString(), e.getValue().toString());
                }
            }

            req.body(body).retrieve().toBodilessEntity();
            log.info("Webhook delivered to {} for notification {}", url, context.notification().getId());
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new ChannelException("Webhook POST failed", e);
        }
    }

    private static String firstNonBlank(Map<String, Object> config, String... keys) {
        if (config == null) {
            return null;
        }
        for (String key : keys) {
            Object v = config.get(key);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return null;
    }
}
