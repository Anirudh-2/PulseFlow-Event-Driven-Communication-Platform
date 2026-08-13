interface StatCardProps {
  label: string;
  value: number | string;
  icon: string;
  accent?: "blue" | "red" | "orange" | "green" | "purple";
  description?: string;
}

export const StatCard: React.FC<StatCardProps> = ({
  label,
  value,
  icon,
  accent = "blue",
  description,
}) => {
  return (
    <div className={`stat-card stat-card-${accent}`}>
      <div className="stat-card-header">
        <span className="stat-card-icon">{icon}</span>
        <span className="stat-card-label">{label}</span>
      </div>
      <div className="stat-card-value">{value}</div>
      {description && <div className="stat-card-desc">{description}</div>}
    </div>
  );
};
