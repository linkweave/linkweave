// Pure (store-free) token parsing for the search autocomplete. Kept separate
// from `useSearchAutocomplete` so it can be unit-tested without pulling in the
// Pinia stores (and, transitively, i18n / browser globals).

import { type OperatorDef, OPERATOR_DEFS } from '@/lib/searchOperators'

type DiscoverableDef = Extract<OperatorDef, { discoverable: true }>
const isDiscoverable = (d: OperatorDef): d is DiscoverableDef => d.discoverable === true

// Derived from the parser's operator table (UC-070 BR-070-1) — the
// autocomplete never defines its own operator list, so the two cannot drift.
export const OPS = OPERATOR_DEFS.filter(isDiscoverable).map((d) => ({
  trigger: d.key,
  full: `${d.key}:`,
  hintKey: d.hintKey,
}))

export interface CursorToken {
  token: string // raw token the cursor sits on
  colonToken: string // token with an implied trailing colon for bare operator names
  range: [number, number] // [start, end] slice of the query this token covers
}

/**
 * Locates the whitespace-delimited token under the cursor and the query range it
 * covers. Also normalizes a bare operator name (e.g. "folder") to its colon form
 * ("folder:") so callers can treat "folder" and "folder:" identically — the
 * search auto-applies as the user types, so there is no Enter-to-search to hijack.
 */
export function tokenAtCursor(query: string, cursor: number): CursorToken {
  const before = query.slice(0, cursor)
  const lastSp = Math.max(before.lastIndexOf(' '), before.lastIndexOf('\t'))
  const tokenStart = lastSp + 1
  const token = before.slice(tokenStart)
  const after = query.slice(cursor)
  const nextSp = after.search(/\s/)
  const tokenEnd = cursor + (nextSp === -1 ? after.length : nextSp)
  const colonToken =
    !token.includes(':') && OPS.some((op) => op.trigger === token.toLowerCase())
      ? token + ':'
      : token
  return { token, colonToken, range: [tokenStart, tokenEnd] }
}
