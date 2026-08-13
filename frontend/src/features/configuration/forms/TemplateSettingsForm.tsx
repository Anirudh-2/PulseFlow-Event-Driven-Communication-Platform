import type { TemplateConfig } from "../../../api/client";

type Props = {
  value: TemplateConfig;
  onChange: (next: TemplateConfig) => void;
};

export function TemplateSettingsForm({ value, onChange }: Props) {
  return (
    <div className="card" style={{ marginBottom: 16 }}>
      <div className="card-title" style={{ marginBottom: 12 }}>Templates</div>
      <div style={{ marginBottom: 12, fontWeight: 600 }}>Teams</div>
      <div className="filter-bar">
        <input className="filter-input" placeholder="Teams title template" value={value.teams.titleTemplate} onChange={(e) => onChange({ ...value, teams: { ...value.teams, titleTemplate: e.target.value } })} />
      </div>
      <div style={{ marginBottom: 16 }}>
        <textarea className="filter-input" style={{ width: "100%", minHeight: 90 }} placeholder="Teams body template" value={value.teams.bodyTemplate} onChange={(e) => onChange({ ...value, teams: { ...value.teams, bodyTemplate: e.target.value } })} />
      </div>

      <div style={{ marginBottom: 12, fontWeight: 600 }}>Email</div>
      <div className="filter-bar">
        <input className="filter-input" placeholder="Email subject template" value={value.email.subjectTemplate} onChange={(e) => onChange({ ...value, email: { ...value.email, subjectTemplate: e.target.value } })} />
      </div>
      <div>
        <textarea className="filter-input" style={{ width: "100%", minHeight: 110 }} placeholder="Email body template" value={value.email.bodyTemplate} onChange={(e) => onChange({ ...value, email: { ...value.email, bodyTemplate: e.target.value } })} />
      </div>
    </div>
  );
}
