import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { getAuditLogs } from "../../api/client";
import type { AuditLog } from "../../api/client";
import { Badge, Skeleton, ErrorState } from "../../shared/components";
import { useTenantContext } from "../../app/TenantContext";

function formatDate(dt: string) {
  return new Date(dt).toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

function MetadataCell({ metadata }: { metadata?: Record<string, unknown> }) {
  const [open, setOpen] = useState(false);
  if (!metadata || Object.keys(metadata).length === 0) {
    return <span style={{ color: "var(--color-text-muted)", fontSize: 12 }}>—</span>;
  }
  return (
    <div>
      <button className="json-toggle" onClick={() => setOpen(!open)}>
        {open ? "▲ hide" : "▼ show"}
      </button>
      {open && (
        <pre className="json-cell" style={{ marginTop: 6 }}>
          {JSON.stringify(metadata, null, 2)}
        </pre>
      )}
    </div>
  );
}

export function AuditLogsPage() {
  const { tenantId } = useTenantContext();
  const [page, setPage] = useState(0);
  const pageSize = 20;

  useEffect(() => {
    setPage(0);
  }, [tenantId]);

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["audit-logs", tenantId, page, pageSize],
    queryFn: () => getAuditLogs(tenantId, page, pageSize),
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

  const rows: AuditLog[] = data?.content ?? [];

  return (
    <>
      <div className="section-header">
        <div className="section-title">Audit Trail ({data?.totalElements ?? 0})</div>
        <button className="btn btn-secondary btn-sm" onClick={() => refetch()}>
          ↻ Refresh
        </button>
      </div>

      {rows.length === 0 ? (
        <div className="error-state">
          <div className="error-state-icon">📋</div>
          <div className="error-state-title">No audit records</div>
          <div className="error-state-message">
            Audit events will appear here as notifications are processed.
          </div>
        </div>
      ) : (
        <>
          <div className="table-wrapper">
            <table className="data-table">
            <thead>
              <tr>
                <th style={{ width: 180 }}>Time</th>
                <th style={{ width: 180 }}>Action</th>
                <th style={{ width: 160 }}>Actor</th>
                <th style={{ width: 160 }}>Correlation ID</th>
                <th>Metadata</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row, idx) => (
                <tr
                  key={row.id ?? idx}
                  style={{
                    borderLeft: idx === 0 ? "3px solid var(--color-accent)" : undefined,
                  }}
                >
                  <td>
                    <span
                      style={{
                        fontSize: 12,
                        fontFamily: "var(--font-mono)",
                        color: "var(--color-text-secondary)",
                      }}
                    >
                      {formatDate(row.occurredAt)}
                    </span>
                  </td>
                  <td>
                    <Badge label={row.action} />
                  </td>
                  <td>
                    <span style={{ fontSize: 12, color: "var(--color-text-secondary)" }}>
                      {row.actorUserId ?? <em style={{ color: "var(--color-text-muted)" }}>system</em>}
                    </span>
                  </td>
                  <td>
                    {row.correlationId ? (
                      <code
                        style={{
                          fontSize: 11,
                          color: "var(--color-text-muted)",
                          background: "var(--color-surface-alt)",
                          padding: "2px 5px",
                          borderRadius: 4,
                        }}
                      >
                        {row.correlationId.slice(0, 8)}…
                      </code>
                    ) : (
                      <span style={{ color: "var(--color-text-muted)" }}>—</span>
                    )}
                  </td>
                  <td>
                    <MetadataCell metadata={row.metadata} />
                  </td>
                </tr>
              ))}
            </tbody>
            </table>
          </div>
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
        </>
      )}
    </>
  );
}
