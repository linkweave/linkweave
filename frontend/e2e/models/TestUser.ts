import {
  type APIRequestContext,
  type Browser,
  type BrowserContext,
  expect,
} from '@playwright/test'
import { mkdirSync, rmdirSync, statSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'

export type TestUser = { email: string; password: string }
export type StorageState = Awaited<ReturnType<BrowserContext['storageState']>>

const REGISTER_URL = '/api/auth/register'
const DELETE_ME_URL = '/api/auth/me'
const LOGIN_URL = '/api/j_security_check'
const ME_URL = '/api/auth/me'

// ── Cross-worker write lock ──────────────────────────────────────────────────
//
// Playwright workers are separate processes, and most specs register a user in
// `beforeAll` — so a parallel run starts with a thundering herd of
// registration + auto-provisioning writes against the single dev SQLite DB,
// which is exactly when it starts throwing transient 401/500s. Serializing
// only these short (~100ms) auth phases across workers removes the herd while
// leaving the actual tests fully parallel.
//
// The lock is a directory (mkdir is atomic on every platform). A stale lock —
// e.g. a worker killed mid-phase — is stolen after LOCK_STALE_MS.

const LOCK_DIR = path.join(tmpdir(), 'linkweave-e2e-auth.lock')
const LOCK_STALE_MS = 20_000

/**
 * Options for a helper context that must start UNAUTHENTICATED.
 *
 * `browser.newContext()` inside @playwright/test inherits the enclosing
 * file's `test.use({ storageState })` — and specs point that fixture at a
 * module-level variable assigned in `beforeAll`. When fullyParallel makes a
 * worker re-enter a spec file it ran earlier, `beforeAll` runs again and a
 * bare `newContext()` would silently inherit the PREVIOUS user's session —
 * a user `afterAll` has already hard-deleted, so every request 401s. The
 * explicit empty storageState overrides the inherited fixture value.
 */
function freshContextOptions(): Parameters<Browser['newContext']>[0] {
  return {
    ignoreHTTPSErrors: true,
    storageState: { cookies: [], origins: [] },
  }
}

async function withAuthPhaseLock<T>(fn: () => Promise<T>): Promise<T> {
  const deadline = Date.now() + 2 * LOCK_STALE_MS
  for (;;) {
    try {
      mkdirSync(LOCK_DIR)
      break
    } catch {
      try {
        if (Date.now() - statSync(LOCK_DIR).mtimeMs > LOCK_STALE_MS) {
          rmdirSync(LOCK_DIR)
          continue
        }
      } catch {
        continue // lock vanished between mkdir and stat — try again immediately
      }
      if (Date.now() > deadline) break // never deadlock the suite over the lock
      await new Promise((r) => setTimeout(r, 100 + Math.random() * 150))
    }
  }
  try {
    return await fn()
  } finally {
    try {
      rmdirSync(LOCK_DIR)
    } catch {
      /* already stolen as stale — nothing to release */
    }
  }
}

/**
 * Registers a fresh user via POST /api/auth/register. The email is unique per
 * call (slug + timestamp + random suffix) so concurrent specs never collide.
 *
 * Retries transient 5xx responses — the dev SQLite DB occasionally returns 500
 * under heavy concurrent writes.
 */
export async function registerTestUser(
  request: APIRequestContext,
  specSlug: string,
): Promise<TestUser> {
  const safeSlug =
    specSlug
      .replace(/[^a-z0-9]/gi, '')
      .toLowerCase()
      .slice(0, 24) || 'spec'
  const email = `e2e-${safeSlug}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`
  const password = 'test-password-123'

  let lastStatus = 0
  let lastBody = ''
  for (let attempt = 0; attempt < 5; attempt++) {
    const resp = await request.post(REGISTER_URL, {
      data: { email, password, vorname: 'E2E', nachname: safeSlug },
    })
    lastStatus = resp.status()
    if (resp.ok()) return { email, password }
    lastBody = await resp.text().catch(() => '')
    // 401 on a public registration endpoint is always transient (SQLite write
    // contention under parallel load). 4xx other than 401 are real errors
    // (conflict, validation) and must not be retried.
    if (lastStatus !== 401 && lastStatus < 500) break
    console.warn(`[e2e] registerTestUser → ${lastStatus}, retrying (attempt ${attempt + 1}/5)`)
    await new Promise((r) => setTimeout(r, 800 * (attempt + 1)))
  }
  throw new Error(`registerTestUser failed: ${lastStatus} ${lastBody}`)
}

/**
 * Authenticates against Quarkus form auth (`/j_security_check`) without
 * booting the SPA. The session cookie is stored on the request context, so
 * any subsequent `request.*` calls — and any browser context constructed from
 * `ctx.storageState()` — are authenticated.
 *
 * Retries transient 5xx responses for parity with `registerTestUser`.
 */
export async function loginViaApi(request: APIRequestContext, user: TestUser): Promise<void> {
  let lastStatus = 0
  let lastBody = ''
  for (let attempt = 0; attempt < 5; attempt++) {
    const resp = await request.post(LOGIN_URL, {
      form: { j_username: user.email, j_password: user.password },
      maxRedirects: 0,
    })
    lastStatus = resp.status()
    // Quarkus form auth answers with a 302 to the landing page on success.
    // 200 also counts (some configs).
    if (lastStatus === 302 || lastStatus === 200) return
    lastBody = await resp.text().catch(() => '')
    // 401 can be a transient SQLite-contention response under heavy parallel
    // load — retry with back-off. Other 4xx are real errors and must not retry.
    if (lastStatus !== 401 && lastStatus < 500) break
    console.warn(`[e2e] loginViaApi → ${lastStatus}, retrying (attempt ${attempt + 1}/5)`)
    await new Promise((r) => setTimeout(r, 800 * (attempt + 1)))
  }
  throw new Error(`loginViaApi failed: ${lastStatus} ${lastBody}`)
}

async function fetchDefaultCollectionId(request: APIRequestContext): Promise<string> {
  // /api/auth/me auto-provisions the user's default collection on first access.
  // Under heavy parallel registration, the auto-provision write contends on
  // SQLite and can return 500 — retry transient failures.
  let lastStatus = 0
  let lastBody = ''
  for (let attempt = 0; attempt < 5; attempt++) {
    const resp = await request.get(ME_URL)
    lastStatus = resp.status()
    if (resp.ok()) {
      const body = (await resp.json()) as { defaultCollectionId: string }
      expect(body.defaultCollectionId, '/auth/me returned no defaultCollectionId').toBeTruthy()
      return body.defaultCollectionId
    }
    lastBody = await resp.text().catch(() => '')
    // 401 right after login is transient (SQLite write contention while the
    // session/provisioning writes settle) — retry like the other helpers.
    if (lastStatus !== 401 && lastStatus < 500) break
    console.warn(`[e2e] fetch /auth/me → ${lastStatus}, retrying (attempt ${attempt + 1}/5)`)
    await new Promise((r) => setTimeout(r, 800 * (attempt + 1)))
  }
  throw new Error(`fetch /auth/me failed: ${lastStatus} ${lastBody}`)
}

/**
 * Registers a fresh user, authenticates them via the form-auth endpoint, and
 * captures the resulting session cookie as a Playwright storageState. The
 * caller passes the result into `test.use({ storageState })` so every test in
 * the describe boots already authenticated — no SPA boot, no UI form.
 */
export async function registerAndCaptureStorageState(
  browser: Browser,
  specSlug: string,
): Promise<{ user: TestUser; storageState: StorageState; collectionId: string }> {
  const ctx = await browser.newContext(freshContextOptions())
  try {
    return await withAuthPhaseLock(async () => {
      const user = await registerTestUser(ctx.request, specSlug)
      await loginViaApi(ctx.request, user)
      const collectionId = await fetchDefaultCollectionId(ctx.request)
      const storageState = await ctx.storageState()
      return { user, storageState, collectionId }
    })
  } finally {
    await ctx.close()
  }
}

/**
 * Hard-deletes the currently-authenticated user and everything they own
 * (collections, bookmarks, tags, folders, auto-tag rules, memberships).
 *
 * Best-effort — failures are swallowed so an `afterAll` never masks the real
 * test failure. Caller MUST be logged in as the user being deleted.
 */
export async function deleteTestUser(request: APIRequestContext): Promise<void> {
  await request.delete(DELETE_ME_URL).catch(() => undefined)
}

/**
 * Standard `afterAll` cleanup: opens a fresh API request context, authenticates
 * via form-auth, and hard-deletes the user via DELETE /auth/me. Pure HTTP — no
 * browser page, no SPA boot, no service-worker dance. This keeps the cleanup
 * window to ~50ms instead of several seconds, which removes most of the
 * SQLite-write contention that previously flaked `afterAll`.
 *
 * Takes a getter rather than the user value because module-level `let user`
 * is assigned in `beforeAll`, after the `afterAll` callback is registered.
 */
export async function deleteTestUserCleanup(
  browser: Browser,
  userGetter: () => TestUser | undefined,
): Promise<void> {
  const user = userGetter()
  if (!user) return
  const ctx = await browser.newContext(freshContextOptions())
  try {
    // The delete cascades over everything the user owns — serialized across
    // workers like registration, so it doesn't stall another spec's writes.
    await withAuthPhaseLock(async () => {
      await loginViaApi(ctx.request, user)
      await deleteTestUser(ctx.request)
    })
  } catch {
    // Best-effort: swallow errors so afterAll never masks the real test failure.
  } finally {
    await ctx.close()
  }
}
