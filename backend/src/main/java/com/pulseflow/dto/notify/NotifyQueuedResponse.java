package com.pulseflow.dto.notify;

import java.util.UUID;

public record NotifyQueuedResponse(UUID notificationId, String status) {}
