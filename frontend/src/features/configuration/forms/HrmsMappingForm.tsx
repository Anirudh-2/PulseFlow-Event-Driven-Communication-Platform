import type { HrmsMappingConfig } from "../../../api/client";

type Props = {
  value: HrmsMappingConfig;
  onChange: (next: HrmsMappingConfig) => void;
};

export function HrmsMappingForm({ value, onChange }: Props) {
  const eventTypeMapText = Object.entries(value.eventTypeMap ?? {})
    .map(([k, v]) => `${k}:${v}`)
    .join("\n");

  return (
    <div className="card" style={{ marginBottom: 16 }}>
      <div className="card-title" style={{ marginBottom: 12 }}>Integration Mapping</div>
      <div className="filter-bar">
        <input className="filter-input" placeholder="Default Tenant ID" value={value.defaultTenantId} onChange={(e) => onChange({ ...value, defaultTenantId: e.target.value })} />
        <input className="filter-input" placeholder="Source Service Name" value={value.sourceServiceName} onChange={(e) => onChange({ ...value, sourceServiceName: e.target.value })} />
      </div>
      <div className="filter-bar">
        <select className="filter-select" value={value.userIdentifierStrategy} onChange={(e) => onChange({ ...value, userIdentifierStrategy: e.target.value as HrmsMappingConfig["userIdentifierStrategy"] })}>
          <option value="AAD_ID_FIRST">AAD ID first</option>
          <option value="EMAIL_ONLY">Email only</option>
          <option value="AAD_ONLY">AAD only</option>
        </select>
      </div>
      <div style={{ marginTop: 8 }}>
        <div style={{ fontSize: 12, color: "var(--color-text-secondary)", marginBottom: 6 }}>Event Type Map (one per line: SOURCE_EVENT:PLATFORM_TYPE)</div>
        <textarea
          className="filter-input"
          style={{ width: "100%", minHeight: 110 }}
          value={eventTypeMapText}
          onChange={(e) => {
            const map = e.target.value
              .split("\n")
              .map((line) => line.trim())
              .filter(Boolean)
              .reduce<Record<string, string>>((acc, line) => {
                const [left, right] = line.split(":");
                if (left && right) acc[left.trim()] = right.trim();
                return acc;
              }, {});
            onChange({ ...value, eventTypeMap: map });
          }}
        />
      </div>
    </div>
  );
}
