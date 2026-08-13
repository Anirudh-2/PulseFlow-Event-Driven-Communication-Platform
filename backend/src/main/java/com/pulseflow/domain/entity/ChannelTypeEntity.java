package com.pulseflow.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "channel_types", schema = "notif")
public class ChannelTypeEntity {
    @Id
    @Column(length = 64)
    private String code;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String handlerKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> capabilities;

    @Column(nullable = false)
    private Boolean isEnabledGlobally;
}
