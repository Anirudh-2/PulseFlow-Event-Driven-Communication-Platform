import { useEffect, useRef } from "react";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { useQueryClient } from "@tanstack/react-query";
import { useTenantContext } from "./TenantContext";
import { useAuth } from "./AuthContext";

function wsBaseUrl(): string {
  const api = import.meta.env.VITE_API_URL as string | undefined;
  if (api && api.startsWith("http")) {
    return api.replace(/\/api\/v1\/?$/, "");
  }
  return window.location.origin.replace(":5173", ":8081");
}

/** Subscribes to live notification pushes for the active tenant. */
export function useNotificationSocket() {
  const { tenantId } = useTenantContext();
  const { authenticated, getToken } = useAuth();
  const qc = useQueryClient();
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    if (!authenticated || !tenantId) return;

    let active = true;
    let client: Client | null = null;

    (async () => {
      const token = await getToken();
      if (!active) return;

      client = new Client({
        webSocketFactory: () => new SockJS(`${wsBaseUrl()}/ws`) as WebSocket,
        connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
        reconnectDelay: 5000,
        onConnect: () => {
          client?.subscribe(`/topic/tenant/${tenantId}`, () => {
            qc.invalidateQueries({ queryKey: ["notifications"] });
          });
        },
      });
      clientRef.current = client;
      client.activate();
    })();

    return () => {
      active = false;
      client?.deactivate();
      clientRef.current = null;
    };
  }, [authenticated, tenantId, getToken, qc]);
}
