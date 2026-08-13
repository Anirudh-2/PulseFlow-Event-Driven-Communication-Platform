import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { test, expect, vi } from "vitest";
import App from "./App";
import { TenantProvider } from "./TenantContext";

vi.mock("./AuthContext", () => ({
  useAuth: () => ({
    ready: true,
    authenticated: true,
    token: "test-token",
    username: "pulseflow-admin",
    roles: ["ADMIN"],
    login: vi.fn(),
    logout: vi.fn(),
    getToken: async () => "test-token",
  }),
  registerAuthTokenGetter: vi.fn(),
  resolveAccessToken: async () => "test-token",
}));

vi.mock("./useNotificationSocket", () => ({
  useNotificationSocket: () => undefined,
}));

test("renders application title", () => {
  const queryClient = new QueryClient();
  render(
    <QueryClientProvider client={queryClient}>
      <TenantProvider>
        <MemoryRouter>
          <App />
        </MemoryRouter>
      </TenantProvider>
    </QueryClientProvider>
  );
  expect(screen.getByText("PulseFlow")).toBeTruthy();
  expect(screen.getByText("Channels")).toBeTruthy();
});
