package com.pulseflow.dto.notify;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record NotifyRequest(
        @NotBlank String eventType,
        String sourceEventId,
        @NotNull @Valid RecipientsInput recipients,
        @NotNull Map<String, Object> payload) {}
