import React from "react"; // needed for JSX generic syntax

export interface Column<T> {
  key: string;
  header: string;
  render?: (row: T) => React.ReactNode;
  width?: string;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[];
  keyField: keyof T;
  emptyMessage?: string;
  className?: string;
}

export function DataTable<T>({
  columns,
  rows,
  keyField,
  emptyMessage = "No data available",
  className,
}: DataTableProps<T>) {
  return (
    <div className={`table-wrapper ${className ?? ""}`}>
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((col) => (
              <th key={col.key} style={col.width ? { width: col.width } : {}}>
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length} className="table-empty">
                <span className="table-empty-icon">📭</span>
                <span>{emptyMessage}</span>
              </td>
            </tr>
          ) : (
            rows.map((row) => (
              <tr key={String(row[keyField])}>
                {columns.map((col) => (
                  <td key={col.key}>
                    {col.render
                      ? col.render(row)
                      : String((row as Record<string, unknown>)[col.key] ?? "")}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
