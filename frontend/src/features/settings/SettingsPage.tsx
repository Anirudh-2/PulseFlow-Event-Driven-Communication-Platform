type Props = {
  tenantId: string;
  userId: string;
  darkMode: boolean;
  onTenantIdChange: (value: string) => void;
  onUserIdChange: (value: string) => void;
  onToggleTheme: () => void;
};

export function SettingsPage({
  tenantId,
  userId,
  darkMode,
  onTenantIdChange,
  onUserIdChange,
  onToggleTheme,
}: Props) {
  return (
    <div className="card" style={{ maxWidth: 720 }}>
      <div className="card-header">
        <div className="card-title">User Preferences</div>
      </div>

      <div style={{ display: "grid", gap: 12 }}>
        <label style={{ display: "grid", gap: 6 }}>
          <span>Tenant ID</span>
          <input
            className="filter-input"
            value={tenantId}
            onChange={(e) => onTenantIdChange(e.target.value)}
            placeholder="Tenant ID"
          />
        </label>

        <label style={{ display: "grid", gap: 6 }}>
          <span>User ID</span>
          <input
            className="filter-input"
            value={userId}
            onChange={(e) => onUserIdChange(e.target.value)}
            placeholder="User ID"
          />
        </label>

        <div style={{ marginTop: 8 }}>
          <button className="btn btn-secondary btn-sm" onClick={onToggleTheme}>
            {darkMode ? "Switch to Light Mode" : "Switch to Dark Mode"}
          </button>
        </div>
      </div>
    </div>
  );
}
