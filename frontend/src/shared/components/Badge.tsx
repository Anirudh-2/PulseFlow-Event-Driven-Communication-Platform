type BadgeVariant =
  | "priority-critical"
  | "priority-high"
  | "priority-medium"
  | "priority-low"
  | "status-pending"
  | "status-sent"
  | "status-delivered"
  | "status-failed"
  | "status-skipped"
  | "status-read"
  | "status-acknowledged"
  | "channel-email"
  | "channel-sms"
  | "channel-push"
  | "channel-teams"
  | "channel-whatsapp"
  | "channel-websocket"
  | "type-system"
  | "type-hr"
  | "type-payroll"
  | "type-alert"
  | "action-created"
  | "action-sent"
  | "action-read"
  | "action-failed"
  | "action-acknowledged"
  | "default";

interface BadgeProps {
  label: string;
  variant?: BadgeVariant;
  className?: string;
}

function resolveVariant(label: string): BadgeVariant {
  const l = label?.toLowerCase() ?? "";
  if (l === "critical") return "priority-critical";
  if (l === "high") return "priority-high";
  if (l === "medium") return "priority-medium";
  if (l === "low") return "priority-low";
  if (l === "pending") return "status-pending";
  if (l === "sent") return "status-sent";
  if (l === "delivered") return "status-delivered";
  if (l === "failed") return "status-failed";
  if (l === "skipped") return "status-skipped";
  if (l === "read") return "status-read";
  if (l === "acknowledged") return "status-acknowledged";
  if (l === "email") return "channel-email";
  if (l === "sms") return "channel-sms";
  if (l === "push") return "channel-push";
  if (l === "teams") return "channel-teams";
  if (l === "whatsapp") return "channel-whatsapp";
  if (l === "websocket") return "channel-websocket";
  if (l === "system") return "type-system";
  if (l === "hr") return "type-hr";
  if (l === "payroll") return "type-payroll";
  if (l === "alert") return "type-alert";
  if (l === "created") return "action-created";
  return "default";
}

export const Badge: React.FC<BadgeProps> = ({ label, variant, className }) => {
  const v = variant ?? resolveVariant(label);
  return (
    <span className={`badge badge-${v} ${className ?? ""}`}>
      {label}
    </span>
  );
};
