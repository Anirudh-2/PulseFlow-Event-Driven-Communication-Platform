package com.pulseflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record CreateEventRequest(
        @NotBlank String tenantId,
        @NotBlank String eventType,
        @NotBlank String sourceService,
        @NotBlank String sourceEventId,
        @NotBlank String userId,
        String userEmail,
        String aadObjectId,
        String telegramChatId,
        @NotBlank String roleName,
        @NotNull Map<String, Object> payload,
        UUID integrationSourceId,
        String locale,
        String correlationId,
        Long sequenceNumber,
        OffsetDateTime eventTimestamp) {

    public CreateEventRequest(
            String tenantId,
            String eventType,
            String sourceService,
            String sourceEventId,
            String userId,
            String userEmail,
            String aadObjectId,
            String telegramChatId,
            String roleName,
            Map<String, Object> payload) {
        this(
                tenantId,
                eventType,
                sourceService,
                sourceEventId,
                userId,
                userEmail,
                aadObjectId,
                telegramChatId,
                roleName,
                payload,
                null,
                null,
                null,
                null,
                null);
    }
}
