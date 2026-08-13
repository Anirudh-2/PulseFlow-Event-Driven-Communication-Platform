import { test, expect } from "@playwright/test";

/**
 * Smoke E2E: verifies the SPA shell is served.
 * Full authenticated flow (Keycloak login → event → websocket) requires a running compose stack
 * and can be enabled by setting PLAYWRIGHT_BASE_URL and credentials in CI secrets.
 */
test.describe("PulseFlow SPA smoke", () => {
  test("home page loads", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveTitle(/PulseFlow|Vite|React/i);
  });
});
