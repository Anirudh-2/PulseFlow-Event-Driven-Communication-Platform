package com.pulseflow.messaging;

import com.pulseflow.domain.entity.NotificationAuditLog;
import com.pulseflow.dto.CreateEventRequest;
import com.pulseflow.repository.NotificationAuditLogRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);
    private final NotificationAuditLogRepository auditLogRepository;

    public NotificationEventListener(NotificationAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @RabbitListener(queues = "${app.messaging.queue}")
    public void handleEvent(CreateEventRequest event) {
        log.info("NotificationEventListener received message: {}", event);
        try {
            NotificationAuditLog row = new NotificationAuditLog();
            row.setTenantId(event.tenantId());
            row.setAction("EVENT_RECEIVED");
            row.setActorUserId(event.sourceService());
            row.setCorrelationId(
                    event.correlationId() == null || event.correlationId().isBlank()
                            ? event.sourceEventId()
                            : event.correlationId());
            row.setMetadata(Map.of("eventType", event.eventType(), "userId", event.userId()));
            row.setOccurredAt(OffsetDateTime.now());
            auditLogRepository.save(row);
            log.info("processed_event tenant={} type={} user={}", event.tenantId(), event.eventType(), event.userId());
        } catch (Exception ex) {
            log.error("Failed to persist EVENT_RECEIVED audit row", ex);
        }
    }
}
