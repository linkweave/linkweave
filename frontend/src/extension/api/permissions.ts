/**
 * Runtime host-permission handling for the self-hosted API.
 *
 * The extension ships with no hardcoded host_permissions — each user grants
 * access to their own LinkWeave instance origin on demand. Granting happens
 * from a user gesture (options Save click, or the popup's Grant Access button).
 */

/** Outcome of validating a user-entered API URL for a host-permission grant. */
export type ApiUrlCheck =
  | { ok: true; pattern: string }
  | { ok: false; reason: 'invalid-url' | 'not-https' }

/**
 * Validates an API base URL and derives its host-permission match pattern.
 *
 * - Match patterns must NOT include a port (a host grant covers all ports), so
 *   the port is stripped.
 * - Only HTTPS origins are grantable — `optional_host_permissions` is HTTPS-only.
 *
 * Returns a discriminated result so callers can give accurate feedback
 * (invalid URL vs. non-HTTPS vs. permission denied).
 */
export function checkApiUrl(apiUrl: string): ApiUrlCheck {
  let url: URL
  try {
    url = new URL(apiUrl)
  } catch {
    return { ok: false, reason: 'invalid-url' }
  }
  if (!url.hostname) return { ok: false, reason: 'invalid-url' }
  if (url.protocol !== 'https:') return { ok: false, reason: 'not-https' }
  return { ok: true, pattern: `${url.protocol}//${url.hostname}/*` }
}

/** True when the extension already holds host permission for the API origin. */
export async function hasApiPermission(apiUrl: string): Promise<boolean> {
  const check = checkApiUrl(apiUrl)
  if (!check.ok) return false
  try {
    return await chrome.permissions.contains({ origins: [check.pattern] })
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
  const check = checkApiUrl(apiUrl)
  if (!check.ok) return false
  try {
    // request() resolves to true silently when the permission is already held.
    return await chrome.permissions.request({ origins: [check.pattern] })
  } catch {
    return false
  }
}
