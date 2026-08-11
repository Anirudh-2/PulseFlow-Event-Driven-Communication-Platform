# Notification Notifications & Alerts — Spring Boot Backend Architecture
> **Version:** 1.0 | **Stack:** Spring Boot 3.3 · Spring Data JPA · PostgreSQL · Keycloak · RabbitMQ · Redis · WebSocket

---

## Table of Contents
1. [Architecture Overview](#1-architecture-overview)
2. [Project Structure](#2-project-structure)
3. [Entity Classes](#3-entity-classes)
4. [Repository Layer](#4-repository-layer)
5. [Service Layer](#5-service-layer)
6. [Controller Layer (REST APIs)](#6-controller-layer)
7. [Advanced Features](#7-advanced-features)
8. [Event-Driven Architecture](#8-event-driven-architecture)
9. [Security](#9-security)
10. [Best Practices](#10-best-practices)

---

## 1. Architecture Overview

### Layered Architecture (Clean Architecture + DDD)

```
┌─────────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                           │
│  REST Controllers · WebSocket Handlers · SSE Endpoints         │
├─────────────────────────────────────────────────────────────────┤
│                    APPLICATION LAYER                            │
│  NotificationService · RuleEngineService · DeliveryService     │
│  AuditService · FailureHandlingService · RecipientService       │
├─────────────────────────────────────────────────────────────────┤
│                    DOMAIN LAYER                                 │
│  Entities · Enums · Domain Events · Value Objects              │
│  Business Rules (Expiry, Retry, Priority, ACK logic)           │
├─────────────────────────────────────────────────────────────────┤
│                    INFRASTRUCTURE LAYER                         │
│  JPA Repositories · RabbitMQ · Redis Cache · Keycloak Client   │
│  SMTP · WebSocket Broker · Scheduled Jobs                      │
└─────────────────────────────────────────────────────────────────┘
```

### How the Schema Maps to Layers

| DB Table                        | Layer        | Component                     |
|---------------------------------|--------------|-------------------------------|
| `notif.notifications`           | Domain       | `Notification` entity         |
| `notif.notification_recipients` | Domain       | `NotificationRecipient` entity|
| `notif.notification_rules`      | Domain       | `NotificationRule` entity     |
| `notif.notification_delivery_log` | Domain     | `NotificationDeliveryLog` entity|
| `notif.notification_failures`   | Domain       | `NotificationFailure` entity  |
| `notif_audit.notification_audit_log` | Infrastructure | `NotificationAuditLog` entity |
| `notif_archive.notifications_archive` | Infrastructure | Archive via scheduler  |
| JSONB `metadata`                | Domain       | `NotificationMetadata` value object |
| ENUM types                      | Domain       | Java enums in `domain/enums`  |
| RLS policies                    | Infrastructure | Spring Security + JWT claims  |
| Retry/DLQ logic                 | Application  | `FailureHandlingService`      |

---

## 2. Project Structure

```
com.notification.notifications
│
├── config/
│   ├── DatabaseConfig.java          ← Multi-schema datasource, Hibernate dialect
│   ├── SecurityConfig.java          ← Keycloak JWT + Spring Security
│   ├── WebSocketConfig.java         ← STOMP broker + SockJS endpoint
│   ├── RabbitMQConfig.java          ← Exchange/Queue/DLQ declarations
│   ├── RedisConfig.java             ← RedisTemplate + CacheManager
│   └── JacksonConfig.java           ← JSONB ObjectMapper bean
│
├── controller/
│   ├── NotificationController.java  ← CRUD + lifecycle endpoints
│   ├── NotificationAdminController.java ← Admin: rules, failures, DLQ
│   └── SseController.java           ← Server-Sent Events endpoint
│
├── service/
│   ├── NotificationService.java
│   ├── RecipientService.java
│   ├── RuleEngineService.java
│   ├── DeliveryService.java
│   ├── AuditService.java
│   ├── FailureHandlingService.java
│   └── impl/
│       ├── NotificationServiceImpl.java
│       ├── RecipientServiceImpl.java
│       ├── RuleEngineServiceImpl.java
│       ├── DeliveryServiceImpl.java
│       ├── AuditServiceImpl.java
│       └── FailureHandlingServiceImpl.java
│
├── repository/
│   ├── NotificationRepository.java
│   ├── NotificationRecipientRepository.java
│   ├── NotificationRuleRepository.java
│   ├── NotificationDeliveryLogRepository.java
│   ├── NotificationFailureRepository.java
│   └── NotificationAuditLogRepository.java
│
├── domain/
│   ├── entity/
│   │   ├── Notification.java
│   │   ├── NotificationRecipient.java
│   │   ├── NotificationRule.java
│   │   ├── NotificationDeliveryLog.java
│   │   ├── NotificationFailure.java
│   │   └── NotificationAuditLog.java
│   ├── enums/
│   │   ├── NotificationType.java
│   │   ├── PriorityLevel.java
│   │   ├── NotificationStatus.java
│   │   ├── DeliveryChannel.java
│   │   ├── DeliveryStatus.java
│   │   ├── AuditAction.java
│   │   └── KeycloakRole.java
│   └── valueobject/
│       └── NotificationMetadata.java
│
├── dto/
│   ├── request/
│   │   ├── CreateNotificationRequest.java
│   │   ├── UpdateRecipientStatusRequest.java
│   │   └── CreateNotificationRuleRequest.java
│   └── response/
│       ├── NotificationResponse.java
│       ├── NotificationSummaryResponse.java
│       ├── UnreadCountResponse.java
│       └── PagedResponse.java
│
├── mapper/
│   ├── NotificationMapper.java       ← MapStruct
│   └── RecipientMapper.java
│
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── NotificationNotFoundException.java
│   ├── DuplicateSourceEventException.java
│   └── NotificationAccessDeniedException.java
│
├── security/
│   ├── JwtClaimsExtractor.java
│   ├── KeycloakRoleConverter.java
│   └── TenantContextHolder.java
│
├── events/
│   ├── NotificationCreatedEvent.java  ← Spring ApplicationEvent
│   ├── NotificationReadEvent.java
│   └── NotificationDeliveryEvent.java
│
├── scheduler/
│   ├── NotificationExpiryScheduler.java
│   ├── NotificationArchiveScheduler.java
│   └── DeliveryRetryScheduler.java
│
├── messaging/
│   ├── NotificationEventListener.java ← RabbitMQ consumer
│   └── NotificationEventPublisher.java ← RabbitMQ producer
│
└── util/
    ├── PaginationUtils.java
    └── SecurityUtils.java
```

### Folder Responsibilities

| Package         | Responsibility |
|----------------|----------------|
| `config`       | All Spring beans: datasource, security, messaging, caching, serialization |
| `controller`   | HTTP/WebSocket endpoints — thin layer, delegates to service |
| `service`      | Business logic, transaction boundaries, orchestration |
| `repository`   | Spring Data JPA + custom JPQL/native queries |
| `domain/entity`| JPA entities mapped 1:1 to `notif.*` tables |
| `domain/enums` | Java mirrors of PostgreSQL ENUM types |
| `dto`          | API contract — never expose entities directly |
| `mapper`       | MapStruct: entity ↔ DTO conversion |
| `exception`    | Domain exceptions + `@ControllerAdvice` handler |
| `security`     | JWT extraction, Keycloak role mapping, tenant context |
| `events`       | Spring internal events for decoupled in-process communication |
| `scheduler`    | Quartz/Spring `@Scheduled` jobs for expiry, archive, retry |
| `messaging`    | RabbitMQ consumer/producer — event-driven cross-service integration |

---

## 3. Entity Classes

### 3.1 `pom.xml` (Key Dependencies)

```xml
<dependencies>
    <!-- Spring Boot -->
    <dependency><groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-amqp</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId></dependency>

    <!-- PostgreSQL + JSONB -->
    <dependency><groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId></dependency>
    <dependency><groupId>io.hypersistence</groupId>
        <artifactId>hypersistence-utils-hibernate-63</artifactId>
        <version>3.7.3</version></dependency>

    <!-- Keycloak -->
    <dependency><groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId></dependency>

    <!-- MapStruct -->
    <dependency><groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId><version>1.5.5.Final</version></dependency>

    <!-- Lombok -->
    <dependency><groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId><optional>true</optional></dependency>
</dependencies>
```

### 3.2 `application.yml`

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/notification
    username: notif_app_user
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
  jpa:
    hibernate:
      ddl-auto: validate          # Schema managed by Flyway
    properties:
      hibernate:
        default_schema: notif
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc:
          lob.non_contextual_creation: true
        format_sql: true
    show-sql: false

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI}
          jwk-set-uri: ${KEYCLOAK_JWK_URI}

  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: 5672
    username: ${RABBITMQ_USER}
    password: ${RABBITMQ_PASSWORD}

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
      password: ${REDIS_PASSWORD}

notification:
  retry:
    max-attempts: 3
    initial-delay-ms: 5000
    multiplier: 2.0
  cache:
    rules-ttl-seconds: 60
    unread-count-ttl-seconds: 30
  rabbitmq:
    exchange: notification.notifications
    routing-key: notification.created
    dlq-queue: notification.notifications.dlq
```

---

### 3.3 Enums

```java
// domain/enums/NotificationType.java
package com.notification.notifications.domain.enums;

public enum NotificationType {
    SYSTEM, HR_ACTION, REMINDER, ANNOUNCEMENT, SECURITY, WORKFLOW
}

// domain/enums/PriorityLevel.java
public enum PriorityLevel {
    LOW, MEDIUM, HIGH, CRITICAL;

    public int retentionDays() {
        return switch (this) {
            case LOW      -> 14;
            case MEDIUM   -> 30;
            case HIGH     -> 60;
            case CRITICAL -> 90;
        };
    }
}

// domain/enums/NotificationStatus.java
public enum NotificationStatus { ACTIVE, EXPIRED, ARCHIVED, SOFT_DELETED }

// domain/enums/DeliveryChannel.java
public enum DeliveryChannel { WEBSOCKET, SSE, EMAIL, PUSH, POLLING }

// domain/enums/DeliveryStatus.java
public enum DeliveryStatus { PENDING, DELIVERED, FAILED, RETRYING, DEAD_LETTERED }

// domain/enums/AuditAction.java
public enum AuditAction {
    CREATED, DELIVERED, READ, ACKNOWLEDGED, DISMISSED, EXPIRED, ARCHIVED,
    SOFT_DELETED, HARD_DELETED, RULE_CREATED, RULE_UPDATED, RULE_DEACTIVATED,
    EMAIL_SENT, EMAIL_FAILED, RETRY_ATTEMPTED, DEAD_LETTERED
}

// domain/enums/KeycloakRole.java
public enum KeycloakRole {
    ADMIN, HR_MANAGER, EMPLOYEE, FINANCE, RECRUITER,
    PAYROLL_OFFICER, DEPARTMENT_HEAD, IT_ADMIN
}
```

---

### 3.4 `Notification.java` Entity

```java
package com.notification.notifications.domain.entity;

import com.notification.notifications.domain.enums.*;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "notifications", schema = "notif",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_notifications_source_event",
        columnNames = {"source_service", "source_event_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@org.hibernate.annotations.TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false,
            columnDefinition = "notif.notification_type")
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false,
            columnDefinition = "notif.priority_level")
    private PriorityLevel priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "notif.notification_status")
    @Builder.Default
    private NotificationStatus status = NotificationStatus.ACTIVE;

    @Column(name = "source_service", nullable = false, length = 100)
    private String sourceService;

    @Column(name = "source_event_id", length = 255)
    private String sourceEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    /**
     * JSONB payload — stores actionUrl, entityId, entityType, deepLink etc.
     * Uses hypersistence-utils for native PostgreSQL JSONB type mapping.
     */
    @Type(type = "jsonb")
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "deleted_by", length = 255)
    private String deletedBy;

    /**
     * Optimistic locking — mirrors DB trigger-managed version column.
     * Spring JPA will throw OptimisticLockingFailureException on conflict.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY)
    @Builder.Default
    private List<NotificationRecipient> recipients = new ArrayList<>();

    @OneToMany(mappedBy = "notification", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY)
    @Builder.Default
    private List<NotificationDeliveryLog> deliveryLogs = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // ── Domain logic ──

    public boolean isExpired() {
        return expiresAt != null && OffsetDateTime.now().isAfter(expiresAt);
    }

    public void softDelete(String deletedBy) {
        this.deleted = true;
        this.deletedAt = OffsetDateTime.now();
        this.deletedBy = deletedBy;
        this.status = NotificationStatus.SOFT_DELETED;
    }

    public void markExpired() {
        this.status = NotificationStatus.EXPIRED;
    }
}
```

---

### 3.5 `NotificationRecipient.java` Entity

```java
@Entity
@Table(name = "notification_recipients", schema = "notif",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_recipients_user_notification",
        columnNames = {"notification_id", "user_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id", foreignKey = @ForeignKey(
        name = "fk_recipients_notification"))
    private Notification notification;

    @Column(name = "user_id", length = 255)
    private String userId;   // Keycloak sub claim

    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", columnDefinition = "notif.keycloak_role")
    private KeycloakRole roleName;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "is_acknowledged", nullable = false)
    @Builder.Default
    private boolean acknowledged = false;

    @Column(name = "is_dismissed", nullable = false)
    @Builder.Default
    private boolean dismissed = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "dismissed_at")
    private OffsetDateTime dismissedAt;

    @Column(name = "email_sent", nullable = false)
    @Builder.Default
    private boolean emailSent = false;

    @Column(name = "email_sent_at")
    private OffsetDateTime emailSentAt;

    @Column(name = "email_unsubscribed", nullable = false)
    @Builder.Default
    private boolean emailUnsubscribed = false;

    @PrePersist
    protected void onCreate() { this.createdAt = OffsetDateTime.now(); }

    // ── Domain logic ──

    public void markRead() {
        if (!this.read) {
            this.read = true;
            this.readAt = OffsetDateTime.now();
        }
    }

    public void acknowledge() {
        if (!this.read) throw new IllegalStateException(
            "Cannot acknowledge before reading notification");
        if (!this.acknowledged) {
            this.acknowledged = true;
            this.acknowledgedAt = OffsetDateTime.now();
        }
    }

    public void dismiss() {
        this.dismissed = true;
        this.dismissedAt = OffsetDateTime.now();
    }
}
```

---

### 3.6 `NotificationRule.java` Entity

```java
@Entity
@Table(name = "notification_rules", schema = "notif")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@org.hibernate.annotations.TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class NotificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", nullable = false,
            columnDefinition = "notif.keycloak_role")
    private KeycloakRole roleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type",
            columnDefinition = "notif.notification_type")
    private NotificationType notificationType;   // null = all types

    @Enumerated(EnumType.STRING)
    @Column(name = "priority_override",
            columnDefinition = "notif.priority_level")
    private PriorityLevel priorityOverride;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Type(type = "jsonb")
    @Column(name = "conditions", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> conditions = new HashMap<>();

    /**
     * PostgreSQL ARRAY type for delivery channels.
     * Hypersistence handles the conversion to/from delivery_channel[].
     */
    @Column(name = "channels", columnDefinition = "notif.delivery_channel[]")
    @Builder.Default
    private DeliveryChannel[] channels = new DeliveryChannel[]{DeliveryChannel.WEBSOCKET};

    @Column(name = "eval_order", nullable = false)
    @Builder.Default
    private short evalOrder = 100;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
```

---

### 3.7 `NotificationDeliveryLog.java` Entity

```java
@Entity
@Table(name = "notification_delivery_log", schema = "notif")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "notification_id",
        foreignKey = @ForeignKey(name = "fk_delivery_log_notification"))
    private Notification notification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id",
        foreignKey = @ForeignKey(name = "fk_delivery_log_recipient"))
    private NotificationRecipient recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false,
            columnDefinition = "notif.delivery_channel")
    private DeliveryChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "notif.delivery_status")
    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private short attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private short maxAttempts = 3;

    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @PrePersist
    protected void onCreate() { this.createdAt = OffsetDateTime.now(); }

    // ── Domain logic ──

    public void recordAttempt(String errorCode, String errorMsg) {
        this.attemptCount++;
        this.lastAttemptAt = OffsetDateTime.now();
        this.errorCode = errorCode;
        this.errorMessage = errorMsg;
        if (this.attemptCount >= this.maxAttempts) {
            this.status = DeliveryStatus.DEAD_LETTERED;
        } else {
            this.status = DeliveryStatus.RETRYING;
            // Exponential back-off: 5s, 10s, 20s...
            long delayMs = 5000L * (long) Math.pow(2, this.attemptCount - 1);
            this.nextRetryAt = OffsetDateTime.now().plusNanos(delayMs * 1_000_000L);
        }
    }

    public void markDelivered() {
        this.status = DeliveryStatus.DELIVERED;
        this.deliveredAt = OffsetDateTime.now();
        this.nextRetryAt = null;
    }

    public boolean isExhausted() {
        return this.attemptCount >= this.maxAttempts;
    }
}
```

---

### 3.8 `NotificationFailure.java` Entity

```java
@Entity
@Table(name = "notification_failures", schema = "notif")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@org.hibernate.annotations.TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class NotificationFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "notification_id")
    private UUID notificationId;    // Nullable — creation itself may have failed

    @Column(name = "recipient_id")
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", columnDefinition = "notif.delivery_channel")
    private DeliveryChannel channel;

    @Type(type = "jsonb")
    @Column(name = "raw_event_payload", columnDefinition = "jsonb")
    private Map<String, Object> rawEventPayload;   // Original RabbitMQ message for replay

    @Column(name = "failure_reason", nullable = false, columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "failure_category", length = 100)
    private String failureCategory;   // e.g. DB_UNAVAILABLE, SMTP_TIMEOUT

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolved_by", length = 255)
    private String resolvedBy;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "is_resolved", nullable = false)
    @Builder.Default
    private boolean resolved = false;

    @PrePersist
    protected void onCreate() { this.occurredAt = OffsetDateTime.now(); }

    public void resolve(String resolvedBy, String notes) {
        this.resolved = true;
        this.resolvedAt = OffsetDateTime.now();
        this.resolvedBy = resolvedBy;
        this.resolutionNotes = notes;
    }
}
```

---

### 3.9 `NotificationAuditLog.java` Entity

```java
/**
 * Mapped to notif_audit schema.
 * READ-ONLY from application side — inserts only.
 * DB trigger prevents UPDATE/DELETE.
 */
@Entity
@Table(name = "notification_audit_log", schema = "notif_audit")
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
@Immutable   // Hibernate: treats as read-only after persist
@org.hibernate.annotations.TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
public class NotificationAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
        generator = "audit_log_seq")
    @SequenceGenerator(name = "audit_log_seq",
        sequenceName = "notif_audit.notification_audit_log_id_seq",
        allocationSize = 1)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "notification_id")
    private UUID notificationId;

    @Column(name = "recipient_id")
    private UUID recipientId;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false,
            columnDefinition = "notif_audit.audit_action")
    private AuditAction action;

    @Column(name = "actor_user_id", length = 255)
    private String actorUserId;

    @Column(name = "actor_role", length = 100)
    private String actorRole;

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "session_id", length = 255)
    private String sessionId;

    @Type(type = "jsonb")
    @Column(name = "old_state", columnDefinition = "jsonb")
    private Map<String, Object> oldState;

    @Type(type = "jsonb")
    @Column(name = "new_state", columnDefinition = "jsonb")
    private Map<String, Object> newState;

    @Type(type = "jsonb")
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;   // Spring Sleuth/Micrometer trace ID

    @PrePersist
    protected void onCreate() { this.occurredAt = OffsetDateTime.now(); }
}
```

---

## 4. Repository Layer

### 4.1 `NotificationRepository.java`

```java
package com.notification.notifications.repository;

import com.notification.notifications.domain.entity.Notification;
import com.notification.notifications.domain.enums.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.*;

public interface NotificationRepository
        extends JpaRepository<Notification, UUID> {

    // ── Active notifications for admin listing ──
    @Query("""
        SELECT n FROM Notification n
        WHERE n.status = 'ACTIVE'
          AND n.deleted = false
        ORDER BY n.priority DESC, n.createdAt DESC
        """)
    Page<Notification> findAllActive(Pageable pageable);

    // ── Idempotency check ──
    Optional<Notification> findBySourceServiceAndSourceEventId(
        String sourceService, String sourceEventId);

    // ── Expiry daemon: find ACTIVE notifications past their expiry ──
    @Query("""
        SELECT n FROM Notification n
        WHERE n.status = 'ACTIVE'
          AND n.expiresAt IS NOT NULL
          AND n.expiresAt < :now
        """)
    List<Notification> findExpiredNotifications(
        @Param("now") OffsetDateTime now);

    // ── Archive daemon: find EXPIRED notifications older than cutoff ──
    @Query("""
        SELECT n FROM Notification n
        WHERE n.status = 'EXPIRED'
          AND n.updatedAt < :cutoff
        """)
    List<Notification> findExpiredBefore(
        @Param("cutoff") OffsetDateTime cutoff, Pageable pageable);

    // ── Bulk status update for expiry scheduler ──
    @Modifying
    @Query("""
        UPDATE Notification n
        SET n.status = 'EXPIRED', n.updatedAt = :now
        WHERE n.id IN :ids
        """)
    int bulkMarkExpired(@Param("ids") List<UUID> ids,
                        @Param("now") OffsetDateTime now);
}
```

---

### 4.2 `NotificationRecipientRepository.java`

```java
public interface NotificationRecipientRepository
        extends JpaRepository<NotificationRecipient, UUID> {

    // ── Core query: user's unread notifications (hits partial index) ──
    @Query("""
        SELECT r FROM NotificationRecipient r
        JOIN FETCH r.notification n
        WHERE r.userId = :userId
          AND r.read = false
          AND r.dismissed = false
          AND n.status = 'ACTIVE'
          AND n.deleted = false
        ORDER BY n.priority DESC, r.createdAt DESC
        """)
    Page<NotificationRecipient> findUnreadByUserId(
        @Param("userId") String userId, Pageable pageable);

    // ── Unread count for badge (Redis-cached in service layer) ──
    @Query("""
        SELECT COUNT(r) FROM NotificationRecipient r
        JOIN r.notification n
        WHERE r.userId = :userId
          AND r.read = false
          AND r.dismissed = false
          AND n.status = 'ACTIVE'
          AND n.deleted = false
        """)
    long countUnreadByUserId(@Param("userId") String userId);

    // ── All recipients of a notification (for delivery fan-out) ──
    @Query("""
        SELECT r FROM NotificationRecipient r
        WHERE r.notification.id = :notificationId
        """)
    List<NotificationRecipient> findByNotificationId(
        @Param("notificationId") UUID notificationId);

    // ── Email digest: pending email sends ──
    @Query("""
        SELECT r FROM NotificationRecipient r
        JOIN FETCH r.notification n
        WHERE r.emailSent = false
          AND r.read = false
          AND r.emailUnsubscribed = false
          AND r.createdAt < :threshold
          AND n.status = 'ACTIVE'
        """)
    List<NotificationRecipient> findPendingEmailDigest(
        @Param("threshold") OffsetDateTime threshold);

    Optional<NotificationRecipient> findByNotificationIdAndUserId(
        UUID notificationId, String userId);
}
```

---

### 4.3 `NotificationRuleRepository.java`

```java
public interface NotificationRuleRepository
        extends JpaRepository<NotificationRule, UUID> {

    // ── RBAC Engine lookup — results cached in Redis ──
    @Query("""
        SELECT r FROM NotificationRule r
        WHERE r.active = true
          AND r.roleName = :role
          AND (r.notificationType IS NULL OR r.notificationType = :type)
        ORDER BY r.evalOrder ASC, r.createdAt ASC
        """)
    List<NotificationRule> findActiveRulesForRoleAndType(
        @Param("role") KeycloakRole role,
        @Param("type") NotificationType type);

    // ── All active rules (for cache warm-up) ──
    @Query("SELECT r FROM NotificationRule r WHERE r.active = true ORDER BY r.evalOrder ASC")
    List<NotificationRule> findAllActiveRules();
}
```

---

### 4.4 `NotificationDeliveryLogRepository.java`

```java
public interface NotificationDeliveryLogRepository
        extends JpaRepository<NotificationDeliveryLog, UUID> {

    // ── Retry worker: pending deliveries due now ──
    @Query("""
        SELECT d FROM NotificationDeliveryLog d
        WHERE d.status IN ('PENDING', 'RETRYING')
          AND d.nextRetryAt IS NOT NULL
          AND d.nextRetryAt <= :now
        ORDER BY d.nextRetryAt ASC
        """)
    List<NotificationDeliveryLog> findDueForRetry(
        @Param("now") OffsetDateTime now, Pageable pageable);

    // ── DLQ escalation: logs that exhausted all attempts ──
    @Query("""
        SELECT d FROM NotificationDeliveryLog d
        WHERE d.status = 'DEAD_LETTERED'
          AND d.lastAttemptAt > :since
        """)
    List<NotificationDeliveryLog> findRecentDeadLettered(
        @Param("since") OffsetDateTime since);

    List<NotificationDeliveryLog> findByNotificationIdAndChannel(
        UUID notificationId, DeliveryChannel channel);
}
```

---

## 5. Service Layer

### 5.1 `NotificationService.java` (Interface + Implementation)

```java
public interface NotificationService {
    NotificationResponse createNotification(CreateNotificationRequest request,
                                            String actorUserId);
    Page<NotificationResponse> getUserNotifications(String userId,
                                                     boolean unreadOnly,
                                                     Pageable pageable);
    long getUnreadCount(String userId);
    NotificationResponse markRead(UUID notificationId, String userId);
    NotificationResponse acknowledge(UUID notificationId, String userId);
    void softDelete(UUID notificationId, String actorUserId);
}
```

```java
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository recipientRepository;
    private final RuleEngineService ruleEngineService;
    private final DeliveryService deliveryService;
    private final AuditService auditService;
    private final NotificationMapper notificationMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisTemplate<String, Long> redisTemplate;

    private static final String UNREAD_COUNT_KEY = "notif:unread:";

    /**
     * Full notification creation flow:
     * 1. Idempotency check (source_service + source_event_id)
     * 2. Persist notification
     * 3. Rule engine resolves recipients + channels
     * 4. Persist recipient rows
     * 5. Enqueue delivery jobs
     * 6. Write audit record
     * 7. Publish internal Spring event (for WebSocket push)
     */
    @Override
    public NotificationResponse createNotification(
            CreateNotificationRequest req, String actorUserId) {

        // Step 1: Idempotency check
        if (req.getSourceEventId() != null) {
            notificationRepository
                .findBySourceServiceAndSourceEventId(
                    req.getSourceService(), req.getSourceEventId())
                .ifPresent(existing -> {
                    log.warn("Duplicate notification event detected: {}/{}",
                        req.getSourceService(), req.getSourceEventId());
                    throw new DuplicateSourceEventException(existing.getId());
                });
        }

        // Step 2: Build + persist
        Notification notification = notificationMapper.toEntity(req);
        notification = notificationRepository.save(notification);
        log.info("Notification created: id={}, type={}, priority={}",
            notification.getId(), notification.getType(), notification.getPriority());

        // Step 3–4: Rule engine fan-out (async in prod via @Async)
        List<NotificationRecipient> recipients =
            ruleEngineService.resolveRecipients(notification, req.getTargetUserIds());
        recipientRepository.saveAll(recipients);

        // Step 5: Enqueue delivery
        deliveryService.enqueueDelivery(notification, recipients);

        // Step 6: Audit
        auditService.record(AuditAction.CREATED, notification.getId(),
            null, actorUserId, null, null,
            Map.of("type", notification.getType(),
                   "priority", notification.getPriority()));

        // Step 7: Invalidate Redis unread count for all recipients
        recipients.stream()
            .filter(r -> r.getUserId() != null)
            .forEach(r -> redisTemplate.delete(UNREAD_COUNT_KEY + r.getUserId()));

        // Step 8: Publish Spring event for WebSocket push
        eventPublisher.publishEvent(
            new NotificationCreatedEvent(this, notification, recipients));

        return notificationMapper.toResponse(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(
            String userId, boolean unreadOnly, Pageable pageable) {

        Page<NotificationRecipient> page = unreadOnly
            ? recipientRepository.findUnreadByUserId(userId, pageable)
            : recipientRepository.findAllByUserId(userId, pageable);

        return page.map(notificationMapper::toResponseFromRecipient);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        String key = UNREAD_COUNT_KEY + userId;
        Long cached = redisTemplate.opsForValue().get(key);
        if (cached != null) return cached;

        long count = recipientRepository.countUnreadByUserId(userId);
        redisTemplate.opsForValue().set(key, count, 
            Duration.ofSeconds(30));
        return count;
    }

    @Override
    public NotificationResponse markRead(UUID notificationId, String userId) {
        NotificationRecipient recipient = getRecipientOrThrow(notificationId, userId);
        recipient.markRead();
        recipientRepository.save(recipient);

        // Invalidate cache
        redisTemplate.delete(UNREAD_COUNT_KEY + userId);

        auditService.record(AuditAction.READ, notificationId,
            recipient.getId(), userId, null, null, Map.of());

        eventPublisher.publishEvent(
            new NotificationReadEvent(this, notificationId, userId));

        return notificationMapper.toResponse(recipient.getNotification());
    }

    @Override
    public NotificationResponse acknowledge(UUID notificationId, String userId) {
        NotificationRecipient recipient = getRecipientOrThrow(notificationId, userId);
        recipient.acknowledge();   // Throws if not read first
        recipientRepository.save(recipient);

        auditService.record(AuditAction.ACKNOWLEDGED, notificationId,
            recipient.getId(), userId, null, null, Map.of());

        return notificationMapper.toResponse(recipient.getNotification());
    }

    private NotificationRecipient getRecipientOrThrow(UUID notificationId, String userId) {
        return recipientRepository
            .findByNotificationIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new NotificationNotFoundException(notificationId));
    }
}
```

---

### 5.2 `RuleEngineService.java`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleEngineServiceImpl implements RuleEngineService {

    private final NotificationRuleRepository ruleRepository;
    private final RedisTemplate<String, List<NotificationRule>> ruleCache;

    private static final String RULES_CACHE_PREFIX = "notif:rules:";
    private static final Duration RULES_TTL = Duration.ofSeconds(60);

    /**
     * Resolves which users/roles should receive the notification and on which channels.
     * 1. Load active rules for notification type from Redis cache (DB fallback)
     * 2. Evaluate JSONB conditions against notification metadata
     * 3. Build NotificationRecipient rows per matching rule
     * 4. Merge explicit targetUserIds from request
     */
    @Override
    public List<NotificationRecipient> resolveRecipients(
            Notification notification, List<String> explicitUserIds) {

        List<NotificationRule> rules = getActiveRules(notification.getType());
        Map<String, NotificationRecipient> recipientMap = new LinkedHashMap<>();

        // Role-based recipients from rules
        for (NotificationRule rule : rules) {
            if (!evaluateConditions(rule.getConditions(), notification)) continue;

            NotificationRecipient recipient = NotificationRecipient.builder()
                .notification(notification)
                .roleName(rule.getRoleName())
                .build();
            recipientMap.put("role:" + rule.getRoleName().name(), recipient);
        }

        // Explicit user-targeted recipients
        if (explicitUserIds != null) {
            explicitUserIds.forEach(uid ->
                recipientMap.put("user:" + uid, NotificationRecipient.builder()
                    .notification(notification)
                    .userId(uid)
                    .build()));
        }

        return new ArrayList<>(recipientMap.values());
    }

    private List<NotificationRule> getActiveRules(NotificationType type) {
        String cacheKey = RULES_CACHE_PREFIX + type.name();
        List<NotificationRule> cached = ruleCache.opsForValue().get(cacheKey);
        if (cached != null) return cached;

        List<NotificationRule> rules = ruleRepository.findAllActiveRules()
            .stream()
            .filter(r -> r.getNotificationType() == null
                      || r.getNotificationType() == type)
            .collect(Collectors.toList());

        ruleCache.opsForValue().set(cacheKey, rules, RULES_TTL);
        return rules;
    }

    /**
     * Evaluates JSONB conditions map against notification.
     * Supports: min_priority, source_service, department.
     */
    private boolean evaluateConditions(Map<String, Object> conditions,
                                        Notification n) {
        if (conditions == null || conditions.isEmpty()) return true;

        String minPriority = (String) conditions.get("min_priority");
        if (minPriority != null) {
            PriorityLevel required = PriorityLevel.valueOf(minPriority);
            if (n.getPriority().ordinal() < required.ordinal()) return false;
        }

        String sourceService = (String) conditions.get("source_service");
        if (sourceService != null &&
            !sourceService.equalsIgnoreCase(n.getSourceService())) return false;

        return true;
    }
}
```

---

### 5.3 `AuditService.java`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditServiceImpl implements AuditService {

    private final NotificationAuditLogRepository auditLogRepository;

    /**
     * All audit writes use a separate transaction to ensure
     * audit records are persisted even if parent TX rolls back.
     * Uses REQUIRES_NEW propagation.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditAction action, UUID notificationId,
                       UUID recipientId, String actorUserId,
                       Map<String, Object> oldState, Map<String, Object> newState,
                       Map<String, Object> metadata) {

        // Extract context from MDC (populated by JWT filter)
        String correlationId = MDC.get("traceId");
        String ipAddress     = MDC.get("clientIp");
        String userAgent     = MDC.get("userAgent");
        String sessionId     = MDC.get("sessionId");

        NotificationAuditLog entry = NotificationAuditLog.builder()
            .action(action)
            .notificationId(notificationId)
            .recipientId(recipientId)
            .actorUserId(actorUserId)
            .oldState(oldState)
            .newState(newState)
            .metadata(metadata)
            .correlationId(correlationId)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .sessionId(sessionId)
            .build();

        auditLogRepository.save(entry);
        log.debug("Audit recorded: action={}, notificationId={}, actor={}",
            action, notificationId, actorUserId);
    }
}
```

---

### 5.4 `FailureHandlingService.java`

```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FailureHandlingServiceImpl implements FailureHandlingService {

    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final NotificationFailureRepository failureRepository;
    private final AuditService auditService;

    /**
     * Called when a delivery attempt fails.
     * Records the attempt, schedules retry or escalates to DLQ.
     */
    @Override
    public void handleDeliveryFailure(NotificationDeliveryLog log,
                                       String errorCode, String errorMsg,
                                       Map<String, Object> rawPayload) {
        log.recordAttempt(errorCode, errorMsg);
        deliveryLogRepository.save(log);

        if (log.isExhausted()) {
            escalateToDlq(log, rawPayload);
            auditService.record(AuditAction.DEAD_LETTERED,
                log.getNotification().getId(), log.getRecipient().getId(),
                "SYSTEM", null, null,
                Map.of("channel", log.getChannel(), "errorCode", errorCode));
        } else {
            auditService.record(AuditAction.RETRY_ATTEMPTED,
                log.getNotification().getId(), log.getRecipient().getId(),
                "SYSTEM", null, null,
                Map.of("attempt", log.getAttemptCount(), "nextRetry", log.getNextRetryAt()));
        }
    }

    private void escalateToDlq(NotificationDeliveryLog deliveryLog,
                                 Map<String, Object> rawPayload) {
        NotificationFailure failure = NotificationFailure.builder()
            .notificationId(deliveryLog.getNotification().getId())
            .recipientId(deliveryLog.getRecipient().getId())
            .channel(deliveryLog.getChannel())
            .failureReason(deliveryLog.getErrorMessage())
            .failureCategory(deliveryLog.getErrorCode())
            .rawEventPayload(rawPayload)
            .build();

        failureRepository.save(failure);
        log.error("DLQ: Notification delivery exhausted retries. notificationId={}, channel={}",
            deliveryLog.getNotification().getId(), deliveryLog.getChannel());
    }

    @Override
    public void resolveFailure(UUID failureId, String resolvedBy, String notes) {
        NotificationFailure failure = failureRepository.findById(failureId)
            .orElseThrow(() -> new NotificationNotFoundException(failureId));
        failure.resolve(resolvedBy, notes);
        failureRepository.save(failure);
    }
}
```

---

## 6. Controller Layer

### 6.1 DTOs

```java
// dto/request/CreateNotificationRequest.java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Validated
public class CreateNotificationRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255)
    private String title;

    @NotBlank(message = "Body is required")
    private String body;

    @NotNull
    private NotificationType type;

    @NotNull
    private PriorityLevel priority;

    @NotBlank
    @Size(max = 100)
    private String sourceService;

    @Size(max = 255)
    private String sourceEventId;

    private OffsetDateTime expiresAt;

    private Map<String, Object> metadata = new HashMap<>();

    private List<String> targetUserIds;  // Explicit user targeting
}

// dto/response/NotificationResponse.java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationResponse {
    private UUID id;
    private String title;
    private String body;
    private NotificationType type;
    private PriorityLevel priority;
    private NotificationStatus status;
    private String sourceService;
    private OffsetDateTime createdAt;
    private OffsetDateTime expiresAt;
    private Map<String, Object> metadata;
    private boolean read;
    private boolean acknowledged;
    private OffsetDateTime readAt;
    private OffsetDateTime acknowledgedAt;
}

// dto/response/UnreadCountResponse.java
@Data @AllArgsConstructor
public class UnreadCountResponse {
    private String userId;
    private long unreadCount;
    private OffsetDateTime computedAt;
}
```

---

### 6.2 `NotificationController.java`

```java
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
@Validated
public class NotificationController {

    private final NotificationService notificationService;
    private final SecurityUtils securityUtils;

    /**
     * GET /api/v1/notifications
     * Returns paginated list of notifications for authenticated user.
     * Supports: ?unreadOnly=true&page=0&size=20&sort=createdAt,desc
     */
    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20, sort = "createdAt",
                             direction = Sort.Direction.DESC) Pageable pageable) {

        String userId = securityUtils.getCurrentUserId();
        Page<NotificationResponse> page =
            notificationService.getUserNotifications(userId, unreadOnly, pageable);
        return ResponseEntity.ok(page);
    }

    /**
     * GET /api/v1/notifications/unread-count
     * Returns Redis-cached unread count for the authenticated user.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount() {
        String userId = securityUtils.getCurrentUserId();
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(
            new UnreadCountResponse(userId, count, OffsetDateTime.now()));
    }

    /**
     * POST /api/v1/notifications
     * Creates a new notification. Restricted to service accounts & ADMIN role.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR_MANAGER') or hasAuthority('SCOPE_notification:write')")
    public ResponseEntity<NotificationResponse> createNotification(
            @RequestBody @Valid CreateNotificationRequest request) {

        String actorId = securityUtils.getCurrentUserId();
        NotificationResponse response =
            notificationService.createNotification(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/notifications/{id}/read
     * Marks a notification as read for the authenticated user.
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(
            @PathVariable UUID id) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(notificationService.markRead(id, userId));
    }

    /**
     * POST /api/v1/notifications/{id}/acknowledge
     * Marks a CRITICAL notification as explicitly acknowledged.
     * Business rule: requires isRead = true first.
     */
    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<NotificationResponse> acknowledge(
            @PathVariable UUID id) {
        String userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(notificationService.acknowledge(id, userId));
    }

    /**
     * DELETE /api/v1/notifications/{id}
     * Soft-delete (is_deleted = true). Admin only.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(@PathVariable UUID id) {
        String userId = securityUtils.getCurrentUserId();
        notificationService.softDelete(id, userId);
    }
}
```

---

### 6.3 Global Exception Handler

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NotificationNotFoundException ex) {
        return buildError(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND",
            ex.getMessage());
    }

    @ExceptionHandler(DuplicateSourceEventException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            DuplicateSourceEventException ex) {
        // 200 OK with existing ID — idempotent behaviour
        return ResponseEntity.ok()
            .header("X-Existing-Notification-Id", ex.getExistingId().toString())
            .body(ErrorResponse.builder()
                .code("DUPLICATE_EVENT").message("Idempotent: already processed")
                .build());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            OptimisticLockingFailureException ex) {
        return buildError(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
            "Resource was modified concurrently, please retry");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult()
            .getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField, FieldError::getDefaultMessage));
        return ResponseEntity.badRequest()
            .body(ErrorResponse.builder()
                .code("VALIDATION_FAILED")
                .message("Request validation failed")
                .details(fieldErrors)
                .build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return buildError(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
            "Insufficient permissions");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> buildError(HttpStatus status,
            String code, String message) {
        return ResponseEntity.status(status)
            .body(ErrorResponse.builder()
                .code(code).message(message)
                .timestamp(OffsetDateTime.now())
                .build());
    }
}
```

---

## 7. Advanced Features

### 7.1 WebSocket Real-Time Push

```java
// config/WebSocketConfig.java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory broker for /topic and /queue destinations
        registry.enableSimpleBroker("/topic", "/queue");
        // Application-level routing prefix
        registry.setApplicationDestinationPrefixes("/app");
        // User-specific messaging prefix (maps to /queue/notifications-user{id})
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/notifications")
            .setAllowedOriginPatterns("*")
            .withSockJS();    // Fallback for browsers without WebSocket
    }
}

// events/NotificationWebSocketPublisher.java
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Listens for Spring internal events and pushes to WebSocket subscribers.
     * User-specific queue: /user/{userId}/queue/notifications
     */
    @EventListener
    public void onNotificationCreated(NotificationCreatedEvent event) {
        Notification n = event.getNotification();
        NotificationSummaryResponse payload = buildSummary(n);

        event.getRecipients().stream()
            .filter(r -> r.getUserId() != null)
            .forEach(recipient -> {
                messagingTemplate.convertAndSendToUser(
                    recipient.getUserId(),
                    "/queue/notifications",
                    payload
                );
                log.debug("WebSocket push sent to userId={}", recipient.getUserId());
            });
    }
}

// controller/SseController.java — Fallback for clients without WebSocket
@RestController
@RequestMapping("/api/v1/sse")
@RequiredArgsConstructor
public class SseController {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @GetMapping(value = "/notifications", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToNotifications() {
        String userId = SecurityUtils.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(0L); // No timeout

        emitters.put(userId, emitter);
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));

        return emitter;
    }

    // Called by DeliveryService for SSE channel
    public void sendToUser(String userId, Object payload) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                emitters.remove(userId);
            }
        }
    }
}
```

---

### 7.2 Retry Scheduler + Archive Scheduler

```java
// scheduler/DeliveryRetryScheduler.java
@Component
@RequiredArgsConstructor
@Slf4j
public class DeliveryRetryScheduler {

    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final DeliveryService deliveryService;

    /**
     * Runs every 30 seconds. Picks up PENDING/RETRYING logs whose
     * nextRetryAt is in the past and re-attempts delivery.
     * Processes in batches of 50 to avoid memory pressure.
     */
    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void retryPendingDeliveries() {
        Pageable batch = PageRequest.of(0, 50);
        List<NotificationDeliveryLog> due =
            deliveryLogRepository.findDueForRetry(OffsetDateTime.now(), batch);

        if (!due.isEmpty()) {
            log.info("Retry scheduler: found {} pending delivery logs", due.size());
            due.forEach(deliveryService::attemptDelivery);
        }
    }
}

// scheduler/NotificationExpiryScheduler.java
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationExpiryScheduler {

    private final NotificationRepository notificationRepository;
    private final AuditService auditService;

    /**
     * Runs every 5 minutes. Bulk-marks ACTIVE notifications whose
     * expires_at is in the past as EXPIRED.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void expireNotifications() {
        List<Notification> expired =
            notificationRepository.findExpiredNotifications(OffsetDateTime.now());

        if (!expired.isEmpty()) {
            List<UUID> ids = expired.stream().map(Notification::getId).toList();
            int count = notificationRepository.bulkMarkExpired(ids, OffsetDateTime.now());
            log.info("Expired {} notifications", count);

            ids.forEach(id -> auditService.record(
                AuditAction.EXPIRED, id, null, "SYSTEM",
                null, null, Map.of()));
        }
    }
}

// scheduler/NotificationArchiveScheduler.java
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationArchiveScheduler {

    private final NotificationRepository notificationRepository;

    /**
     * Nightly job: move EXPIRED notifications older than 7 days to notif_archive.
     * Uses stored procedure for efficient bulk insert-then-delete.
     */
    @Scheduled(cron = "0 2 * * *")    // 2:00 AM daily
    @Transactional
    public void archiveExpiredNotifications() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(7);
        Pageable batch = PageRequest.of(0, 1000);
        List<Notification> toArchive =
            notificationRepository.findExpiredBefore(cutoff, batch);

        log.info("Archive job: archiving {} notifications", toArchive.size());
        // Calls PostgreSQL fn_archive_expired_notifications() stored proc
        // (see DatabaseConfig for native query call)
    }
}
```

---

## 8. Event-Driven Architecture

### 8.1 RabbitMQ Configuration

```java
// config/RabbitMQConfig.java
@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {

    @Value("${notification.rabbitmq.exchange}")
    private String exchange;

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable("notification.notifications.main")
            .withArgument("x-dead-letter-exchange", "notification.notifications.dlx")
            .withArgument("x-dead-letter-routing-key", "notification.dead")
            .withArgument("x-message-ttl", 86_400_000)  // 24h TTL
            .build();
    }

    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable("notification.notifications.dlq").build();
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange("notification.notifications.dlx");
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue,
                                        TopicExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue)
            .to(notificationExchange)
            .with("notification.#");
    }

    @Bean
    public Binding dlqBinding(Queue dlqQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(dlqQueue)
            .to(dlxExchange)
            .with("notification.dead");
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

// messaging/NotificationEventListener.java
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    /**
     * Consumes notification creation events from other microservices
     * (leave-service, payroll-service, etc.).
     * The source_event_id in the payload ensures idempotent processing.
     */
    @RabbitListener(queues = "notification.notifications.main")
    public void handleNotificationEvent(
            CreateNotificationRequest request,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            Channel channel) throws IOException {

        try {
            log.info("Received notification event from: {}, eventId: {}",
                request.getSourceService(), request.getSourceEventId());

            notificationService.createNotification(request, "SYSTEM");
            channel.basicAck(deliveryTag, false);

        } catch (DuplicateSourceEventException e) {
            // Already processed — ACK to remove from queue
            log.info("Duplicate event ignored: {}", request.getSourceEventId());
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("Failed to process notification event", e);
            // Reject and requeue (up to x-message-ttl, then goes to DLQ)
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
```

---

## 9. Security

### 9.1 Keycloak JWT Configuration

```java
// config/SecurityConfig.java
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final KeycloakRoleConverter keycloakRoleConverter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/api/v1/sse/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/notifications").
                    hasAnyRole("ADMIN", "HR_MANAGER")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthConverter())));
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(keycloakRoleConverter);
        return converter;
    }
}

// security/KeycloakRoleConverter.java
@Component
public class KeycloakRoleConverter
        implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess =
            jwt.getClaim("realm_access");
        if (realmAccess == null) return List.of();

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) realmAccess.get("roles");
        return roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
            .collect(Collectors.toList());
    }
}

// security/SecurityUtils.java
@Component
public class SecurityUtils {

    public String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();  // Keycloak sub claim
        }
        throw new NotificationAccessDeniedException("No authenticated user");
    }

    public Set<String> getCurrentRoles() {
        return SecurityContextHolder.getContext().getAuthentication()
            .getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
    }
}
```

---

### 9.2 JWT MDC Filter (for Audit Correlation)

```java
@Component
@Order(1)
public class JwtMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain)
            throws ServletException, IOException {
        try {
            // Extract trace ID from Micrometer/Sleuth header
            String traceId = request.getHeader("X-B3-TraceId");
            String clientIp = getClientIp(request);

            MDC.put("traceId", traceId != null ? traceId : UUID.randomUUID().toString());
            MDC.put("clientIp", clientIp);
            MDC.put("userAgent", request.getHeader("User-Agent"));
            MDC.put("sessionId", request.getHeader("X-Session-Id"));

            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0].trim()
                                 : request.getRemoteAddr();
    }
}
```

---

## 10. Best Practices

### 10.1 MapStruct Mapper

```java
@Mapper(componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface NotificationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "recipients", ignore = true)
    @Mapping(target = "deliveryLogs", ignore = true)
    Notification toEntity(CreateNotificationRequest request);

    @Mapping(target = "read", ignore = true)
    @Mapping(target = "acknowledged", ignore = true)
    @Mapping(target = "readAt", ignore = true)
    @Mapping(target = "acknowledgedAt", ignore = true)
    NotificationResponse toResponse(Notification entity);

    @Mapping(target = "id", source = "notification.id")
    @Mapping(target = "title", source = "notification.title")
    @Mapping(target = "body", source = "notification.body")
    @Mapping(target = "type", source = "notification.type")
    @Mapping(target = "priority", source = "notification.priority")
    @Mapping(target = "status", source = "notification.status")
    @Mapping(target = "sourceService", source = "notification.sourceService")
    @Mapping(target = "createdAt", source = "notification.createdAt")
    @Mapping(target = "expiresAt", source = "notification.expiresAt")
    @Mapping(target = "metadata", source = "notification.metadata")
    @Mapping(target = "read", source = "recipient.read")
    @Mapping(target = "acknowledged", source = "recipient.acknowledged")
    @Mapping(target = "readAt", source = "recipient.readAt")
    @Mapping(target = "acknowledgedAt", source = "recipient.acknowledgedAt")
    NotificationResponse toResponseFromRecipient(NotificationRecipient recipient);
}
```

---

### 10.2 Redis Cache Config

```java
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
            .defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(60))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
            "notification-rules", defaultConfig.entryTtl(Duration.ofSeconds(60)),
            "unread-counts",      defaultConfig.entryTtl(Duration.ofSeconds(30))
        );

        return RedisCacheManager.builder(factory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigs)
            .build();
    }
}
```

---

### 10.3 Notification Flow Summary

```
External Microservice (leave-service, payroll-service, etc.)
        │
        ▼ RabbitMQ: notification.notifications.main
NotificationEventListener.handleNotificationEvent()
        │
        ▼
NotificationServiceImpl.createNotification()
  ├── Idempotency check (source_service + source_event_id)
  ├── Persist Notification entity  ──────────────────────────► notif.notifications
  ├── RuleEngineService.resolveRecipients()
  │     ├── Load rules from Redis (TTL 60s) or DB fallback
  │     └── Evaluate JSONB conditions
  ├── Persist NotificationRecipient rows  ──────────────────► notif.notification_recipients
  ├── DeliveryService.enqueueDelivery()
  │     ├── WEBSOCKET → SimpMessagingTemplate.convertAndSendToUser()
  │     ├── SSE       → SseController.sendToUser()
  │     ├── EMAIL     → JavaMailSender (async via @Async)
  │     └── PUSH      → Web Push API (async via @Async)
  ├── AuditService.record(CREATED)  ────────────────────────► notif_audit.notification_audit_log
  ├── Invalidate Redis unread count keys
  └── Publish Spring ApplicationEvent → WebSocket push

Retry Flow:
DeliveryRetryScheduler (every 30s)
  └── findDueForRetry() → DeliveryService.attemptDelivery()
        ├── Success → markDelivered() + AuditService.record(DELIVERED)
        └── Failure → FailureHandlingService.handleDeliveryFailure()
              ├── attemptCount < maxAttempts → RETRYING + schedule next retry
              └── attemptCount >= maxAttempts → DEAD_LETTERED
                    └── NotificationFailure (DLQ) ─────────► notif.notification_failures

Nightly Jobs:
NotificationExpiryScheduler  → bulkMarkExpired() ──────────► notif.notifications (status=EXPIRED)
NotificationArchiveScheduler → archive to ──────────────────► notif_archive.notifications_archive
```

---

### 10.4 `application-local.yml` (Dev Profile)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/notification_dev
    username: notification_dev_user
    password: dev_password
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: validate
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/notification

logging:
  level:
    com.notification.notifications: DEBUG
    org.hibernate.SQL: DEBUG
    org.springframework.security: DEBUG
```

---

*End of Notification Notifications Backend Architecture Document*
*Generated from: notification_notifications_db.sql | Stack: Spring Boot 3.3, JPA, PostgreSQL, Keycloak, RabbitMQ, Redis*
