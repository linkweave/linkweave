/**
 * If the value looks like a bare host/path (no colon present) and is non-empty,
 * prepend `https://`. Otherwise return as-is. Whitespace is trimmed.
 *
 * The presence of `:` is treated as the marker of a protocol or port — anything
 * with a colon is left untouched (e.g. `http://...`, `mailto:...`, `file://...`).
 */
export function ensureUrlProtocol(value: string): string {
  const trimmed = value.trim()
  if (!trimmed) return trimmed
  if (trimmed.includes(':')) return trimmed
  return `https://${trimmed}`
}

/**
 * Whether the value parses as an absolute URL: a scheme followed by `//`
 * (e.g. `https://…`, `http://…`). Such a token is a single free-text search
 * term, never an operator, so pasting a URL can never degrade to a match-all
 * operator parse. Deliberately stricter than `parseAbsoluteUrl`: this guards
 * *token shape* for bare pasted terms, and any looser rule (any scheme)
 * would claim `folder:work` — the whole operator grammar — as free text.
 */
export function isAbsoluteUrl(value: string): boolean {
  return /^[a-z][a-z0-9+.-]*:\/\//i.test(value)
}

// The authority runs from after `scheme://` up to the first `/`, `?` or `#`.
// Whitespace there (e.g. `https://two words`) is a forbidden host code point
// per WHATWG, but implementations disagree — Node rejects the URL, Chromium
// accepts it — so we reject it ourselves to keep validity (and with it the
// invalid-syntax flag, the matcher, and unit vs e2e expectations) identical
// in every runtime. Spaces in the path/query are unaffected: those
// percent-encode and parse everywhere.
const AUTHORITY_WHITESPACE_RE = /^[a-z][a-z0-9+.-]*:\/\/[^/?#\s]*\s/i

/**
 * Parse a value as an absolute URL via a WHATWG `new URL` round-trip, or
 * `null` when it does not parse. This is the validity rule for `url:`
 * operator VALUES: it accepts non-hierarchical schemes the app can store via
 * API/import (e.g. `mailto:…`) and rejects values with a malformed authority
 * (e.g. `https://two words`) — cases the prefix check above would pass. The
 * matcher and the invalid-syntax flag both use this, so they can never
 * disagree about which `url:` queries are valid.
 */
export function parseAbsoluteUrl(value: string): URL | null {
  if (AUTHORITY_WHITESPACE_RE.test(value)) return null
  try {
    return new URL(value)
  } catch {
    return null
  }
}

/**
 * Normalize a URL for duplicate comparison per BR-080:
 * - Lowercase scheme and host
 * - Strip trailing slashes from path, including a lone root "/" so that
 *   `example.com` and `example.com/` compare equal
 * - Sort query parameters
 * - Exclude fragment identifiers
 *
 * Kept in lockstep with the backend `ImportUrlNormalizer` (UC-096) so client
 * and server agree on what counts as a duplicate.
 */
export function normalizeUrl(url: string): string {
  try {
    const parsed = new URL(url)
    let result = `${parsed.protocol.toLowerCase()}//${parsed.hostname.toLowerCase()}`
    if (parsed.port) {
      result += `:${parsed.port}`
    }
    let path = parsed.pathname
    while (path.endsWith('/')) {
      path = path.slice(0, -1)
    }
    result += path
    if (parsed.search && parsed.search.length > 1) {
      const params = parsed.search.slice(1).split('&').sort()
      result += `?${params.join('&')}`
    }
    return result
  } catch {
    return url.trim().toLowerCase()
  }
}
