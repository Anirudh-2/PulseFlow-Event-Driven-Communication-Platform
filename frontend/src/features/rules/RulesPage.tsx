import { useQuery } from "@tanstack/react-query";
import { getRules } from "../../api/client";
import type { NotificationRule } from "../../api/client";
import { Badge, DataTable, Skeleton, ErrorState } from "../../shared/components";
import type { Column } from "../../shared/components";
import { useTenantContext } from "../../app/TenantContext";

const columns: Column<NotificationRule>[] = [
  {
    key: "name",
    header: "Rule Name",
    render: (r) => (
      <div>
        <div style={{ fontWeight: 600, color: "var(--color-text-primary)" }}>{r.name}</div>
        <div style={{ fontSize: 11, color: "var(--color-text-muted)", marginTop: 2 }}>
          {r.eventType}
        </div>
      </div>
    ),
  },
  {
    key: "targetRole",
    header: "Role",
    render: (r) => (
      <code style={{ fontSize: 12, background: "var(--color-surface-alt)", padding: "2px 6px", borderRadius: 4 }}>
        {r.targetRole}
      </code>
    ),
  },
  {
    key: "notificationType",
    header: "Type",
    render: (r) => <Badge label={r.notificationType ?? "—"} />,
  },
  {
    key: "channels",
    header: "Channels",
    render: (r) => (
      <div style={{ display: "flex", gap: 4, flexWrap: "wrap" }}>
        {(r.channels ?? []).map((ch) => (
          <Badge key={ch} label={ch} />
        ))}
      </div>
    ),
  },
  {
    key: "priorityOverride",
    header: "Priority",
    render: (r) =>
      r.priorityOverride ? <Badge label={r.priorityOverride} /> : (
        <span style={{ color: "var(--color-text-muted)", fontSize: 12 }}>—</span>
      ),
  },
  {
    key: "evaluationOrder",
    header: "Order",
    render: (r) => (
      <span style={{ fontFamily: "var(--font-mono)", fontSize: 12 }}>
        {r.evaluationOrder}
      </span>
    ),
  },
  {
    key: "active",
    header: "Status",
    render: (r) => (
      <span
        style={{
          display: "inline-flex",
          alignItems: "center",
          gap: 5,
          fontSize: 12,
          fontWeight: 600,
          color: r.active ? "var(--color-success)" : "var(--color-text-muted)",
        }}
      >
        <span
          style={{
            width: 8,
            height: 8,
            borderRadius: "50%",
            background: r.active ? "var(--color-success)" : "var(--color-border)",
            display: "inline-block",
          }}
        />
        {r.active ? "Active" : "Inactive"}
      </span>
    ),
  },
];

export function RulesPage() {
  const { tenantId } = useTenantContext();
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["rules", tenantId],
    queryFn: () => getRules(tenantId),
  });

  if (isLoading) return <Skeleton type="row" count={6} />;

  if (isError) {
    return (
      <ErrorState
        message={(error as Error).message}
        onRetry={() => refetch()}
      />
    );
  }

  return (
    <>
      <div className="section-header">
        <div className="section-title">Notification Rules ({data?.length ?? 0})</div>
        <button className="btn btn-secondary btn-sm" onClick={() => refetch()}>
          ↻ Refresh
        </button>
      </div>
      <DataTable<NotificationRule>
        columns={columns}
        rows={data ?? []}
        keyField="id"
        emptyMessage="No rules configured. Add rules to start routing notifications."
      />
    </>
  );
}
