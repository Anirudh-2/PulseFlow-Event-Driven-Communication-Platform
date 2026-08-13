package com.pulseflow.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
@Table(name = "integration_sources", schema = "notif")
public class IntegrationSource {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String sourceKey;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private Boolean isActive;

    @JsonIgnore
    private String webhookApiKeyHash;

    @JsonProperty("webhookKeyConfigured")
    public boolean isWebhookKeyConfigured() {
        return webhookApiKeyHash != null && !webhookApiKeyHash.isBlank();
    }

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> metadata;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
