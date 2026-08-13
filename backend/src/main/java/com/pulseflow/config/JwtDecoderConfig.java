package com.pulseflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

/**
 * When running the backend in Docker, JWT issuer in tokens is {@code http://localhost:8080/...}
 * (browser-facing Keycloak URL) but OIDC discovery on {@code localhost} from inside the container
 * would hit the wrong host. Optional {@code app.keycloak.jwks-uri} points at the internal Keycloak
 * service while issuer validation still uses the public issuer URI.
 */
@Configuration
public class JwtDecoderConfig {

    @Bean
    @Primary
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${app.keycloak.jwks-uri:}") String jwksUri) {
        if (!StringUtils.hasText(jwksUri)) {
            return JwtDecoders.fromIssuerLocation(issuerUri);
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri));
        return decoder;
    }
}
