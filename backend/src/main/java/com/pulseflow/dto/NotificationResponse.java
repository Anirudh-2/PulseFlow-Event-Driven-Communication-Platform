package com.pulseflow.dto;

import com.pulseflow.domain.enums.NotificationType;
import com.pulseflow.domain.enums.PriorityLevel;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        String tenantId,
        String title,
        String body,
        NotificationType type,
        PriorityLevel priority,
        String sourceService,
        String sourceEventId,
        Map<String, Object> metadata,
        OffsetDateTime createdAt,
        String eventType,
        /** Per-recipient inbox state when listing with userId: UNREAD, READ, ACKNOWLEDGED. */
        String status,
        /** Notification row lifecycle, e.g. ACTIVE. */
        String lifecycleStatus
) {
}
