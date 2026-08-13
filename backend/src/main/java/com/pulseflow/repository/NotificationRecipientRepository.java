package com.pulseflow.repository;

import com.pulseflow.domain.entity.NotificationRecipient;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, UUID> {
    List<NotificationRecipient> findByTenantIdAndUserIdOrderByCreatedAtDesc(String tenantId, String userId);
    List<NotificationRecipient> findByTenantIdAndNotificationId(String tenantId, UUID notificationId);
}
