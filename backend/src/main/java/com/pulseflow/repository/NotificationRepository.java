package com.pulseflow.repository;

import com.pulseflow.domain.entity.Notification;
import com.pulseflow.domain.enums.NotificationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Optional<Notification> findByTenantIdAndSourceServiceAndSourceEventId(
            String tenantId, String sourceService, String sourceEventId);
    List<Notification> findByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, NotificationStatus status);

    Optional<Notification> findTopByTenantIdAndSourceServiceAndEventTypeAndStatusAndIsDeletedFalseOrderBySequenceNumberDesc(
            String tenantId, String sourceService, String eventType, NotificationStatus status);
}
