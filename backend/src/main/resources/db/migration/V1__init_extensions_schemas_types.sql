CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE SCHEMA IF NOT EXISTS notif;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace WHERE t.typname='notification_type' AND n.nspname='notif') THEN
        CREATE TYPE notif.notification_type AS ENUM ('SYSTEM','HR_ACTION','REMINDER','ANNOUNCEMENT','SECURITY','WORKFLOW');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace WHERE t.typname='priority_level' AND n.nspname='notif') THEN
        CREATE TYPE notif.priority_level AS ENUM ('LOW','MEDIUM','HIGH','CRITICAL');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace WHERE t.typname='notification_status' AND n.nspname='notif') THEN
        CREATE TYPE notif.notification_status AS ENUM ('ACTIVE','EXPIRED','ARCHIVED','SOFT_DELETED');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace WHERE t.typname='delivery_channel' AND n.nspname='notif') THEN
        CREATE TYPE notif.delivery_channel AS ENUM ('WEBSOCKET','SSE','EMAIL','PUSH','POLLING','TEAMS','WHATSAPP');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace WHERE t.typname='delivery_status' AND n.nspname='notif') THEN
        CREATE TYPE notif.delivery_status AS ENUM ('PENDING','DELIVERED','FAILED','RETRYING','DEAD_LETTERED');
    END IF;
END $$;
