package com.pulseflow.domain.entity;

import com.pulseflow.domain.enums.DeliveryChannel;
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
@Table(name = "notification_rules", schema = "notif")
public class NotificationRule {
    @Id
    private UUID id;
    private String tenantId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String roleName;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private NotificationType notificationType;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    private PriorityLevel priorityOverride;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> conditions;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_source_id")
    private IntegrationSource integrationSource;

    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions_jsonlogic")
    private Map<String, Object> conditionsJsonlogic;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "notif.delivery_channel[]")
    private DeliveryChannel[] channels;
    private Boolean isActive;
    private Short evalOrder;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
