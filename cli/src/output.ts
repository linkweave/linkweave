import { CliError, EXIT_USAGE } from './errors'

export const OUTPUT_FORMATS = ['table', 'json', 'ids'] as const

export type OutputFormat = (typeof OUTPUT_FORMATS)[number]

const FORMATS: ReadonlySet<string> = new Set(OUTPUT_FORMATS)

export function parseFormat(value: string): OutputFormat {
  if (!FORMATS.has(value)) {
    throw new CliError(
      `Invalid format '${value}'. Expected one of: table, json, ids.`,
      EXIT_USAGE,
    )
  }
  return value as OutputFormat
}

const MAX_CELL_WIDTH = 60

function truncate(value: string): string {
  return value.length > MAX_CELL_WIDTH ? value.slice(0, MAX_CELL_WIDTH - 1) + '…' : value
}

/** Renders a plain-text table with padded columns for --format=table. */
export function renderTable(headers: string[], rows: string[][]): string {
  const cells = [headers, ...rows].map((row) => row.map((cell) => truncate(cell ?? '')))
  const widths = headers.map((_, col) =>
    Math.max(...cells.map((row) => (row[col] ?? '').length)),
  )
  const renderRow = (row: string[]) =>
    row.map((cell, col) => cell.padEnd(widths[col] ?? 0)).join('  ').trimEnd()
  const separator = widths.map((w) => '-'.repeat(w)).join('  ')
  const [headerRow, ...bodyRows] = cells
  return [renderRow(headerRow ?? []), separator, ...bodyRows.map(renderRow)].join('\n')
}
