package com.pulseflow.repository;

import com.pulseflow.domain.entity.NotificationTemplateEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationTemplateEntityRepository extends JpaRepository<NotificationTemplateEntity, UUID> {
    List<NotificationTemplateEntity> findByTenantIdAndIsActiveTrueOrderByEventTypeAsc(String tenantId);

    @EntityGraph(attributePaths = "channelType")
    Page<NotificationTemplateEntity> findByTenantIdAndIsActiveTrue(String tenantId, Pageable pageable);

    @EntityGraph(attributePaths = "channelType")
    Optional<NotificationTemplateEntity> findByIdAndTenantId(UUID id, String tenantId);

    List<NotificationTemplateEntity> findByTenantIdAndEventTypeIgnoreCaseAndChannelType_CodeIgnoreCaseAndLocaleIgnoreCaseAndIsActiveTrue(
            String tenantId, String eventType, String channelTypeCode, String locale);
}
