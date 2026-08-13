package com.pulseflow.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public record AdminRuleRequest(
        @NotBlank String name,
        @NotBlank String roleName,
        String notificationType,
        String eventType,
        String integrationSourceId,
        @NotEmpty List<String> channels,
        @Min(1) short evalOrder,
        boolean isActive,
        Map<String, Object> conditions,
        Map<String, Object> conditionsJsonlogic
) {
}
