package com.pulseflow.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "notification_audit_log", schema = "notif")
public class NotificationAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tenantId;
    private String action;
    private String actorUserId;
    private String correlationId;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;
    private OffsetDateTime occurredAt;
}
