import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { getIntegrationConfig, getRules, updateIntegrationConfig } from "../../api/client";
import type { IntegrationConfig } from "../../api/client";
import { ErrorState, Skeleton } from "../../shared/components";
import { useTenantContext } from "../../app/TenantContext";
import { TeamsSettingsForm } from "./forms/TeamsSettingsForm";
import { SmtpSettingsForm } from "./forms/SmtpSettingsForm";
import { WebhookSecurityForm } from "./forms/WebhookSecurityForm";
import { HrmsMappingForm } from "./forms/HrmsMappingForm";
import { TemplateSettingsForm } from "./forms/TemplateSettingsForm";
import { TelegramSettingsForm } from "./forms/TelegramSettingsForm";
import { RuleEditor } from "./RuleEditor";
import { PlatformAdminPanel } from "./PlatformAdminPanel";

type Tab = "integrations" | "rules" | "platform";

const DEFAULT_CONFIG: IntegrationConfig = {
  teams: { enabled: false, webhookUrl: "" },
  smtp: { enabled: false, host: "smtp.office365.com", port: 587, username: "", fromAddress: "", startTls: true, auth: true },
  webhookSecurity: { mode: "API_KEY", apiKeyHeader: "X-Webhook-Api-Key" },
  hrmsMapping: { defaultTenantId: "default", sourceServiceName: "HRMS", userIdentifierStrategy: "AAD_ID_FIRST", eventTypeMap: {} },
  templates: {
    teams: { titleTemplate: "{{eventType}}", bodyTemplate: "{{body}}" },
    email: { subjectTemplate: "{{eventType}}", bodyTemplate: "{{body}}" },
  },
  telegram: { enabled: false, apiBase: "https://api.telegram.org", parseMode: "Markdown", botToken: "", chatId: "" },
};

export function ConfigurationPage() {
  const { tenantId } = useTenantContext();
  const qc = useQueryClient();
  const [tab, setTab] = useState<Tab>("integrations");
  const [local, setLocal] = useState<IntegrationConfig | null>(null);

  const configQuery = useQuery({
    queryKey: ["integration-config", tenantId],
    queryFn: () => getIntegrationConfig(tenantId),
  });

  const rulesQuery = useQuery({
    queryKey: ["rules", tenantId],
    queryFn: () => getRules(tenantId),
  });

  const mutation = useMutation({
    mutationFn: (payload: IntegrationConfig) => updateIntegrationConfig(tenantId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["integration-config", tenantId] });
    },
  });

  const current = useMemo(() => local ?? configQuery.data ?? DEFAULT_CONFIG, [local, configQuery.data]);
  const isDirty = JSON.stringify(current) !== JSON.stringify(configQuery.data ?? DEFAULT_CONFIG);

  if (configQuery.isLoading || rulesQuery.isLoading) return <Skeleton type="card" count={6} />;
  if (configQuery.isError) return <ErrorState message={(configQuery.error as Error).message} onRetry={() => configQuery.refetch()} />;
  if (rulesQuery.isError) return <ErrorState message={(rulesQuery.error as Error).message} onRetry={() => rulesQuery.refetch()} />;

  return (
    <>
      <div className="section-header">
        <div className="section-title">Configuration ({tenantId})</div>
        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
          {isDirty && <span style={{ fontSize: 12, color: "var(--color-warning)" }}>Unsaved changes</span>}
          <button className="btn btn-secondary btn-sm" onClick={() => setLocal(configQuery.data ?? DEFAULT_CONFIG)}>Reset</button>
          <button className="btn btn-primary btn-sm" disabled={!isDirty || mutation.isPending} onClick={() => mutation.mutate(current)}>Save</button>
        </div>
      </div>

      <div className="filter-bar">
        <button className={`btn btn-sm ${tab === "integrations" ? "btn-primary" : "btn-secondary"}`} onClick={() => setTab("integrations")}>Integrations</button>
        <button className={`btn btn-sm ${tab === "rules" ? "btn-primary" : "btn-secondary"}`} onClick={() => setTab("rules")}>Rules</button>
        <button className={`btn btn-sm ${tab === "platform" ? "btn-primary" : "btn-secondary"}`} onClick={() => setTab("platform")}>Platform</button>
      </div>

      {tab === "integrations" ? (
        <>
          <TeamsSettingsForm value={current.teams} onChange={(teams) => setLocal({ ...current, teams })} />
          <SmtpSettingsForm value={current.smtp} onChange={(smtp) => setLocal({ ...current, smtp })} />
          <WebhookSecurityForm value={current.webhookSecurity} onChange={(webhookSecurity) => setLocal({ ...current, webhookSecurity })} />
          <HrmsMappingForm value={current.hrmsMapping} onChange={(hrmsMapping) => setLocal({ ...current, hrmsMapping })} />
          <TemplateSettingsForm value={current.templates} onChange={(templates) => setLocal({ ...current, templates })} />
          <TelegramSettingsForm value={current.telegram} onChange={(telegram) => setLocal({ ...current, telegram })} />
        </>
      ) : tab === "rules" ? (
        <RuleEditor tenantId={tenantId} rules={rulesQuery.data ?? []} />
      ) : (
        <PlatformAdminPanel tenantId={tenantId} />
      )}
    </>
  );
}
