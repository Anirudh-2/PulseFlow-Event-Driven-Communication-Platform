package com.pulseflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.keycloak.bootstrap")
public record KeycloakBootstrapProperties(
        boolean enabled,
        String serverUrl,
        String adminRealm,
        String adminClientId,
        String adminUsername,
        String adminPassword,
        String importPath,
        String postmanClientId,
        String postmanClientSecret) {
}
