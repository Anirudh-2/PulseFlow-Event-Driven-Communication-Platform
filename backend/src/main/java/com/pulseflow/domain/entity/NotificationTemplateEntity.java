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
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "notification_templates", schema = "notif")
public class NotificationTemplateEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String tenantId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_source_id")
    private IntegrationSource integrationSource;

    @Column(nullable = false)
    private String eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_type_code", referencedColumnName = "code", nullable = false)
    private ChannelTypeEntity channelType;

    @Column(nullable = false, length = 32)
    private String locale;

    @Column(columnDefinition = "TEXT")
    private String subjectTemplate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;

    @Column(nullable = false, length = 32)
    private String contentType;

    @Column(nullable = false)
    private Integer templateVersion;

    @Column(nullable = false)
    private Boolean isActive;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
