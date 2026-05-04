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
 * Normalize a URL for duplicate comparison per BR-080:
 * - Lowercase scheme and host
 * - Strip trailing slashes from path
 * - Sort query parameters
 * - Exclude fragment identifiers
 */
export function normalizeUrl(url: string): string {
  try {
    const parsed = new URL(url)
    let result = `${parsed.protocol.toLowerCase()}//${parsed.hostname.toLowerCase()}`
    if (parsed.port) {
      result += `:${parsed.port}`
    }
    let path = parsed.pathname
    while (path.length > 1 && path.endsWith('/')) {
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
