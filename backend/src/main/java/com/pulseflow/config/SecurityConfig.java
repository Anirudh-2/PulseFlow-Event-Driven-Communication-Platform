package com.pulseflow.config;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final WebhookApiKeyFilter webhookApiKeyFilter;

    public SecurityConfig(WebhookApiKeyFilter webhookApiKeyFilter) {
        this.webhookApiKeyFilter = webhookApiKeyFilter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(cors -> cors.configure(http))
                .csrf(csrf -> csrf.disable())
                .addFilterBefore(webhookApiKeyFilter, BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/hrms/webhook", "/api/v1/integrations/*/webhook", "/api/v1/integrations/*/notify").permitAll()
                        .requestMatchers("/ws", "/ws/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())));
        return http.build();
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthConverter() {
        return new Converter<Jwt, AbstractAuthenticationToken>() {
            @Override
            public AbstractAuthenticationToken convert(Jwt jwt) {
                Collection<String> roles = jwt.getClaimAsStringList("roles");
                if (roles == null) {
                    Map<String, Object> realmAccess = jwt.getClaim("realm_access");
                    if (realmAccess != null && realmAccess.get("roles") instanceof List<?> list) {
                        roles = list.stream().filter(x -> x instanceof String).map(String.class::cast).toList();
                    }
                }
                Collection<String> safeRoles = roles == null ? Collections.emptyList() : roles;

                var authorities = safeRoles.stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                        .collect(Collectors.toList());
                return new JwtAuthenticationToken(jwt, authorities);
            }
        };
    }
}
