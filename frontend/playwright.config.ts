import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'https://local-chainlink.localhost:5173'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  // Per-test timeout — bumped from the default 30s because each spec now
  // registers a dedicated test user via the form-auth flow, and under heavy
  // parallel load that round-trip can take longer than the default 30s budget.
  timeout: 90_000,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  // Cap parallel workers locally. The dev SQLite DB serializes writes and
  // returns 5xx/401 responses under high concurrent registration/login load.
  // The API-only auth flow (registerAndCaptureStorageState) is fast enough
  // that the default worker count (~CPU cores) saturates the backend.
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
