// Combines unit (Vitest, istanbul) and e2e (vite-plugin-istanbul) coverage into
// a single HTML + text-summary report.
//
// The two sources are instrumented by different tools, so a given file's raw
// statementMap differs between them (e.g. @vitest/coverage-istanbul remaps to
// source with a null end column; vite-plugin-istanbul reports a real column),
// and they carry different per-file hashes. Feeding the raw coverage-final.json
// files straight to `nyc report --temp-dir` is NOT safe: on a hash mismatch
// istanbul's merge clobbers rather than unions, so an all-zero copy of a file
// (loaded-but-not-exercised in one suite) wipes out real coverage from the
// other — a file covered only by unit tests then shows 0%.
//
// `nyc merge` normalizes each source (canonicalizes the statementMap, strips the
// hash) before we report. After that the two maps line up by location and
// `nyc report` unions them correctly. This is the documented "combining reports
// from multiple runs" pipeline. See istanbuljs/nyc#combining-reports.
//
// Report-only: never sets an exit threshold, mirroring coverage:e2e. Both inputs
// must be produced on the same machine (see e2e.yml) — file paths only line up
// within a single checkout.
import { existsSync, mkdirSync, readdirSync, rmSync } from 'node:fs'
import { execFileSync } from 'node:child_process'

const TMP = '.nyc_output_combined'
const REPORT_DIR = 'coverage-combined'
const E2E_OUTPUT = '.nyc_output' // e2e: many raw per-test istanbul JSON files
const UNIT_DIR = 'coverage-unit' // unit: vitest's coverage-final.json

const nyc = (args) => execFileSync('nyc', args, { stdio: 'inherit' })
const hasJson = (dir) => existsSync(dir) && readdirSync(dir).some((f) => f.endsWith('.json'))

rmSync(TMP, { recursive: true, force: true })
mkdirSync(TMP, { recursive: true })

let sources = 0

// Merge each source into a single normalized file in TMP. The normalization
// `nyc merge` performs is what makes the cross-tool report correct.
if (hasJson(E2E_OUTPUT)) {
  nyc(['merge', E2E_OUTPUT, `${TMP}/e2e.json`])
  sources++
} else {
  console.warn(`No e2e coverage found in ${E2E_OUTPUT} — combined report will omit it.`)
}

if (hasJson(UNIT_DIR)) {
  nyc(['merge', UNIT_DIR, `${TMP}/unit.json`])
  sources++
} else {
  console.warn(`No unit coverage found in ${UNIT_DIR} — combined report will omit it.`)
}

if (sources === 0) {
  console.error('No coverage inputs found — nothing to combine.')
  process.exit(1)
}

nyc([
  'report',
  '--temp-dir',
  TMP,
  '--report-dir',
  REPORT_DIR,
  '--reporter',
  'html',
  '--reporter',
  'text-summary',
])
