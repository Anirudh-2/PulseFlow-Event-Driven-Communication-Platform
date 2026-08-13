package com.pulseflow.repository;

import com.pulseflow.domain.entity.NotificationRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, UUID> {
    List<NotificationRule> findByTenantIdAndIsActiveTrueOrderByEvalOrderAsc(String tenantId);
    List<NotificationRule> findByTenantIdOrderByEvalOrderAsc(String tenantId);
    Optional<NotificationRule> findByIdAndTenantId(UUID id, String tenantId);
}
