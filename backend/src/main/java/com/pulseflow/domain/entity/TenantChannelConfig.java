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
@Table(name = "tenant_channel_configs", schema = "notif")
public class TenantChannelConfig {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_type_code", referencedColumnName = "code", nullable = false)
    private ChannelTypeEntity channelType;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> config;

    @Column(nullable = false)
    private Short priority;

    @Column(nullable = false)
    private Boolean isDefault;

    @Column(nullable = false)
    private Boolean isEnabled;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
