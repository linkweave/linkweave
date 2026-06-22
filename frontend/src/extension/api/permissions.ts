/**
 * Runtime host-permission handling for the self-hosted API.
 *
 * The extension ships with no hardcoded host_permissions — each user grants
 * access to their own LinkWeave instance origin on demand. Granting happens
 * from a user gesture (options Save click, or the popup's Grant Access button).
 */

/** Converts an API base URL to a host-permission match pattern (scheme + host + "/*"). */
export function toOriginPattern(apiUrl: string): string | null {
  try {
    // Match patterns must NOT include a port; a host grant covers all ports.
    const { protocol, hostname } = new URL(apiUrl)
    return hostname ? `${protocol}//${hostname}/*` : null
  } catch {
    return null
  }
}

/** True when the extension already holds host permission for the API origin. */
export async function hasApiPermission(apiUrl: string): Promise<boolean> {
  const pattern = toOriginPattern(apiUrl)
  if (!pattern) return false
  try {
    return await chrome.permissions.contains({ origins: [pattern] })
  } catch {
    return false
  }
}

/**
 * Requests host permission for the API origin. MUST run inside a user gesture
 * (e.g. a click handler) — the browser will otherwise reject the prompt.
 * Returns true if the permission is granted (or was already held).
 */
export async function requestApiPermission(apiUrl: string): Promise<boolean> {
  const pattern = toOriginPattern(apiUrl)
  if (!pattern) return false
  try {
    // request() resolves to true silently when the permission is already held.
    return await chrome.permissions.request({ origins: [pattern] })
  } catch {
    return false
  }
}
