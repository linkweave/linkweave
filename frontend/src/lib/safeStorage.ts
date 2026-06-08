/**
 * Thin wrappers around `localStorage` that never throw. Access can fail in
 * private-browsing mode or when the quota is exceeded; preference helpers
 * should degrade gracefully rather than crash.
 */

/** `localStorage.getItem` that returns `null` instead of throwing when storage is unavailable. */
export function safeGetItem(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

/** `localStorage.setItem` that fails silently when storage is unavailable. */
export function safeSetItem(key: string, value: string): void {
  try {
    localStorage.setItem(key, value)
  } catch {
    // localStorage may be unavailable (private mode, quota exceeded) — fail silently.
  }
}
