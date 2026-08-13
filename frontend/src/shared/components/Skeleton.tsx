interface SkeletonProps {
  type?: "card" | "row" | "text" | "stat";
  count?: number;
}

export const Skeleton: React.FC<SkeletonProps> = ({
  type = "row",
  count = 5,
}) => {
  const items = Array.from({ length: count });

  if (type === "stat") {
    return (
      <div className="skeleton-stat-grid">
        {items.map((_, i) => (
          <div key={i} className="skeleton-stat-card">
            <div className="skeleton skeleton-h-4 skeleton-w-6" />
            <div className="skeleton skeleton-h-8 skeleton-w-16 mt-2" />
            <div className="skeleton skeleton-h-3 skeleton-w-12 mt-1" />
          </div>
        ))}
      </div>
    );
  }

  if (type === "card") {
    return (
      <div className="skeleton-card-list">
        {items.map((_, i) => (
          <div key={i} className="skeleton-card">
            <div className="skeleton-card-header">
              <div className="skeleton skeleton-h-4 skeleton-w-32" />
              <div className="skeleton skeleton-h-4 skeleton-w-16" />
            </div>
            <div className="skeleton skeleton-h-3 skeleton-w-full mt-2" />
            <div className="skeleton skeleton-h-3 skeleton-w-3/4 mt-1" />
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className="skeleton-row-list">
      {items.map((_, i) => (
        <div key={i} className="skeleton-row">
          <div className="skeleton skeleton-h-4 skeleton-w-full" />
        </div>
      ))}
    </div>
  );
};
