import { useEffect } from "react";
import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import App from "./app/App";
import { AuthProvider, registerAuthTokenGetter, useAuth } from "./app/AuthContext";
import { TenantProvider } from "./app/TenantContext";
import "./style.css";

const queryClient = new QueryClient();

function AuthTokenBridge({ children }: { children: React.ReactNode }) {
  const { getToken } = useAuth();
  useEffect(() => {
    registerAuthTokenGetter(getToken);
  }, [getToken]);
  return <>{children}</>;
}

ReactDOM.createRoot(document.getElementById("app")!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <AuthTokenBridge>
          <TenantProvider>
            <BrowserRouter>
              <App />
            </BrowserRouter>
          </TenantProvider>
        </AuthTokenBridge>
      </AuthProvider>
    </QueryClientProvider>
  </React.StrictMode>
);
