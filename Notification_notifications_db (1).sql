-- ============================================================================
-- Notification ALERTS & NOTIFICATIONS MODULE — POSTGRESQL DATABASE SCHEMA
-- Version: 1.0 | Designed for: Production-grade Notification Platform
-- Design Principles: Robust · Secure · Flexible · Optimized
-- ============================================================================

-- ============================================================================
-- SECTION 0: EXTENSIONS
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS "pgcrypto";       -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS "pg_trgm";        -- GIN trigram indexes for text search
CREATE EXTENSION IF NOT EXISTS "btree_gin";      -- composite GIN index support
CREATE EXTENSION IF NOT EXISTS "pg_stat_statements"; -- query performance monitoring

-- ============================================================================
-- SECTION 1: SCHEMAS (Namespacing for multi-tenant security isolation)
-- ============================================================================
CREATE SCHEMA IF NOT EXISTS notif;          -- core notification domain
CREATE SCHEMA IF NOT EXISTS notif_audit;    -- immutable audit records
CREATE SCHEMA IF NOT EXISTS notif_archive;  -- expired/purged notification history
CREATE SCHEMA IF NOT EXISTS notif_admin;    -- admin configuration & reporting views

COMMENT ON SCHEMA notif        IS 'Core notification domain tables';
COMMENT ON SCHEMA notif_audit  IS 'Immutable compliance audit trail — no DELETE permitted';
COMMENT ON SCHEMA notif_archive IS 'Archived expired notifications for compliance retention';
COMMENT ON SCHEMA notif_admin  IS 'Admin views, materialized summaries, rule management';

-- ============================================================================
-- SECTION 2: CUSTOM TYPES / ENUMS
-- ============================================================================

-- Notification type classification
CREATE TYPE notif.notification_type AS ENUM (
    'SYSTEM',        -- Platform outages, maintenance windows, policy changes
    'HR_ACTION',     -- Leave approvals, performance reviews, contract expiry
    'REMINDER',      -- Upcoming deadlines, scheduled interviews, probation end
    'ANNOUNCEMENT',  -- Company news, new policy rollouts, department updates
    'SECURITY',      -- Password expiry, failed login alerts, suspicious activity
    'WORKFLOW'       -- Approval pending, task assigned, document signing
);

-- Priority levels with deterministic ordering
CREATE TYPE notif.priority_level AS ENUM (
    'LOW',           -- Bell only; 14-day retention
    'MEDIUM',        -- In-app + batched email; 30-day retention
    'HIGH',          -- Banner + push + email digest; 60-day retention
    'CRITICAL'       -- Full-screen modal + push + email; 90-day retention; forced ACK
);

-- Lifecycle state of a notification
CREATE TYPE notif.notification_status AS ENUM (
    'ACTIVE',        -- Live and visible to recipients
    'EXPIRED',       -- Past expires_at; kept for audit, excluded from live queries
    'ARCHIVED',      -- Moved to notif_archive by nightly job
    'SOFT_DELETED'   -- Admin soft-delete; preserves audit trail
);

-- Delivery channel enum
CREATE TYPE notif.delivery_channel AS ENUM (
    'WEBSOCKET',     -- Real-time STOMP push
    'SSE',           -- Server-Sent Events fallback
    'EMAIL',         -- Async email digest
    'PUSH',          -- Web Push API (mobile browsers)
    'POLLING'        -- 30-second interval REST polling fallback
);

-- Delivery outcome per channel attempt
CREATE TYPE notif.delivery_status AS ENUM (
    'PENDING',
    'DELIVERED',
    'FAILED',
    'RETRYING',
    'DEAD_LETTERED'  -- Exhausted retries, moved to DLQ
);

-- Audit action types
CREATE TYPE notif_audit.audit_action AS ENUM (
    'CREATED',
    'DELIVERED',
    'READ',
    'ACKNOWLEDGED',
    'DISMISSED',
    'EXPIRED',
    'ARCHIVED',
    'SOFT_DELETED',
    'HARD_DELETED',
    'RULE_CREATED',
    'RULE_UPDATED',
    'RULE_DEACTIVATED',
    'EMAIL_SENT',
    'EMAIL_FAILED',
    'RETRY_ATTEMPTED',
    'DEAD_LETTERED'
);

-- RBAC roles mirrored from Keycloak for rule targeting
CREATE TYPE notif.keycloak_role AS ENUM (
    'ADMIN',
    'HR_MANAGER',
    'EMPLOYEE',
    'FINANCE',
    'RECRUITER',
    'PAYROLL_OFFICER',
    'DEPARTMENT_HEAD',
    'IT_ADMIN'
);

-- ============================================================================
-- SECTION 3: CORE TABLES
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 3.1  notifications  — Master notification registry (one row per unique event)
-- ----------------------------------------------------------------------------
CREATE TABLE notif.notifications (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    -- Human-readable content
    title               VARCHAR(255)    NOT NULL,
    body                TEXT            NOT NULL,
    -- Classification
    type                notif.notification_type   NOT NULL,
    priority            notif.priority_level      NOT NULL,
    status              notif.notification_status NOT NULL DEFAULT 'ACTIVE',
    -- Source tracking (which microservice fired this)
    source_service      VARCHAR(100)    NOT NULL,   -- e.g. 'leave-service', 'payroll-service'
    source_event_id     VARCHAR(255)    NULL,        -- Idempotency key from originating event
    -- Timestamps
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ     NULL,        -- NULL = never expires
    -- Flexible payload — stores action URLs, entity refs, extra context
    metadata            JSONB           NOT NULL DEFAULT '{}',
    -- Soft-delete support
    is_deleted          BOOLEAN         NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMPTZ     NULL,
    deleted_by          VARCHAR(255)    NULL,
    -- Optimistic locking
    version             BIGINT          NOT NULL DEFAULT 1,

    CONSTRAINT pk_notifications PRIMARY KEY (id),
    -- Prevent duplicate events from the same source
    CONSTRAINT uq_notifications_source_event UNIQUE (source_service, source_event_id)
        DEFERRABLE INITIALLY DEFERRED,
    -- Business rules
    CONSTRAINT chk_notifications_title_nonempty
        CHECK (char_length(trim(title)) > 0),
    CONSTRAINT chk_notifications_body_nonempty
        CHECK (char_length(trim(body)) > 0),
    CONSTRAINT chk_notifications_expires_future
        CHECK (expires_at IS NULL OR expires_at > created_at),
    CONSTRAINT chk_notifications_deleted_consistency
        CHECK (
            (is_deleted = FALSE AND deleted_at IS NULL AND deleted_by IS NULL)
            OR
            (is_deleted = TRUE AND deleted_at IS NOT NULL AND deleted_by IS NOT NULL)
        )
);

COMMENT ON TABLE  notif.notifications IS 'Master registry — one row per unique notification event';
COMMENT ON COLUMN notif.notifications.source_event_id IS 'Idempotency key — prevents duplicate inserts from RabbitMQ redelivery';
COMMENT ON COLUMN notif.notifications.metadata IS 'JSONB payload: action_url, entity_id, entity_type, deep_link, extra context';
COMMENT ON COLUMN notif.notifications.version    IS 'Optimistic locking — increment on every UPDATE';

-- Trigger: auto-update updated_at
CREATE OR REPLACE FUNCTION notif.fn_set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at := NOW();
    NEW.version    := OLD.version + 1;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_notifications_updated_at
    BEFORE UPDATE ON notif.notifications
    FOR EACH ROW EXECUTE FUNCTION notif.fn_set_updated_at();


-- ----------------------------------------------------------------------------
-- 3.2  notification_recipients  — Per-user/role delivery tracking
-- ----------------------------------------------------------------------------
CREATE TABLE notif.notification_recipients (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    notification_id     UUID            NOT NULL,
    -- Target identity (either user_id OR role_name; both may be set for role-broadcast)
    user_id             VARCHAR(255)    NULL,        -- Keycloak subject (sub claim)
    role_name           notif.keycloak_role NULL,    -- For role-targeted notifications
    -- Delivery state
    is_read             BOOLEAN         NOT NULL DEFAULT FALSE,
    is_acknowledged     BOOLEAN         NOT NULL DEFAULT FALSE,
    is_dismissed        BOOLEAN         NOT NULL DEFAULT FALSE,
    -- Timestamps
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    read_at             TIMESTAMPTZ     NULL,
    acknowledged_at     TIMESTAMPTZ     NULL,
    dismissed_at        TIMESTAMPTZ     NULL,
    -- Email digest opt-out tracking
    email_sent          BOOLEAN         NOT NULL DEFAULT FALSE,
    email_sent_at       TIMESTAMPTZ     NULL,
    -- Unsubscribe support
    email_unsubscribed  BOOLEAN         NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_notification_recipients PRIMARY KEY (id),
    CONSTRAINT fk_recipients_notification
        FOREIGN KEY (notification_id)
        REFERENCES notif.notifications(id)
        ON DELETE RESTRICT,   -- Never silently delete recipients; go through soft-delete
    -- Prevent duplicate recipient rows for same user+notification
    CONSTRAINT uq_recipients_user_notification
        UNIQUE (notification_id, user_id)
        DEFERRABLE INITIALLY DEFERRED,
    -- At least one of user_id or role_name must be set
    CONSTRAINT chk_recipients_target_notnull
        CHECK (user_id IS NOT NULL OR role_name IS NOT NULL),
    -- Read timestamps must be consistent
    CONSTRAINT chk_recipients_read_consistency
        CHECK (
            (is_read = FALSE AND read_at IS NULL)
            OR
            (is_read = TRUE  AND read_at IS NOT NULL)
        ),
    -- Ack requires read first
    CONSTRAINT chk_recipients_ack_requires_read
        CHECK (
            is_acknowledged = FALSE
            OR (is_acknowledged = TRUE AND is_read = TRUE AND acknowledged_at IS NOT NULL)
        ),
    -- Email state consistency
    CONSTRAINT chk_recipients_email_consistency
        CHECK (
            (email_sent = FALSE AND email_sent_at IS NULL)
            OR
            (email_sent = TRUE  AND email_sent_at IS NOT NULL)
        )
);

COMMENT ON TABLE  notif.notification_recipients IS 'Per-user delivery tracking; one row per (notification, user)';
COMMENT ON COLUMN notif.notification_recipients.user_id   IS 'Keycloak sub claim — pseudonymised in logs';
COMMENT ON COLUMN notif.notification_recipients.role_name IS 'Set for role-broadcast; resolved to user_id at query time via RBAC Engine';


-- ----------------------------------------------------------------------------
-- 3.3  notification_rules  — RBAC routing configuration
-- ----------------------------------------------------------------------------
CREATE TABLE notif.notification_rules (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    name                VARCHAR(255)    NOT NULL,
    description         TEXT            NULL,
    -- Target role for this rule
    role_name           notif.keycloak_role       NOT NULL,
    -- Which notification type this rule applies to (NULL = all types)
    notification_type   notif.notification_type   NULL,
    -- Optional priority override for this role+type combination
    priority_override   notif.priority_level      NULL,
    -- Rule active flag
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    -- JSONB condition predicates evaluated by RBAC Engine
    -- e.g.: {"department": "HR", "min_priority": "MEDIUM", "source_service": "leave-service"}
    conditions          JSONB           NOT NULL DEFAULT '{}',
    -- Delivery channel preferences for this rule
    channels            notif.delivery_channel[]  NOT NULL DEFAULT ARRAY['WEBSOCKET']::notif.delivery_channel[],
    -- Ordering weight — lower = evaluated first
    eval_order          SMALLINT        NOT NULL DEFAULT 100,
    -- Auditing
    created_by          VARCHAR(255)    NOT NULL,
    updated_by          VARCHAR(255)    NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version             BIGINT          NOT NULL DEFAULT 1,

    CONSTRAINT pk_notification_rules PRIMARY KEY (id),
    CONSTRAINT uq_notification_rules_name UNIQUE (name),
    CONSTRAINT chk_notification_rules_name_nonempty
        CHECK (char_length(trim(name)) > 0),
    CONSTRAINT chk_notification_rules_eval_order_positive
        CHECK (eval_order > 0)
);

COMMENT ON TABLE  notif.notification_rules IS 'RBAC routing rules — loaded by RBAC Engine, cached in Redis (60s TTL)';
COMMENT ON COLUMN notif.notification_rules.conditions IS 'JSONB predicate map evaluated against notification metadata at rule time';
COMMENT ON COLUMN notif.notification_rules.channels   IS 'Array of delivery channels configured for this rule';
COMMENT ON COLUMN notif.notification_rules.eval_order IS 'Ascending evaluation priority; ties broken by created_at';

CREATE TRIGGER trg_notification_rules_updated_at
    BEFORE UPDATE ON notif.notification_rules
    FOR EACH ROW EXECUTE FUNCTION notif.fn_set_updated_at();


-- ----------------------------------------------------------------------------
-- 3.4  notification_delivery_log  — Per-channel delivery attempt tracking
-- ----------------------------------------------------------------------------
CREATE TABLE notif.notification_delivery_log (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    notification_id     UUID            NOT NULL,
    recipient_id        UUID            NOT NULL,
    channel             notif.delivery_channel  NOT NULL,
    status              notif.delivery_status   NOT NULL DEFAULT 'PENDING',
    -- Attempt tracking
    attempt_count       SMALLINT        NOT NULL DEFAULT 0,
    max_attempts        SMALLINT        NOT NULL DEFAULT 3,
    last_attempt_at     TIMESTAMPTZ     NULL,
    next_retry_at       TIMESTAMPTZ     NULL,
    -- Error capture
    error_code          VARCHAR(50)     NULL,
    error_message       TEXT            NULL,
    -- Timestamps
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    delivered_at        TIMESTAMPTZ     NULL,

    CONSTRAINT pk_notification_delivery_log PRIMARY KEY (id),
    CONSTRAINT fk_delivery_log_notification
        FOREIGN KEY (notification_id)
        REFERENCES notif.notifications(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_delivery_log_recipient
        FOREIGN KEY (recipient_id)
        REFERENCES notif.notification_recipients(id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_delivery_attempt_count
        CHECK (attempt_count >= 0 AND attempt_count <= max_attempts),
    CONSTRAINT chk_delivery_delivered_consistency
        CHECK (
            (status != 'DELIVERED' AND delivered_at IS NULL)
            OR
            (status = 'DELIVERED'  AND delivered_at IS NOT NULL)
        )
);

COMMENT ON TABLE notif.notification_delivery_log IS 'Per-channel delivery attempts; drives retry logic and DLQ escalation';


-- ----------------------------------------------------------------------------
-- 3.5  notification_failures  — Dead-letter records for admin review
-- ----------------------------------------------------------------------------
CREATE TABLE notif.notification_failures (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid(),
    notification_id     UUID            NULL,        -- NULL if creation itself failed
    recipient_id        UUID            NULL,
    channel             notif.delivery_channel  NULL,
    raw_event_payload   JSONB           NULL,        -- Original RabbitMQ message for replay
    failure_reason      TEXT            NOT NULL,
    failure_category    VARCHAR(100)    NULL,        -- e.g. 'DB_UNAVAILABLE', 'SMTP_TIMEOUT'
    occurred_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    resolved_at         TIMESTAMPTZ     NULL,
    resolved_by         VARCHAR(255)    NULL,
    resolution_notes    TEXT            NULL,
    is_resolved         BOOLEAN         NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_notification_failures PRIMARY KEY (id),
    CONSTRAINT chk_failures_resolved_consistency
        CHECK (
            (is_resolved = FALSE AND resolved_at IS NULL AND resolved_by IS NULL)
            OR
            (is_resolved = TRUE  AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL)
        )
);

COMMENT ON TABLE notif.notification_failures IS 'DLQ landing table for exhausted retries; admin reviews and replays from here';


-- ============================================================================
-- SECTION 4: AUDIT SCHEMA — IMMUTABLE TRAIL
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 4.1  notification_audit_log  — Full compliance audit; never auto-purged
-- ----------------------------------------------------------------------------
CREATE TABLE notif_audit.notification_audit_log (
    id                  BIGSERIAL       NOT NULL,   -- Sequential for ordering guarantees
    -- What was acted on
    notification_id     UUID            NULL,
    recipient_id        UUID            NULL,
    rule_id             UUID            NULL,
    -- Who and what
    action              notif_audit.audit_action    NOT NULL,
    actor_user_id       VARCHAR(255)    NULL,        -- NULL for system actions
    actor_role          VARCHAR(100)    NULL,
    -- Connection context
    ip_address          INET            NULL,        -- Pseudonymised in GDPR regions
    user_agent          TEXT            NULL,
    session_id          VARCHAR(255)    NULL,
    -- State snapshot (before/after for mutations)
    old_state           JSONB           NULL,
    new_state           JSONB           NULL,
    -- Additional context
    metadata            JSONB           NOT NULL DEFAULT '{}',
    -- When
    occurred_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    -- Correlation for distributed tracing
    correlation_id      VARCHAR(255)    NULL,        -- Spring Sleuth trace ID

    CONSTRAINT pk_audit_log PRIMARY KEY (id)
    -- No FK constraints on audit table — records must survive notification deletion
);

COMMENT ON TABLE  notif_audit.notification_audit_log IS 'Immutable compliance audit — configurable retention defaulting to 2 years; no auto-purge';
COMMENT ON COLUMN notif_audit.notification_audit_log.old_state IS 'Pre-mutation snapshot for change tracking';
COMMENT ON COLUMN notif_audit.notification_audit_log.new_state IS 'Post-mutation snapshot for change tracking';
COMMENT ON COLUMN notif_audit.notification_audit_log.ip_address IS 'INET type; pseudonymise last octet for GDPR compliance';

-- SECURITY: Revoke DELETE and UPDATE on audit table from all application roles
-- (enforced in SECTION 7: Row-Level Security)

-- Partition audit_log by month for retention management
-- Create partitions for current + next year (Flyway will add more annually)
CREATE TABLE notif_audit.notification_audit_log_2025_01
    PARTITION OF notif_audit.notification_audit_log
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

-- (In production: generate all 12 monthly partitions per year via Flyway migration)


-- ============================================================================
-- SECTION 5: ARCHIVE SCHEMA
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 5.1  notifications_archive  — Expired/purged notifications (nightly job)
-- ----------------------------------------------------------------------------
CREATE TABLE notif_archive.notifications_archive (
    -- Mirror of notif.notifications columns
    id                  UUID            NOT NULL,
    title               VARCHAR(255)    NOT NULL,
    body                TEXT            NOT NULL,
    type                notif.notification_type   NOT NULL,
    priority            notif.priority_level      NOT NULL,
    source_service      VARCHAR(100)    NOT NULL,
    source_event_id     VARCHAR(255)    NULL,
    metadata            JSONB           NOT NULL DEFAULT '{}',
    -- Original timestamps
    created_at          TIMESTAMPTZ     NOT NULL,
    expires_at          TIMESTAMPTZ     NULL,
    -- Archive metadata
    archived_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    archived_by         VARCHAR(100)    NOT NULL DEFAULT 'SYSTEM',
    archive_reason      VARCHAR(50)     NOT NULL DEFAULT 'EXPIRED',  -- EXPIRED | PURGED | COMPLIANCE

    CONSTRAINT pk_notifications_archive PRIMARY KEY (id)
) PARTITION BY RANGE (archived_at);

-- Monthly archive partitions
CREATE TABLE notif_archive.notifications_archive_2025_01
    PARTITION OF notif_archive.notifications_archive
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

COMMENT ON TABLE notif_archive.notifications_archive IS 'Cold storage for expired notifications; partitioned by archive month for efficient retention purging';


-- ============================================================================
-- SECTION 6: INDEXES (Performance + Security)
-- ============================================================================

-- ── notifications ──
-- Primary read path: fetch active, non-deleted notifications by type/priority
CREATE INDEX idx_notifications_active_type
    ON notif.notifications (type, priority DESC, created_at DESC)
    WHERE status = 'ACTIVE' AND is_deleted = FALSE;

-- Expiry daemon scan
CREATE INDEX idx_notifications_expires_at
    ON notif.notifications (expires_at)
    WHERE expires_at IS NOT NULL AND status = 'ACTIVE';

-- Source idempotency lookup
CREATE INDEX idx_notifications_source_event
    ON notif.notifications (source_service, source_event_id)
    WHERE source_event_id IS NOT NULL;

-- JSONB metadata GIN index for flexible querying (e.g., metadata->>'entity_id')
CREATE INDEX idx_notifications_metadata_gin
    ON notif.notifications USING GIN (metadata jsonb_path_ops);

-- Full-text search on title + body
CREATE INDEX idx_notifications_fts
    ON notif.notifications USING GIN (
        to_tsvector('english', coalesce(title,'') || ' ' || coalesce(body,''))
    );

-- ── notification_recipients ──
-- Core query path: user's unread notifications (MOST CRITICAL INDEX)
CREATE INDEX idx_recipients_user_unread
    ON notif.notification_recipients (user_id, created_at DESC)
    WHERE is_read = FALSE AND is_dismissed = FALSE;

-- Role-targeted notifications lookup
CREATE INDEX idx_recipients_role_unread
    ON notif.notification_recipients (role_name, created_at DESC)
    WHERE is_read = FALSE;

-- Notification fan-out: all recipients of a given notification
CREATE INDEX idx_recipients_notification_id
    ON notif.notification_recipients (notification_id);

-- Email digest job: find users needing email (online threshold check)
CREATE INDEX idx_recipients_email_pending
    ON notif.notification_recipients (created_at)
    WHERE email_sent = FALSE AND is_read = FALSE AND email_unsubscribed = FALSE;

-- ── notification_rules ──
-- RBAC Engine rule lookup (cached in Redis but DB fallback must be fast)
CREATE INDEX idx_rules_active_role_type
    ON notif.notification_rules (role_name, notification_type, eval_order)
    WHERE is_active = TRUE;

-- ── notification_delivery_log ──
-- Retry worker: find pending/retrying deliveries due for retry
CREATE INDEX idx_delivery_pending_retry
    ON notif.notification_delivery_log (next_retry_at)
    WHERE status IN ('PENDING', 'RETRYING') AND next_retry_at IS NOT NULL;

-- Dashboard: delivery status by notification
CREATE INDEX idx_delivery_notification_channel
    ON notif.notification_delivery_log (notification_id, channel, status);

-- ── notification_failures ──
CREATE INDEX idx_failures_unresolved
    ON notif.notification_failures (occurred_at DESC)
    WHERE is_resolved = FALSE;

-- ── audit log ──
-- Actor-based compliance queries (e.g., "all actions by user X")
CREATE INDEX idx_audit_actor_occurred
    ON notif_audit.notification_audit_log (actor_user_id, occurred_at DESC);

-- Notification lifecycle audit trail
CREATE INDEX idx_audit_notification_occurred
    ON notif_audit.notification_audit_log (notification_id, occurred_at DESC);

-- Action-type queries (e.g., find all SECURITY events)
CREATE INDEX idx_audit_action_occurred
    ON notif_audit.notification_audit_log (action, occurred_at DESC);


-- ============================================================================
-- SECTION 7: ROW-LEVEL SECURITY (PostgreSQL RLS)
-- ============================================================================

-- Enable RLS on sensitive tables
ALTER TABLE notif.notifications              ENABLE ROW LEVEL SECURITY;
ALTER TABLE notif.notification_recipients    ENABLE ROW LEVEL SECURITY;
ALTER TABLE notif_audit.notification_audit_log ENABLE ROW LEVEL SECURITY;

-- ── Application roles ──
-- notif_app_role   — Spring Boot service account; can read/write notif schema
-- notif_admin_role — Admin service account; elevated read access
-- notif_audit_role — Read-only audit access (compliance/SOC2)
-- notif_archive_role — Archive job service account

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'notif_app_role') THEN
        CREATE ROLE notif_app_role NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'notif_admin_role') THEN
        CREATE ROLE notif_admin_role NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'notif_audit_role') THEN
        CREATE ROLE notif_audit_role NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'notif_archive_role') THEN
        CREATE ROLE notif_archive_role NOLOGIN;
    END IF;
END$$;

-- ── Schema-level grants ──
GRANT USAGE ON SCHEMA notif         TO notif_app_role, notif_admin_role;
GRANT USAGE ON SCHEMA notif_audit   TO notif_app_role, notif_admin_role, notif_audit_role;
GRANT USAGE ON SCHEMA notif_archive TO notif_admin_role, notif_archive_role;
GRANT USAGE ON SCHEMA notif_admin   TO notif_admin_role;

-- ── Table-level grants ──
-- App role: full CRUD on core tables; INSERT-only on audit
GRANT SELECT, INSERT, UPDATE ON notif.notifications             TO notif_app_role;
GRANT SELECT, INSERT, UPDATE ON notif.notification_recipients   TO notif_app_role;
GRANT SELECT                  ON notif.notification_rules        TO notif_app_role;
GRANT SELECT, INSERT, UPDATE ON notif.notification_delivery_log TO notif_app_role;
GRANT SELECT, INSERT, UPDATE ON notif.notification_failures      TO notif_app_role;
GRANT INSERT                  ON notif_audit.notification_audit_log TO notif_app_role;

-- Admin role: adds rule management and wider read
GRANT SELECT, INSERT, UPDATE, DELETE ON notif.notification_rules TO notif_admin_role;
GRANT SELECT ON ALL TABLES IN SCHEMA notif         TO notif_admin_role;
GRANT SELECT ON ALL TABLES IN SCHEMA notif_audit   TO notif_admin_role;
GRANT SELECT ON ALL TABLES IN SCHEMA notif_archive TO notif_admin_role;

-- Audit role: read-only on audit schema
GRANT SELECT ON ALL TABLES IN SCHEMA notif_audit TO notif_audit_role;

-- Archive role: read from notif, write to archive
GRANT SELECT ON notif.notifications TO notif_archive_role;
GRANT SELECT, INSERT ON notif_archive.notifications_archive TO notif_archive_role;

-- CRITICAL: No DELETE on audit tables — enforced at role level
REVOKE DELETE ON notif_audit.notification_audit_log FROM PUBLIC;
REVOKE DELETE ON notif_audit.notification_audit_log FROM notif_admin_role;
REVOKE UPDATE ON notif_audit.notification_audit_log FROM PUBLIC;

-- ── RLS Policies ──
-- Recipients policy: users can only see their own notification rows
CREATE POLICY policy_recipients_self_access
    ON notif.notification_recipients
    AS PERMISSIVE
    FOR SELECT
    TO notif_app_role
    USING (
        user_id = current_setting('app.current_user_id', TRUE)
        OR
        role_name::TEXT = current_setting('app.current_user_role', TRUE)
    );

CREATE POLICY policy_recipients_self_write
    ON notif.notification_recipients
    AS PERMISSIVE
    FOR UPDATE
    TO notif_app_role
    USING (user_id = current_setting('app.current_user_id', TRUE));

-- Admin bypass policy
CREATE POLICY policy_recipients_admin_bypass
    ON notif.notification_recipients
    AS PERMISSIVE
    FOR ALL
    TO notif_admin_role
    USING (TRUE);

-- Audit log: users can only read their own audit entries
CREATE POLICY policy_audit_self_access
    ON notif_audit.notification_audit_log
    AS PERMISSIVE
    FOR SELECT
    TO notif_app_role
    USING (actor_user_id = current_setting('app.current_user_id', TRUE));

CREATE POLICY policy_audit_admin_access
    ON notif_audit.notification_audit_log
    AS PERMISSIVE
    FOR SELECT
    TO notif_admin_role
    USING (TRUE);


-- ============================================================================
-- SECTION 8: STORED FUNCTIONS & PROCEDURES
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 8.1  fn_get_user_notifications  — Paginated, RBAC-filtered fetch
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION notif.fn_get_user_notifications(
    p_user_id       VARCHAR(255),
    p_role_name     notif.keycloak_role,
    p_include_read  BOOLEAN             DEFAULT FALSE,
    p_type_filter   notif.notification_type DEFAULT NULL,
    p_limit         INT                 DEFAULT 20,
    p_offset        INT                 DEFAULT 0
)
RETURNS TABLE (
    notification_id     UUID,
    title               VARCHAR(255),
    body                TEXT,
    type                notif.notification_type,
    priority            notif.priority_level,
    source_service      VARCHAR(100),
    metadata            JSONB,
    is_read             BOOLEAN,
    is_acknowledged     BOOLEAN,
    created_at          TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ,
    recipient_id        UUID
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = notif, public
AS $$
BEGIN
    RETURN QUERY
    SELECT
        n.id            AS notification_id,
        n.title,
        n.body,
        n.type,
        n.priority,
        n.source_service,
        n.metadata,
        r.is_read,
        r.is_acknowledged,
        n.created_at,
        n.expires_at,
        r.id            AS recipient_id
    FROM notif.notifications n
    JOIN notif.notification_recipients r
        ON n.id = r.notification_id
    WHERE
        n.status = 'ACTIVE'
        AND n.is_deleted = FALSE
        AND (n.expires_at IS NULL OR n.expires_at > NOW())
        AND (
            (r.user_id    = p_user_id)
            OR
            (r.role_name  = p_role_name AND r.user_id IS NULL)
        )
        AND r.is_dismissed = FALSE
        AND (p_include_read  OR r.is_read = FALSE)
        AND (p_type_filter IS NULL OR n.type = p_type_filter)
    ORDER BY
        -- CRITICAL always first, then by recency
        CASE n.priority
            WHEN 'CRITICAL' THEN 1
            WHEN 'HIGH'     THEN 2
            WHEN 'MEDIUM'   THEN 3
            WHEN 'LOW'      THEN 4
        END ASC,
        n.created_at DESC
    LIMIT  p_limit
    OFFSET p_offset;
END;
$$;

COMMENT ON FUNCTION notif.fn_get_user_notifications IS 'RBAC-filtered, paginated notification fetch; primary read path for REST GET /api/notifications';


-- ----------------------------------------------------------------------------
-- 8.2  fn_mark_read  — Idempotent mark-as-read with audit
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION notif.fn_mark_read(
    p_recipient_id  UUID,
    p_user_id       VARCHAR(255),
    p_correlation_id VARCHAR(255) DEFAULT NULL
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = notif, notif_audit, public
AS $$
DECLARE
    v_already_read BOOLEAN;
    v_notif_id     UUID;
BEGIN
    -- Fetch and lock for update
    SELECT is_read, notification_id
    INTO   v_already_read, v_notif_id
    FROM   notif.notification_recipients
    WHERE  id = p_recipient_id AND user_id = p_user_id
    FOR    UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Recipient record not found or access denied (id=%, user=%)',
            p_recipient_id, p_user_id;
    END IF;

    -- Idempotent: if already read, return TRUE without touching DB
    IF v_already_read THEN
        RETURN TRUE;
    END IF;

    -- Mark as read
    UPDATE notif.notification_recipients
    SET    is_read  = TRUE,
           read_at  = NOW()
    WHERE  id = p_recipient_id;

    -- Audit entry
    INSERT INTO notif_audit.notification_audit_log
        (notification_id, recipient_id, action, actor_user_id, correlation_id,
         new_state, occurred_at)
    VALUES
        (v_notif_id, p_recipient_id, 'READ', p_user_id, p_correlation_id,
         jsonb_build_object('is_read', TRUE, 'read_at', NOW()), NOW());

    RETURN TRUE;

EXCEPTION
    WHEN OTHERS THEN
        RAISE WARNING 'fn_mark_read failed: %', SQLERRM;
        RETURN FALSE;
END;
$$;


-- ----------------------------------------------------------------------------
-- 8.3  fn_acknowledge  — Idempotent acknowledgement (required for CRITICAL)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION notif.fn_acknowledge(
    p_recipient_id   UUID,
    p_user_id        VARCHAR(255),
    p_correlation_id VARCHAR(255) DEFAULT NULL
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = notif, notif_audit, public
AS $$
DECLARE
    v_notif_id      UUID;
    v_already_acked BOOLEAN;
    v_priority      notif.priority_level;
BEGIN
    SELECT r.is_acknowledged, r.notification_id, n.priority
    INTO   v_already_acked, v_notif_id, v_priority
    FROM   notif.notification_recipients r
    JOIN   notif.notifications n ON n.id = r.notification_id
    WHERE  r.id = p_recipient_id AND r.user_id = p_user_id
    FOR    UPDATE OF r;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Recipient not found or access denied';
    END IF;

    IF v_already_acked THEN
        RETURN TRUE;  -- Idempotent
    END IF;

    UPDATE notif.notification_recipients
    SET    is_read          = TRUE,
           read_at          = COALESCE(read_at, NOW()),
           is_acknowledged  = TRUE,
           acknowledged_at  = NOW()
    WHERE  id = p_recipient_id;

    INSERT INTO notif_audit.notification_audit_log
        (notification_id, recipient_id, action, actor_user_id, correlation_id,
         new_state, occurred_at)
    VALUES
        (v_notif_id, p_recipient_id, 'ACKNOWLEDGED', p_user_id, p_correlation_id,
         jsonb_build_object(
             'is_acknowledged', TRUE,
             'acknowledged_at', NOW(),
             'priority', v_priority
         ), NOW());

    RETURN TRUE;
END;
$$;


-- ----------------------------------------------------------------------------
-- 8.4  fn_expire_notifications  — Nightly expiry job
-- ----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE notif.proc_expire_notifications(
    p_batch_size    INT DEFAULT 500
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_expired_count INT;
BEGIN
    -- Expire in batches to avoid long locks
    WITH expired AS (
        SELECT id
        FROM   notif.notifications
        WHERE  status = 'ACTIVE'
          AND  expires_at IS NOT NULL
          AND  expires_at < NOW()
        LIMIT  p_batch_size
        FOR    UPDATE SKIP LOCKED
    )
    UPDATE notif.notifications n
    SET    status     = 'EXPIRED',
           updated_at = NOW()
    FROM   expired
    WHERE  n.id = expired.id;

    GET DIAGNOSTICS v_expired_count = ROW_COUNT;

    -- Audit the batch
    IF v_expired_count > 0 THEN
        INSERT INTO notif_audit.notification_audit_log
            (action, actor_user_id, metadata, occurred_at)
        VALUES
            ('EXPIRED', 'SYSTEM',
             jsonb_build_object('batch_size', v_expired_count, 'job', 'proc_expire_notifications'),
             NOW());
    END IF;

    RAISE NOTICE 'Expired % notifications', v_expired_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 8.5  fn_archive_expired  — Move expired → archive schema
-- ----------------------------------------------------------------------------
CREATE OR REPLACE PROCEDURE notif_archive.proc_archive_expired(
    p_older_than_days INT DEFAULT 1,
    p_batch_size      INT DEFAULT 1000
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_count INT;
BEGIN
    WITH to_archive AS (
        DELETE FROM notif.notifications
        WHERE  status = 'EXPIRED'
          AND  updated_at < NOW() - (p_older_than_days || ' days')::INTERVAL
        LIMIT  p_batch_size
        RETURNING *
    )
    INSERT INTO notif_archive.notifications_archive
        (id, title, body, type, priority, source_service, source_event_id,
         metadata, created_at, expires_at, archived_at, archived_by, archive_reason)
    SELECT
        id, title, body, type, priority, source_service, source_event_id,
        metadata, created_at, expires_at, NOW(), 'SYSTEM', 'EXPIRED'
    FROM to_archive;

    GET DIAGNOSTICS v_count = ROW_COUNT;
    RAISE NOTICE 'Archived % notifications to notif_archive', v_count;
END;
$$;


-- ----------------------------------------------------------------------------
-- 8.6  fn_get_unread_count  — Fast unread badge count (backed by Redis in prod)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION notif.fn_get_unread_count(
    p_user_id   VARCHAR(255),
    p_role_name notif.keycloak_role DEFAULT NULL
)
RETURNS BIGINT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = notif, public
AS $$
    SELECT COUNT(*)
    FROM   notif.notification_recipients r
    JOIN   notif.notifications n ON n.id = r.notification_id
    WHERE  (r.user_id = p_user_id OR (p_role_name IS NOT NULL AND r.role_name = p_role_name))
      AND  r.is_read       = FALSE
      AND  r.is_dismissed  = FALSE
      AND  n.status        = 'ACTIVE'
      AND  n.is_deleted    = FALSE
      AND  (n.expires_at IS NULL OR n.expires_at > NOW());
$$;


-- ============================================================================
-- SECTION 9: ADMIN VIEWS & MATERIALIZED VIEWS
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 9.1  vw_active_notifications  — RBAC-filtered operational view
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW notif_admin.vw_active_notifications AS
SELECT
    n.id,
    n.title,
    n.type,
    n.priority,
    n.status,
    n.source_service,
    n.created_at,
    n.expires_at,
    COUNT(r.id)                                         AS total_recipients,
    COUNT(r.id) FILTER (WHERE r.is_read)               AS read_count,
    COUNT(r.id) FILTER (WHERE r.is_acknowledged)        AS ack_count,
    COUNT(r.id) FILTER (WHERE NOT r.is_read)            AS unread_count,
    ROUND(
        100.0 * COUNT(r.id) FILTER (WHERE r.is_read)
        / NULLIF(COUNT(r.id), 0), 1
    )                                                   AS read_rate_pct
FROM notif.notifications n
LEFT JOIN notif.notification_recipients r ON r.notification_id = n.id
WHERE n.is_deleted = FALSE
GROUP BY n.id, n.title, n.type, n.priority, n.status,
         n.source_service, n.created_at, n.expires_at;

COMMENT ON VIEW notif_admin.vw_active_notifications IS 'Admin dashboard view with per-notification delivery stats';

-- ----------------------------------------------------------------------------
-- 9.2  mvw_daily_notification_stats  — Materialized for Grafana dashboards
-- ----------------------------------------------------------------------------
CREATE MATERIALIZED VIEW notif_admin.mvw_daily_notification_stats AS
SELECT
    date_trunc('day', n.created_at)::DATE   AS stat_date,
    n.type,
    n.priority,
    n.source_service,
    COUNT(DISTINCT n.id)                    AS notifications_created,
    COUNT(r.id)                             AS total_deliveries,
    COUNT(r.id) FILTER (WHERE r.is_read)   AS reads,
    COUNT(r.id) FILTER (WHERE r.is_acknowledged) AS acknowledgements,
    AVG(
        EXTRACT(EPOCH FROM (r.read_at - n.created_at))
    ) FILTER (WHERE r.read_at IS NOT NULL)  AS avg_time_to_read_seconds
FROM notif.notifications n
LEFT JOIN notif.notification_recipients r ON r.notification_id = n.id
GROUP BY 1, 2, 3, 4
WITH DATA;

CREATE UNIQUE INDEX idx_mvw_daily_stats
    ON notif_admin.mvw_daily_notification_stats (stat_date, type, priority, source_service);

COMMENT ON MATERIALIZED VIEW notif_admin.mvw_daily_notification_stats
    IS 'Refreshed every 15 min via pg_cron / scheduled job; feeds Grafana metrics dashboards';

-- ----------------------------------------------------------------------------
-- 9.3  vw_delivery_health  — Channel delivery health for observability
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW notif_admin.vw_delivery_health AS
SELECT
    channel,
    status,
    COUNT(*)                                    AS count,
    AVG(attempt_count)                          AS avg_attempts,
    MAX(attempt_count)                          AS max_attempts,
    COUNT(*) FILTER (WHERE status = 'FAILED')  AS failures,
    COUNT(*) FILTER (WHERE status = 'DEAD_LETTERED') AS dead_lettered
FROM notif.notification_delivery_log
WHERE created_at > NOW() - INTERVAL '24 hours'
GROUP BY channel, status;

-- ----------------------------------------------------------------------------
-- 9.4  vw_unresolved_failures  — Admin failure queue
-- ----------------------------------------------------------------------------
CREATE OR REPLACE VIEW notif_admin.vw_unresolved_failures AS
SELECT
    f.id,
    f.notification_id,
    f.channel,
    f.failure_category,
    f.failure_reason,
    f.occurred_at,
    n.title          AS notification_title,
    n.priority       AS notification_priority
FROM notif.notification_failures f
LEFT JOIN notif.notifications n ON n.id = f.notification_id
WHERE f.is_resolved = FALSE
ORDER BY
    CASE WHEN n.priority = 'CRITICAL' THEN 1
         WHEN n.priority = 'HIGH'     THEN 2
         ELSE 3 END,
    f.occurred_at ASC;


-- ============================================================================
-- SECTION 10: TRIGGERS FOR DATA INTEGRITY
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 10.1  Prevent updates to audit log (belt-and-suspenders beyond REVOKE)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION notif_audit.fn_prevent_audit_mutation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Mutations on notification_audit_log are forbidden. '
        'Audit records are immutable.';
END;
$$;

CREATE TRIGGER trg_prevent_audit_update
    BEFORE UPDATE ON notif_audit.notification_audit_log
    FOR EACH ROW EXECUTE FUNCTION notif_audit.fn_prevent_audit_mutation();

CREATE TRIGGER trg_prevent_audit_delete
    BEFORE DELETE ON notif_audit.notification_audit_log
    FOR EACH ROW EXECUTE FUNCTION notif_audit.fn_prevent_audit_mutation();


-- ----------------------------------------------------------------------------
-- 10.2  Auto-expire notifications past expires_at on read (lazy expiry)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION notif.fn_lazy_expire_on_fetch()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status = 'ACTIVE'
       AND NEW.status = 'ACTIVE'
       AND OLD.expires_at IS NOT NULL
       AND OLD.expires_at < NOW() THEN
        NEW.status := 'EXPIRED';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_notifications_lazy_expire
    BEFORE UPDATE ON notif.notifications
    FOR EACH ROW EXECUTE FUNCTION notif.fn_lazy_expire_on_fetch();


-- ----------------------------------------------------------------------------
-- 10.3  Validate JSONB metadata schema on insert/update
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION notif.fn_validate_metadata()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    -- metadata must be a JSON object, not array or scalar
    IF jsonb_typeof(NEW.metadata) != 'object' THEN
        RAISE EXCEPTION 'notifications.metadata must be a JSON object, got: %',
            jsonb_typeof(NEW.metadata);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_notifications_validate_metadata
    BEFORE INSERT OR UPDATE OF metadata ON notif.notifications
    FOR EACH ROW EXECUTE FUNCTION notif.fn_validate_metadata();


-- ============================================================================
-- SECTION 11: SEED DATA — Notification Rules (Keycloak role → type mapping)
-- ============================================================================

INSERT INTO notif.notification_rules
    (name, description, role_name, notification_type, priority_override,
     channels, eval_order, created_by, conditions)
VALUES
    ('admin-all-notifications',
     'Admins receive all notification types at native priority',
     'ADMIN', NULL, NULL,
     ARRAY['WEBSOCKET','EMAIL','PUSH']::notif.delivery_channel[],
     10, 'SYSTEM', '{}'),

    ('hr-manager-hr-actions',
     'HR Managers receive all HR_ACTION and WORKFLOW notifications',
     'HR_MANAGER', 'HR_ACTION', NULL,
     ARRAY['WEBSOCKET','EMAIL']::notif.delivery_channel[],
     20, 'SYSTEM', '{}'),

    ('hr-manager-workflow',
     'HR Managers receive WORKFLOW notifications',
     'HR_MANAGER', 'WORKFLOW', NULL,
     ARRAY['WEBSOCKET','EMAIL']::notif.delivery_channel[],
     21, 'SYSTEM', '{}'),

    ('employee-personal-reminders',
     'Employees receive REMINDER and HR_ACTION relevant to them',
     'EMPLOYEE', 'REMINDER', NULL,
     ARRAY['WEBSOCKET']::notif.delivery_channel[],
     30, 'SYSTEM', '{}'),

    ('employee-hr-action',
     'Employees receive HR_ACTION notifications',
     'EMPLOYEE', 'HR_ACTION', NULL,
     ARRAY['WEBSOCKET','EMAIL']::notif.delivery_channel[],
     31, 'SYSTEM', '{}'),

    ('all-roles-system-critical',
     'All roles receive SYSTEM and SECURITY notifications at CRITICAL priority',
     'EMPLOYEE', 'SYSTEM', 'CRITICAL',
     ARRAY['WEBSOCKET','EMAIL','PUSH']::notif.delivery_channel[],
     5, 'SYSTEM', '{"min_priority": "HIGH"}'),

    ('all-roles-security',
     'All roles receive SECURITY notifications',
     'EMPLOYEE', 'SECURITY', 'CRITICAL',
     ARRAY['WEBSOCKET','EMAIL','PUSH']::notif.delivery_channel[],
     5, 'SYSTEM', '{}'),

    ('finance-announcements',
     'Finance team receives ANNOUNCEMENT notifications',
     'FINANCE', 'ANNOUNCEMENT', NULL,
     ARRAY['WEBSOCKET']::notif.delivery_channel[],
     40, 'SYSTEM', '{}'),

    ('recruiter-workflow',
     'Recruiters receive WORKFLOW and REMINDER notifications',
     'RECRUITER', 'WORKFLOW', NULL,
     ARRAY['WEBSOCKET','EMAIL']::notif.delivery_channel[],
     30, 'SYSTEM', '{}')
ON CONFLICT (name) DO NOTHING;


-- ============================================================================
-- SECTION 12: PERFORMANCE TUNING SETTINGS (document for DBA / docker config)
-- ============================================================================
-- These are recommended postgresql.conf settings for Notification notification workload.
-- Apply via ALTER SYSTEM or docker environment variables.

COMMENT ON DATABASE postgres IS $doc$
Recommended postgresql.conf tuning for Notification Notifications workload:

-- Memory
shared_buffers          = 25% of RAM (e.g., 2GB on 8GB server)
effective_cache_size    = 75% of RAM
work_mem                = 32MB          -- Paginated notification sorts
maintenance_work_mem    = 256MB         -- Index builds

-- WAL / Reliability
wal_level               = replica       -- Enable read replicas
max_wal_senders         = 5
synchronous_commit      = on            -- Critical for audit durability
checkpoint_completion_target = 0.9

-- Query Planner
random_page_cost        = 1.1           -- SSD storage (cloud/NVMe)
effective_io_concurrency = 200          -- SSDs support high concurrency
default_statistics_target = 200         -- Better plans for JSONB columns

-- Connection pooling (use PgBouncer in front)
max_connections         = 100           -- PgBouncer handles the rest

-- Autovacuum (notification_recipients is high-churn)
autovacuum_vacuum_scale_factor   = 0.01   -- Vacuum at 1% dead tuples
autovacuum_analyze_scale_factor  = 0.005  -- Analyze at 0.5%
$doc$;


-- ============================================================================
-- SECTION 13: MAINTENANCE GRANTS ON SEQUENCES
-- ============================================================================
GRANT USAGE, SELECT ON SEQUENCE notif_audit.notification_audit_log_id_seq
    TO notif_app_role, notif_admin_role;


-- ============================================================================
-- END OF SCHEMA
-- ============================================================================
