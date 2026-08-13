package com.pulseflow.repository;

import com.pulseflow.domain.entity.IntegrationSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationSourceRepository extends JpaRepository<IntegrationSource, UUID> {
    Optional<IntegrationSource> findByTenantIdAndSourceKeyIgnoreCase(String tenantId, String sourceKey);

    List<IntegrationSource> findByTenantIdOrderBySourceKeyAsc(String tenantId);

    List<IntegrationSource> findByTenantIdAndIsActiveTrueOrderBySourceKeyAsc(String tenantId);
}
