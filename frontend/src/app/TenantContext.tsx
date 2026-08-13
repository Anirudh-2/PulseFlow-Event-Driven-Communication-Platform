import { createContext, useContext, useMemo, useState } from "react";
import type { ReactNode } from "react";

type TenantContextValue = {
  tenantId: string;
  userId: string;
  setTenantId: (value: string) => void;
  setUserId: (value: string) => void;
};

const TenantContext = createContext<TenantContextValue | null>(null);

const TENANT_KEY = "pulseflow.tenantId";
const USER_KEY = "pulseflow.userId";

function readStorage(key: string, fallback: string): string {
  const value = window.localStorage.getItem(key);
  return value && value.trim() ? value : fallback;
}

export function TenantProvider({ children }: { children: ReactNode }) {
  const [tenantId, setTenantIdState] = useState<string>(() => readStorage(TENANT_KEY, "default"));
  const [userId, setUserIdState] = useState<string>(() => readStorage(USER_KEY, "demo-user"));

  const setTenantId = (value: string) => {
    const next = value.trim() || "default";
    setTenantIdState(next);
    window.localStorage.setItem(TENANT_KEY, next);
  };

  const setUserId = (value: string) => {
    const next = value.trim() || "demo-user";
    setUserIdState(next);
    window.localStorage.setItem(USER_KEY, next);
  };

  const contextValue = useMemo(
    () => ({ tenantId, userId, setTenantId, setUserId }),
    [tenantId, userId]
  );

  return <TenantContext.Provider value={contextValue}>{children}</TenantContext.Provider>;
}

export function useTenantContext(): TenantContextValue {
  const value = useContext(TenantContext);
  if (!value) {
    throw new Error("useTenantContext must be used inside TenantProvider");
  }
  return value;
}
