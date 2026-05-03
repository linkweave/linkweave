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
