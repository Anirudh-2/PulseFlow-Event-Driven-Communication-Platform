package com.pulseflow.service;

import com.pulseflow.domain.entity.IntegrationSource;
import com.pulseflow.dto.CreateEventRequest;
import com.pulseflow.repository.IntegrationFieldMappingRepository;
import com.pulseflow.repository.IntegrationSourceRepository;
import java.util.Map;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationIngestService {

    private final IntegrationSourceRepository integrationSourceRepository;
    private final IntegrationFieldMappingRepository fieldMappingRepository;
    private final PayloadMappingService payloadMappingService;
    private final NotificationService notificationService;

    public IntegrationIngestService(
            IntegrationSourceRepository integrationSourceRepository,
            IntegrationFieldMappingRepository fieldMappingRepository,
            PayloadMappingService payloadMappingService,
            NotificationService notificationService) {
        this.integrationSourceRepository = integrationSourceRepository;
        this.fieldMappingRepository = fieldMappingRepository;
        this.payloadMappingService = payloadMappingService;
        this.notificationService = notificationService;
    }

    @Transactional
    public com.pulseflow.dto.NotificationResponse ingest(
            String tenantId, String sourceKey, Map<String, Object> rawPayload) {
        IntegrationSource source = integrationSourceRepository
                .findByTenantIdAndSourceKeyIgnoreCase(tenantId, sourceKey)
                .filter(IntegrationSource::getIsActive)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or inactive integration: " + sourceKey));

        Map<String, Object> mapping = fieldMappingRepository
                .findFirstByIntegrationSource_IdAndIsActiveTrueOrderByVersionDesc(source.getId())
                .map(m -> m.getMapping())
                .orElse(Map.of());

        Map<String, Object> normalized = payloadMappingService.applyMapping(rawPayload, mapping);
        CreateEventRequest request = toCreateEventRequest(tenantId, source.getSourceKey(), source.getId(), normalized);
        return notificationService.processEvent(request);
    }

    @SuppressWarnings("unchecked")
    private static CreateEventRequest toCreateEventRequest(
            String tenantId, String sourceKey, UUID integrationSourceId, Map<String, Object> n) {
        String eventType = str(n.get("eventType"));
        if (eventType == null || eventType.isBlank()) {
            eventType = str(n.get("type"));
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException(
                    "Mapped payload must include eventType (or type). Keys present: " + n.keySet());
        }
        String userId = str(n.get("userId"));
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException(
                    "Mapped payload must include userId. Keys present: " + n.keySet());
        }
        String sourceEventId = str(n.get("sourceEventId"));
        if (sourceEventId == null || sourceEventId.isBlank()) {
            sourceEventId = str(n.get("id"));
        }
        if (sourceEventId == null || sourceEventId.isBlank()) {
            throw new IllegalArgumentException(
                    "Mapped payload must include sourceEventId (or id). Keys present: " + n.keySet());
        }
        String roleName = Optional.ofNullable(str(n.get("roleName"))).orElse("EMPLOYEE");
        Map<String, Object> payload =
                n.get("payload") instanceof Map<?, ?> p ? (Map<String, Object>) p : n;

        Long sequenceNumber = null;
        Object seqObj = n.get("sequenceNumber");
        if (seqObj instanceof Number num) {
            sequenceNumber = num.longValue();
        } else if (seqObj != null && !seqObj.toString().isBlank()) {
            try {
                sequenceNumber = Long.parseLong(seqObj.toString());
            } catch (NumberFormatException e) {
                // ignore, keep null (ordering is optional)
            }
        }

        OffsetDateTime eventTimestamp = null;
        Object tsObj = n.get("eventTimestamp");
        if (tsObj instanceof String s && !s.isBlank()) {
            try {
                eventTimestamp = OffsetDateTime.parse(s);
            } catch (Exception e) {
                // ignore, keep null (ordering is optional)
            }
        }
        return new CreateEventRequest(
                tenantId,
                eventType,
                str(n.get("sourceService")) != null ? str(n.get("sourceService")) : sourceKey,
                sourceEventId,
                userId,
                str(n.get("userEmail")),
                str(n.get("aadObjectId")),
                str(n.get("telegramChatId")),
                roleName,
                payload,
                integrationSourceId,
                str(n.get("locale")),
                str(n.get("correlationId")),
                sequenceNumber,
                eventTimestamp);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
