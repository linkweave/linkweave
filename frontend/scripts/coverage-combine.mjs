// Combines unit (Vitest, istanbul) and e2e (vite-plugin-istanbul) coverage into
// a single HTML + text-summary report.
//
// Both inputs are istanbul-format JSON, so nyc can merge them: e2e drops raw
// per-test maps into .nyc_output, and `coverage:unit` writes one merged map to
// coverage-unit/coverage-final.json. We gather both into one temp dir and let
// `nyc report` merge everything it finds there.
//
// Report-only: this never sets an exit threshold, mirroring coverage:e2e. The
// two datasets must be produced on the same machine (see e2e.yml) — their file
// paths only line up within a single checkout.
import { cpSync, existsSync, mkdirSync, rmSync } from 'node:fs'
import { execFileSync } from 'node:child_process'

const TMP = '.nyc_output_combined'
const REPORT_DIR = 'coverage-combined'
const E2E_OUTPUT = '.nyc_output'
const UNIT_FINAL = 'coverage-unit/coverage-final.json'

rmSync(TMP, { recursive: true, force: true })
mkdirSync(TMP, { recursive: true })

let sources = 0

// e2e: raw per-test istanbul JSON files.
if (existsSync(E2E_OUTPUT)) {
  cpSync(E2E_OUTPUT, TMP, { recursive: true })
  sources++
} else {
  console.warn(`No e2e coverage found at ${E2E_OUTPUT} — combined report will omit it.`)
}

// unit: vitest's single merged map. Rename so it can't collide with an e2e file.
if (existsSync(UNIT_FINAL)) {
  cpSync(UNIT_FINAL, `${TMP}/unit-final.json`)
  sources++
} else {
  console.warn(`No unit coverage found at ${UNIT_FINAL} — combined report will omit it.`)
}

if (sources === 0) {
  console.error('No coverage inputs found — nothing to combine.')
  process.exit(1)
}

execFileSync(
  'nyc',
  [
    'report',
    '--temp-dir',
    TMP,
    '--report-dir',
    REPORT_DIR,
    '--reporter',
    'html',
    '--reporter',
    'text-summary',
  ],
  { stdio: 'inherit' },
)
