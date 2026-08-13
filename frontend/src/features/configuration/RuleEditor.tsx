import { useMemo, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createRule, deleteRule, updateRule } from "../../api/client";
import type { NotificationRule, RuleConfigInput } from "../../api/client";

type Props = {
  tenantId: string;
  rules: NotificationRule[];
};

const EMPTY_RULE: RuleConfigInput = {
  name: "",
  roleName: "EMPLOYEE",
  notificationType: "HR_ACTION",
  eventType: "",
  integrationSourceId: "",
  channels: ["TEAMS", "EMAIL"],
  evalOrder: 100,
  isActive: true,
  conditions: {},
  conditionsJsonlogic: undefined,
};

export function RuleEditor({ tenantId, rules }: Props) {
  const qc = useQueryClient();
  const [draft, setDraft] = useState<RuleConfigInput>(EMPTY_RULE);
  const [editingRuleId, setEditingRuleId] = useState<string | null>(null);

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (editingRuleId) {
        return updateRule(tenantId, editingRuleId, draft);
      }
      return createRule(tenantId, draft);
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["rules", tenantId] });
      setDraft(EMPTY_RULE);
      setEditingRuleId(null);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (ruleId: string) => deleteRule(tenantId, ruleId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["rules", tenantId] }),
  });

  const ruleRows = useMemo(() => rules ?? [], [rules]);

  return (
    <div className="card">
      <div className="card-header">
        <div className="card-title">Rule Editor</div>
        <button className="btn btn-secondary btn-sm" onClick={() => { setDraft(EMPTY_RULE); setEditingRuleId(null); }}>
          New Rule
        </button>
      </div>

      <div className="filter-bar">
        <input className="filter-input" placeholder="Rule name" value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />
        <input className="filter-input" placeholder="Role" value={draft.roleName} onChange={(e) => setDraft({ ...draft, roleName: e.target.value })} />
        <input className="filter-input" placeholder="Notification type" value={draft.notificationType ?? ""} onChange={(e) => setDraft({ ...draft, notificationType: e.target.value })} />
        <input className="filter-input" placeholder="Event type (optional)" value={draft.eventType ?? ""} onChange={(e) => setDraft({ ...draft, eventType: e.target.value })} />
        <input className="filter-input" placeholder="Integration source UUID (optional)" value={draft.integrationSourceId ?? ""} onChange={(e) => setDraft({ ...draft, integrationSourceId: e.target.value })} />
        <input className="filter-input" type="number" placeholder="Order" value={draft.evalOrder} onChange={(e) => setDraft({ ...draft, evalOrder: Number(e.target.value) || 100 })} />
      </div>

      <div className="filter-bar">
        <label><input type="checkbox" checked={draft.channels.includes("TEAMS")} onChange={(e) => setDraft({ ...draft, channels: e.target.checked ? Array.from(new Set([...draft.channels, "TEAMS"])) : draft.channels.filter((c) => c !== "TEAMS") })} /> Teams</label>
        <label><input type="checkbox" checked={draft.channels.includes("EMAIL")} onChange={(e) => setDraft({ ...draft, channels: e.target.checked ? Array.from(new Set([...draft.channels, "EMAIL"])) : draft.channels.filter((c) => c !== "EMAIL") })} /> Email</label>
        <label><input type="checkbox" checked={draft.channels.includes("TELEGRAM")} onChange={(e) => setDraft({ ...draft, channels: e.target.checked ? Array.from(new Set([...draft.channels, "TELEGRAM"])) : draft.channels.filter((c) => c !== "TELEGRAM") })} /> Telegram</label>
        <label><input type="checkbox" checked={draft.channels.includes("WHATSAPP")} onChange={(e) => setDraft({ ...draft, channels: e.target.checked ? Array.from(new Set([...draft.channels, "WHATSAPP"])) : draft.channels.filter((c) => c !== "WHATSAPP") })} /> WhatsApp</label>
        <label><input type="checkbox" checked={draft.channels.includes("WEBSOCKET")} onChange={(e) => setDraft({ ...draft, channels: e.target.checked ? Array.from(new Set([...draft.channels, "WEBSOCKET"])) : draft.channels.filter((c) => c !== "WEBSOCKET") })} /> WebSocket</label>
        <label><input type="checkbox" checked={draft.channels.includes("WEBHOOK")} onChange={(e) => setDraft({ ...draft, channels: e.target.checked ? Array.from(new Set([...draft.channels, "WEBHOOK"])) : draft.channels.filter((c) => c !== "WEBHOOK") })} /> Webhook</label>
        <label><input type="checkbox" checked={draft.isActive} onChange={(e) => setDraft({ ...draft, isActive: e.target.checked })} /> Active</label>
      </div>

      <div className="filter-bar" style={{ flexDirection: "column", alignItems: "stretch" }}>
        <label style={{ fontSize: 12, color: "var(--color-muted)" }}>JSON Logic conditions (optional)</label>
        <textarea
          className="filter-input"
          rows={3}
          placeholder='e.g. {"==":[{"var":"amount"},100]}'
          value={draft.conditionsJsonlogic ? JSON.stringify(draft.conditionsJsonlogic) : ""}
          onChange={(e) => {
            const v = e.target.value.trim();
            if (!v) {
              setDraft({ ...draft, conditionsJsonlogic: undefined });
              return;
            }
            try {
              setDraft({ ...draft, conditionsJsonlogic: JSON.parse(v) as Record<string, unknown> });
            } catch {
              /* keep typing */
            }
          }}
        />
      </div>

      <div className="filter-bar">
        <button
          className="btn btn-primary btn-sm"
          disabled={!draft.name.trim() || !draft.roleName.trim() || draft.channels.length === 0 || saveMutation.isPending}
          onClick={() => saveMutation.mutate()}
        >
          {editingRuleId ? "Update Rule" : "Create Rule"}
        </button>
      </div>

      <div className="section-header" style={{ marginTop: 16 }}>
        <div className="section-title">Existing Rules ({ruleRows.length})</div>
      </div>
      <div className="table-wrapper">
        <table className="data-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Role</th>
              <th>Type</th>
              <th>Event</th>
              <th>Channels</th>
              <th>Order</th>
              <th>Status</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {ruleRows.length === 0 ? (
              <tr><td colSpan={8} className="table-empty">No rules configured.</td></tr>
            ) : ruleRows.map((r) => (
              <tr key={r.id}>
                <td>{r.name}</td>
                <td>{r.targetRole}</td>
                <td>{r.notificationType}</td>
                <td>{r.eventType ?? "—"}</td>
                <td>{(r.channels ?? []).join(", ")}</td>
                <td>{r.evaluationOrder}</td>
                <td>{r.active ? "Active" : "Inactive"}</td>
                <td>
                  <div style={{ display: "flex", gap: 6 }}>
                    <button className="btn btn-ghost btn-xs" onClick={() => {
                      setEditingRuleId(r.id);
                      setDraft({
                        name: r.name,
                        roleName: (r.targetRole ?? (r as { roleName?: string }).roleName ?? "EMPLOYEE") as string,
                        notificationType: r.notificationType,
                        eventType: r.eventType ?? "",
                        integrationSourceId: r.integrationSourceId ?? "",
                        channels: r.channels ?? [],
                        evalOrder: r.evaluationOrder,
                        isActive: r.active,
                        conditions: r.conditions ?? {},
                        conditionsJsonlogic: r.conditionsJsonlogic,
                      });
                    }}>Edit</button>
                    <button className="btn btn-danger btn-xs" disabled={deleteMutation.isPending} onClick={() => deleteMutation.mutate(r.id)}>Delete</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
