package com.pulseflow.repository;

import com.pulseflow.domain.entity.NotificationDeliveryLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryLogRepository extends JpaRepository<NotificationDeliveryLog, UUID> {
    List<NotificationDeliveryLog> findByNotificationId(UUID notificationId);
    List<NotificationDeliveryLog> findByTenantIdAndNotificationId(String tenantId, UUID notificationId);
    boolean existsByTemplateId(UUID templateId);
    Page<NotificationDeliveryLog> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);
    List<NotificationDeliveryLog> findTop200ByTenantIdOrderByCreatedAtDesc(String tenantId);
}