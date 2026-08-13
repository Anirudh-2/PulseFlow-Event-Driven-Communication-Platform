package com.pulseflow.service;

import com.pulseflow.dto.CreateEventRequest;
import com.pulseflow.dto.NotificationResponse;
import java.util.List;
import java.util.UUID;

public interface NotificationService {
    NotificationResponse processEvent(CreateEventRequest request);
    List<NotificationResponse> getNotifications(String tenantId, String userId);
    void markRead(String tenantId, String userId, UUID notificationId);
}
