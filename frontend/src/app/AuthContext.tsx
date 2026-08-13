import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import Keycloak from "keycloak-js";

type AuthState = {
  ready: boolean;
  authenticated: boolean;
  token: string | null;
  username: string | null;
  roles: string[];
  login: () => void;
  logout: () => void;
  getToken: () => Promise<string | null>;
};

const AuthContext = createContext<AuthState | null>(null);

const keycloakUrl = import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8080";
const keycloakRealm = import.meta.env.VITE_KEYCLOAK_REALM ?? "pulseflow";
const keycloakClientId = import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? "pulseflow-frontend";

let keycloakSingleton: Keycloak | null = null;

function getKeycloak(): Keycloak {
  if (!keycloakSingleton) {
    keycloakSingleton = new Keycloak({
      url: keycloakUrl,
      realm: keycloakRealm,
      clientId: keycloakClientId,
    });
  }
  return keycloakSingleton;
}

function envTokenOverride(): string | null {
  const token = import.meta.env.VITE_JWT_TOKEN;
  return token && String(token).trim() ? String(token).trim() : null;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [username, setUsername] = useState<string | null>(null);
  const [roles, setRoles] = useState<string[]>([]);

  useEffect(() => {
    const override = envTokenOverride();
    if (override) {
      setToken(override);
      setAuthenticated(true);
      setUsername("env-token");
      setRoles(["ADMIN"]);
      setReady(true);
      return;
    }

    const kc = getKeycloak();
    let cancelled = false;

    kc.init({
      onLoad: "check-sso",
      pkceMethod: "S256",
      checkLoginIframe: false,
    })
      .then((auth) => {
        if (cancelled) return;
        setAuthenticated(Boolean(auth));
        setToken(kc.token ?? null);
        setUsername(kc.tokenParsed?.preferred_username ?? kc.tokenParsed?.name ?? null);
        const realmRoles = (kc.tokenParsed?.realm_access as { roles?: string[] } | undefined)?.roles ?? [];
        setRoles(realmRoles);
        setReady(true);
      })
      .catch((err) => {
        console.error("Keycloak init failed", err);
        if (!cancelled) {
          setAuthenticated(false);
          setReady(true);
        }
      });

    kc.onAuthSuccess = () => {
      setAuthenticated(true);
      setToken(kc.token ?? null);
      setUsername(kc.tokenParsed?.preferred_username ?? null);
      const realmRoles = (kc.tokenParsed?.realm_access as { roles?: string[] } | undefined)?.roles ?? [];
      setRoles(realmRoles);
    };
    kc.onAuthLogout = () => {
      setAuthenticated(false);
      setToken(null);
      setUsername(null);
      setRoles([]);
    };
    kc.onTokenExpired = () => {
      kc.updateToken(30).then((refreshed) => {
        if (refreshed) setToken(kc.token ?? null);
      }).catch(() => kc.login());
    };

    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(() => {
    if (envTokenOverride()) return;
    getKeycloak().login();
  }, []);

  const logout = useCallback(() => {
    if (envTokenOverride()) {
      setAuthenticated(false);
      setToken(null);
      setUsername(null);
      setRoles([]);
      return;
    }
    getKeycloak().logout({ redirectUri: window.location.origin });
  }, []);

  const getToken = useCallback(async () => {
    const override = envTokenOverride();
    if (override) return override;
    const kc = getKeycloak();
    if (!kc.authenticated) return null;
    try {
      await kc.updateToken(30);
      setToken(kc.token ?? null);
      return kc.token ?? null;
    } catch {
      return kc.token ?? null;
    }
  }, []);

  const value = useMemo<AuthState>(
    () => ({ ready, authenticated, token, username, roles, login, logout, getToken }),
    [ready, authenticated, token, username, roles, login, logout, getToken]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}

/** Module-level token getter used by the Axios interceptor. */
let tokenGetter: (() => Promise<string | null>) | null = null;

export function registerAuthTokenGetter(getter: () => Promise<string | null>) {
  tokenGetter = getter;
}

export async function resolveAccessToken(): Promise<string | null> {
  const override = envTokenOverride();
  if (override) return override;
  if (tokenGetter) return tokenGetter();
  return null;
}
