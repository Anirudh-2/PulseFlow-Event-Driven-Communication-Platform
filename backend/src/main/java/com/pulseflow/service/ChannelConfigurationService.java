package com.pulseflow.service;

import com.pulseflow.domain.entity.ChannelConfiguration;
import com.pulseflow.repository.ChannelConfigurationRepository;
import com.pulseflow.repository.IntegrationSourceRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

@Service
public class ChannelConfigurationService {
    private static final Set<String> ALLOWED_CHANNELS = Set.of("teams", "whatsapp", "telegram", "smtp", "webhook");

    private final ChannelConfigurationRepository repository;
    private final IntegrationSourceRepository integrationSourceRepository;

    public ChannelConfigurationService(
            ChannelConfigurationRepository repository,
            IntegrationSourceRepository integrationSourceRepository) {
        this.repository = repository;
        this.integrationSourceRepository = integrationSourceRepository;
    }

    public List<ChannelConfiguration> list(String tenantId) {
        return repository.findByTenantIdOrderByChannelTypeAscCreatedAtDesc(tenantId);
    }

    public Map<String, Object> testConnection(ChannelConfiguration cfg) {
        return switch (cfg.getChannelType()) {
            case "webhook" -> testWebhook(cfg.getConfigJson());
            case "smtp" -> testSmtp(cfg.getConfigJson());
            case "teams" -> testTeams(cfg.getConfigJson());
            case "telegram" -> testTelegram(cfg.getConfigJson());
            case "whatsapp" -> testWhatsapp(cfg.getConfigJson());
            default -> Map.of("ok", false, "message", "Unsupported channel type");
        };
    }

    private Map<String, Object> testTeams(Map<String, Object> config) {
        String webhookUrl = firstNonBlank(config, "webhook_url", "webhookUrl");
        if (webhookUrl == null) {
            return Map.of("ok", false, "message", "Missing required key: webhook_url");
        }
        try {
            // Teams incoming webhooks are typically POST-only; we still do a lightweight GET
            // to validate reachability + TLS + basic routing. 405 is considered "reachable".
            URI uri = URI.create(webhookUrl);
            HttpClient client =
                    HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(3))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(4)).GET().build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            int code = resp.statusCode();
            if (code == 405) {
                return Map.of("ok", true, "message", "Teams webhook is reachable (GET => 405 Method Not Allowed).");
            }
            if (code >= 200 && code < 500) {
                return Map.of("ok", true, "message", "Teams webhook is reachable (HTTP " + code + ").");
            }
            return Map.of("ok", false, "message", "Teams webhook returned unexpected HTTP status: " + code);
        } catch (Exception ex) {
            return Map.of("ok", false, "message", "Teams webhook validation failed: " + ex.getMessage());
        }
    }

    private Map<String, Object> testTelegram(Map<String, Object> config) {
        if (firstNonBlank(config, "bot_token", "botToken") == null) {
            return Map.of("ok", false, "message", "Missing required key: bot_token");
        }
        if (firstNonBlank(config, "chat_id", "chatId") == null) {
            return Map.of(
                    "ok",
                    true,
                    "message",
                    "Telegram bot token present; chat_id can be set per recipient (telegramChatId)");
        }
        return Map.of("ok", true, "message", "Telegram configuration is valid");
    }

    private Map<String, Object> testWhatsapp(Map<String, Object> config) {
        if (firstNonBlank(config, "accountSid") == null
                || firstNonBlank(config, "authToken") == null
                || firstNonBlank(config, "whatsappFrom", "from") == null) {
            return Map.of(
                    "ok",
                    false,
                    "message",
                    "Missing required keys: accountSid, authToken, whatsappFrom (or from)");
        }
        return Map.of("ok", true, "message", "WhatsApp/Twilio configuration is valid");
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

    public ChannelConfiguration create(String tenantId, UUID appId, String channelType, Map<String, Object> configJson, boolean isActive) {
        validateChannelType(channelType);
        var app = integrationSourceRepository
                .findById(appId)
                .filter(x -> x.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Integration source not found for tenant"));
        var entity = new ChannelConfiguration();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setApp(app);
        entity.setChannelType(channelType.toLowerCase());
        entity.setConfigJson(configJson == null ? Map.of() : configJson);
        entity.setIsActive(isActive);
        entity.setCreatedAt(java.time.OffsetDateTime.now());
        return repository.save(entity);
    }

    public ChannelConfiguration update(String tenantId, UUID id, UUID appId, String channelType, Map<String, Object> configJson, Boolean isActive) {
        var existing = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Channel configuration not found"));
        if (appId != null) {
            var app = integrationSourceRepository
                    .findById(appId)
                    .filter(x -> x.getTenantId().equals(tenantId))
                    .orElseThrow(() -> new IllegalArgumentException("Integration source not found for tenant"));
            existing.setApp(app);
        }
        if (channelType != null) {
            validateChannelType(channelType);
            existing.setChannelType(channelType.toLowerCase());
        }
        if (configJson != null) {
            existing.setConfigJson(configJson);
        }
        if (isActive != null) {
            existing.setIsActive(isActive);
        }
        repository.save(existing);
        // Re-load with EntityGraph so Jackson can serialize app without LazyInitializationException.
        return repository.findByIdAndTenantId(id, tenantId).orElse(existing);
    }

    public void delete(String tenantId, UUID id) {
        var existing = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Channel configuration not found"));
        repository.delete(existing);
    }

    public ChannelConfiguration getById(String tenantId, UUID id) {
        return repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Channel configuration not found"));
    }

    private void validateChannelType(String channelType) {
        String normalized = channelType == null ? "" : channelType.toLowerCase();
        if (!ALLOWED_CHANNELS.contains(normalized)) {
            throw new IllegalArgumentException("Invalid channel_type: " + channelType);
        }
    }

    private Map<String, Object> testWebhook(Map<String, Object> config) {
        Object url = config.get("url");
        if (url == null || url.toString().isBlank()) {
            return Map.of("ok", false, "message", "Missing webhook url");
        }
        return Map.of("ok", true, "message", "Webhook configuration is valid");
    }

    private Map<String, Object> testSmtp(Map<String, Object> config) {
        String host = str(config.get("host"));
        if (host == null || host.isBlank()) {
            return Map.of("ok", false, "message", "Missing SMTP host");
        }
        // Demo contract note: we currently don't support attachments/tracking pixel in EmailChannelSender/SmtpDeliveryService.
        if (config.containsKey("attachments")
                || config.containsKey("tracking")
                || config.containsKey("trackingPixel")
                || config.containsKey("openTracking")
                || config.containsKey("clickTracking")) {
            return Map.of(
                    "ok",
                    false,
                    "message",
                    "SMTP attachments/open-click tracking are not supported in this version. Remove those keys and retry test.");
        }
        try {
            var sender = new JavaMailSenderImpl();
            sender.setHost(host);
            sender.setPort(intVal(config.get("port"), 587));
            sender.setUsername(str(config.get("username")));
            sender.setPassword(str(config.get("password")));
            sender.testConnection();
            return Map.of("ok", true, "message", "SMTP connection successful");
        } catch (Exception ex) {
            return Map.of("ok", false, "message", "SMTP connection failed: " + ex.getMessage());
        }
    }

    private Map<String, Object> validateRequired(Map<String, Object> config, List<String> keys) {
        for (String key : keys) {
            Object v = config.get(key);
            if (v == null || v.toString().isBlank()) {
                return Map.of("ok", false, "message", "Missing required key: " + key);
            }
        }
        return Map.of("ok", true, "message", "Configuration is valid");
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static int intVal(Object v, int def) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? def : Integer.parseInt(v.toString());
        } catch (Exception e) {
            return def;
        }
    }
}
