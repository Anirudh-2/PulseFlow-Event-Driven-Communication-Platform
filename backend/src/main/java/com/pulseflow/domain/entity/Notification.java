package com.pulseflow.domain.entity;

import com.pulseflow.domain.enums.NotificationStatus;
import com.pulseflow.domain.enums.NotificationType;
import com.pulseflow.domain.enums.PriorityLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "notifications", schema = "notif")
public class Notification {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String tenantId;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String body;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private NotificationType type;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private PriorityLevel priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private NotificationStatus status;
    @Column(nullable = false)
    private String sourceService;
    private String sourceEventId;

    // Optional ordering metadata provided by upstream systems.
    // When present, we use sequenceNumber to drop/ignore older out-of-order events.
    @Column(name = "sequence_number")
    private Long sequenceNumber;

    @Column(name = "event_timestamp")
    private OffsetDateTime eventTimestamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_source_id")
    private IntegrationSource integrationSource;

    private String eventType;

    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime expiresAt;
    private Boolean isDeleted;
    private Long version;
}
