package com.pulseflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record HrmsWebhookPayload(
        @NotBlank String eventType,
        @NotBlank String sourceEventId,
        @NotBlank String userId,
        @NotBlank @Email String userEmail,
        String aadObjectId,
        String telegramChatId,
        @NotBlank String roleName,
        @NotNull Map<String, Object> payload
) {
}
