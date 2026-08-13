package com.pulseflow.domain.entity;

import com.pulseflow.domain.enums.DeliveryChannel;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "notification_failures", schema = "notif")
public class NotificationFailure {
    @Id
    private UUID id;
    private String tenantId;
    private UUID notificationId;
    private UUID recipientId;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private DeliveryChannel channel;
    private String failureReason;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> rawEventPayload;
    private Boolean isResolved;
    private OffsetDateTime occurredAt;
}
