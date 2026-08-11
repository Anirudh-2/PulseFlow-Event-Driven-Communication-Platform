import { useState, useEffect, useRef } from "react";

// ─── Mock Data ────────────────────────────────────────────────────────────────
const MOCK_NOTIFICATIONS = [
  { id: "a1b2c3d4-0001-0000-0000-000000000001", title: "Leave Request Approved", body: "Your annual leave request for 12–16 May has been approved by your manager.", type: "HR_ACTION", priority: "HIGH", status: "ACTIVE", sourceService: "leave-service", sourceEventId: "EVT-20240512-001", createdAt: "2025-03-20T09:14:00Z", expiresAt: "2025-05-20T00:00:00Z", read: false, acknowledged: false, readAt: null, acknowledgedAt: null, metadata: { actionUrl: "/leave/requests/1234", entityId: "1234", entityType: "LEAVE_REQUEST" } },
  { id: "a1b2c3d4-0002-0000-0000-000000000002", title: "Payroll Processed — March 2025", body: "Your March 2025 payslip is now available. Net pay: ₹87,400.", type: "ANNOUNCEMENT", priority: "MEDIUM", status: "ACTIVE", sourceService: "payroll-service", sourceEventId: "EVT-20240301-002", createdAt: "2025-03-19T06:00:00Z", expiresAt: null, read: true, acknowledged: false, readAt: "2025-03-19T08:22:00Z", acknowledgedAt: null, metadata: { actionUrl: "/payroll/slips/march-2025", entityType: "PAYSLIP" } },
  { id: "a1b2c3d4-0003-0000-0000-000000000003", title: "CRITICAL: Password Expiry in 3 Days", body: "Your account password will expire on 23 March 2025. Please update it immediately to avoid lockout.", type: "SECURITY", priority: "CRITICAL", status: "ACTIVE", sourceService: "iam-service", sourceEventId: "EVT-20240320-003", createdAt: "2025-03-20T07:00:00Z", expiresAt: "2025-03-23T23:59:00Z", read: false, acknowledged: false, readAt: null, acknowledgedAt: null, metadata: { actionUrl: "/account/password/change", deepLink: "notification://account/security" } },
  { id: "a1b2c3d4-0004-0000-0000-000000000004", title: "Performance Review Due — Q1 2025", body: "Your Q1 2025 performance self-assessment is due by 28 March. Please complete it in the HR portal.", type: "REMINDER", priority: "HIGH", status: "ACTIVE", sourceService: "performance-service", sourceEventId: "EVT-20240315-004", createdAt: "2025-03-15T10:00:00Z", expiresAt: "2025-03-28T23:59:00Z", read: true, acknowledged: true, readAt: "2025-03-15T11:30:00Z", acknowledgedAt: "2025-03-15T11:31:00Z", metadata: { actionUrl: "/performance/reviews/q1-2025" } },
  { id: "a1b2c3d4-0005-0000-0000-000000000005", title: "Document Signing Required", body: "Please sign the updated remote work policy agreement by 25 March 2025.", type: "WORKFLOW", priority: "HIGH", status: "ACTIVE", sourceService: "docusign-service", sourceEventId: "EVT-20240318-005", createdAt: "2025-03-18T14:00:00Z", expiresAt: "2025-03-25T23:59:00Z", read: false, acknowledged: false, readAt: null, acknowledgedAt: null, metadata: { actionUrl: "/documents/sign/remote-work-2025", entityId: "DOC-5678" } },
  { id: "a1b2c3d4-0006-0000-0000-000000000006", title: "System Maintenance — 22 Mar 02:00–04:00 IST", body: "Notification platform will be under planned maintenance on Saturday 22 March from 2 AM to 4 AM IST. All services will be unavailable during this window.", type: "SYSTEM", priority: "MEDIUM", status: "EXPIRED", sourceService: "platform-ops", sourceEventId: "EVT-20240322-006", createdAt: "2025-03-20T16:00:00Z", expiresAt: "2025-03-22T04:00:00Z", read: true, acknowledged: false, readAt: "2025-03-20T16:45:00Z", acknowledgedAt: null, metadata: {} },
  { id: "a1b2c3d4-0007-0000-0000-000000000007", title: "New Hire Onboarding Task Assigned", body: "You have been assigned as the buddy for new hire Priya Sharma (joining 24 March). Please review the onboarding checklist.", type: "WORKFLOW", priority: "MEDIUM", status: "ACTIVE", sourceService: "onboarding-service", sourceEventId: "EVT-20240321-007", createdAt: "2025-03-21T11:00:00Z", expiresAt: null, read: false, acknowledged: false, readAt: null, acknowledgedAt: null, metadata: { actionUrl: "/onboarding/tasks/buddy/priya-sharma" } },
];

const MOCK_RULES = [
  { id: "r001", name: "admin-all-notifications", description: "Admins receive all notification types at native priority", roleName: "ADMIN", notificationType: null, priorityOverride: null, active: true, channels: ["WEBSOCKET", "EMAIL", "PUSH"], evalOrder: 10, createdBy: "SYSTEM", createdAt: "2025-01-01T00:00:00Z", conditions: {} },
  { id: "r002", name: "hr-manager-hr-actions", description: "HR Managers receive all HR_ACTION and WORKFLOW notifications", roleName: "HR_MANAGER", notificationType: "HR_ACTION", priorityOverride: null, active: true, channels: ["WEBSOCKET", "EMAIL"], evalOrder: 20, createdBy: "SYSTEM", createdAt: "2025-01-01T00:00:00Z", conditions: {} },
  { id: "r003", name: "hr-manager-workflow", description: "HR Managers receive WORKFLOW notifications", roleName: "HR_MANAGER", notificationType: "WORKFLOW", priorityOverride: null, active: true, channels: ["WEBSOCKET", "EMAIL"], evalOrder: 21, createdBy: "SYSTEM", createdAt: "2025-01-01T00:00:00Z", conditions: {} },
  { id: "r004", name: "employee-personal-reminders", description: "Employees receive REMINDER and HR_ACTION relevant to them", roleName: "EMPLOYEE", notificationType: "REMINDER", priorityOverride: null, active: true, channels: ["WEBSOCKET"], evalOrder: 30, createdBy: "SYSTEM", createdAt: "2025-01-01T00:00:00Z", conditions: {} },
  { id: "r005", name: "all-roles-system-critical", description: "All roles receive SYSTEM and SECURITY notifications at CRITICAL priority", roleName: "EMPLOYEE", notificationType: "SYSTEM", priorityOverride: "CRITICAL", active: true, channels: ["WEBSOCKET", "EMAIL", "PUSH"], evalOrder: 5, createdBy: "SYSTEM", createdAt: "2025-01-01T00:00:00Z", conditions: { min_priority: "HIGH" } },
  { id: "r006", name: "finance-announcements", description: "Finance team receives ANNOUNCEMENT notifications", roleName: "FINANCE", notificationType: "ANNOUNCEMENT", priorityOverride: null, active: false, channels: ["WEBSOCKET"], evalOrder: 40, createdBy: "SYSTEM", createdAt: "2025-01-01T00:00:00Z", conditions: {} },
  { id: "r007", name: "recruiter-workflow", description: "Recruiters receive WORKFLOW and REMINDER notifications", roleName: "RECRUITER", notificationType: "WORKFLOW", priorityOverride: null, active: true, channels: ["WEBSOCKET", "EMAIL"], evalOrder: 30, createdBy: "SYSTEM", createdAt: "2025-01-01T00:00:00Z", conditions: {} },
];

const MOCK_DELIVERY_LOGS = [
  { id: "dl001", notificationId: "a1b2c3d4-0001-0000-0000-000000000001", notificationTitle: "Leave Request Approved", recipientId: "u-abc123", channel: "WEBSOCKET", status: "DELIVERED", attemptCount: 1, maxAttempts: 3, lastAttemptAt: "2025-03-20T09:14:02Z", deliveredAt: "2025-03-20T09:14:02Z", nextRetryAt: null, errorCode: null, errorMessage: null },
  { id: "dl002", notificationId: "a1b2c3d4-0001-0000-0000-000000000001", notificationTitle: "Leave Request Approved", recipientId: "u-abc123", channel: "EMAIL", status: "DELIVERED", attemptCount: 1, maxAttempts: 3, lastAttemptAt: "2025-03-20T09:14:15Z", deliveredAt: "2025-03-20T09:14:15Z", nextRetryAt: null, errorCode: null, errorMessage: null },
  { id: "dl003", notificationId: "a1b2c3d4-0003-0000-0000-000000000003", notificationTitle: "CRITICAL: Password Expiry in 3 Days", recipientId: "u-abc123", channel: "PUSH", status: "FAILED", attemptCount: 3, maxAttempts: 3, lastAttemptAt: "2025-03-20T07:10:00Z", deliveredAt: null, nextRetryAt: null, errorCode: "PUSH_TOKEN_EXPIRED", errorMessage: "FCM device token expired or invalid" },
  { id: "dl004", notificationId: "a1b2c3d4-0003-0000-0000-000000000003", notificationTitle: "CRITICAL: Password Expiry in 3 Days", recipientId: "u-abc123", channel: "EMAIL", status: "RETRYING", attemptCount: 2, maxAttempts: 3, lastAttemptAt: "2025-03-20T07:05:00Z", deliveredAt: null, nextRetryAt: "2025-03-20T07:25:00Z", errorCode: "SMTP_TIMEOUT", errorMessage: "SMTP connection timed out after 30s" },
  { id: "dl005", notificationId: "a1b2c3d4-0005-0000-0000-000000000005", notificationTitle: "Document Signing Required", recipientId: "u-abc123", channel: "WEBSOCKET", status: "DELIVERED", attemptCount: 1, maxAttempts: 3, lastAttemptAt: "2025-03-18T14:00:03Z", deliveredAt: "2025-03-18T14:00:03Z", nextRetryAt: null, errorCode: null, errorMessage: null },
  { id: "dl006", notificationId: "a1b2c3d4-0007-0000-0000-000000000007", notificationTitle: "New Hire Onboarding Task Assigned", recipientId: "u-abc123", channel: "WEBSOCKET", status: "PENDING", attemptCount: 0, maxAttempts: 3, lastAttemptAt: null, deliveredAt: null, nextRetryAt: "2025-03-21T11:00:30Z", errorCode: null, errorMessage: null },
];

const MOCK_AUDIT_LOGS = [
  { id: 1001, notificationId: "a1b2c3d4-0001-0000-0000-000000000001", action: "CREATED", actorUserId: "svc-leave-service", actorRole: "SERVICE_ACCOUNT", ipAddress: "10.0.1.5", occurredAt: "2025-03-20T09:14:00Z", metadata: { type: "HR_ACTION", priority: "HIGH" } },
  { id: 1002, notificationId: "a1b2c3d4-0001-0000-0000-000000000001", action: "DELIVERED", actorUserId: "SYSTEM", actorRole: null, ipAddress: null, occurredAt: "2025-03-20T09:14:02Z", metadata: { channel: "WEBSOCKET" } },
  { id: 1003, notificationId: "a1b2c3d4-0001-0000-0000-000000000001", action: "READ", actorUserId: "u-abc123", actorRole: "EMPLOYEE", ipAddress: "103.26.14.x", occurredAt: "2025-03-20T10:02:00Z", metadata: {} },
  { id: 1004, notificationId: "a1b2c3d4-0003-0000-0000-000000000003", action: "CREATED", actorUserId: "svc-iam", actorRole: "SERVICE_ACCOUNT", ipAddress: "10.0.2.1", occurredAt: "2025-03-20T07:00:00Z", metadata: { type: "SECURITY", priority: "CRITICAL" } },
  { id: 1005, notificationId: "a1b2c3d4-0003-0000-0000-000000000003", action: "RETRY_ATTEMPTED", actorUserId: "SYSTEM", actorRole: null, ipAddress: null, occurredAt: "2025-03-20T07:05:00Z", metadata: { attempt: 2, channel: "EMAIL" } },
  { id: 1006, notificationId: "a1b2c3d4-0003-0000-0000-000000000003", action: "DEAD_LETTERED", actorUserId: "SYSTEM", actorRole: null, ipAddress: null, occurredAt: "2025-03-20T07:10:00Z", metadata: { channel: "PUSH", errorCode: "PUSH_TOKEN_EXPIRED" } },
  { id: 1007, notificationId: "a1b2c3d4-0004-0000-0000-000000000004", action: "ACKNOWLEDGED", actorUserId: "u-abc123", actorRole: "EMPLOYEE", ipAddress: "103.26.14.x", occurredAt: "2025-03-15T11:31:00Z", metadata: {} },
  { id: 1008, notificationId: "a1b2c3d4-0006-0000-0000-000000000006", action: "EXPIRED", actorUserId: "SYSTEM", actorRole: null, ipAddress: null, occurredAt: "2025-03-22T04:00:01Z", metadata: {} },
];

const MOCK_FAILURES = [
  { id: "f001", notificationId: "a1b2c3d4-0003-0000-0000-000000000003", notificationTitle: "CRITICAL: Password Expiry in 3 Days", notificationPriority: "CRITICAL", channel: "PUSH", failureReason: "FCM device token expired or invalid for recipient u-abc123", failureCategory: "PUSH_TOKEN_EXPIRED", occurredAt: "2025-03-20T07:10:00Z", isResolved: false, resolvedAt: null, resolvedBy: null, resolutionNotes: null, rawEventPayload: { userId: "u-abc123", deviceToken: "expired-fcm-token-xxx" } },
  { id: "f002", notificationId: "a1b2c3d4-0002-0000-0000-000000000002", notificationTitle: "Payroll Processed — March 2025", notificationPriority: "MEDIUM", channel: "EMAIL", failureReason: "SMTP relay rejected message: mailbox full", failureCategory: "SMTP_MAILBOX_FULL", occurredAt: "2025-03-19T06:03:00Z", isResolved: true, resolvedAt: "2025-03-19T09:00:00Z", resolvedBy: "admin-user-001", resolutionNotes: "Manually resent after mailbox cleanup", rawEventPayload: {} },
];

// ─── Constants ────────────────────────────────────────────────────────────────
const NOTIFICATION_TYPES = ["SYSTEM", "HR_ACTION", "REMINDER", "ANNOUNCEMENT", "SECURITY", "WORKFLOW"];
const PRIORITY_LEVELS = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];
const NOTIFICATION_STATUSES = ["ACTIVE", "EXPIRED", "ARCHIVED", "SOFT_DELETED"];
const DELIVERY_CHANNELS = ["WEBSOCKET", "SSE", "EMAIL", "PUSH", "POLLING"];
const DELIVERY_STATUSES = ["PENDING", "DELIVERED", "FAILED", "RETRYING", "DEAD_LETTERED"];
const KEYCLOAK_ROLES = ["ADMIN", "HR_MANAGER", "EMPLOYEE", "FINANCE", "RECRUITER", "PAYROLL_OFFICER", "DEPARTMENT_HEAD", "IT_ADMIN"];
const AUDIT_ACTIONS = ["CREATED", "DELIVERED", "READ", "ACKNOWLEDGED", "DISMISSED", "EXPIRED", "ARCHIVED", "SOFT_DELETED", "HARD_DELETED", "RULE_CREATED", "RULE_UPDATED", "RULE_DEACTIVATED", "EMAIL_SENT", "EMAIL_FAILED", "RETRY_ATTEMPTED", "DEAD_LETTERED"];

// ─── Helpers ──────────────────────────────────────────────────────────────────
const fmtDate = (iso) => {
  if (!iso) return "—";
  const d = new Date(iso);
  return d.toLocaleString("en-IN", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", hour12: false });
};
const timeAgo = (iso) => {
  if (!iso) return "—";
  const diff = (Date.now() - new Date(iso)) / 1000;
  if (diff < 60) return `${Math.floor(diff)}s ago`;
  if (diff < 3600) return `${Math.floor(diff / 60)}m ago`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}h ago`;
  return `${Math.floor(diff / 86400)}d ago`;
};
const shortId = (id) => id ? id.split("-")[0] + "…" : "—";

// ─── Color Maps ───────────────────────────────────────────────────────────────
const priorityColors = { LOW: "#6ee7b7", MEDIUM: "#fbbf24", HIGH: "#f97316", CRITICAL: "#ef4444" };
const priorityBg = { LOW: "rgba(110,231,183,0.12)", MEDIUM: "rgba(251,191,36,0.12)", HIGH: "rgba(249,115,22,0.12)", CRITICAL: "rgba(239,68,68,0.15)" };
const typeColors = { SYSTEM: "#94a3b8", HR_ACTION: "#a78bfa", REMINDER: "#38bdf8", ANNOUNCEMENT: "#34d399", SECURITY: "#f87171", WORKFLOW: "#fb923c" };
const statusColors = { ACTIVE: "#34d399", EXPIRED: "#64748b", ARCHIVED: "#475569", SOFT_DELETED: "#ef4444" };
const deliveryStatusColors = { PENDING: "#fbbf24", DELIVERED: "#34d399", FAILED: "#ef4444", RETRYING: "#fb923c", DEAD_LETTERED: "#dc2626" };
const channelIcons = { WEBSOCKET: "⚡", SSE: "📡", EMAIL: "✉️", PUSH: "🔔", POLLING: "🔄" };
const typeIcons = { SYSTEM: "⚙️", HR_ACTION: "👤", REMINDER: "⏰", ANNOUNCEMENT: "📢", SECURITY: "🔐", WORKFLOW: "🔀" };
const auditActionColors = { CREATED: "#34d399", DELIVERED: "#38bdf8", READ: "#a78bfa", ACKNOWLEDGED: "#fb923c", DISMISSED: "#94a3b8", EXPIRED: "#64748b", ARCHIVED: "#475569", SOFT_DELETED: "#f87171", HARD_DELETED: "#dc2626", RULE_CREATED: "#34d399", RULE_UPDATED: "#fbbf24", RULE_DEACTIVATED: "#f87171", EMAIL_SENT: "#34d399", EMAIL_FAILED: "#ef4444", RETRY_ATTEMPTED: "#fb923c", DEAD_LETTERED: "#dc2626" };

// ─── Sub-Components ───────────────────────────────────────────────────────────
const Badge = ({ label, color, bg }) => (
  <span style={{ display: "inline-flex", alignItems: "center", gap: 4, padding: "2px 8px", borderRadius: 4, fontSize: 11, fontWeight: 700, letterSpacing: "0.06em", color, background: bg || color + "22", border: `1px solid ${color}44`, fontFamily: "monospace", whiteSpace: "nowrap" }}>
    {label}
  </span>
);

const Tag = ({ text, color }) => (
  <span style={{ display: "inline-block", padding: "1px 6px", borderRadius: 3, fontSize: 10, fontWeight: 600, color, background: color + "18", border: `1px solid ${color}33`, fontFamily: "monospace", marginRight: 3 }}>
    {text}
  </span>
);

const PriorityBadge = ({ p }) => <Badge label={p} color={priorityColors[p]} bg={priorityBg[p]} />;
const TypeBadge = ({ t }) => <Badge label={t} color={typeColors[t]} />;
const StatusBadge = ({ s }) => <Badge label={s} color={statusColors[s]} />;
const DeliveryStatusBadge = ({ s }) => <Badge label={s} color={deliveryStatusColors[s]} />;

const Pill = ({ active }) => (
  <span style={{ display: "inline-block", width: 8, height: 8, borderRadius: "50%", background: active ? "#34d399" : "#475569", marginRight: 6, boxShadow: active ? "0 0 6px #34d399aa" : "none" }} />
);

// ─── Modal ────────────────────────────────────────────────────────────────────
const Modal = ({ open, onClose, title, children }) => {
  if (!open) return null;
  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(2,6,23,0.85)", zIndex: 1000, display: "flex", alignItems: "center", justifyContent: "center", backdropFilter: "blur(4px)" }} onClick={onClose}>
      <div style={{ background: "#0f172a", border: "1px solid #1e293b", borderRadius: 12, padding: 28, minWidth: 520, maxWidth: 680, maxHeight: "85vh", overflow: "auto", boxShadow: "0 25px 80px rgba(0,0,0,0.7)" }} onClick={e => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 20 }}>
          <h3 style={{ margin: 0, fontSize: 17, fontWeight: 700, color: "#e2e8f0", fontFamily: "'Syne', sans-serif" }}>{title}</h3>
          <button onClick={onClose} style={{ background: "none", border: "none", color: "#64748b", fontSize: 20, cursor: "pointer", padding: "0 4px", lineHeight: 1 }}>✕</button>
        </div>
        {children}
      </div>
    </div>
  );
};

// ─── Input ────────────────────────────────────────────────────────────────────
const Input = ({ label, value, onChange, type = "text", required, placeholder }) => (
  <div style={{ marginBottom: 14 }}>
    {label && <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: 5 }}>{label}{required && <span style={{ color: "#ef4444" }}> *</span>}</label>}
    <input type={type} value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} style={{ width: "100%", background: "#0a0f1e", border: "1px solid #1e293b", borderRadius: 6, padding: "9px 12px", color: "#e2e8f0", fontSize: 13, fontFamily: "monospace", outline: "none", boxSizing: "border-box" }} />
  </div>
);

const Select = ({ label, value, onChange, options, required, placeholder }) => (
  <div style={{ marginBottom: 14 }}>
    {label && <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: 5 }}>{label}{required && <span style={{ color: "#ef4444" }}> *</span>}</label>}
    <select value={value} onChange={e => onChange(e.target.value)} style={{ width: "100%", background: "#0a0f1e", border: "1px solid #1e293b", borderRadius: 6, padding: "9px 12px", color: value ? "#e2e8f0" : "#475569", fontSize: 13, outline: "none", boxSizing: "border-box" }}>
      {placeholder && <option value="">{placeholder}</option>}
      {options.map(o => <option key={o} value={o}>{o}</option>)}
    </select>
  </div>
);

const Textarea = ({ label, value, onChange, required, rows = 3 }) => (
  <div style={{ marginBottom: 14 }}>
    {label && <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: 5 }}>{label}{required && <span style={{ color: "#ef4444" }}> *</span>}</label>}
    <textarea value={value} onChange={e => onChange(e.target.value)} rows={rows} style={{ width: "100%", background: "#0a0f1e", border: "1px solid #1e293b", borderRadius: 6, padding: "9px 12px", color: "#e2e8f0", fontSize: 13, fontFamily: "inherit", outline: "none", resize: "vertical", boxSizing: "border-box" }} />
  </div>
);

// ─── Stat Card ────────────────────────────────────────────────────────────────
const StatCard = ({ label, value, sub, accent, icon }) => (
  <div style={{ background: "#0f172a", border: "1px solid #1e293b", borderRadius: 10, padding: "18px 22px", flex: 1, minWidth: 150, position: "relative", overflow: "hidden" }}>
    <div style={{ position: "absolute", top: 0, left: 0, width: 3, height: "100%", background: accent, borderRadius: "10px 0 0 10px" }} />
    <div style={{ fontSize: 26, marginBottom: 2 }}>{icon}</div>
    <div style={{ fontSize: 28, fontWeight: 800, color: accent, fontFamily: "monospace", lineHeight: 1 }}>{value}</div>
    <div style={{ fontSize: 12, color: "#94a3b8", fontWeight: 600, textTransform: "uppercase", letterSpacing: "0.06em", marginTop: 4 }}>{label}</div>
    {sub && <div style={{ fontSize: 11, color: "#475569", marginTop: 3 }}>{sub}</div>}
  </div>
);

// ─── Notification Card ────────────────────────────────────────────────────────
const NotifCard = ({ n, onSelect, onMarkRead, onAck, onDelete }) => (
  <div onClick={() => onSelect(n)} style={{ background: n.read ? "#0a0f1e" : "#0f172a", border: `1px solid ${n.read ? "#1e293b" : "#1e3a5f"}`, borderLeft: `3px solid ${priorityColors[n.priority]}`, borderRadius: 8, padding: "14px 16px", cursor: "pointer", transition: "border-color 0.15s, background 0.15s", position: "relative" }}>
    {!n.read && <div style={{ position: "absolute", top: 10, right: 10, width: 8, height: 8, borderRadius: "50%", background: "#3b82f6", boxShadow: "0 0 8px #3b82f6" }} />}
    <div style={{ display: "flex", alignItems: "flex-start", gap: 10, marginBottom: 8 }}>
      <span style={{ fontSize: 18, lineHeight: 1, marginTop: 1 }}>{typeIcons[n.type]}</span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap", marginBottom: 4 }}>
          <span style={{ fontWeight: 700, color: "#e2e8f0", fontSize: 14, fontFamily: "'Syne', sans-serif" }}>{n.title}</span>
          {n.priority === "CRITICAL" && <span style={{ fontSize: 10, color: "#ef4444", fontWeight: 800, fontFamily: "monospace", animation: "pulse 1.5s infinite" }}>● CRITICAL</span>}
        </div>
        <p style={{ margin: 0, color: "#94a3b8", fontSize: 13, lineHeight: 1.5, overflow: "hidden", textOverflow: "ellipsis", display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical" }}>{n.body}</p>
      </div>
    </div>
    <div style={{ display: "flex", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
      <TypeBadge t={n.type} />
      <PriorityBadge p={n.priority} />
      <StatusBadge s={n.status} />
      <span style={{ marginLeft: "auto", fontSize: 11, color: "#475569", fontFamily: "monospace" }}>{timeAgo(n.createdAt)}</span>
    </div>
    <div style={{ display: "flex", gap: 6, marginTop: 10 }} onClick={e => e.stopPropagation()}>
      {!n.read && <button onClick={() => onMarkRead(n.id)} style={btnSmStyle("#3b82f6")}>Mark Read</button>}
      {n.read && !n.acknowledged && n.priority === "CRITICAL" && <button onClick={() => onAck(n.id)} style={btnSmStyle("#f97316")}>Acknowledge</button>}
      <button onClick={() => onDelete(n.id)} style={btnSmStyle("#ef4444")}>Delete</button>
      <span style={{ marginLeft: "auto", fontSize: 10, color: "#334155", fontFamily: "monospace" }}>src: {n.sourceService}</span>
    </div>
  </div>
);

const btnSmStyle = (color) => ({
  background: color + "18", border: `1px solid ${color}44`, borderRadius: 4, color, fontSize: 11, fontWeight: 600, padding: "3px 10px", cursor: "pointer", fontFamily: "monospace"
});

// ─── Notification Detail ──────────────────────────────────────────────────────
const NotifDetail = ({ n, onClose }) => (
  <div style={{ padding: 0 }}>
    <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 16 }}>
      <TypeBadge t={n.type} />
      <PriorityBadge p={n.priority} />
      <StatusBadge s={n.status} />
      {n.read && <Badge label="READ" color="#34d399" />}
      {n.acknowledged && <Badge label="ACKNOWLEDGED" color="#fb923c" />}
    </div>
    <p style={{ color: "#94a3b8", fontSize: 14, lineHeight: 1.6, marginBottom: 18 }}>{n.body}</p>
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, fontSize: 12, fontFamily: "monospace" }}>
      {[
        ["ID", shortId(n.id)],
        ["Source Service", n.sourceService],
        ["Source Event ID", n.sourceEventId || "—"],
        ["Created At", fmtDate(n.createdAt)],
        ["Expires At", fmtDate(n.expiresAt)],
        ["Read At", fmtDate(n.readAt)],
        ["Acknowledged At", fmtDate(n.acknowledgedAt)],
      ].map(([k, v]) => (
        <div key={k} style={{ background: "#0a0f1e", border: "1px solid #1e293b", borderRadius: 6, padding: "8px 12px" }}>
          <div style={{ color: "#475569", fontSize: 10, textTransform: "uppercase", letterSpacing: "0.06em", marginBottom: 2 }}>{k}</div>
          <div style={{ color: "#cbd5e1" }}>{v}</div>
        </div>
      ))}
    </div>
    {n.metadata && Object.keys(n.metadata).length > 0 && (
      <div style={{ marginTop: 16 }}>
        <div style={{ fontSize: 11, color: "#64748b", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: 6 }}>Metadata (JSONB)</div>
        <pre style={{ background: "#0a0f1e", border: "1px solid #1e293b", borderRadius: 6, padding: 12, fontSize: 12, color: "#94a3b8", margin: 0, overflow: "auto" }}>{JSON.stringify(n.metadata, null, 2)}</pre>
      </div>
    )}
  </div>
);

// ─── Create Notification Form ─────────────────────────────────────────────────
const CreateNotificationForm = ({ onSubmit, onClose }) => {
  const [form, setForm] = useState({ title: "", body: "", type: "", priority: "", sourceService: "", sourceEventId: "", expiresAt: "", targetUserIds: "" });
  const set = (k) => (v) => setForm(f => ({ ...f, [k]: v }));
  const handleSubmit = () => {
    if (!form.title || !form.body || !form.type || !form.priority || !form.sourceService) return;
    onSubmit({ ...form, targetUserIds: form.targetUserIds ? form.targetUserIds.split(",").map(s => s.trim()) : [], metadata: {} });
    onClose();
  };
  return (
    <div>
      <Input label="Title" value={form.title} onChange={set("title")} required placeholder="e.g. Performance Review Due" />
      <Textarea label="Body" value={form.body} onChange={set("body")} required rows={3} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <Select label="Type" value={form.type} onChange={set("type")} options={NOTIFICATION_TYPES} required placeholder="Select type…" />
        <Select label="Priority" value={form.priority} onChange={set("priority")} options={PRIORITY_LEVELS} required placeholder="Select priority…" />
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <Input label="Source Service" value={form.sourceService} onChange={set("sourceService")} required placeholder="e.g. leave-service" />
        <Input label="Source Event ID" value={form.sourceEventId} onChange={set("sourceEventId")} placeholder="Idempotency key" />
      </div>
      <Input label="Expires At" value={form.expiresAt} onChange={set("expiresAt")} type="datetime-local" />
      <Input label="Target User IDs (comma-separated)" value={form.targetUserIds} onChange={set("targetUserIds")} placeholder="u-abc123, u-def456" />
      <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 8 }}>
        <button onClick={onClose} style={{ ...btnStyle, background: "transparent", color: "#64748b", border: "1px solid #1e293b" }}>Cancel</button>
        <button onClick={handleSubmit} style={{ ...btnStyle, background: "#3b82f6", color: "#fff", border: "none" }}>Create Notification</button>
      </div>
    </div>
  );
};

// ─── Create Rule Form ─────────────────────────────────────────────────────────
const CreateRuleForm = ({ onSubmit, onClose }) => {
  const [form, setForm] = useState({ name: "", description: "", roleName: "", notificationType: "", priorityOverride: "", channels: [], evalOrder: 100, conditions: "{}" });
  const set = (k) => (v) => setForm(f => ({ ...f, [k]: v }));
  const toggleChannel = (ch) => setForm(f => ({ ...f, channels: f.channels.includes(ch) ? f.channels.filter(c => c !== ch) : [...f.channels, ch] }));
  const handleSubmit = () => {
    if (!form.name || !form.roleName) return;
    onSubmit({ ...form });
    onClose();
  };
  return (
    <div>
      <Input label="Rule Name" value={form.name} onChange={set("name")} required placeholder="e.g. employee-security-alerts" />
      <Textarea label="Description" value={form.description} onChange={set("description")} rows={2} />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <Select label="Role Name" value={form.roleName} onChange={set("roleName")} options={KEYCLOAK_ROLES} required placeholder="Select role…" />
        <Select label="Notification Type" value={form.notificationType} onChange={set("notificationType")} options={NOTIFICATION_TYPES} placeholder="All types" />
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <Select label="Priority Override" value={form.priorityOverride} onChange={set("priorityOverride")} options={PRIORITY_LEVELS} placeholder="None (native)" />
        <Input label="Eval Order" value={form.evalOrder} onChange={set("evalOrder")} type="number" />
      </div>
      <div style={{ marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: 8 }}>Delivery Channels</label>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {DELIVERY_CHANNELS.map(ch => (
            <button key={ch} onClick={() => toggleChannel(ch)} style={{ padding: "5px 12px", borderRadius: 5, fontSize: 12, fontWeight: 600, fontFamily: "monospace", cursor: "pointer", background: form.channels.includes(ch) ? "#3b82f6" : "#0a0f1e", color: form.channels.includes(ch) ? "#fff" : "#64748b", border: `1px solid ${form.channels.includes(ch) ? "#3b82f6" : "#1e293b"}`, transition: "all 0.15s" }}>
              {channelIcons[ch]} {ch}
            </button>
          ))}
        </div>
      </div>
      <div style={{ marginBottom: 14 }}>
        <label style={{ display: "block", fontSize: 11, fontWeight: 600, color: "#94a3b8", textTransform: "uppercase", letterSpacing: "0.08em", marginBottom: 5 }}>Conditions (JSONB)</label>
        <textarea value={form.conditions} onChange={e => set("conditions")(e.target.value)} rows={2} placeholder='e.g. {"min_priority": "HIGH"}' style={{ width: "100%", background: "#0a0f1e", border: "1px solid #1e293b", borderRadius: 6, padding: "9px 12px", color: "#e2e8f0", fontSize: 12, fontFamily: "monospace", outline: "none", resize: "vertical", boxSizing: "border-box" }} />
      </div>
      <div style={{ display: "flex", gap: 10, justifyContent: "flex-end", marginTop: 8 }}>
        <button onClick={onClose} style={{ ...btnStyle, background: "transparent", color: "#64748b", border: "1px solid #1e293b" }}>Cancel</button>
        <button onClick={handleSubmit} style={{ ...btnStyle, background: "#a78bfa", color: "#fff", border: "none" }}>Create Rule</button>
      </div>
    </div>
  );
};

const btnStyle = { padding: "9px 20px", borderRadius: 7, fontSize: 13, fontWeight: 700, cursor: "pointer", fontFamily: "'Syne', sans-serif", letterSpacing: "0.02em" };

// ─── Tabs ─────────────────────────────────────────────────────────────────────
const TabBar = ({ tabs, active, onSelect }) => (
  <div style={{ display: "flex", gap: 2, borderBottom: "1px solid #1e293b", marginBottom: 24 }}>
    {tabs.map(t => (
      <button key={t.id} onClick={() => onSelect(t.id)} style={{ background: "none", border: "none", borderBottom: active === t.id ? "2px solid #3b82f6" : "2px solid transparent", color: active === t.id ? "#e2e8f0" : "#64748b", padding: "10px 18px", fontSize: 13, fontWeight: 600, cursor: "pointer", fontFamily: "'Syne', sans-serif", transition: "all 0.15s", letterSpacing: "0.02em" }}>
        {t.icon} {t.label}
        {t.badge != null && t.badge > 0 && <span style={{ marginLeft: 6, background: "#ef4444", color: "#fff", borderRadius: 10, fontSize: 10, fontWeight: 700, padding: "1px 5px" }}>{t.badge}</span>}
      </button>
    ))}
  </div>
);

// ─── Filter Bar ───────────────────────────────────────────────────────────────
const FilterBar = ({ filters, setFilters, showUnreadToggle = false }) => (
  <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 18, alignItems: "center" }}>
    <select value={filters.type || ""} onChange={e => setFilters(f => ({ ...f, type: e.target.value }))} style={filterSelectStyle}>
      <option value="">All Types</option>
      {NOTIFICATION_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
    </select>
    <select value={filters.priority || ""} onChange={e => setFilters(f => ({ ...f, priority: e.target.value }))} style={filterSelectStyle}>
      <option value="">All Priorities</option>
      {PRIORITY_LEVELS.map(t => <option key={t} value={t}>{t}</option>)}
    </select>
    <select value={filters.status || ""} onChange={e => setFilters(f => ({ ...f, status: e.target.value }))} style={filterSelectStyle}>
      <option value="">All Statuses</option>
      {NOTIFICATION_STATUSES.map(t => <option key={t} value={t}>{t}</option>)}
    </select>
    {showUnreadToggle && (
      <button onClick={() => setFilters(f => ({ ...f, unreadOnly: !f.unreadOnly }))} style={{ ...filterSelectStyle, cursor: "pointer", background: filters.unreadOnly ? "#1e3a5f" : "#0a0f1e", color: filters.unreadOnly ? "#38bdf8" : "#64748b", border: `1px solid ${filters.unreadOnly ? "#3b82f6" : "#1e293b"}`, fontWeight: 600 }}>
        {filters.unreadOnly ? "● Unread Only" : "All Notifications"}
      </button>
    )}
    {Object.values(filters).some(Boolean) && (
      <button onClick={() => setFilters({})} style={{ ...filterSelectStyle, cursor: "pointer", color: "#ef4444", border: "1px solid #ef444433" }}>✕ Clear</button>
    )}
  </div>
);

const filterSelectStyle = { background: "#0a0f1e", border: "1px solid #1e293b", borderRadius: 6, padding: "7px 12px", color: "#94a3b8", fontSize: 12, outline: "none", fontFamily: "monospace" };

// ─── Main App ─────────────────────────────────────────────────────────────────
export default function NotificationNotificationsApp() {
  const [activeTab, setActiveTab] = useState("dashboard");
  const [notifications, setNotifications] = useState(MOCK_NOTIFICATIONS);
  const [rules, setRules] = useState(MOCK_RULES);
  const [failures, setFailures] = useState(MOCK_FAILURES);
  const [filters, setFilters] = useState({});
  const [selectedNotif, setSelectedNotif] = useState(null);
  const [showCreateNotif, setShowCreateNotif] = useState(false);
  const [showCreateRule, setShowCreateRule] = useState(false);
  const [toast, setToast] = useState(null);
  const [currentRole, setCurrentRole] = useState("ADMIN");
  const [wsConnected, setWsConnected] = useState(true);

  // Simulate WS blinking
  useEffect(() => {
    const t = setInterval(() => setWsConnected(v => v), 5000);
    return () => clearInterval(t);
  }, []);

  const showToast = (msg, color = "#34d399") => {
    setToast({ msg, color });
    setTimeout(() => setToast(null), 3000);
  };

  const unreadCount = notifications.filter(n => !n.read && n.status === "ACTIVE").length;
  const activeCount = notifications.filter(n => n.status === "ACTIVE").length;
  const criticalCount = notifications.filter(n => n.priority === "CRITICAL" && n.status === "ACTIVE").length;
  const unresolvedFailures = failures.filter(f => !f.isResolved).length;

  const filteredNotifications = notifications.filter(n => {
    if (filters.type && n.type !== filters.type) return false;
    if (filters.priority && n.priority !== filters.priority) return false;
    if (filters.status && n.status !== filters.status) return false;
    if (filters.unreadOnly && n.read) return false;
    return true;
  });

  const markRead = (id) => {
    setNotifications(ns => ns.map(n => n.id === id ? { ...n, read: true, readAt: new Date().toISOString() } : n));
    showToast("Notification marked as read");
  };

  const acknowledge = (id) => {
    setNotifications(ns => ns.map(n => n.id === id ? { ...n, acknowledged: true, acknowledgedAt: new Date().toISOString() } : n));
    showToast("Notification acknowledged", "#fb923c");
  };

  const softDelete = (id) => {
    setNotifications(ns => ns.map(n => n.id === id ? { ...n, status: "SOFT_DELETED" } : n));
    showToast("Notification soft-deleted", "#ef4444");
  };

  const createNotification = (data) => {
    const newNotif = { id: `new-${Date.now()}`, ...data, status: "ACTIVE", read: false, acknowledged: false, readAt: null, acknowledgedAt: null, createdAt: new Date().toISOString() };
    setNotifications(ns => [newNotif, ...ns]);
    showToast("✓ Notification created successfully");
  };

  const createRule = (data) => {
    const newRule = { id: `r-${Date.now()}`, ...data, active: true, createdAt: new Date().toISOString() };
    setRules(rs => [newRule, ...rs]);
    showToast("✓ Rule created", "#a78bfa");
  };

  const toggleRule = (id) => {
    setRules(rs => rs.map(r => r.id === id ? { ...r, active: !r.active } : r));
    showToast("Rule updated", "#fbbf24");
  };

  const resolveFailure = (id) => {
    setFailures(fs => fs.map(f => f.id === id ? { ...f, isResolved: true, resolvedAt: new Date().toISOString(), resolvedBy: "admin-user-001", resolutionNotes: "Manually resolved via admin panel" } : f));
    showToast("Failure resolved", "#34d399");
  };

  const tabs = [
    { id: "dashboard", label: "Dashboard", icon: "◈" },
    { id: "notifications", label: "Notifications", icon: "🔔", badge: unreadCount },
    { id: "rules", label: "Rules Engine", icon: "⚖️" },
    { id: "delivery", label: "Delivery Logs", icon: "📦" },
    { id: "audit", label: "Audit Trail", icon: "🗂️" },
    { id: "dlq", label: "DLQ / Failures", icon: "💀", badge: unresolvedFailures },
  ];

  return (
    <div style={{ minHeight: "100vh", background: "#020617", color: "#e2e8f0", fontFamily: "'Inter', sans-serif" }}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=Syne:wght@600;700;800&family=Inter:wght@400;500;600&display=swap');
        * { box-sizing: border-box; }
        ::-webkit-scrollbar { width: 6px; height: 6px; }
        ::-webkit-scrollbar-track { background: #0a0f1e; }
        ::-webkit-scrollbar-thumb { background: #1e293b; border-radius: 3px; }
        @keyframes pulse { 0%,100%{opacity:1} 50%{opacity:0.4} }
        @keyframes slideIn { from{opacity:0;transform:translateY(10px)} to{opacity:1;transform:translateY(0)} }
        @keyframes fadeIn { from{opacity:0} to{opacity:1} }
        .notif-card:hover { border-color: #334155 !important; }
      `}</style>

      {/* Header */}
      <div style={{ background: "#080d1a", borderBottom: "1px solid #0f172a", padding: "0 28px", display: "flex", alignItems: "center", height: 60, gap: 20, position: "sticky", top: 0, zIndex: 100 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{ width: 32, height: 32, background: "linear-gradient(135deg, #3b82f6, #a78bfa)", borderRadius: 8, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 16 }}>🔔</div>
          <div>
            <div style={{ fontSize: 15, fontWeight: 800, color: "#e2e8f0", fontFamily: "'Syne', sans-serif", lineHeight: 1, letterSpacing: "-0.02em" }}>Notification Notifications</div>
            <div style={{ fontSize: 10, color: "#334155", fontFamily: "monospace" }}>Spring Boot 3.3 · PostgreSQL · RabbitMQ · Redis · Keycloak</div>
          </div>
        </div>
        <div style={{ flex: 1 }} />
        {/* WebSocket status */}
        <div style={{ display: "flex", alignItems: "center", gap: 6, background: "#0a0f1e", border: "1px solid #0f172a", borderRadius: 6, padding: "4px 10px", fontSize: 11, fontFamily: "monospace" }}>
          <span style={{ width: 7, height: 7, borderRadius: "50%", background: wsConnected ? "#34d399" : "#ef4444", display: "inline-block", boxShadow: wsConnected ? "0 0 5px #34d399" : "none" }} />
          <span style={{ color: "#64748b" }}>WS</span>
          <span style={{ color: wsConnected ? "#34d399" : "#ef4444" }}>{wsConnected ? "CONNECTED" : "DISCONNECTED"}</span>
        </div>
        {/* Role selector */}
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span style={{ fontSize: 11, color: "#475569", fontFamily: "monospace" }}>Role:</span>
          <select value={currentRole} onChange={e => setCurrentRole(e.target.value)} style={{ background: "#0a0f1e", border: "1px solid #1e293b", borderRadius: 5, padding: "4px 8px", color: "#a78bfa", fontSize: 12, fontFamily: "monospace", outline: "none" }}>
            {KEYCLOAK_ROLES.map(r => <option key={r} value={r}>{r}</option>)}
          </select>
        </div>
        {/* Unread badge */}
        <div style={{ position: "relative", cursor: "pointer" }} onClick={() => setActiveTab("notifications")}>
          <span style={{ fontSize: 22 }}>🔔</span>
          {unreadCount > 0 && <span style={{ position: "absolute", top: -4, right: -6, background: "#ef4444", color: "#fff", borderRadius: 10, fontSize: 10, fontWeight: 700, padding: "1px 5px", minWidth: 16, textAlign: "center" }}>{unreadCount}</span>}
        </div>
      </div>

      {/* Body */}
      <div style={{ maxWidth: 1280, margin: "0 auto", padding: "28px 28px 48px" }}>
        <TabBar tabs={tabs} active={activeTab} onSelect={setActiveTab} />

        {/* ── DASHBOARD ── */}
        {activeTab === "dashboard" && (
          <div style={{ animation: "fadeIn 0.3s" }}>
            <div style={{ marginBottom: 24 }}>
              <h2 style={{ margin: "0 0 4px", fontFamily: "'Syne', sans-serif", fontSize: 22, fontWeight: 800, color: "#f1f5f9" }}>Overview</h2>
              <p style={{ margin: 0, color: "#475569", fontSize: 13 }}>Notification Notification Center — Real-time delivery across WebSocket, SSE, Email, Push channels</p>
            </div>
            <div style={{ display: "flex", gap: 14, flexWrap: "wrap", marginBottom: 28 }}>
              <StatCard label="Unread" value={unreadCount} accent="#3b82f6" icon="📬" sub="Active notifications" />
              <StatCard label="Active" value={activeCount} accent="#34d399" icon="✅" sub="Live notifications" />
              <StatCard label="Critical" value={criticalCount} accent="#ef4444" icon="🚨" sub="Require ACK" />
              <StatCard label="DLQ Items" value={unresolvedFailures} accent="#f97316" icon="💀" sub="Unresolved failures" />
              <StatCard label="Rules" value={rules.filter(r => r.active).length} accent="#a78bfa" icon="⚖️" sub={`of ${rules.length} total`} />
            </div>

            {/* Flow diagram */}
            <div style={{ background: "#0f172a", border: "1px solid #1e293b", borderRadius: 10, padding: 22, marginBottom: 24 }}>
              <h3 style={{ margin: "0 0 16px", fontFamily: "'Syne', sans-serif", fontSize: 15, fontWeight: 700, color: "#94a3b8" }}>⚙️ Notification Flow Architecture</h3>
              <div style={{ display: "flex", alignItems: "center", gap: 0, overflowX: "auto", paddingBottom: 8 }}>
                {[
                  { label: "Microservice", sub: "leave-service\npayroll-service\niam-service", color: "#38bdf8" },
                  { label: "RabbitMQ", sub: "notification.notifications\n.main exchange", color: "#fb923c" },
                  { label: "Event Listener", sub: "NotificationEvent\nListener.java", color: "#a78bfa" },
                  { label: "NotificationService", sub: "Idempotency check\nPersist entity", color: "#34d399" },
                  { label: "Rule Engine", sub: "Redis cache TTL 60s\nResolve recipients", color: "#fbbf24" },
                  { label: "DeliveryService", sub: "WS · SSE\nEmail · Push", color: "#3b82f6" },
                  { label: "AuditService", sub: "notif_audit\n.audit_log", color: "#94a3b8" },
                ].map((node, i, arr) => (
                  <div key={i} style={{ display: "flex", alignItems: "center", gap: 0, flexShrink: 0 }}>
                    <div style={{ background: node.color + "15", border: `1px solid ${node.color}44`, borderRadius: 8, padding: "10px 16px", textAlign: "center", minWidth: 120 }}>
                      <div style={{ fontSize: 12, fontWeight: 700, color: node.color, fontFamily: "'Syne', sans-serif" }}>{node.label}</div>
                      <div style={{ fontSize: 10, color: "#475569", fontFamily: "monospace", marginTop: 3, whiteSpace: "pre" }}>{node.sub}</div>
                    </div>
                    {i < arr.length - 1 && <div style={{ color: "#334155", fontSize: 20, padding: "0 4px", userSelect: "none" }}>→</div>}
                  </div>
                ))}
              </div>
            </div>

            {/* Recent notifications */}
            <div>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 14 }}>
                <h3 style={{ margin: 0, fontFamily: "'Syne', sans-serif", fontSize: 15, fontWeight: 700, color: "#94a3b8" }}>📋 Recent Notifications</h3>
                <button onClick={() => setActiveTab("notifications")} style={{ background: "none", border: "none", color: "#3b82f6", fontSize: 12, cursor: "pointer", fontWeight: 600 }}>View All →</button>
              </div>
              <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                {notifications.slice(0, 4).map(n => (
                  <NotifCard key={n.id} n={n} onSelect={n => { setSelectedNotif(n); }} onMarkRead={markRead} onAck={acknowledge} onDelete={softDelete} />
                ))}
              </div>
            </div>
          </div>
        )}

        {/* ── NOTIFICATIONS ── */}
        {activeTab === "notifications" && (
          <div style={{ animation: "fadeIn 0.3s" }}>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 20 }}>
              <div>
                <h2 style={{ margin: "0 0 2px", fontFamily: "'Syne', sans-serif", fontSize: 20, fontWeight: 800 }}>Notifications Feed</h2>
                <p style={{ margin: 0, color: "#475569", fontSize: 12, fontFamily: "monospace" }}>GET /api/v1/notifications · Page&lt;NotificationResponse&gt;</p>
              </div>
              {(currentRole === "ADMIN" || currentRole === "HR_MANAGER") && (
                <button onClick={() => setShowCreateNotif(true)} style={{ ...btnStyle, background: "#3b82f6", color: "#fff", border: "none", fontSize: 13 }}>+ Create Notification</button>
              )}
            </div>
            <FilterBar filters={filters} setFilters={setFilters} showUnreadToggle />
            <div style={{ fontSize: 12, color: "#475569", marginBottom: 12, fontFamily: "monospace" }}>
              Showing {filteredNotifications.length} of {notifications.length} notifications
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {filteredNotifications.length === 0 ? (
                <div style={{ textAlign: "center", color: "#334155", padding: "40px 0", fontFamily: "monospace" }}>No notifications match the current filters.</div>
              ) : filteredNotifications.map(n => (
                <NotifCard key={n.id} n={n} onSelect={setSelectedNotif} onMarkRead={markRead} onAck={acknowledge} onDelete={softDelete} />
              ))}
            </div>
          </div>
        )}

        {/* ── RULES ENGINE ── */}
        {activeTab === "rules" && (
          <div style={{ animation: "fadeIn 0.3s" }}>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 20 }}>
              <div>
                <h2 style={{ margin: "0 0 2px", fontFamily: "'Syne', sans-serif", fontSize: 20, fontWeight: 800 }}>Rules Engine (RBAC Routing)</h2>
                <p style={{ margin: 0, color: "#475569", fontSize: 12, fontFamily: "monospace" }}>notif.notification_rules · Redis-cached TTL 60s · RuleEngineService</p>
              </div>
              {currentRole === "ADMIN" && (
                <button onClick={() => setShowCreateRule(true)} style={{ ...btnStyle, background: "#a78bfa", color: "#fff", border: "none", fontSize: 13 }}>+ Create Rule</button>
              )}
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              {rules.sort((a, b) => a.evalOrder - b.evalOrder).map(r => (
                <div key={r.id} style={{ background: "#0f172a", border: `1px solid ${r.active ? "#1e293b" : "#0f172a"}`, borderLeft: `3px solid ${r.active ? "#a78bfa" : "#334155"}`, borderRadius: 8, padding: "14px 18px", opacity: r.active ? 1 : 0.55 }}>
                  <div style={{ display: "flex", alignItems: "flex-start", gap: 12 }}>
                    <div style={{ background: "#a78bfa22", border: "1px solid #a78bfa44", borderRadius: 6, padding: "6px 10px", fontSize: 11, fontFamily: "monospace", color: "#a78bfa", fontWeight: 700, minWidth: 48, textAlign: "center" }}>
                      #{r.evalOrder}
                    </div>
                    <div style={{ flex: 1 }}>
                      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 5 }}>
                        <span style={{ fontWeight: 700, color: "#e2e8f0", fontFamily: "'Syne', sans-serif", fontSize: 14 }}>{r.name}</span>
                        <Pill active={r.active} />
                        <span style={{ fontSize: 11, color: r.active ? "#34d399" : "#64748b", fontFamily: "monospace" }}>{r.active ? "ACTIVE" : "INACTIVE"}</span>
                      </div>
                      <p style={{ margin: "0 0 10px", color: "#64748b", fontSize: 12 }}>{r.description}</p>
                      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
                        <Badge label={r.roleName} color="#a78bfa" />
                        {r.notificationType ? <TypeBadge t={r.notificationType} /> : <Badge label="ALL TYPES" color="#64748b" />}
                        {r.priorityOverride && <PriorityBadge p={r.priorityOverride} />}
                        {r.channels.map(ch => <Tag key={ch} text={`${channelIcons[ch]} ${ch}`} color="#38bdf8" />)}
                        {Object.keys(r.conditions || {}).length > 0 && (
                          <span style={{ fontSize: 11, color: "#fbbf24", fontFamily: "monospace", background: "#fbbf2412", border: "1px solid #fbbf2433", borderRadius: 4, padding: "2px 7px" }}>conditions: {JSON.stringify(r.conditions)}</span>
                        )}
                      </div>
                    </div>
                    {currentRole === "ADMIN" && (
                      <button onClick={() => toggleRule(r.id)} style={{ ...btnSmStyle(r.active ? "#ef4444" : "#34d399"), padding: "6px 14px", fontSize: 12 }}>
                        {r.active ? "Deactivate" : "Activate"}
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ── DELIVERY LOGS ── */}
        {activeTab === "delivery" && (
          <div style={{ animation: "fadeIn 0.3s" }}>
            <div style={{ marginBottom: 20 }}>
              <h2 style={{ margin: "0 0 2px", fontFamily: "'Syne', sans-serif", fontSize: 20, fontWeight: 800 }}>Delivery Logs</h2>
              <p style={{ margin: 0, color: "#475569", fontSize: 12, fontFamily: "monospace" }}>notif.notification_delivery_log · Per-channel attempt tracking · Retry & DLQ escalation</p>
            </div>
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12, fontFamily: "monospace" }}>
                <thead>
                  <tr style={{ borderBottom: "1px solid #1e293b" }}>
                    {["ID", "Notification", "Recipient", "Channel", "Status", "Attempts", "Last Attempt", "Next Retry", "Error"].map(h => (
                      <th key={h} style={{ padding: "10px 12px", textAlign: "left", color: "#475569", fontWeight: 700, textTransform: "uppercase", fontSize: 10, letterSpacing: "0.06em", whiteSpace: "nowrap" }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {MOCK_DELIVERY_LOGS.map(d => (
                    <tr key={d.id} style={{ borderBottom: "1px solid #0f172a" }}>
                      <td style={{ padding: "10px 12px", color: "#334155" }}>{d.id}</td>
                      <td style={{ padding: "10px 12px", color: "#cbd5e1", maxWidth: 180, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{d.notificationTitle}</td>
                      <td style={{ padding: "10px 12px", color: "#64748b" }}>{d.recipientId}</td>
                      <td style={{ padding: "10px 12px" }}><Tag text={`${channelIcons[d.channel]} ${d.channel}`} color="#38bdf8" /></td>
                      <td style={{ padding: "10px 12px" }}><DeliveryStatusBadge s={d.status} /></td>
                      <td style={{ padding: "10px 12px", textAlign: "center" }}>
                        <span style={{ color: d.attemptCount >= d.maxAttempts ? "#ef4444" : "#94a3b8" }}>{d.attemptCount}/{d.maxAttempts}</span>
                      </td>
                      <td style={{ padding: "10px 12px", color: "#475569", whiteSpace: "nowrap" }}>{fmtDate(d.lastAttemptAt)}</td>
                      <td style={{ padding: "10px 12px", color: d.nextRetryAt ? "#fbbf24" : "#334155", whiteSpace: "nowrap" }}>{fmtDate(d.nextRetryAt)}</td>
                      <td style={{ padding: "10px 12px", color: "#ef4444", maxWidth: 180, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{d.errorCode ? `${d.errorCode}: ${d.errorMessage}` : "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ── AUDIT TRAIL ── */}
        {activeTab === "audit" && (
          <div style={{ animation: "fadeIn 0.3s" }}>
            <div style={{ marginBottom: 20 }}>
              <h2 style={{ margin: "0 0 2px", fontFamily: "'Syne', sans-serif", fontSize: 20, fontWeight: 800 }}>Audit Trail</h2>
              <p style={{ margin: 0, color: "#475569", fontSize: 12, fontFamily: "monospace" }}>notif_audit.notification_audit_log · Immutable · Monthly partitions · 2-year retention</p>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
              {MOCK_AUDIT_LOGS.slice().reverse().map(a => (
                <div key={a.id} style={{ display: "flex", gap: 14, alignItems: "flex-start", background: "#0f172a", border: "1px solid #0f172a", borderRadius: 8, padding: "12px 16px" }}>
                  <div style={{ fontSize: 10, fontFamily: "monospace", color: "#334155", minWidth: 30, textAlign: "right", paddingTop: 2 }}>#{a.id}</div>
                  <div style={{ width: 2, background: auditActionColors[a.action] || "#334155", borderRadius: 2, minHeight: 36, flexShrink: 0, alignSelf: "stretch" }} />
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", marginBottom: 5 }}>
                      <Badge label={a.action} color={auditActionColors[a.action] || "#64748b"} />
                      {a.actorRole && <Badge label={a.actorRole} color="#94a3b8" />}
                      <span style={{ fontSize: 11, fontFamily: "monospace", color: "#475569" }}>actor: {a.actorUserId}</span>
                      {a.ipAddress && <span style={{ fontSize: 11, fontFamily: "monospace", color: "#334155" }}>ip: {a.ipAddress}</span>}
                    </div>
                    <div style={{ display: "flex", gap: 14, fontSize: 11, fontFamily: "monospace", color: "#475569" }}>
                      <span>notif: {shortId(a.notificationId)}</span>
                      {Object.keys(a.metadata || {}).length > 0 && (
                        <span style={{ color: "#334155" }}>{JSON.stringify(a.metadata)}</span>
                      )}
                      <span style={{ marginLeft: "auto" }}>{fmtDate(a.occurredAt)}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* ── DLQ / FAILURES ── */}
        {activeTab === "dlq" && (
          <div style={{ animation: "fadeIn 0.3s" }}>
            <div style={{ marginBottom: 20 }}>
              <h2 style={{ margin: "0 0 2px", fontFamily: "'Syne', sans-serif", fontSize: 20, fontWeight: 800 }}>DLQ — Dead Letter Queue</h2>
              <p style={{ margin: 0, color: "#475569", fontSize: 12, fontFamily: "monospace" }}>notif.notification_failures · Exhausted retries · FailureHandlingService · Admin review & replay</p>
            </div>
            {unresolvedFailures > 0 && (
              <div style={{ background: "#ef444415", border: "1px solid #ef444433", borderRadius: 8, padding: "12px 16px", marginBottom: 18, display: "flex", gap: 10, alignItems: "center" }}>
                <span style={{ fontSize: 18 }}>🚨</span>
                <span style={{ fontSize: 13, color: "#fca5a5" }}><strong>{unresolvedFailures} unresolved failure(s)</strong> in DLQ require admin attention.</span>
              </div>
            )}
            <div style={{ display: "flex", flexDirection: "column", gap: 12 }}>
              {failures.map(f => (
                <div key={f.id} style={{ background: "#0f172a", border: `1px solid ${f.isResolved ? "#1e293b" : "#7f1d1d44"}`, borderLeft: `3px solid ${f.isResolved ? "#334155" : "#ef4444"}`, borderRadius: 8, padding: "16px 18px" }}>
                  <div style={{ display: "flex", alignItems: "flex-start", gap: 12 }}>
                    <div style={{ flex: 1 }}>
                      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", marginBottom: 8 }}>
                        <Badge label={f.isResolved ? "RESOLVED" : "UNRESOLVED"} color={f.isResolved ? "#34d399" : "#ef4444"} />
                        {f.channel && <Tag text={`${channelIcons[f.channel]} ${f.channel}`} color="#38bdf8" />}
                        {f.notificationPriority && <PriorityBadge p={f.notificationPriority} />}
                        <span style={{ fontFamily: "monospace", fontSize: 11, color: "#64748b" }}>id: {f.id}</span>
                      </div>
                      <div style={{ fontWeight: 700, color: "#e2e8f0", fontSize: 14, marginBottom: 5, fontFamily: "'Syne', sans-serif" }}>{f.notificationTitle}</div>
                      <div style={{ fontSize: 12, color: "#94a3b8", marginBottom: 8 }}>{f.failureReason}</div>
                      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 8, fontSize: 11, fontFamily: "monospace" }}>
                        <div><span style={{ color: "#334155" }}>Category: </span><span style={{ color: "#f97316" }}>{f.failureCategory}</span></div>
                        <div><span style={{ color: "#334155" }}>Occurred: </span><span style={{ color: "#64748b" }}>{fmtDate(f.occurredAt)}</span></div>
                        {f.isResolved && <div><span style={{ color: "#334155" }}>Resolved by: </span><span style={{ color: "#34d399" }}>{f.resolvedBy}</span></div>}
                      </div>
                      {f.rawEventPayload && Object.keys(f.rawEventPayload).length > 0 && (
                        <details style={{ marginTop: 10 }}>
                          <summary style={{ fontSize: 11, color: "#475569", cursor: "pointer", fontFamily: "monospace" }}>Raw Event Payload</summary>
                          <pre style={{ background: "#0a0f1e", border: "1px solid #1e293b", borderRadius: 5, padding: 10, fontSize: 11, color: "#64748b", margin: "6px 0 0" }}>{JSON.stringify(f.rawEventPayload, null, 2)}</pre>
                        </details>
                      )}
                      {f.isResolved && f.resolutionNotes && (
                        <div style={{ marginTop: 8, fontSize: 12, color: "#34d39988", fontFamily: "monospace" }}>✓ {f.resolutionNotes}</div>
                      )}
                    </div>
                    {!f.isResolved && currentRole === "ADMIN" && (
                      <button onClick={() => resolveFailure(f.id)} style={{ ...btnSmStyle("#34d399"), padding: "8px 16px", fontSize: 12, flexShrink: 0 }}>✓ Resolve</button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* Modals */}
      <Modal open={!!selectedNotif} onClose={() => setSelectedNotif(null)} title={selectedNotif?.title || ""}>
        {selectedNotif && <NotifDetail n={selectedNotif} onClose={() => setSelectedNotif(null)} />}
      </Modal>

      <Modal open={showCreateNotif} onClose={() => setShowCreateNotif(false)} title="Create Notification — POST /api/v1/notifications">
        <CreateNotificationForm onSubmit={createNotification} onClose={() => setShowCreateNotif(false)} />
      </Modal>

      <Modal open={showCreateRule} onClose={() => setShowCreateRule(false)} title="Create Notification Rule">
        <CreateRuleForm onSubmit={createRule} onClose={() => setShowCreateRule(false)} />
      </Modal>

      {/* Toast */}
      {toast && (
        <div style={{ position: "fixed", bottom: 28, right: 28, background: "#0f172a", border: `1px solid ${toast.color}44`, borderLeft: `3px solid ${toast.color}`, borderRadius: 8, padding: "12px 20px", fontSize: 13, color: toast.color, fontWeight: 600, boxShadow: "0 10px 40px rgba(0,0,0,0.5)", animation: "slideIn 0.3s", zIndex: 2000, fontFamily: "'Syne', sans-serif" }}>
          {toast.msg}
        </div>
      )}
    </div>
  );
}
