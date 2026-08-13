import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createDbTemplate,
  createFieldMapping,
  createIntegrationSource,
  createRoutingRule,
  createTenantChannelConfig,
  deleteRoutingRule,
  getDbTemplates,
  getIntegrationSources,
  getRoutingRules,
  getTenantChannelConfigs,
  listFieldMappings,
  updateIntegrationSource,
} from "../../api/client";
import { ErrorState, Skeleton } from "../../shared/components";

type Props = { tenantId: string };

export function PlatformAdminPanel({ tenantId }: Props) {
  const qc = useQueryClient();
  const [newSourceKey, setNewSourceKey] = useState("");
  const [newSourceName, setNewSourceName] = useState("");
  const [newSourceWebhookKey, setNewSourceWebhookKey] = useState("");
  const [mappingSourceId, setMappingSourceId] = useState("");
  const [mappingJson, setMappingJson] = useState('{"externalEventType":"eventType"}');
  const [newChannelType, setNewChannelType] = useState("EMAIL");
  const [newChannelName, setNewChannelName] = useState("");
  const [newChannelJson, setNewChannelJson] = useState("{}");
  const [newRouteName, setNewRouteName] = useState("");
  const [newRouteEvent, setNewRouteEvent] = useState("");
  const [newRouteChannels, setNewRouteChannels] = useState("WEBSOCKET");
  const [newTplEvent, setNewTplEvent] = useState("");
  const [newTplChannel, setNewTplChannel] = useState("EMAIL");
  const [newTplBody, setNewTplBody] = useState("Hello {{userId}} — {{eventType}}");

  const sourcesQ = useQuery({ queryKey: ["integration-sources", tenantId], queryFn: () => getIntegrationSources(tenantId) });
  const channelsQ = useQuery({ queryKey: ["channel-configs", tenantId], queryFn: () => getTenantChannelConfigs(tenantId) });
  const routingQ = useQuery({ queryKey: ["routing-rules", tenantId], queryFn: () => getRoutingRules(tenantId) });
  const tplQ = useQuery({ queryKey: ["db-templates", tenantId], queryFn: () => getDbTemplates(tenantId) });
  const fieldMapQ = useQuery({
    queryKey: ["field-mappings", tenantId, mappingSourceId],
    queryFn: () => listFieldMappings(tenantId, mappingSourceId),
    enabled: Boolean(mappingSourceId),
  });

  const invalidateAll = () => {
    qc.invalidateQueries({ queryKey: ["integration-sources", tenantId] });
    qc.invalidateQueries({ queryKey: ["channel-configs", tenantId] });
    qc.invalidateQueries({ queryKey: ["routing-rules", tenantId] });
    qc.invalidateQueries({ queryKey: ["db-templates", tenantId] });
    qc.invalidateQueries({ queryKey: ["field-mappings", tenantId] });
  };

  const createSourceM = useMutation({
    mutationFn: () =>
      createIntegrationSource(tenantId, {
        sourceKey: newSourceKey.toUpperCase(),
        displayName: newSourceName || newSourceKey,
        isActive: true,
        ...(newSourceWebhookKey.trim() ? { webhookApiKey: newSourceWebhookKey.trim() } : {}),
      }),
    onSuccess: () => {
      invalidateAll();
      setNewSourceKey("");
      setNewSourceName("");
      setNewSourceWebhookKey("");
    },
  });

  const toggleSourceM = useMutation({
    mutationFn: ({ id, isActive }: { id: string; isActive: boolean }) =>
      updateIntegrationSource(tenantId, id, { isActive }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["integration-sources", tenantId] }),
  });

  const createMappingM = useMutation({
    mutationFn: async () => {
      const mapping = JSON.parse(mappingJson || "{}") as Record<string, unknown>;
      return createFieldMapping(tenantId, mappingSourceId, { version: 1, mapping });
    },
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["field-mappings", tenantId, mappingSourceId] }),
  });

  const createChannelM = useMutation({
    mutationFn: () => {
      let config: Record<string, unknown> = {};
      try {
        config = JSON.parse(newChannelJson || "{}") as Record<string, unknown>;
      } catch {
        config = {};
      }
      return createTenantChannelConfig(tenantId, {
        channelTypeCode: newChannelType,
        name: newChannelName || newChannelType,
        config,
        priority: 100,
        isDefault: true,
        isEnabled: true,
      });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["channel-configs", tenantId] });
      setNewChannelJson("{}");
    },
  });

  const createRouteM = useMutation({
    mutationFn: () =>
      createRoutingRule(tenantId, {
        name: newRouteName,
        eventType: newRouteEvent || null,
        roleName: null,
        channelTypeCodes: newRouteChannels.split(",").map((s) => s.trim().toUpperCase()).filter(Boolean),
        evalOrder: 100,
        isActive: true,
        conditionsJsonlogic: {},
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["routing-rules", tenantId] });
      setNewRouteName("");
      setNewRouteEvent("");
    },
  });

  const deleteRouteM = useMutation({
    mutationFn: (id: string) => deleteRoutingRule(tenantId, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["routing-rules", tenantId] }),
  });

  const createTplM = useMutation({
    mutationFn: () =>
      createDbTemplate(tenantId, {
        eventType: newTplEvent,
        channelTypeCode: newTplChannel,
        locale: "en",
        subjectTemplate: "{{eventType}}",
        bodyTemplate: newTplBody,
        contentType: "text",
        isActive: true,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["db-templates", tenantId] });
      setNewTplEvent("");
    },
  });

  if (sourcesQ.isLoading || channelsQ.isLoading || routingQ.isLoading || tplQ.isLoading) {
    return <Skeleton type="card" count={5} />;
  }
  if (sourcesQ.isError) {
    return <ErrorState message={(sourcesQ.error as Error).message} onRetry={() => sourcesQ.refetch()} />;
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
      <div className="card">
        <div className="card-header"><div className="card-title">Integration sources</div></div>
        <p style={{ fontSize: 13, color: "var(--color-muted)", marginBottom: 12 }}>
          Register external systems. Webhook: <code>/api/v1/integrations/&#123;sourceKey&#125;/webhook</code> with{" "}
          <code>X-Webhook-Api-Key</code> and <code>X-Tenant-Id</code> when not using the default tenant. Per-source keys
          are stored as SHA-256; leave empty to use the global integration key.
        </p>
        <div className="filter-bar" style={{ flexWrap: "wrap" }}>
          <input className="filter-input" placeholder="Source key (e.g. TMS)" value={newSourceKey} onChange={(e) => setNewSourceKey(e.target.value)} />
          <input className="filter-input" placeholder="Display name" value={newSourceName} onChange={(e) => setNewSourceName(e.target.value)} />
          <input
            className="filter-input"
            type="password"
            autoComplete="new-password"
            placeholder="Webhook API key (optional)"
            value={newSourceWebhookKey}
            onChange={(e) => setNewSourceWebhookKey(e.target.value)}
          />
          <button className="btn btn-primary btn-sm" disabled={!newSourceKey.trim() || createSourceM.isPending} onClick={() => createSourceM.mutate()}>Add</button>
        </div>
        <ul style={{ marginTop: 12, paddingLeft: 18 }}>
          {(sourcesQ.data ?? []).map((s) => (
            <li key={s.id} style={{ marginBottom: 6 }}>
              <strong>{s.sourceKey}</strong> — {s.displayName}{" "}
              {s.isActive ? "" : "(inactive)"}{" "}
              {s.webhookKeyConfigured ? <span style={{ color: "var(--color-text-muted)" }}>[webhook key]</span> : null}{" "}
              <button
                type="button"
                className="btn btn-ghost btn-xs"
                disabled={toggleSourceM.isPending}
                onClick={() => toggleSourceM.mutate({ id: s.id, isActive: !s.isActive })}
              >
                {s.isActive ? "Deactivate" : "Activate"}
              </button>
            </li>
          ))}
        </ul>
      </div>

      <div className="card">
        <div className="card-header"><div className="card-title">Field mappings</div></div>
        <p style={{ fontSize: 13, color: "var(--color-muted)", marginBottom: 12 }}>
          JSON map from external payload paths to normalized PulseFlow field names (used during ingest). Pick a source, edit the
          mapping object, then add a version.
        </p>
        <div className="filter-bar" style={{ flexWrap: "wrap" }}>
          <select
            className="filter-input"
            value={mappingSourceId}
            onChange={(e) => setMappingSourceId(e.target.value)}
          >
            <option value="">Select integration source…</option>
            {(sourcesQ.data ?? []).map((s) => (
              <option key={s.id} value={s.id}>{s.sourceKey}</option>
            ))}
          </select>
        </div>
        {mappingSourceId ? (
          <>
            <textarea
              className="filter-input"
              style={{ width: "100%", minHeight: 120, marginTop: 12, fontFamily: "monospace", fontSize: 12 }}
              value={mappingJson}
              onChange={(e) => setMappingJson(e.target.value)}
            />
            <button
              type="button"
              className="btn btn-primary btn-sm"
              style={{ marginTop: 8 }}
              disabled={createMappingM.isPending}
              onClick={() => createMappingM.mutate()}
            >
              Save mapping version
            </button>
            {fieldMapQ.isLoading ? (
              <p style={{ marginTop: 12, fontSize: 13 }}>Loading versions…</p>
            ) : (
              <ul style={{ marginTop: 12, paddingLeft: 18 }}>
                {(fieldMapQ.data ?? []).map((m) => (
                  <li key={m.id}>
                    v{m.version} {m.isActive ? "" : "(inactive)"} — {JSON.stringify(m.mapping).slice(0, 80)}
                    {JSON.stringify(m.mapping).length > 80 ? "…" : ""}
                  </li>
                ))}
              </ul>
            )}
          </>
        ) : null}
      </div>

      <div className="card">
        <div className="card-header"><div className="card-title">Tenant channel configs</div></div>
        <div className="filter-bar">
          <select className="filter-input" value={newChannelType} onChange={(e) => setNewChannelType(e.target.value)}>
            {["EMAIL", "TEAMS", "TELEGRAM", "WHATSAPP", "WEBHOOK", "WEBSOCKET"].map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
          <input className="filter-input" placeholder="Name" value={newChannelName} onChange={(e) => setNewChannelName(e.target.value)} />
          <input className="filter-input" style={{ flex: 2 }} placeholder='Config JSON e.g. {"enabled":true,"host":"smtp..."}' value={newChannelJson} onChange={(e) => setNewChannelJson(e.target.value)} />
          <button className="btn btn-primary btn-sm" disabled={createChannelM.isPending} onClick={() => createChannelM.mutate()}>Add</button>
        </div>
        <ul style={{ marginTop: 12, paddingLeft: 18 }}>
          {(channelsQ.data ?? []).map((c) => (
            <li key={c.id}>{c.channelType?.code ?? "?"} — {c.name} (priority {c.priority})</li>
          ))}
        </ul>
      </div>

      <div className="card">
        <div className="card-header"><div className="card-title">Routing rules (DB)</div></div>
        <div className="filter-bar">
          <input className="filter-input" placeholder="Rule name" value={newRouteName} onChange={(e) => setNewRouteName(e.target.value)} />
          <input className="filter-input" placeholder="Event type (optional)" value={newRouteEvent} onChange={(e) => setNewRouteEvent(e.target.value)} />
          <input className="filter-input" placeholder="Channels comma-separated" value={newRouteChannels} onChange={(e) => setNewRouteChannels(e.target.value)} />
          <button className="btn btn-primary btn-sm" disabled={!newRouteName.trim() || createRouteM.isPending} onClick={() => createRouteM.mutate()}>Add</button>
        </div>
        <div className="table-wrapper" style={{ marginTop: 12 }}>
          <table className="data-table">
            <thead><tr><th>Name</th><th>Event</th><th>Channels</th><th /></tr></thead>
            <tbody>
              {(routingQ.data ?? []).map((r) => (
                <tr key={r.id}>
                  <td>{r.name}</td>
                  <td>{r.eventType ?? "—"}</td>
                  <td>{(r.channelTypeCodes ?? []).join(", ")}</td>
                  <td><button type="button" className="btn btn-danger btn-xs" disabled={deleteRouteM.isPending} onClick={() => deleteRouteM.mutate(r.id)}>Delete</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="card">
        <div className="card-header"><div className="card-title">DB notification templates</div></div>
        <div className="filter-bar">
          <input className="filter-input" placeholder="Event type" value={newTplEvent} onChange={(e) => setNewTplEvent(e.target.value)} />
          <select className="filter-input" value={newTplChannel} onChange={(e) => setNewTplChannel(e.target.value)}>
            {["EMAIL", "TEAMS", "TELEGRAM", "WHATSAPP", "WEBSOCKET", "WEBHOOK"].map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
          <input className="filter-input" style={{ flex: 2 }} placeholder="Body Mustache template" value={newTplBody} onChange={(e) => setNewTplBody(e.target.value)} />
          <button className="btn btn-primary btn-sm" disabled={!newTplEvent.trim() || createTplM.isPending} onClick={() => createTplM.mutate()}>Add</button>
        </div>
        <ul style={{ marginTop: 12, paddingLeft: 18 }}>
          {(tplQ.data ?? []).map((t) => (
            <li key={t.id}>{t.eventType} / {t.channelType?.code} — {t.bodyTemplate.slice(0, 60)}…</li>
          ))}
        </ul>
      </div>
    </div>
  );
}
