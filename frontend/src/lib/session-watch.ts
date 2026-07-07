/**
 * Proactive session-expiry detection (UC-098): when the tab regains
 * visibility after being hidden — the classic overnight tab — silently
 * re-validate the session so the user is sent to the login page before
 * their first action fails.
 */

const SESSION_PROBE_PATH = '/api/auth/me'

/**
 * At most one probe per interval (BR-098-2): catches the overnight tab,
 * where the hidden period vastly exceeds this, without generating traffic
 * on rapid tab switching.
 */
const PROBE_THROTTLE_MS = 60_000

type SessionProbeResult = 'valid' | 'expired' | 'unreachable'

/**
 * we use fetch here to probe if the session is still active. This is because in this case
 * we do want to avoid hitting the middleware and it's offline mode detection
 */
async function probeSession(): Promise<SessionProbeResult> {
  try {
    const res = await fetch(SESSION_PROBE_PATH, {
      credentials: 'include',
      cache: 'no-store',
      // Marks the probe as AJAX so an expired session yields a clean 499
      // instead of a 302 to the IDP (see api/client.ts).
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    })
    if (res.status === 401 || res.status === 499) return 'expired'
    if (res.ok) return 'valid'
    return 'unreachable'
  } catch {
    return 'unreachable'
  }
}

let installed = false
let lastProbeAt = 0

/**
 * Installs the visibility listener once. `onExpired` fires only on an
 * explicit 401/499 — network failures and 5xx never count as expiry
 * (BR-098-1), otherwise a merely-offline user would be logged out and
 * lose their offline cache.
 */
export function installSessionExpiryWatch(opts: {
  isSessionAvailable: () => boolean
  onExpired: () => void
}): void {
  if (installed) return
  installed = true

  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState !== 'visible') return
    if (!opts.isSessionAvailable()) return
    const now = Date.now()
    if (now - lastProbeAt < PROBE_THROTTLE_MS) return
    lastProbeAt = now
    void probeSession().then((result) => {
      if (result === 'expired') opts.onExpired()
    })
  })
}
