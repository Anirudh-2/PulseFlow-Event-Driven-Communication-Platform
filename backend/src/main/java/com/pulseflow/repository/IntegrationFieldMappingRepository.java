package com.pulseflow.repository;

import com.pulseflow.domain.entity.IntegrationFieldMapping;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationFieldMappingRepository extends JpaRepository<IntegrationFieldMapping, UUID> {
    Optional<IntegrationFieldMapping> findFirstByIntegrationSource_IdAndIsActiveTrueOrderByVersionDesc(UUID integrationSourceId);

    @EntityGraph(attributePaths = "integrationSource")
    List<IntegrationFieldMapping> findByIntegrationSource_IdOrderByVersionDesc(UUID integrationSourceId);

    @EntityGraph(attributePaths = "integrationSource")
    Optional<IntegrationFieldMapping> findById(UUID id);
}
