package com.pulseflow.repository;

import com.pulseflow.domain.entity.TenantIntegrationConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantIntegrationConfigRepository extends JpaRepository<TenantIntegrationConfig, String> {
}
