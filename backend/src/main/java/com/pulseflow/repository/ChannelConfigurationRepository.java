package com.pulseflow.repository;

import com.pulseflow.domain.entity.ChannelConfiguration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelConfigurationRepository extends JpaRepository<ChannelConfiguration, UUID> {
    @EntityGraph(attributePaths = "app")
    List<ChannelConfiguration> findByTenantIdOrderByChannelTypeAscCreatedAtDesc(String tenantId);

    @EntityGraph(attributePaths = "app")
    Optional<ChannelConfiguration> findByIdAndTenantId(UUID id, String tenantId);

    Optional<ChannelConfiguration> findFirstByTenantIdAndApp_IdAndChannelTypeAndIsActiveTrueOrderByCreatedAtDesc(
            String tenantId, UUID appId, String channelType);
}
