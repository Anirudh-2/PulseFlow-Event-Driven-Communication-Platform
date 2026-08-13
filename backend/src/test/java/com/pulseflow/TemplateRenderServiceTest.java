package com.pulseflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.pulseflow.config.DeliveryProperties;
import com.pulseflow.domain.entity.NotificationTemplateEntity;
import com.pulseflow.repository.NotificationTemplateEntityRepository;
import com.pulseflow.service.TemplateRenderService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TemplateRenderServiceTest {
    @Test
    void shouldFallbackToPlainTextWrapperWhenNoTemplateExists() {
        NotificationTemplateEntityRepository templateRepository = Mockito.mock(NotificationTemplateEntityRepository.class);
        when(templateRepository.findByTenantIdAndEventTypeIgnoreCaseAndChannelType_CodeIgnoreCaseAndLocaleIgnoreCaseAndIsActiveTrue(
                        any(), any(), any(), any()))
                .thenReturn(List.of());

        TemplateRenderService service = new TemplateRenderService(templateRepository, new DeliveryProperties());

        var rendered = service.render(
                "default",
                "SOME_UNKNOWN",
                "TEAMS",
                "en",
                null,
                Map.of("sourceService", "HRMS", "someKey", "someValue"));

        assertEquals("Notification: SOME_UNKNOWN", rendered.subject());
        assertTrue(rendered.body().contains("eventType: SOME_UNKNOWN"));
        assertTrue(rendered.body().contains("sourceService: HRMS"));
    }

    @Test
    void shouldPopulateTitleAndBodyInContextForTemplates() {
        NotificationTemplateEntityRepository templateRepository = Mockito.mock(NotificationTemplateEntityRepository.class);
        NotificationTemplateEntity tpl = new NotificationTemplateEntity();
        tpl.setSubjectTemplate("Subject {{eventType}}");
        tpl.setBodyTemplate("Body {{body}}");
        tpl.setIsActive(true);

        // Return template for the lookup call.
        when(templateRepository.findByTenantIdAndEventTypeIgnoreCaseAndChannelType_CodeIgnoreCaseAndLocaleIgnoreCaseAndIsActiveTrue(
                        any(), any(), any(), any()))
                .thenReturn(List.of(tpl));

        TemplateRenderService service = new TemplateRenderService(templateRepository, new DeliveryProperties());

        var rendered = service.render(
                "default",
                "ORDER_CREATED",
                "TEAMS",
                "en",
                null,
                Map.of("sourceService", "orders", "orderId", "o1"));

        assertEquals("Subject ORDER_CREATED", rendered.subject());
        assertTrue(rendered.body().startsWith("Body "));
    }
}

