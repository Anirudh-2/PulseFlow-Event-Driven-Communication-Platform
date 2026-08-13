package com.pulseflow.config;

import com.pulseflow.repository.IntegrationSourceRepository;
import com.pulseflow.util.ApiKeyHasher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Secures legacy HRMS and generic integration webhook POSTs with {@code X-Webhook-Api-Key}.
 * Integration webhooks use per-source SHA-256 of the key when {@code webhook_api_key_hash} is set;
 * otherwise the global {@code app.integrations.hrmsWebhookApiKey} applies. Resolves tenant via
 * {@code X-Tenant-Id} or {@code app.integrations.hrmsDefaultTenantId}.
 */
@Component
public class WebhookApiKeyFilter extends OncePerRequestFilter {

    private static final String LEGACY_HRMS_WEBHOOK = "/api/v1/hrms/webhook";
    private static final String INTEGRATIONS_PREFIX = "/api/v1/integrations/";
    private static final String WEBHOOK_SUFFIX = "/webhook";
    private static final String NOTIFY_SUFFIX = "/notify";
    private static final String API_KEY_HEADER = "X-Webhook-Api-Key";
    public static final String TENANT_HEADER = "X-Tenant-Id";

    private final byte[] globalKeyBytes;
    private final IntegrationSourceRepository integrationSourceRepository;
    private final String defaultTenantId;

    public WebhookApiKeyFilter(
            @Value("${app.integrations.hrmsWebhookApiKey:changeme}") String globalWebhookApiKey,
            IntegrationSourceRepository integrationSourceRepository,
            @Value("${app.integrations.hrmsDefaultTenantId:default}") String defaultTenantId) {
        this.globalKeyBytes = globalWebhookApiKey.getBytes(StandardCharsets.UTF_8);
        this.integrationSourceRepository = integrationSourceRepository;
        this.defaultTenantId = defaultTenantId;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = resolvePath(request);
        if (LEGACY_HRMS_WEBHOOK.equals(path)) {
            return false;
        }
        if (path.startsWith(INTEGRATIONS_PREFIX) && (path.endsWith(WEBHOOK_SUFFIX) || path.endsWith(NOTIFY_SUFFIX))) {
            return false;
        }
        return true;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = resolvePath(request);
        String providedKey = request.getHeader(API_KEY_HEADER);
        if (providedKey == null || !isKeyValid(path, request, providedKey)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Invalid or missing API key\"}");
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                "hrms-webhook",
                null,
                java.util.Collections.singletonList(new SimpleGrantedAuthority("ROLE_HRMS")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }

    private boolean isKeyValid(String path, HttpServletRequest request, String provided) {
        if (LEGACY_HRMS_WEBHOOK.equals(path)) {
            return globalKeyMatches(provided);
        }
        if (path.startsWith(INTEGRATIONS_PREFIX) && (path.endsWith(WEBHOOK_SUFFIX) || path.endsWith(NOTIFY_SUFFIX))) {
            return integrationWebhookKeyValid(request, path, provided);
        }
        return false;
    }

    private boolean globalKeyMatches(String provided) {
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(globalKeyBytes, providedBytes);
    }

    private boolean integrationWebhookKeyValid(HttpServletRequest request, String path, String provided) {
        String sourceKey = extractIntegrationSourceKey(path);
        if (sourceKey == null || sourceKey.isBlank()) {
            return false;
        }
        String tenantHeader = request.getHeader(TENANT_HEADER);
        String tenantId =
                tenantHeader != null && !tenantHeader.isBlank() ? tenantHeader.trim() : defaultTenantId;
        var sourceOpt = integrationSourceRepository.findByTenantIdAndSourceKeyIgnoreCase(tenantId, sourceKey);
        if (sourceOpt.isEmpty()) {
            return false;
        }
        var source = sourceOpt.get();
        String hash = source.getWebhookApiKeyHash();
        if (hash != null && !hash.isBlank()) {
            return ApiKeyHasher.matches(provided, hash);
        }
        return globalKeyMatches(provided);
    }

    static String extractIntegrationSourceKey(String path) {
        if (!path.startsWith(INTEGRATIONS_PREFIX)) {
            return null;
        }
        boolean isWebhook = path.endsWith(WEBHOOK_SUFFIX);
        boolean isNotify = path.endsWith(NOTIFY_SUFFIX);
        if (!isWebhook && !isNotify) {
            return null;
        }
        int suffixLength = isWebhook ? WEBHOOK_SUFFIX.length() : NOTIFY_SUFFIX.length();
        String mid = path.substring(INTEGRATIONS_PREFIX.length(), path.length() - suffixLength);
        if (mid.isEmpty() || mid.contains("/")) {
            return null;
        }
        return mid;
    }

    static String resolvePath(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
            String ctx = request.getContextPath();
            if (ctx != null && !ctx.isEmpty() && path.startsWith(ctx)) {
                path = path.substring(ctx.length());
            }
        }
        return path;
    }
}
