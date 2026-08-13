interface ErrorStateProps {
  title?: string;
  message: string;
  onRetry?: () => void;
}

export const ErrorState: React.FC<ErrorStateProps> = ({
  title = "Something went wrong",
  message,
  onRetry,
}) => {
  return (
    <div className="error-state">
      <div className="error-state-icon">⚠️</div>
      <div className="error-state-title">{title}</div>
      <div className="error-state-message">{message}</div>
      {onRetry && (
        <button className="btn btn-primary" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
};
