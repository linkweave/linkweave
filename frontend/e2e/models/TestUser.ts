import {
  type APIRequestContext,
  type Browser,
  type BrowserContext,
  expect,
} from '@playwright/test'

export type TestUser = { email: string; password: string }
export type StorageState = Awaited<ReturnType<BrowserContext['storageState']>>

const REGISTER_URL = '/api/auth/register'
const DELETE_ME_URL = '/api/auth/me'
const LOGIN_URL = '/api/j_security_check'
const ME_URL = '/api/auth/me'

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
  for (let attempt = 0; attempt < 3; attempt++) {
    const resp = await request.get(ME_URL)
    lastStatus = resp.status()
    if (resp.ok()) {
      const body = (await resp.json()) as { defaultCollectionId: string }
      expect(body.defaultCollectionId, '/auth/me returned no defaultCollectionId').toBeTruthy()
      return body.defaultCollectionId
    }
    lastBody = await resp.text().catch(() => '')
    if (lastStatus < 500) break
    await new Promise((r) => setTimeout(r, 500))
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
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true })
  try {
    const user = await registerTestUser(ctx.request, specSlug)
    await loginViaApi(ctx.request, user)
    const collectionId = await fetchDefaultCollectionId(ctx.request)
    const storageState = await ctx.storageState()
    return { user, storageState, collectionId }
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
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true })
  try {
    await loginViaApi(ctx.request, user)
    await deleteTestUser(ctx.request)
  } catch {
    // Best-effort: swallow errors so afterAll never masks the real test failure.
  } finally {
    await ctx.close()
  }
}
