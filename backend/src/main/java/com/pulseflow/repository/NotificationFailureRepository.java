package com.pulseflow.repository;

import com.pulseflow.domain.entity.NotificationFailure;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationFailureRepository extends JpaRepository<NotificationFailure, UUID> {}
