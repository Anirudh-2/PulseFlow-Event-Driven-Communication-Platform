package com.pulseflow.domain.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "routing_rules", schema = "notif")
public class RoutingRule {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String tenantId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_source_id")
    private IntegrationSource integrationSource;

    @Column(nullable = false)
    private String name;

    private String eventType;

    private String roleName;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> conditionsJsonlogic;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] channelTypeCodes;

    @Column(nullable = false)
    private Short evalOrder;

    @Column(nullable = false)
    private Boolean isActive;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
