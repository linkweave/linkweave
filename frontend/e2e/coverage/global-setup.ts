import { rmSync } from 'node:fs'
import path from 'node:path'
import { COVERAGE_REPORT_DIR, coverageEnabled, NYC_OUTPUT_DIR } from './options'

// Clears stale per-test coverage and the previous report so a run only reflects
// the current suite. No-op unless E2E_COVERAGE is set.
export default async function globalSetup() {
  if (!coverageEnabled) return
  for (const dir of [NYC_OUTPUT_DIR, COVERAGE_REPORT_DIR]) {
    rmSync(path.resolve(process.cwd(), dir), { recursive: true, force: true })
  }
}
