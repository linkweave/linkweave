// Pure (store-free) token parsing for the search autocomplete. Kept separate
// from `useSearchAutocomplete` so it can be unit-tested without pulling in the
// Pinia stores (and, transitively, i18n / browser globals).

export const OPS = [
  { trigger: 'tag', full: 'tag:', hintKey: 'opTag' },
  { trigger: 'folder', full: 'folder:', hintKey: 'opFolder' },
  { trigger: 'under', full: 'under:', hintKey: 'opUnder' },
  { trigger: 'property', full: 'property:', hintKey: 'opProperty' },
] as const

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
