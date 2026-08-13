package com.pulseflow.domain.entity;

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
@Table(name = "channel_configurations", schema = "notif")
public class ChannelConfiguration {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", referencedColumnName = "id", nullable = false)
    private IntegrationSource app;

    @Column(nullable = false)
    private String channelType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", nullable = false)
    private Map<String, Object> configJson;

    @Column(nullable = false)
    private Boolean isActive;

    @Column(nullable = false)
    private OffsetDateTime createdAt;
}
