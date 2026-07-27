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
  // Three Chromiums share the machine with Vite and Quarkus dev, so a render
  // can occasionally miss Playwright's 5s default on a busy box. 10s absorbs
  // that jitter without hiding anything: a genuinely wrong value still fails,
  // it just fails 5s later. Prefer raising this over sprinkling per-assertion
  // timeouts — and never "fix" a wait with a reload, which turns a real
  // read-after-write bug into a green result.
  expect: { timeout: 10_000 },
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  // Three workers by default for local runs: ~45s vs ~103s serial.
  //
  // This was previously flaky, and the cause was not CPU or SQLite: SmallRye
  // @RateLimit buckets are process-wide, so parallel workers drained the
  // per-resource caps and the backend rejected requests outright. No client
  // timeout can wait that out, which is why generous timeouts never fixed it.
  // The caps are raised in RateLimitConst (and further in the %dev profile),
  // and three workers has been green since.
  //
  // On a heavily loaded machine, fall back to `--workers=1` — three Chromiums
  // still need real CPU. CI pins workers=1 for cross-browser reproducibility.
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
