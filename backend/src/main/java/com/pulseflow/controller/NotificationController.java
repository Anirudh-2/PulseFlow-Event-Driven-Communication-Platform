package com.pulseflow.controller;

import com.pulseflow.dto.CreateEventRequest;
import com.pulseflow.dto.NotificationResponse;
import com.pulseflow.service.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/events")
    public NotificationResponse processEvent(@Valid @RequestBody CreateEventRequest request) {
        return notificationService.processEvent(request);
    }

    @GetMapping
    public List<NotificationResponse> list(
            @RequestParam String tenantId, @RequestParam(required = false) String userId) {
        return notificationService.getNotifications(tenantId, userId);
    }

    @PostMapping("/{notificationId}/read")
    public Map<String, String> markRead(
            @PathVariable UUID notificationId,
            @RequestParam String tenantId,
            @RequestParam String userId) {
        notificationService.markRead(tenantId, userId, notificationId);
        return Map.of("status", "ok");
    }
}
