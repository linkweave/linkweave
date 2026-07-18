import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'https://local-linkweave.localhost:5173'

export default defineConfig({
  testDir: './e2e',
  // Clears stale coverage before the run (no-op unless E2E_COVERAGE is set).
  globalSetup: './e2e/coverage/global-setup.ts',
  fullyParallel: true,
  // Per-test timeout — bumped from the default 30s because each spec now
  // registers a dedicated test user via the form-auth flow, and under heavy
  // parallel load that round-trip can take longer than the default 30s budget.
  timeout: 90_000,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  // Three workers by default for local runs: finishes the chromium suite in
  // ~1 min vs ~3 min serial. The page objects and helpers all carry bounded
  // timeouts + one-shot reload fallbacks for the data-load waits that used
  // to flake under parallel e2e load, so three workers is now reliable on a
  // typical dev machine (Vite + Quarkus dev + IntelliJ all running).
  //
  // On a heavily loaded machine, fall back to `--workers=1` for deterministic
  // runs — three Chromiums still need real CPU, and the backend is SQLite.
  // CI pins workers=1 separately for cross-browser reproducibility.
  workers: process.env.CI ? 1 : 3,
  reporter: 'html',
  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    ignoreHTTPSErrors: true,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
    {
      name: 'Mobile Chrome',
      use: { ...devices['Pixel 5'] },
    },
    {
      name: 'Mobile Safari',
      use: { ...devices['iPhone 12'] },
    },
  ],
  webServer: {
    command: 'npm run dev',
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    env: { VITE_E2E: 'true' },
    stdout: 'pipe',
    stderr: 'pipe',
    ignoreHTTPSErrors: true,
    timeout: 20 * 1000,
  },
})
