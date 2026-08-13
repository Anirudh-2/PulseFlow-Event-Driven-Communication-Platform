import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createDbTemplate, deleteDbTemplate, getDbTemplates, updateDbTemplate } from "../../api/client";
import type { DbTemplateRow } from "../../api/client";
import { useTenantContext } from "../../app/TenantContext";
import { ErrorState, Skeleton } from "../../shared/components";

type TemplateForm = {
  id?: string;
  eventType: string;
  channelTypeCode: string;
  subjectTemplate: string;
  bodyTemplate: string;
  locale: string;
  isActive: boolean;
};

const CHANNEL_OPTIONS = ["EMAIL", "TEAMS", "TELEGRAM", "WHATSAPP", "WEBHOOK", "WEBSOCKET"];

const DEFAULT_FORM: TemplateForm = {
  eventType: "",
  channelTypeCode: "EMAIL",
  subjectTemplate: "",
  bodyTemplate: "",
  locale: "en",
  isActive: true,
};

function keyPathValue(source: unknown, path: string): unknown {
  return path.split(".").reduce<unknown>((acc, segment) => {
    if (acc && typeof acc === "object" && segment in (acc as Record<string, unknown>)) {
      return (acc as Record<string, unknown>)[segment];
    }
    return "";
  }, source);
}

function renderTemplate(template: string, payload: unknown): string {
  return template.replace(/\{\{\s*([a-zA-Z0-9_.]+)\s*\}\}/g, (_, key: string) => {
    const value = keyPathValue(payload, key);
    return value == null ? "" : String(value);
  });
}

function resolveVersion(template: DbTemplateRow): string {
  if (typeof template.templateVersion === "number") {
    return `v${template.templateVersion}`;
  }
  if (typeof template.template_version === "number") {
    return `v${template.template_version}`;
  }
  if (template.id) {
    return `id:${template.id.slice(0, 6)}`;
  }
  return "N/A";
}

export function TemplatesPage() {
  const { tenantId } = useTenantContext();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<TemplateForm>(DEFAULT_FORM);
  const [samplePayload, setSamplePayload] = useState('{\n  "userId": "u-123",\n  "eventType": "LEAVE_APPROVED"\n}');
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const templatesQuery = useQuery({
    queryKey: ["db-templates", tenantId],
    queryFn: () => getDbTemplates(tenantId),
  });

  const createMutation = useMutation({
    mutationFn: () =>
      createDbTemplate(tenantId, {
        eventType: form.eventType.trim(),
        channelTypeCode: form.channelTypeCode,
        locale: form.locale.trim() || "en",
        subjectTemplate: form.subjectTemplate,
        bodyTemplate: form.bodyTemplate,
        contentType: "text",
        isActive: form.isActive,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["db-templates", tenantId] });
      setForm(DEFAULT_FORM);
      setSelectedId(null);
    },
  });

  const updateMutation = useMutation({
    mutationFn: () =>
      updateDbTemplate(tenantId, form.id!, {
        eventType: form.eventType.trim(),
        channelTypeCode: form.channelTypeCode,
        locale: form.locale.trim() || "en",
        subjectTemplate: form.subjectTemplate,
        bodyTemplate: form.bodyTemplate,
        contentType: "text",
        isActive: form.isActive,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["db-templates", tenantId] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteDbTemplate(tenantId, id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["db-templates", tenantId] });
      setSelectedId(null);
      setForm(DEFAULT_FORM);
    },
  });

  const grouped = useMemo(() => {
    const items = templatesQuery.data ?? [];
    const map = new Map<string, DbTemplateRow[]>();
    for (const item of items) {
      const channel = item.channelType?.code ?? "UNKNOWN";
      const key = `${item.eventType}::${channel}`;
      if (!map.has(key)) {
        map.set(key, []);
      }
      map.get(key)!.push(item);
    }
    return Array.from(map.entries())
      .map(([key, templates]) => {
        const [eventType, channel] = key.split("::");
        templates.sort((a, b) => {
          if (a.isActive !== b.isActive) {
            return a.isActive ? -1 : 1;
          }
          return a.locale.localeCompare(b.locale);
        });
        return { key, eventType, channel, templates };
      })
      .sort((a, b) => a.key.localeCompare(b.key));
  }, [templatesQuery.data]);

  const parsedPayload = useMemo(() => {
    try {
      return { value: JSON.parse(samplePayload) as unknown, error: "" };
    } catch (error) {
      return { value: {}, error: (error as Error).message };
    }
  }, [samplePayload]);

  const previewSubject = useMemo(
    () => renderTemplate(form.subjectTemplate || "{{eventType}}", parsedPayload.value),
    [form.subjectTemplate, parsedPayload.value]
  );
  const previewBody = useMemo(
    () => renderTemplate(form.bodyTemplate || "", parsedPayload.value),
    [form.bodyTemplate, parsedPayload.value]
  );

  const variableTokens = useMemo(() => {
    const matches = form.bodyTemplate.match(/\{\{\s*[a-zA-Z0-9_.]+\s*\}\}/g) ?? [];
    return Array.from(new Set(matches));
  }, [form.bodyTemplate]);

  if (templatesQuery.isLoading) {
    return <Skeleton type="card" count={2} />;
  }
  if (templatesQuery.isError) {
    return <ErrorState message={(templatesQuery.error as Error).message} onRetry={() => templatesQuery.refetch()} />;
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
      <div className="section-header">
        <div className="section-title">Template Library ({tenantId})</div>
        <div style={{ display: "flex", gap: 8 }}>
          <button
            className="btn btn-secondary btn-sm"
            onClick={() => {
              setSelectedId(null);
              setForm(DEFAULT_FORM);
            }}
          >
            New Template
          </button>
        </div>
      </div>

      <div className="templates-layout">
        <div className="card">
          <div className="card-header">
            <div className="card-title">Templates by Event + Channel</div>
          </div>
          <div className="templates-groups">
            {grouped.length === 0 ? (
              <div className="table-empty">No templates found</div>
            ) : (
              grouped.map((group) => (
                <div key={group.key} className="templates-group-card">
                  <div className="templates-group-header">
                    <strong>{group.eventType}</strong>
                    <span className="badge badge-default">{group.channel}</span>
                    <span className="badge badge-default">{group.templates.length} template(s)</span>
                  </div>
                  <div>
                    {group.templates.map((tpl) => (
                      <div key={tpl.id} className="templates-row-wrap">
                        <button
                          type="button"
                          className={`templates-row ${selectedId === tpl.id ? "selected" : ""}`}
                          onClick={() => {
                            setSelectedId(tpl.id);
                            setForm({
                              id: tpl.id,
                              eventType: tpl.eventType,
                              channelTypeCode: tpl.channelType?.code ?? "EMAIL",
                              subjectTemplate: tpl.subjectTemplate ?? "",
                              bodyTemplate: tpl.bodyTemplate,
                              locale: tpl.locale,
                              isActive: tpl.isActive,
                            });
                          }}
                        >
                          <span>{tpl.locale}</span>
                          <span className={`badge ${tpl.isActive ? "badge-status-delivered" : "badge-status-failed"}`}>
                            {tpl.isActive ? "ACTIVE" : "INACTIVE"}
                          </span>
                          <span className="badge badge-default">{resolveVersion(tpl)}</span>
                        </button>
                        <button
                          className="btn btn-danger btn-xs"
                          disabled={deleteMutation.isPending}
                          onClick={() => {
                            if (!window.confirm("Delete this template?")) return;
                            deleteMutation.mutate(tpl.id);
                          }}
                        >
                          Delete
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="templates-side">
          <div className="card">
            <div className="card-header">
              <div className="card-title">{form.id ? "Edit Template" : "Create Template"}</div>
            </div>
            <div className="templates-form-grid">
              <input
                className="filter-input"
                placeholder="eventType"
                value={form.eventType}
                onChange={(e) => setForm((prev) => ({ ...prev, eventType: e.target.value }))}
              />
              <select
                className="filter-input"
                value={form.channelTypeCode}
                onChange={(e) => setForm((prev) => ({ ...prev, channelTypeCode: e.target.value }))}
              >
                {CHANNEL_OPTIONS.map((channel) => (
                  <option key={channel} value={channel}>
                    {channel}
                  </option>
                ))}
              </select>
              <input
                className="filter-input"
                placeholder="locale"
                value={form.locale}
                onChange={(e) => setForm((prev) => ({ ...prev, locale: e.target.value }))}
              />
              <label className="templates-checkbox">
                <input
                  type="checkbox"
                  checked={form.isActive}
                  onChange={(e) => setForm((prev) => ({ ...prev, isActive: e.target.checked }))}
                />
                isActive
              </label>
              <input
                className="filter-input"
                placeholder="subject (email)"
                value={form.subjectTemplate}
                disabled={form.channelTypeCode !== "EMAIL"}
                onChange={(e) => setForm((prev) => ({ ...prev, subjectTemplate: e.target.value }))}
              />
              <textarea
                className="filter-input"
                style={{ minHeight: 130 }}
                placeholder="body template"
                value={form.bodyTemplate}
                onChange={(e) => setForm((prev) => ({ ...prev, bodyTemplate: e.target.value }))}
              />
              <div className="templates-variables">
                {(variableTokens.length ? variableTokens : ["No variables detected"]).map((token) => (
                  <span key={token} className="badge badge-default">
                    {token}
                  </span>
                ))}
              </div>
              <div style={{ display: "flex", gap: 8 }}>
                <button
                  className="btn btn-primary btn-sm"
                  disabled={!form.eventType.trim() || !form.bodyTemplate.trim() || createMutation.isPending || updateMutation.isPending}
                  onClick={() => (form.id ? updateMutation.mutate() : createMutation.mutate())}
                >
                  {form.id ? "Save Changes" : "Create Template"}
                </button>
                <button
                  className="btn btn-secondary btn-sm"
                  onClick={() => {
                    setSelectedId(null);
                    setForm(DEFAULT_FORM);
                  }}
                >
                  Clear
                </button>
              </div>
            </div>
          </div>

          <div className="card">
            <div className="card-header">
              <div className="card-title">Live Preview</div>
            </div>
            <textarea
              className="filter-input"
              style={{ width: "100%", minHeight: 120, fontFamily: "var(--font-mono)", fontSize: 12 }}
              value={samplePayload}
              onChange={(e) => setSamplePayload(e.target.value)}
            />
            {parsedPayload.error ? (
              <div style={{ marginTop: 8, color: "var(--color-danger)", fontSize: 12 }}>
                Invalid JSON: {parsedPayload.error}
              </div>
            ) : null}
            <div className="templates-preview">
              <div>
                <div className="templates-preview-label">Subject</div>
                <div className="templates-preview-box">{previewSubject || "—"}</div>
              </div>
              <div>
                <div className="templates-preview-label">Body</div>
                <div className="templates-preview-box">{previewBody || "—"}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
