package com.pulseflow;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pulseflow.config.WebhookApiKeyFilter;
import com.pulseflow.controller.HrmsWebhookController;
import com.pulseflow.domain.enums.NotificationType;
import com.pulseflow.domain.enums.PriorityLevel;
import com.pulseflow.dto.CreateEventRequest;
import com.pulseflow.dto.HrmsWebhookPayload;
import com.pulseflow.dto.NotificationResponse;
import com.pulseflow.repository.IntegrationSourceRepository;
import com.pulseflow.service.NotificationService;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class HrmsWebhookControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Mock
    private NotificationService notificationService;

    @Mock
    private IntegrationSourceRepository integrationSourceRepository;

    private static final String VALID_KEY = "changeme";
    private static final String ENDPOINT = "/api/v1/hrms/webhook";

    @BeforeEach
    void setUp() {
        var filter = new WebhookApiKeyFilter(VALID_KEY, integrationSourceRepository, "default");
        var controller = new HrmsWebhookController(notificationService, integrationSourceRepository, "default");
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .addFilters(filter)
                .build();
    }

    private HrmsWebhookPayload validPayload() {
        return new HrmsWebhookPayload(
                "LEAVE_APPROVED",
                "evt-001",
                "emp-123",
                "john.doe@company.com",
                "aad-object-id-abc",
                null,
                "EMPLOYEE",
                Map.of("leaveType", "annual", "days", 3));
    }

    private NotificationResponse stubResponse() {
        return new NotificationResponse(
                UUID.randomUUID(),
                "default",
                "Leave Request Approved",
                "Your leave request has been approved.",
                NotificationType.HR_ACTION,
                PriorityLevel.MEDIUM,
                "HRMS",
                "evt-001",
                Map.of(),
                OffsetDateTime.now(),
                "LEAVE_APPROVED",
                "UNREAD",
                "ACTIVE");
    }

    @Test
    void givenValidApiKey_whenPost_thenReturns201() throws Exception {
        Mockito.when(integrationSourceRepository.findByTenantIdAndSourceKeyIgnoreCase(Mockito.eq("default"), Mockito.eq("HRMS")))
                .thenReturn(Optional.empty());
        Mockito.when(notificationService.processEvent(any(CreateEventRequest.class)))
                .thenReturn(stubResponse());

        mockMvc.perform(post(ENDPOINT)
                        .header("X-Webhook-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPayload())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceService").value("HRMS"));
    }

    @Test
    void givenMissingApiKey_whenPost_thenReturns401() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPayload())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void givenWrongApiKey_whenPost_thenReturns401() throws Exception {
        mockMvc.perform(post(ENDPOINT)
                        .header("X-Webhook-Api-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validPayload())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void givenValidKeyButInvalidPayload_whenPost_thenReturns400() throws Exception {
        String badPayload = "{\"eventType\":\"\"}";
        mockMvc.perform(post(ENDPOINT)
                        .header("X-Webhook-Api-Key", VALID_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPayload))
                .andExpect(status().isBadRequest());
    }
}
