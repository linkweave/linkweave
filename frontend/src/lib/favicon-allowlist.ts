/**
 * Per-collection favicon allowlist matcher.
 *
 * Hosts in the allowlist (e.g. `intranet.local`, `*.mycompany.domain`) are
 * loaded directly by the browser via `<img src="https://host/favicon.ico">`,
 * since the backend can't reach them. Everything else goes through the
 * server-side proxy with SSRF guards. The backend stores the allowlist
 * already normalized (lowercased, trimmed, one pattern per line).
 */

export function parseAllowlist(raw: string | null | undefined): string[] {
  if (!raw) return []
  return raw
    .split('\n')
    .map((p) => p.trim().toLowerCase())
    .filter((p) => p.length > 0)
}

export function matchesAllowlist(host: string, patterns: readonly string[]): boolean {
  const h = host.toLowerCase()
  for (const pattern of patterns) {
    if (pattern.startsWith('*.')) {
      const suffix = pattern.slice(1) // ".mycompany.domain"
      if (h.endsWith(suffix) && h.length > suffix.length) return true
      if (h === suffix.slice(1)) return true // bare apex matches "*.foo"
    } else if (h === pattern) {
      return true
    }
  }
  return false
}

export function hostnameOf(url: string): string | null {
  try {
    return new URL(url).hostname
  } catch {
    return null
  }
}
