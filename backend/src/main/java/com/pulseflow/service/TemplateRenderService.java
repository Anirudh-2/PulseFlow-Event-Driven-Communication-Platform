package com.pulseflow.service;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.pulseflow.config.DeliveryProperties;
import com.pulseflow.domain.entity.NotificationTemplateEntity;
import com.pulseflow.repository.NotificationTemplateEntityRepository;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateRenderService {
    private static final Logger log = LoggerFactory.getLogger(TemplateRenderService.class);

    private static final Map<String, String> DEFAULT_TITLES = Map.ofEntries(
            Map.entry("LEAVE_APPROVED", "Leave Request Approved"),
            Map.entry("LEAVE_REJECTED", "Leave Request Rejected"),
            Map.entry("LEAVE_SUBMITTED", "Leave Request Submitted"),
            Map.entry("PAYSLIP_GENERATED", "Your Payslip is Ready"),
            Map.entry("EMPLOYEE_ONBOARDED", "Welcome to the Organisation"),
            Map.entry("EMPLOYEE_OFFBOARDED", "Offboarding Process Initiated"),
            Map.entry("PERFORMANCE_REVIEW_DUE", "Performance Review Due"),
            Map.entry("PROBATION_ENDING", "Probation Period Ending Soon"),
            Map.entry("CONTRACT_EXPIRING", "Employment Contract Expiring Soon"),
            Map.entry("TRAINING_ASSIGNED", "New Training Assigned"),
            Map.entry("ORDER_CREATED", "New Order Created"));

    private static final Map<String, String> DEFAULT_BODIES = Map.ofEntries(
            Map.entry("LEAVE_APPROVED", "Your leave request has been approved. Please check your schedule."),
            Map.entry("LEAVE_REJECTED", "Your leave request has been rejected. Please contact your manager for details."),
            Map.entry("LEAVE_SUBMITTED", "Your leave request has been submitted and is pending approval."),
            Map.entry("PAYSLIP_GENERATED", "Your payslip for the current period has been generated and is available for download."),
            Map.entry("EMPLOYEE_ONBOARDED", "Welcome! Your employee account has been set up. Please complete your onboarding checklist."),
            Map.entry("EMPLOYEE_OFFBOARDED", "Your offboarding process has been initiated. Please follow up with HR."),
            Map.entry("PERFORMANCE_REVIEW_DUE", "Your performance review is due. Please complete your self-assessment."),
            Map.entry("PROBATION_ENDING", "Your probation period is ending soon. Your manager will be in touch."),
            Map.entry("CONTRACT_EXPIRING", "Your employment contract is expiring soon. Please contact HR for renewal."),
            Map.entry("TRAINING_ASSIGNED", "A new training course has been assigned to you. Please complete it by the due date."),
            Map.entry("ORDER_CREATED", "A new order has been created and is being processed."));

    private final NotificationTemplateEntityRepository templateRepository;
    private final DeliveryProperties deliveryProperties;
    private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();
    private final ConcurrentHashMap<String, Mustache> compiled = new ConcurrentHashMap<>();
    @Autowired
    @Lazy
    private TemplateRenderService self;

    public TemplateRenderService(
            NotificationTemplateEntityRepository templateRepository, DeliveryProperties deliveryProperties) {
        this.templateRepository = templateRepository;
        this.deliveryProperties = deliveryProperties;
    }

    public record RenderedMessage(String subject, String body) {}
    public record CachedTemplate(String subjectTemplate, String bodyTemplate) implements java.io.Serializable {}

    @Transactional(readOnly = true)
    public NotificationTemplateEntity resolveTemplate(
            String tenantId,
            String eventType,
            String channelCode,
            String locale,
            UUID integrationSourceId) {
        String loc = locale == null || locale.isBlank() ? deliveryProperties.getDefaultLocale() : locale;
        String ev = eventType == null ? "" : eventType;
        String ch = channelCode == null ? deliveryProperties.getDefaultChannel() : channelCode.toUpperCase();
        if (integrationSourceId != null) {
            List<NotificationTemplateEntity> scoped =
                    templateRepository.findByTenantIdAndEventTypeIgnoreCaseAndChannelType_CodeIgnoreCaseAndLocaleIgnoreCaseAndIsActiveTrue(
                            tenantId, ev, ch, loc);
            var withSource = scoped.stream()
                    .filter(t -> t.getIntegrationSource() != null
                            && integrationSourceId.equals(t.getIntegrationSource().getId()))
                    .findFirst();
            if (withSource.isPresent()) {
                return withSource.get();
            }
        }
        List<NotificationTemplateEntity> list =
                templateRepository.findByTenantIdAndEventTypeIgnoreCaseAndChannelType_CodeIgnoreCaseAndLocaleIgnoreCaseAndIsActiveTrue(
                        tenantId, ev, ch, loc);
        return list.stream()
                .filter(t -> t.getIntegrationSource() == null)
                .findFirst()
                .orElse(null);
    }

    @Cacheable(
            cacheNames = "templateCache",
            key =
                    "'template:' + #tenantId + ':' + #eventType + ':' + #channelCode + ':' + "
                            + "(#locale == null || #locale.isBlank() ? #root.target.deliveryProperties.defaultLocale : #locale)",
            unless = "#result == null")
    public CachedTemplate resolveTemplateCached(
            String tenantId,
            String eventType,
            String channelCode,
            String locale,
            UUID integrationSourceId) {
        log.debug(
                "Cache miss - loading template from DB for key: {}:{}:{}:{}:{}",
                tenantId,
                eventType,
                channelCode,
                locale,
                integrationSourceId);
        NotificationTemplateEntity tpl = resolveTemplate(tenantId, eventType, channelCode, locale, integrationSourceId);
        if (tpl == null) {
            return null;
        }
        return new CachedTemplate(tpl.getSubjectTemplate(), tpl.getBodyTemplate());
    }

    public RenderedMessage render(
            String tenantId,
            String eventType,
            String channelCode,
            String locale,
            UUID integrationSourceId,
            Map<String, Object> context) {
        CachedTemplate tpl = (self != null ? self : this)
                .resolveTemplateCached(tenantId, eventType, channelCode, locale, integrationSourceId);
        String norm = eventType == null ? "" : eventType.toUpperCase();
        Map<String, Object> ctx = new HashMap<>(context == null ? Map.of() : context);
        if (!ctx.containsKey("eventType")) {
            ctx.put("eventType", norm);
        }
        // Ensure templates have access to default wrapper fields at delivery time.
        // This preserves compatibility with templates that reference {{title}} or {{body}}.
        if (!ctx.containsKey("title")) {
            ctx.put("title", defaultTitle(norm));
        }
        if (!ctx.containsKey("body")) {
            ctx.put("body", defaultBody(norm, ctx));
        }
        if (tpl != null) {
            String subject = tpl.subjectTemplate() == null ? "" : compileAndExecute(tpl.subjectTemplate(), ctx);
            String body = compileAndExecute(tpl.bodyTemplate(), ctx);
            return new RenderedMessage(subject.isBlank() ? defaultTitle(norm) : subject, body);
        }
        return new RenderedMessage(defaultTitle(norm), defaultBody(norm, ctx));
    }

    private String defaultTitle(String normalizedEventType) {
        return DEFAULT_TITLES.getOrDefault(normalizedEventType, "Notification: " + normalizedEventType);
    }

    private String defaultBody(String normalizedEventType, Map<String, Object> ctx) {
        if (DEFAULT_BODIES.containsKey(normalizedEventType)) {
            return compileAndExecute(DEFAULT_BODIES.get(normalizedEventType), ctx);
        }
        // Explicit plain-text fallback wrapper for channel+event template misses.
        return plainTextFallback(normalizedEventType, ctx);
    }

    private String plainTextFallback(String normalizedEventType, Map<String, Object> ctx) {
        String source = ctx.get("sourceService") == null ? "unknown" : String.valueOf(ctx.get("sourceService"));
        String payloadSummary = summarizePayload(ctx);
        return "New notification\n"
                + "eventType: " + normalizedEventType + "\n"
                + "sourceService: " + source + "\n"
                + "payload: " + payloadSummary;
    }

    private String summarizePayload(Map<String, Object> ctx) {
        String raw = String.valueOf(ctx);
        return raw.length() > 600 ? raw.substring(0, 600) + "..." : raw;
    }

    private String compileAndExecute(String template, Map<String, Object> context) {
        try {
            Mustache m = compiled.computeIfAbsent(template, t -> mustacheFactory.compile(new StringReader(t), t));
            var w = new StringWriter();
            m.execute(w, context).flush();
            return w.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Template render failed", e);
        }
    }
}
