import { useMemo, useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { getNotifications, markNotificationRead } from "../../api/client";
import { StatCard, Skeleton, ErrorState, Badge } from "../../shared/components";
import { useTenantContext } from "../../app/TenantContext";

function formatDate(dt: string) {
  return new Date(dt).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function DashboardPage() {
  const qc = useQueryClient();
  const { tenantId, userId } = useTenantContext();
  const [filterStatus, setFilterStatus] = useState("ALL");
  const [filterPriority, setFilterPriority] = useState("ALL");

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["notifications", tenantId, userId],
    queryFn: () => getNotifications(tenantId, userId),
  });

  const markReadMutation = useMutation({
    mutationFn: (id: string) => markNotificationRead(tenantId, userId, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["notifications", tenantId, userId] }),
  });

  const stats = useMemo(() => {
    if (!data) return { total: 0, unread: 0, critical: 0, acknowledged: 0 };
    return {
      total: data.length,
      unread: data.filter((n) => n.status === "UNREAD" || n.status == null).length,
      critical: data.filter((n) => n.priority === "CRITICAL").length,
      acknowledged: data.filter((n) => n.status === "ACKNOWLEDGED").length,
    };
  }, [data]);

  const filtered = useMemo(() => {
    if (!data) return [];
    return data.filter((n) => {
      const rowStatus = n.status ?? "UNREAD";
      const statusOk = filterStatus === "ALL" || rowStatus === filterStatus;
      const priorOk = filterPriority === "ALL" || n.priority === filterPriority;
      return statusOk && priorOk;
    });
  }, [data, filterStatus, filterPriority]);

  if (isLoading) {
    return (
      <>
        <Skeleton type="stat" count={4} />
        <div style={{ marginTop: 24 }}>
          <Skeleton type="card" count={5} />
        </div>
      </>
    );
  }

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
      <div className="stat-grid">
        <StatCard label="Total" value={stats.total} icon="📊" accent="blue" />
        <StatCard label="Unread" value={stats.unread} icon="🔔" accent="orange" />
        <StatCard label="Critical" value={stats.critical} icon="🚨" accent="red" />
        <StatCard label="Acknowledged" value={stats.acknowledged} icon="✅" accent="green" />
      </div>

      <div className="filter-bar">
        <select
          className="filter-select"
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value)}
        >
          <option value="ALL">All statuses</option>
          <option value="UNREAD">Unread</option>
          <option value="READ">Read</option>
          <option value="ACKNOWLEDGED">Acknowledged</option>
        </select>
        <select
          className="filter-select"
          value={filterPriority}
          onChange={(e) => setFilterPriority(e.target.value)}
        >
          <option value="ALL">All Priorities</option>
          <option value="CRITICAL">Critical</option>
          <option value="HIGH">High</option>
          <option value="MEDIUM">Medium</option>
          <option value="LOW">Low</option>
        </select>
        <button className="btn btn-secondary btn-sm" onClick={() => refetch()}>
          ↻ Refresh
        </button>
        <span style={{ marginLeft: "auto", fontSize: 12, color: "var(--color-text-muted)" }}>
          Showing {filtered.length} of {data?.length ?? 0} notifications
        </span>
      </div>

      {filtered.length === 0 ? (
        <div className="error-state">
          <div className="error-state-icon">📭</div>
          <div className="error-state-title">No notifications</div>
          <div className="error-state-message">
            No notifications match the current filters.
          </div>
        </div>
      ) : (
        <div className="notif-list">
          {filtered.map((n) => (
            <div key={n.id} className={`notif-card priority-${n.priority?.toLowerCase()}`}>
              <div className="notif-card-header">
                <div className="notif-card-subject">
                  {n.title}
                  {n.eventType ? (
                    <span style={{ fontWeight: 400, color: "var(--color-text-muted)", marginLeft: 8 }}>
                      ({n.eventType})
                    </span>
                  ) : null}
                </div>
                <div className="notif-card-meta">
                  <Badge label={n.priority} />
                  <Badge label={n.status ?? "—"} />
                </div>
              </div>
              {n.body && <div className="notif-card-body">{n.body}</div>}
              <div className="notif-card-footer">
                <div className="notif-card-badges">
                  {n.type && <Badge label={n.type} />}
                  <span className="notif-card-time">{formatDate(n.createdAt)}</span>
                </div>
                {(n.status === "UNREAD" || n.status == null) && (
                  <button
                    className="btn btn-ghost btn-xs"
                    disabled={markReadMutation.isPending}
                    onClick={() => markReadMutation.mutate(n.id)}
                  >
                    Mark as Read
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
