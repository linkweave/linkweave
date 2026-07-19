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
  // One worker by default: deterministic ~3 min runs. `--workers=3` finishes
  // in ~1 min on an otherwise idle machine, but three Chromiums + Vite +
  // Quarkus dev + SQLite need most of the machine — with an IDE or other
  // load running alongside, specs start missing their navigation/visibility
  // timeouts and fail spuriously. Opt in explicitly when the machine is quiet.
  workers: 1,
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
