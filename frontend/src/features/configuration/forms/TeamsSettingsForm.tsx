import type { TeamsConfig } from "../../../api/client";

type Props = {
  value: TeamsConfig;
  onChange: (next: TeamsConfig) => void;
};

export function TeamsSettingsForm({ value, onChange }: Props) {
  return (
    <div className="card" style={{ marginBottom: 16 }}>
      <div className="card-title" style={{ marginBottom: 12 }}>Microsoft Teams (Incoming Webhook)</div>
      <p style={{ fontSize: 13, color: "var(--color-muted)", marginBottom: 12 }}>
        PulseFlow posts Adaptive Cards to a Teams Incoming Webhook URL. Create a webhook in your Teams channel and paste it below.
      </p>
      <div className="filter-bar">
        <label>
          <input
            type="checkbox"
            checked={value.enabled}
            onChange={(e) => onChange({ ...value, enabled: e.target.checked })}
          />{" "}
          Enable Teams delivery
        </label>
      </div>
      <div className="filter-bar">
        <input
          className="filter-input"
          style={{ flex: 2 }}
          placeholder="Incoming Webhook URL (webhook_url)"
          value={value.webhookUrl ?? ""}
          onChange={(e) => onChange({ ...value, webhookUrl: e.target.value })}
        />
      </div>
    </div>
  );
}
