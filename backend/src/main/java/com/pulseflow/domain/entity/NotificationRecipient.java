package com.pulseflow.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "notification_recipients", schema = "notif")
public class NotificationRecipient {
    @Id
    private UUID id;
    private String tenantId;
    private UUID notificationId;
    private String userId;
    private String userEmail;
    private String aadObjectId;
    private String telegramChatId;
    private String roleName;
    private Boolean isRead;
    private Boolean isAcknowledged;
    private OffsetDateTime createdAt;
    private OffsetDateTime readAt;
    private OffsetDateTime acknowledgedAt;
}
