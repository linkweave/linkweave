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

const segmenter = new Intl.Segmenter('en', { granularity: 'grapheme' })

/** Ranges terminals render two columns wide: CJK, Hangul, Kana, emoji. */
const WIDE =
  /[ᄀ-ᅟ⺀-〾ぁ-㏿㐀-䶿一-鿿ꀀ-꓏가-힣豈-﫿︰-﹯＀-｠￠-￦]|[\u{1F300}-\u{1FAFF}]|[\u{20000}-\u{3FFFD}]/u

function graphemes(value: string): string[] {
  return [...segmenter.segment(value)].map((entry) => entry.segment)
}

function graphemeWidth(grapheme: string): number {
  return WIDE.test(grapheme) ? 2 : 1
}

/** Terminal columns a string occupies — not its UTF-16 length. */
export function displayWidth(value: string): number {
  let width = 0
  for (const grapheme of graphemes(value)) width += graphemeWidth(grapheme)
  return width
}

/**
 * Cell values come from the server, so they may contain newlines, tabs, or
 * escape sequences. Newlines and tabs break the column layout outright, and a
 * bare ESC would let a bookmark title emit terminal control sequences. Every
 * C0 control and DEL collapses to a single space.
 */
function sanitize(value: string): string {
  return value.replace(/[\u0000-\u001F\u007F]+/gu, ' ')
}

function truncate(value: string): string {
  if (displayWidth(value) <= MAX_CELL_WIDTH) return value
  let out = ''
  let width = 0
  for (const grapheme of graphemes(value)) {
    const next = graphemeWidth(grapheme)
    // Leave one column for the ellipsis.
    if (width + next > MAX_CELL_WIDTH - 1) break
    out += grapheme
    width += next
  }
  return out + '…'
}

/** Renders a plain-text table with padded columns for --format=table. */
export function renderTable(headers: string[], rows: string[][]): string {
  const cells = [headers, ...rows].map((row) => row.map((cell) => truncate(sanitize(cell ?? ''))))
  const widths = headers.map((_, col) =>
    Math.max(...cells.map((row) => displayWidth(row[col] ?? ''))),
  )
  const pad = (cell: string, col: number): string =>
    cell + ' '.repeat(Math.max(0, (widths[col] ?? 0) - displayWidth(cell)))
  const renderRow = (row: string[]) => row.map(pad).join('  ').trimEnd()
  const separator = widths.map((w) => '-'.repeat(w)).join('  ')
  const [headerRow, ...bodyRows] = cells
  return [renderRow(headerRow ?? []), separator, ...bodyRows.map(renderRow)].join('\n')
}
