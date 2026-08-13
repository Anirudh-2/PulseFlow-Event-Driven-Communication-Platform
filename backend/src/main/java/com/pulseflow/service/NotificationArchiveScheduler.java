package com.pulseflow.service;

import com.pulseflow.config.ArchiveProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationArchiveScheduler {
    private static final Logger log = LoggerFactory.getLogger(NotificationArchiveScheduler.class);

    private final JdbcTemplate jdbcTemplate;
    private final ArchiveProperties archiveProperties;

    public NotificationArchiveScheduler(JdbcTemplate jdbcTemplate, ArchiveProperties archiveProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.archiveProperties = archiveProperties;
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void archiveOldNotifications() {
        int retentionDays = Math.max(archiveProperties.getRetentionDays(), 1);

        int copied = jdbcTemplate.update(
                """
                INSERT INTO notif.notifications_archive (
                    id, tenant_id, title, body, type, priority, source_service, source_event_id, metadata, created_at, archived_at
                )
                SELECT
                    n.id, n.tenant_id, n.title, n.body, n.type, n.priority, n.source_service, n.source_event_id, n.metadata, n.created_at, NOW()
                FROM notif.notifications n
                WHERE n.created_at < NOW() - (? * INTERVAL '1 day')
                  AND n.status IN ('DELIVERED', 'EXPIRED')
                  AND n.status <> 'SOFT_DELETED'
                  AND NOT EXISTS (
                      SELECT 1 FROM notif.notifications_archive a WHERE a.id = n.id
                  )
                """,
                retentionDays);

        int updated = jdbcTemplate.update(
                """
                UPDATE notif.notifications n
                SET status = 'SOFT_DELETED',
                    is_deleted = true,
                    updated_at = NOW()
                WHERE n.created_at < NOW() - (? * INTERVAL '1 day')
                  AND n.status IN ('DELIVERED', 'EXPIRED')
                  AND n.status <> 'SOFT_DELETED'
                """,
                retentionDays);

        if (copied > 0 || updated > 0) {
            log.info("Archived notifications: copied={}, softDeleted={}, retentionDays={}", copied, updated, retentionDays);
        }
    }
}
