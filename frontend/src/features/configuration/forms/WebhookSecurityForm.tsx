import type { WebhookSecurityConfig } from "../../../api/client";

type Props = {
  value: WebhookSecurityConfig;
  onChange: (next: WebhookSecurityConfig) => void;
};

export function WebhookSecurityForm({ value, onChange }: Props) {
  return (
    <div className="card" style={{ marginBottom: 16 }}>
      <div className="card-title" style={{ marginBottom: 12 }}>Webhook Security</div>
      <div className="filter-bar">
        <select className="filter-select" value={value.mode} onChange={(e) => onChange({ ...value, mode: e.target.value as WebhookSecurityConfig["mode"] })}>
          <option value="API_KEY">API Key</option>
          <option value="JWT">Internal JWT</option>
        </select>
        <input className="filter-input" placeholder="Header name" value={value.apiKeyHeader} onChange={(e) => onChange({ ...value, apiKeyHeader: e.target.value })} />
      </div>
      {value.mode === "API_KEY" ? (
        <div className="filter-bar">
          <input className="filter-input" placeholder="API key secret provider" value={value.apiKeyRef?.provider ?? ""} onChange={(e) => onChange({ ...value, apiKeyRef: { provider: (e.target.value || "azure-key-vault") as NonNullable<WebhookSecurityConfig["apiKeyRef"]>["provider"], path: value.apiKeyRef?.path ?? "", key: value.apiKeyRef?.key ?? "" } })} />
          <input className="filter-input" placeholder="API key secret path" value={value.apiKeyRef?.path ?? ""} onChange={(e) => onChange({ ...value, apiKeyRef: { provider: value.apiKeyRef?.provider ?? "azure-key-vault", path: e.target.value, key: value.apiKeyRef?.key ?? "" } })} />
          <input className="filter-input" placeholder="API key secret key" value={value.apiKeyRef?.key ?? ""} onChange={(e) => onChange({ ...value, apiKeyRef: { provider: value.apiKeyRef?.provider ?? "azure-key-vault", path: value.apiKeyRef?.path ?? "", key: e.target.value } })} />
        </div>
      ) : (
        <div className="filter-bar">
          <input className="filter-input" placeholder="JWT Issuer" value={value.jwtIssuer ?? ""} onChange={(e) => onChange({ ...value, jwtIssuer: e.target.value })} />
          <input className="filter-input" placeholder="JWT Audience" value={value.jwtAudience ?? ""} onChange={(e) => onChange({ ...value, jwtAudience: e.target.value })} />
        </div>
      )}
    </div>
  );
}
