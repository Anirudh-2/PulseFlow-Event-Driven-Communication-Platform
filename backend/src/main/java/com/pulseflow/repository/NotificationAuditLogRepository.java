package com.pulseflow.repository;

import com.pulseflow.domain.entity.NotificationAuditLog;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationAuditLogRepository extends JpaRepository<NotificationAuditLog, Long> {
    Page<NotificationAuditLog> findByTenantIdOrderByOccurredAtDesc(String tenantId, Pageable pageable);
    List<NotificationAuditLog> findTop200ByTenantIdOrderByOccurredAtDesc(String tenantId);
}
