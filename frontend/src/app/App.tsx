import { useState } from "react";
import { Navigate, NavLink, Route, Routes, useLocation } from "react-router-dom";
import { DashboardPage } from "../features/dashboard/DashboardPage";
import { RulesPage } from "../features/rules/RulesPage";
import { DeliveryLogsPage } from "../features/delivery/DeliveryLogsPage";
import { AuditLogsPage } from "../features/audit/AuditLogsPage";
import { ChannelsPage } from "../features/channels/ChannelsPage";
import { TemplatesPage } from "../features/templates/TemplatesPage";
import { ApplicationsPage } from "../features/applications/ApplicationsPage";
import { SettingsPage } from "../features/settings/SettingsPage";
import { ConfigurationPage } from "../features/configuration/ConfigurationPage";
import { useTenantContext } from "./TenantContext";
import { useAuth } from "./AuthContext";
import { useNotificationSocket } from "./useNotificationSocket";
import "../style.css";

const NAV_ITEMS: { to: string; label: string; icon: string }[] = [
  { to: "/", label: "Dashboard", icon: "🔔" },
  { to: "/rules", label: "Rules", icon: "⚙️" },
  { to: "/delivery", label: "Delivery Logs", icon: "📬" },
  { to: "/audit", label: "Audit Logs", icon: "📋" },
  { to: "/channels", label: "Channels", icon: "📡" },
  { to: "/templates", label: "Template Library", icon: "🧩" },
  { to: "/applications", label: "Applications", icon: "🗂️" },
  { to: "/settings", label: "Settings", icon: "⚙️" },
  { to: "/configuration", label: "Configuration", icon: "⚙️" },
];

const PAGE_TITLES: Record<string, { title: string; subtitle: string }> = {
  "/": { title: "Notifications", subtitle: "Monitor and manage all notification events" },
  "/rules": { title: "Rule Management", subtitle: "Configure notification routing rules" },
  "/delivery": { title: "Delivery Logs", subtitle: "Track channel delivery status and retries" },
  "/audit": { title: "Audit Trail", subtitle: "Full history of all notification actions" },
  "/channels": { title: "Channels", subtitle: "Manage app-level channel credentials and test connectivity" },
  "/templates": { title: "Template Library", subtitle: "Manage subject and body templates" },
  "/applications": { title: "Registered Applications", subtitle: "Manage integration sources and platform setup" },
  "/settings": { title: "User Preferences", subtitle: "Manage local tenant and user defaults" },
  "/configuration": { title: "Configuration", subtitle: "Manage integrations, rules, and platform settings" },
};

export default function App() {
  const [darkMode, setDarkMode] = useState(false);
  const { pathname } = useLocation();
  const { tenantId, userId, setTenantId, setUserId } = useTenantContext();
  const { ready, authenticated, username, roles, login, logout } = useAuth();
  useNotificationSocket();

  const toggleTheme = () => {
    const next = !darkMode;
    setDarkMode(next);
    document.documentElement.setAttribute("data-theme", next ? "dark" : "light");
  };

  const { title, subtitle } = PAGE_TITLES[pathname] ?? PAGE_TITLES["/"];

  if (!ready) {
    return (
      <div className="app-shell" style={{ placeItems: "center", display: "grid", minHeight: "100vh" }}>
        <div>Connecting to PulseFlow…</div>
      </div>
    );
  }

  if (!authenticated) {
    return (
      <div className="app-shell" style={{ placeItems: "center", display: "grid", minHeight: "100vh" }}>
        <div className="card" style={{ maxWidth: 420, padding: 24, textAlign: "center" }}>
          <div className="sidebar-brand-title" style={{ marginBottom: 8 }}>PulseFlow</div>
          <div className="topbar-subtitle" style={{ marginBottom: 20 }}>
            Event-Driven Communication Platform
          </div>
          <p style={{ fontSize: 14, color: "var(--color-muted)", marginBottom: 16 }}>
            Sign in with Keycloak to manage notifications, rules, and delivery channels.
          </p>
          <button className="btn btn-primary" onClick={login}>
            Sign in with Keycloak
          </button>
          <p style={{ fontSize: 12, color: "var(--color-muted)", marginTop: 16 }}>
            Demo: pulseflow-admin / admin123 · pulseflow-employee / employee123
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="sidebar-brand-title">PulseFlow</div>
          <div className="sidebar-brand-sub">Event-Driven Communication Platform</div>
        </div>

        <nav className="sidebar-nav">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === "/"}
              className={({ isActive }) => `sidebar-nav-item ${isActive ? "active" : ""}`}
            >
              <span className="sidebar-nav-icon">{item.icon}</span>
              <span>{item.label}</span>
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-footer">
          <div style={{ fontSize: 12, color: "var(--color-muted)", marginBottom: 8, padding: "0 12px" }}>
            {username ?? "user"}
            {roles.includes("ADMIN") ? " · ADMIN" : ""}
          </div>
          <button className="theme-toggle" onClick={logout}>
            <span className="sidebar-nav-icon">🚪</span>
            <span>Sign out</span>
          </button>
          <button className="theme-toggle" onClick={toggleTheme}>
            <span className="sidebar-nav-icon">{darkMode ? "☀️" : "🌙"}</span>
            <span>{darkMode ? "Light Mode" : "Dark Mode"}</span>
          </button>
        </div>
      </aside>

      <div className="main-content">
        <header className="topbar">
          <div>
            <div className="topbar-title">{title}</div>
            <div className="topbar-subtitle">{subtitle}</div>
          </div>
          <div style={{ display: "flex", gap: 8 }}>
            <input
              className="filter-input"
              style={{ width: 160 }}
              value={tenantId}
              onChange={(e) => setTenantId(e.target.value)}
              placeholder="Tenant ID"
            />
            <input
              className="filter-input"
              style={{ width: 160 }}
              value={userId}
              onChange={(e) => setUserId(e.target.value)}
              placeholder="User ID"
            />
          </div>
        </header>

        <main className="page-body">
          <Routes>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/rules" element={<RulesPage />} />
            <Route path="/delivery" element={<DeliveryLogsPage />} />
            <Route path="/audit" element={<AuditLogsPage />} />
            <Route path="/channels" element={<ChannelsPage />} />
            <Route path="/templates" element={<TemplatesPage />} />
            <Route path="/applications" element={<ApplicationsPage />} />
            <Route
              path="/settings"
              element={
                <SettingsPage
                  tenantId={tenantId}
                  userId={userId}
                  darkMode={darkMode}
                  onTenantIdChange={setTenantId}
                  onUserIdChange={setUserId}
                  onToggleTheme={toggleTheme}
                />
              }
            />
            <Route path="/configuration" element={<ConfigurationPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </main>
      </div>
    </div>
  );
}
