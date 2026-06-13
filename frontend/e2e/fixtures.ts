// Thin wrapper around Playwright's `test` that auto-harvests istanbul coverage
// from the page after each test. Specs import `test`/`expect` from here instead
// of '@playwright/test'; everything else is re-exported unchanged.
//
// The app is instrumented by vite-plugin-istanbul (only when E2E_COVERAGE is
// set), exposing window.__coverage__. After each test we read it and drop one
// JSON file into .nyc_output/, which `nyc report` later merges. No-op when
// coverage is disabled.
import { test as base, expect } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { coverageEnabled, NYC_OUTPUT_DIR } from './coverage/options'

const nycDir = path.resolve(process.cwd(), NYC_OUTPUT_DIR)

export const test = base.extend<{ collectCoverage: void }>({
  collectCoverage: [
    async ({ page }, use) => {
      await use()
      if (!coverageEnabled) return
      const coverage = await page.evaluate(
        () => (globalThis as unknown as { __coverage__?: unknown }).__coverage__,
      )
      if (coverage) {
        mkdirSync(nycDir, { recursive: true })
        writeFileSync(path.join(nycDir, `${randomUUID()}.json`), JSON.stringify(coverage))
      }
    },
    { auto: true },
  ],
})

export { expect }
export type { APIRequestContext, Browser, Page } from '@playwright/test'
