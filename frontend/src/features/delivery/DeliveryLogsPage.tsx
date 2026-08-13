import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { getDeliveryLogs, getDeliveryLogsByNotificationId } from "../../api/client";
import type { DeliveryLog } from "../../api/client";
import { Badge, DataTable, Skeleton, ErrorState } from "../../shared/components";
import type { Column } from "../../shared/components";
import { useTenantContext } from "../../app/TenantContext";

function formatDate(dt?: string) {
  if (!dt) return "—";
  return new Date(dt).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function ErrorCell({ message }: { message?: string }) {
  const [expanded, setExpanded] = useState(false);
  if (!message) return <span style={{ color: "var(--color-text-muted)" }}>—</span>;
  const short = message.length > 60 ? message.slice(0, 60) + "…" : message;
  return (
    <div>
      <span style={{ fontSize: 12, color: "var(--color-danger)" }}>
        {expanded ? message : short}
      </span>
      {message.length > 60 && (
        <button
          className="btn btn-ghost btn-xs"
          style={{ marginLeft: 4 }}
          onClick={() => setExpanded(!expanded)}
        >
          {expanded ? "less" : "more"}
        </button>
      )}
    </div>
  );
}

export function DeliveryLogsPage() {
  const { tenantId } = useTenantContext();
  const [page, setPage] = useState(0);
  const [selectedNotificationId, setSelectedNotificationId] = useState<string | null>(null);
  const pageSize = 20;

  const columns: Column<DeliveryLog>[] = [
    {
      key: "createdAt",
      header: "Time",
      render: (r) => (
        <span style={{ fontSize: 12, fontFamily: "var(--font-mono)", color: "var(--color-text-secondary)" }}>
          {formatDate(r.createdAt)}
        </span>
      ),
      width: "160px",
    },
    {
      key: "notificationId",
      header: "Notification ID",
      render: (r) => (
        <button className="btn btn-ghost btn-xs" onClick={() => setSelectedNotificationId(r.notificationId)}>
          <code style={{ fontSize: 11, color: "var(--color-text-muted)" }}>
            {r.notificationId?.slice(0, 8)}…
          </code>
        </button>
      ),
      width: "120px",
    },
    {
      key: "channel",
      header: "Channel",
      render: (r) => <Badge label={r.channel} />,
      width: "130px",
    },
    {
      key: "status",
      header: "Status",
      render: (r) => <Badge label={r.status} />,
      width: "130px",
    },
    {
      key: "attemptCount",
      header: "Attempts",
      render: (r) => (
        <span style={{ fontFamily: "var(--font-mono)", fontSize: 13 }}>
          {r.attemptCount ?? 0}
        </span>
      ),
      width: "90px",
    },
    {
      key: "deliveredAt",
      header: "Delivered At",
      render: (r) => (
        <span style={{ fontSize: 12, color: "var(--color-text-secondary)" }}>
          {formatDate(r.deliveredAt)}
        </span>
      ),
      width: "160px",
    },
    {
      key: "errorMessage",
      header: "Error",
      render: (r) => <ErrorCell message={r.errorMessage} />,
    },
  ];

  useEffect(() => {
    setPage(0);
  }, [tenantId]);

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["delivery-logs", tenantId, page, pageSize],
    queryFn: () => getDeliveryLogs(tenantId, page, pageSize),
  });

  const detailsQuery = useQuery({
    queryKey: ["delivery-logs-detail", tenantId, selectedNotificationId],
    queryFn: () => getDeliveryLogsByNotificationId(tenantId, selectedNotificationId!),
    enabled: !!selectedNotificationId,
  });

  if (isLoading) return <Skeleton type="row" count={8} />;

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
        <div className="section-title">Delivery Logs ({data?.totalElements ?? 0})</div>
        <button className="btn btn-secondary btn-sm" onClick={() => refetch()}>
          ↻ Refresh
        </button>
      </div>
      <DataTable<DeliveryLog>
        columns={columns}
        rows={data?.content ?? []}
        keyField="id"
        emptyMessage="No delivery records yet. Send a notification to see logs here."
      />
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 16 }}>
        <span style={{ color: "var(--color-text-secondary)" }}>
          Page {(data?.page ?? 0) + 1} of {data?.totalPages ?? 1} ({data?.totalElements ?? 0} total)
        </span>
        <div style={{ display: "flex", gap: 8 }}>
          <button
            className="btn btn-secondary btn-sm"
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={data?.first ?? true}
          >
            Previous
          </button>
          <button className="btn btn-secondary btn-sm" onClick={() => setPage((p) => p + 1)} disabled={data?.last ?? true}>
            Next
          </button>
        </div>
      </div>
      {selectedNotificationId ? (
        <div className="delivery-detail-overlay" onClick={() => setSelectedNotificationId(null)}>
          <div className="delivery-detail-panel" onClick={(e) => e.stopPropagation()}>
            <div className="section-header">
              <div className="section-title">Delivery Attempts: {selectedNotificationId.slice(0, 12)}...</div>
              <button className="btn btn-secondary btn-sm" onClick={() => setSelectedNotificationId(null)}>
                Close
              </button>
            </div>
            {detailsQuery.isLoading ? (
              <Skeleton type="row" count={4} />
            ) : (
              <DataTable<DeliveryLog>
                columns={columns}
                rows={detailsQuery.data ?? []}
                keyField="id"
                emptyMessage="No detailed delivery rows for this notification."
              />
            )}
          </div>
        </div>
      ) : null}
    </>
  );
}
