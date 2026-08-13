package com.pulseflow.service;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationHistoryService {
    private final JdbcTemplate jdbcTemplate;

    public NotificationHistoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listActiveAndArchived(String tenantId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return jdbcTemplate.queryForList(
                """
                SELECT id, tenant_id, title, body, type, priority, status, source_service, source_event_id,
                       metadata, created_at, archived_at, is_archived
                FROM notif.vw_notifications_all
                WHERE tenant_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """,
                tenantId,
                safeLimit);
    }
}
