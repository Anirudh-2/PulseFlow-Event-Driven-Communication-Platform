package com.pulseflow.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.keycloak.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
public class KeycloakRealmBootstrap implements ApplicationRunner {

    private final KeycloakBootstrapProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public KeycloakRealmBootstrap(
            KeycloakBootstrapProperties properties, ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        int maxAttempts = 20;
        long backoffMs = 1000;

        Exception lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                bootstrapRealmAndClients();
                log.info("Keycloak bootstrap completed successfully on attempt {}", attempt);
                return;
            } catch (Exception ex) {
                lastFailure = ex;
                log.warn("Keycloak bootstrap attempt {}/{} failed: {}", attempt, maxAttempts, ex.getMessage());
                if (attempt == maxAttempts) {
                    break;
                }
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                backoffMs = Math.min(backoffMs * 2, 8000);
            }
        }

        if (lastFailure != null) {
            log.error("Keycloak bootstrap failed after {} attempts. Last error: {}", maxAttempts, lastFailure.getMessage());
            log.debug("Keycloak bootstrap stacktrace", lastFailure);
        }
    }

    private void bootstrapRealmAndClients() throws IOException {
        if (!StringUtils.hasText(properties.serverUrl())) {
            throw new IllegalStateException("app.keycloak.bootstrap.server-url is required");
        }

        JsonNode realmConfig = readRealmConfig();
        String realmName = textValue(realmConfig, "realm");
        if (!StringUtils.hasText(realmName)) {
            throw new IllegalStateException("Realm import JSON must include 'realm'");
        }

        String token = fetchAdminAccessToken();
        RestClient adminClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.serverUrl()))
                .defaultHeader("Authorization", "Bearer " + token)
                .build();

        if (!realmExists(adminClient, realmName)) {
            adminClient.post().uri("/admin/realms").body(realmConfig).retrieve().toBodilessEntity();
            log.info("Created Keycloak realm '{}'", realmName);
        } else {
            log.info("Keycloak realm '{}' already exists", realmName);
        }

        ensurePostmanClient(adminClient, realmName);
    }

    private JsonNode readRealmConfig() throws IOException {
        Resource resource = resourceLoader.getResource(properties.importPath());
        if (!resource.exists()) {
            throw new IllegalStateException("Realm import file not found at " + properties.importPath());
        }
        try (var in = resource.getInputStream()) {
            return objectMapper.readTree(in);
        }
    }

    private String fetchAdminAccessToken() {
        RestClient tokenClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.serverUrl()))
                .build();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", properties.adminClientId());
        form.add("username", properties.adminUsername());
        form.add("password", properties.adminPassword());

        Map<?, ?> tokenResponse = tokenClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", properties.adminRealm())
                .body(form)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .retrieve()
                .body(Map.class);

        if (tokenResponse == null || tokenResponse.get("access_token") == null) {
            throw new IllegalStateException("Unable to obtain Keycloak admin access token");
        }
        return String.valueOf(tokenResponse.get("access_token"));
    }

    private boolean realmExists(RestClient adminClient, String realmName) {
        try {
            adminClient.get().uri("/admin/realms/{realm}", realmName).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                return false;
            }
            throw ex;
        }
    }

    @SuppressWarnings("unchecked")
    private void ensurePostmanClient(RestClient adminClient, String realmName) {
        String clientId = properties.postmanClientId();
        if (!StringUtils.hasText(clientId)) {
            return;
        }

        List<Map<String, Object>> existing = adminClient.get()
                .uri("/admin/realms/{realm}/clients?clientId={clientId}", realmName, clientId)
                .retrieve()
                .body(List.class);

        Map<String, Object> representation = Map.of(
                "clientId", clientId,
                "name", "PulseFlow Postman",
                "enabled", true,
                "publicClient", false,
                "directAccessGrantsEnabled", true,
                "standardFlowEnabled", false,
                "serviceAccountsEnabled", false,
                "protocol", "openid-connect",
                "secret", properties.postmanClientSecret(),
                "fullScopeAllowed", true);

        if (existing == null || existing.isEmpty()) {
            adminClient.post()
                    .uri("/admin/realms/{realm}/clients", realmName)
                    .body(representation)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Created Keycloak client '{}' with direct access grants enabled", clientId);
            return;
        }

        Object internalId = existing.getFirst().get("id");
        if (internalId == null) {
            throw new IllegalStateException("Existing Keycloak client is missing id for clientId=" + clientId);
        }

        adminClient.put()
                .uri("/admin/realms/{realm}/clients/{id}", realmName, internalId)
                .body(representation)
                .retrieve()
                .toBodilessEntity();
        log.info("Updated Keycloak client '{}' with direct access grants enabled", clientId);
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? null : value.asText();
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
