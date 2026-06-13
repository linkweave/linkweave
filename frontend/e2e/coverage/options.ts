// Coverage is opt-in via E2E_COVERAGE so normal e2e runs stay fast and the app
// is only instrumented (by vite-plugin-istanbul) when we actually want a report.
// Read directly from process.env here and in vite.config.ts so the same flag
// drives both instrumentation and harvesting.
export const coverageEnabled =
  process.env.E2E_COVERAGE === 'true' || process.env.E2E_COVERAGE === '1'

// nyc reads per-test istanbul JSON from here; the report writes to coverage-e2e.
export const NYC_OUTPUT_DIR = '.nyc_output'
export const COVERAGE_REPORT_DIR = 'coverage-e2e'
