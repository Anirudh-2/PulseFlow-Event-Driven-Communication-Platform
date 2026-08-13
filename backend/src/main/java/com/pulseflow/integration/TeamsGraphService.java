package com.pulseflow.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Delivers 1:1 Microsoft Teams chat messages via the Microsoft Graph API.
 *
 * <p>Credentials may come from environment defaults or per-tenant {@code tenant_channel_configs} JSON
 * (keys: clientId, clientSecret, tenantId, botUserId).
 */
@Service
public class TeamsGraphService {

    private static final Logger log = LoggerFactory.getLogger(TeamsGraphService.class);
    private static final String GRAPH_BASE = "https://graph.microsoft.com/v1.0";
    private static final String TOKEN_URL_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";

    private final String defaultClientId;
    private final String defaultClientSecret;
    private final String defaultTenantId;
    private final String defaultBotUserId;
    private final RestClient restClient;

    private final ConcurrentHashMap<String, AtomicReference<CachedToken>> tokenCaches = new ConcurrentHashMap<>();

    public TeamsGraphService(
            @Value("${app.integrations.graph.clientId:}") String clientId,
            @Value("${app.integrations.graph.clientSecret:}") String clientSecret,
            @Value("${app.integrations.graph.tenantId:}") String tenantId,
            @Value("${app.integrations.graph.botUserId:}") String botUserId) {
        this.defaultClientId = clientId;
        this.defaultClientSecret = clientSecret;
        this.defaultTenantId = tenantId;
        this.defaultBotUserId = botUserId;
        this.restClient = RestClient.create();
    }

    public void sendMessage(String userAadIdOrEmail, String content) {
        sendMessage(userAadIdOrEmail, content, null);
    }

    public void sendMessage(String userAadIdOrEmail, String content, Map<String, Object> teamsConfigOverride) {
        TeamsCredentials cred = TeamsCredentials.resolve(
                defaultClientId, defaultClientSecret, defaultTenantId, defaultBotUserId, teamsConfigOverride);
        if (!cred.complete()) {
            log.warn("Teams Graph integration is not configured. Skipping delivery.");
            return;
        }
        if (userAadIdOrEmail == null || userAadIdOrEmail.isBlank()) {
            log.warn("Target user AAD ID/email is blank. Skipping Teams delivery.");
            return;
        }

        try {
            String token = acquireToken(cred);
            String chatId = getOrCreateChat(token, cred, userAadIdOrEmail);
            postMessage(token, chatId, content);
            log.info("Teams message delivered to user '{}' via chat '{}'", userAadIdOrEmail, chatId);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to send Teams message to '" + userAadIdOrEmail + "': " + ex.getMessage(), ex);
        }
    }

    private String acquireToken(TeamsCredentials cred) {
        String cacheKey = cred.cacheKey();
        AtomicReference<CachedToken> ref = tokenCaches.computeIfAbsent(cacheKey, k -> new AtomicReference<>());
        CachedToken cached = ref.get();
        if (cached != null && cached.isValid()) {
            return cached.accessToken();
        }

        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", cred.clientId());
        form.add("client_secret", cred.clientSecret());
        form.add("scope", "https://graph.microsoft.com/.default");

        var tokenResponse = restClient.post()
                .uri(TOKEN_URL_TEMPLATE.formatted(cred.azureTenantId()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        if (tokenResponse == null || tokenResponse.accessToken() == null) {
            throw new IllegalStateException("Received null token response from Azure AD");
        }

        long expiresIn = tokenResponse.expiresIn() != null ? tokenResponse.expiresIn() : 3600L;
        var newToken = new CachedToken(tokenResponse.accessToken(), Instant.now().plusSeconds(expiresIn - 60));
        ref.set(newToken);
        return newToken.accessToken();
    }

    private String getOrCreateChat(String token, TeamsCredentials cred, String userAadIdOrEmail) {
        var body = Map.of(
                "chatType", "oneOnOne",
                "members", List.of(
                        memberEntry(cred.botUserId(), "owner"),
                        memberEntry(userAadIdOrEmail, "owner")
                )
        );

        var chatResponse = restClient.post()
                .uri(GRAPH_BASE + "/chats")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ChatResponse.class);

        if (chatResponse == null || chatResponse.id() == null) {
            throw new IllegalStateException("Null chat response from Graph API");
        }
        return chatResponse.id();
    }

    private void postMessage(String token, String chatId, String content) {
        var body = Map.of(
                "body", Map.of("content", content)
        );

        restClient.post()
                .uri(GRAPH_BASE + "/chats/" + chatId + "/messages")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private Map<String, Object> memberEntry(String userIdOrEmail, String role) {
        return Map.of(
                "@odata.type", "#microsoft.graph.aadUserConversationMember",
                "roles", List.of(role),
                "user@odata.bind", "https://graph.microsoft.com/v1.0/users('" + userIdOrEmail + "')"
        );
    }

    private record TeamsCredentials(
            String clientId, String clientSecret, String azureTenantId, String botUserId) {
        boolean complete() {
            return clientId != null
                    && !clientId.isBlank()
                    && clientSecret != null
                    && !clientSecret.isBlank()
                    && azureTenantId != null
                    && !azureTenantId.isBlank()
                    && botUserId != null
                    && !botUserId.isBlank();
        }

        String cacheKey() {
            return clientId + "|" + azureTenantId;
        }

        static TeamsCredentials resolve(
                String defClientId,
                String defSecret,
                String defTenant,
                String defBot,
                Map<String, Object> o) {
            if (o == null || o.isEmpty()) {
                return new TeamsCredentials(defClientId, defSecret, defTenant, defBot);
            }
            return new TeamsCredentials(
                    firstNonBlank(o.get("clientId"), defClientId),
                    firstNonBlank(o.get("clientSecret"), defSecret),
                    firstNonBlank(o.get("tenantId"), defTenant),
                    firstNonBlank(o.get("botUserId"), defBot));
        }

        private static String firstNonBlank(Object v, String fallback) {
            if (v == null) {
                return fallback;
            }
            String s = v.toString();
            return s.isBlank() ? fallback : s;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatResponse(String id) {}

    private record CachedToken(String accessToken, Instant expiresAt) {
        boolean isValid() {
            return Instant.now().isBefore(expiresAt);
        }
    }
}
