package com.pulseflow.repository;

import com.pulseflow.domain.entity.TenantChannelConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantChannelConfigRepository extends JpaRepository<TenantChannelConfig, UUID> {
    List<TenantChannelConfig> findByTenantIdAndIsEnabledTrueAndChannelType_CodeOrderByPriorityAsc(
            String tenantId, String channelTypeCode);

    Optional<TenantChannelConfig> findFirstByTenantIdAndChannelType_CodeAndIsDefaultTrueAndIsEnabledTrue(
            String tenantId, String channelTypeCode);

    @EntityGraph(attributePaths = "channelType")
    List<TenantChannelConfig> findByTenantIdOrderByChannelType_CodeAscPriorityAsc(String tenantId);

    @EntityGraph(attributePaths = "channelType")
    Optional<TenantChannelConfig> findByIdAndTenantId(UUID id, String tenantId);
}
