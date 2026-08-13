package com.pulseflow.dto.admin;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AdminRuleResponse(
        UUID id,
        String tenantId,
        String name,
        String targetRole,
        String roleName,
        String notificationType,
        String eventType,
        UUID integrationSourceId,
        List<String> channels,
        String priorityOverride,
        short evaluationOrder,
        short evalOrder,
        boolean active,
        boolean isActive,
        Map<String, Object> conditions,
        Map<String, Object> conditionsJsonlogic,
        OffsetDateTime createdAt
) {
}
