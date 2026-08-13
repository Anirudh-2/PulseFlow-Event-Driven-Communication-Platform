import type { SecretRef, SmtpConfig } from "../../../api/client";

type Props = {
  value: SmtpConfig;
  onChange: (next: SmtpConfig) => void;
};

export function SmtpSettingsForm({ value, onChange }: Props) {
  const defaultProvider: SecretRef["provider"] = "azure-key-vault";

  return (
    <div className="card" style={{ marginBottom: 16 }}>
      <div className="card-title" style={{ marginBottom: 12 }}>SMTP Email Settings</div>
      <div className="filter-bar">
        <label><input type="checkbox" checked={value.enabled} onChange={(e) => onChange({ ...value, enabled: e.target.checked })} /> Enable Email delivery</label>
        <label><input type="checkbox" checked={value.auth} onChange={(e) => onChange({ ...value, auth: e.target.checked })} /> SMTP Auth</label>
        <label><input type="checkbox" checked={value.startTls} onChange={(e) => onChange({ ...value, startTls: e.target.checked })} /> STARTTLS</label>
      </div>
      <div className="filter-bar">
        <input className="filter-input" placeholder="SMTP Host" value={value.host} onChange={(e) => onChange({ ...value, host: e.target.value })} />
        <input className="filter-input" placeholder="SMTP Port" type="number" value={value.port} onChange={(e) => onChange({ ...value, port: Number(e.target.value) || 0 })} />
      </div>
      <div className="filter-bar">
        <input className="filter-input" placeholder="SMTP Username" value={value.username} onChange={(e) => onChange({ ...value, username: e.target.value })} />
        <input className="filter-input" placeholder="From Address" value={value.fromAddress} onChange={(e) => onChange({ ...value, fromAddress: e.target.value })} />
      </div>
      <div className="filter-bar">
        <input className="filter-input" placeholder="Password secret provider" value={value.passwordRef?.provider ?? ""} onChange={(e) => onChange({ ...value, passwordRef: { provider: (e.target.value || "azure-key-vault") as SecretRef["provider"], path: value.passwordRef?.path ?? "", key: value.passwordRef?.key ?? "" } })} />
        <input className="filter-input" placeholder="Password secret path" value={value.passwordRef?.path ?? ""} onChange={(e) => onChange({ ...value, passwordRef: { provider: value.passwordRef?.provider ?? defaultProvider, path: e.target.value, key: value.passwordRef?.key ?? "" } })} />
        <input className="filter-input" placeholder="Password secret key" value={value.passwordRef?.key ?? ""} onChange={(e) => onChange({ ...value, passwordRef: { provider: value.passwordRef?.provider ?? defaultProvider, path: value.passwordRef?.path ?? "", key: e.target.value } })} />
      </div>
    </div>
  );
}
