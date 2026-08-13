package com.pulseflow.repository;

import com.pulseflow.domain.entity.RoutingRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutingRuleRepository extends JpaRepository<RoutingRule, UUID> {
    List<RoutingRule> findByTenantIdAndIsActiveTrueOrderByEvalOrderAsc(String tenantId);

    List<RoutingRule> findByTenantIdOrderByEvalOrderAsc(String tenantId);
    Page<RoutingRule> findByTenantId(String tenantId, Pageable pageable);

    Optional<RoutingRule> findByIdAndTenantId(UUID id, String tenantId);
}
