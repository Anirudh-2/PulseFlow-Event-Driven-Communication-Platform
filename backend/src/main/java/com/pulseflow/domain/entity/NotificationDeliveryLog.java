package com.pulseflow.domain.entity;

import com.pulseflow.domain.enums.DeliveryChannel;
import com.pulseflow.domain.enums.DeliveryStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "notification_delivery_log", schema = "notif")
public class NotificationDeliveryLog {
    @Id
    private UUID id;
    private String tenantId;
    private UUID notificationId;
    private UUID recipientId;
    private UUID templateId;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private DeliveryChannel channel;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private DeliveryStatus status;
    private Short attemptCount;
    private Short maxAttempts;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime deliveredAt;
}
