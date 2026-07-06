/**
 * Remembers the route the user was on when their session expired, so the
 * post-login landing can return there instead of the default collection
 * (UC-099). Persisted in sessionStorage: it survives the full-page OIDC
 * round trip to the identity provider (BR-099-2) but not the tab — exactly
 * the lifetime a return target should have.
 */
const STORAGE_KEY = 'linkweave.postLoginRedirect'

/**
 * Public/auth routes must never be a return target — navigating back to
 * them after login would bounce straight to the default landing anyway,
 * or worse, loop (UC-099 A4).
 */
const EXCLUDED_PATHS = new Set(['/login', '/register', '/privacy'])

/**
 * Only application-internal routes are acceptable targets (BR-099-1):
 * absolute and protocol-relative URLs would turn the login page into an
 * open redirector, and API paths are not navigable routes.
 */
function isInternalRedirectTarget(target: string): boolean {
  if (!target.startsWith('/') || target.startsWith('//')) return false
  const pathname = target.split(/[?#]/, 1)[0]!
  if (pathname === '/api' || pathname.startsWith('/api/')) return false
  return !EXCLUDED_PATHS.has(pathname)
}

/** Saves the return target. Invalid targets are silently dropped (BR-099-4). */
export function savePostLoginRedirect(target: string): void {
  if (!isInternalRedirectTarget(target)) return
  try {
    sessionStorage.setItem(STORAGE_KEY, target)
  } catch {
    // storage unavailable (private mode quota, disabled) — best effort only
  }
}

/** Returns the saved target and removes it, or null if none/invalid. */
export function consumePostLoginRedirect(): string | null {
  try {
    const target = sessionStorage.getItem(STORAGE_KEY)
    sessionStorage.removeItem(STORAGE_KEY)
    return target && isInternalRedirectTarget(target) ? target : null
  } catch {
    return null
  }
}

/** Discards any saved target, e.g. on a deliberate logout (UC-099 A2). */
export function clearPostLoginRedirect(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // storage unavailable — nothing to clear
  }
}
