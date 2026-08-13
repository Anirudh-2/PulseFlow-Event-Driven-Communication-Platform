import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createChannelConfiguration,
  deleteChannelConfiguration,
  getIntegrationSources,
  listChannelConfigurations,
  testChannelConfiguration,
  updateChannelConfiguration,
} from "../../api/client";
import { ErrorState, Skeleton } from "../../shared/components";
import { useTenantContext } from "../../app/TenantContext";

const CHANNEL_OPTIONS = ["teams", "whatsapp", "telegram", "smtp", "webhook"] as const;

export function ChannelsPage() {
  const { tenantId } = useTenantContext();
  const qc = useQueryClient();
  const [appId, setAppId] = useState("");
  const [channelType, setChannelType] = useState<(typeof CHANNEL_OPTIONS)[number]>("teams");
  const [configJson, setConfigJson] = useState("{}");
  const [isActive, setIsActive] = useState(true);

  const sourcesQ = useQuery({
    queryKey: ["integration-sources", tenantId],
    queryFn: () => getIntegrationSources(tenantId),
  });
  const configsQ = useQuery({
    queryKey: ["channel-configurations", tenantId],
    queryFn: () => listChannelConfigurations(tenantId),
  });

  const invalidate = () => qc.invalidateQueries({ queryKey: ["channel-configurations", tenantId] });

  const createM = useMutation({
    mutationFn: async () =>
      createChannelConfiguration(tenantId, {
        appId,
        channelType,
        configJson: JSON.parse(configJson || "{}") as Record<string, unknown>,
        isActive,
      }),
    onSuccess: () => {
      invalidate();
      setConfigJson("{}");
    },
  });

  const toggleM = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) =>
      updateChannelConfiguration(tenantId, id, { isActive: active }),
    onSuccess: invalidate,
  });

  const deleteM = useMutation({
    mutationFn: (id: string) => deleteChannelConfiguration(tenantId, id),
    onSuccess: invalidate,
  });

  const testM = useMutation({
    mutationFn: (id: string) => testChannelConfiguration(tenantId, id),
  });

  const canCreate = useMemo(() => Boolean(appId) && Boolean(channelType), [appId, channelType]);

  if (sourcesQ.isLoading || configsQ.isLoading) return <Skeleton type="card" count={4} />;
  if (sourcesQ.isError) return <ErrorState message={(sourcesQ.error as Error).message} onRetry={() => sourcesQ.refetch()} />;
  if (configsQ.isError) return <ErrorState message={(configsQ.error as Error).message} onRetry={() => configsQ.refetch()} />;

  return (
    <>
      <div className="section-header">
        <div className="section-title">Channels ({tenantId})</div>
      </div>

      <div className="card">
        <div className="card-header"><div className="card-title">Add Channel Configuration</div></div>
        <div className="filter-bar" style={{ flexWrap: "wrap" }}>
          <select className="filter-input" value={appId} onChange={(e) => setAppId(e.target.value)}>
            <option value="">Select app/integration source…</option>
            {(sourcesQ.data ?? []).map((s) => (
              <option key={s.id} value={s.id}>{s.sourceKey} - {s.displayName}</option>
            ))}
          </select>
          <select className="filter-input" value={channelType} onChange={(e) => setChannelType(e.target.value as (typeof CHANNEL_OPTIONS)[number])}>
            {CHANNEL_OPTIONS.map((c) => <option key={c} value={c}>{c}</option>)}
          </select>
          <label style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
            <input type="checkbox" checked={isActive} onChange={(e) => setIsActive(e.target.checked)} />
            Active
          </label>
        </div>
        <textarea
          className="filter-input"
          style={{ width: "100%", minHeight: 120, marginTop: 12, fontFamily: "monospace", fontSize: 12 }}
          value={configJson}
          onChange={(e) => setConfigJson(e.target.value)}
          placeholder='Example: teams {"webhook_url":"https://..."} | telegram {"bot_token":"...","chat_id":"...","parseMode":"Markdown"} | smtp {"host":"...","port":587,"username":"...","password":"...","from":"..."} | webhook {"url":"https://...","authType":"API_KEY","apiKeyHeader":"X-Api-Key","apiKey":"..."} | whatsapp {"accountSid":"...","authToken":"...","whatsappFrom":"whatsapp:+1..."}'
        />
        <button className="btn btn-primary btn-sm" style={{ marginTop: 8 }} disabled={!canCreate || createM.isPending} onClick={() => createM.mutate()}>
          Add Channel Config
        </button>
      </div>

      <div className="table-wrapper" style={{ marginTop: 16 }}>
        <table className="data-table">
          <thead>
            <tr>
              <th>App</th>
              <th>Channel</th>
              <th>Active</th>
              <th>Config</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {(configsQ.data ?? []).length === 0 ? (
              <tr><td colSpan={5} className="table-empty">No channel configurations yet.</td></tr>
            ) : (
              (configsQ.data ?? []).map((row) => (
                <tr key={row.id}>
                  <td>{row.app?.sourceKey ?? "?"}</td>
                  <td>{row.channelType}</td>
                  <td>{row.isActive ? "Yes" : "No"}</td>
                  <td><code style={{ fontSize: 11 }}>{JSON.stringify(row.configJson).slice(0, 80)}{JSON.stringify(row.configJson).length > 80 ? "…" : ""}</code></td>
                  <td>
                    <div style={{ display: "flex", gap: 6 }}>
                      <button
                        type="button"
                        className="btn btn-ghost btn-xs"
                        disabled={toggleM.isPending}
                        onClick={() => toggleM.mutate({ id: row.id, active: !row.isActive })}
                      >
                        {row.isActive ? "Disable" : "Enable"}
                      </button>
                      <button
                        type="button"
                        className="btn btn-secondary btn-xs"
                        disabled={testM.isPending}
                        onClick={async () => {
                          const result = await testM.mutateAsync(row.id);
                          window.alert(result.message);
                        }}
                      >
                        Test connection
                      </button>
                      <button
                        type="button"
                        className="btn btn-danger btn-xs"
                        disabled={deleteM.isPending}
                        onClick={() => deleteM.mutate(row.id)}
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}
