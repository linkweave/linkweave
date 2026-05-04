import { type APIRequestContext, type Browser, expect, type Page } from '@playwright/test'
import { LoginPageObject } from './LoginPageObject'

export type TestUser = { email: string; password: string }

const REGISTER_URL = '/api/auth/register'
const DELETE_ME_URL = '/api/auth/me'

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
  for (let attempt = 0; attempt < 3; attempt++) {
    const resp = await request.post(REGISTER_URL, {
      data: { email, password, vorname: 'E2E', nachname: safeSlug },
    })
    lastStatus = resp.status()
    if (resp.ok()) return { email, password }
    lastBody = await resp.text().catch(() => '')
    // Don't retry 4xx (validation / conflict) — only transient server errors.
    if (lastStatus < 500) break
    await new Promise((r) => setTimeout(r, 500))
  }
  throw new Error(`registerTestUser failed: ${lastStatus} ${lastBody}`)
}

/**
 * Logs the user in via the UI form and returns the auto-provisioned default
 * collection id (read from the post-login redirect URL).
 */
export async function loginAsTestUser(page: Page, user: TestUser): Promise<string> {
  const loginPage = new LoginPageObject(page)

  // In a Playwright worker, the BrowserContext (and its cookies, IndexedDB,
  // service-worker registration, in-memory Pinia state etc.) persists across
  // tests in the same describe — both serial mode and even the default
  // parallel describes inside a single worker. The previous test's session
  // cookie + cached UserInfo + active SW make /login auto-redirect to
  // /collections and the Email field never appears. Nuke everything.
  await page.context().clearCookies()
  // Land on the app origin so we can run in-page cleanup against the real
  // SPA storage scope.
  await loginPage.goto()
  await page.evaluate(async () => {
    try {
      // Unregister any service worker so cached responses can't fool the
      // auth store on the next navigation.
      if ('serviceWorker' in navigator) {
        const regs = await navigator.serviceWorker.getRegistrations()
        await Promise.all(regs.map((r) => r.unregister()))
      }
      const dbs =
        (await (
          indexedDB as unknown as { databases?: () => Promise<{ name?: string }[]> }
        ).databases?.()) ?? []
      await Promise.all(
        dbs.map((db) =>
          db.name
            ? new Promise<void>((resolve) => {
                const req = indexedDB.deleteDatabase(db.name!)
                req.onsuccess = req.onerror = req.onblocked = () => resolve()
              })
            : Promise.resolve(),
        ),
      )
      localStorage.clear()
      sessionStorage.clear()
    } catch (err: unknown) {
      console.error('loginAsTestUser: failed to clear storage', err)
    }
  })

  // After the storage wipe, a final reload to make sure the SPA boots
  // without any leftover Pinia/auth state. Retry the login click twice if
  // the form bounces back to /login under transient SQLite write contention.
  for (let attempt = 0; attempt < 3; attempt++) {
    await page.goto('/login', { waitUntil: 'load' })
    await page.getByLabel('Email').waitFor({ state: 'visible', timeout: 15000 })
    await loginPage.login(user.email, user.password)
    try {
      await expect(page).toHaveURL(/\/collections\//, { timeout: 12000 })
      const match = page.url().match(/\/collections\/([^/?#]+)/)
      if (!match) throw new Error(`could not parse collection id from URL: ${page.url()}`)
      return match[1]
    } catch (err) {
      if (attempt === 2) throw err
      await page.waitForTimeout(750)
    }
  }
  throw new Error('unreachable')
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
 * Standard `afterAll` cleanup: opens a fresh browser context, logs in as the
 * given user, and hard-deletes them via DELETE /auth/me. Use directly as the
 * `afterAll` callback to avoid copy-pasting the same boilerplate in every
 * spec:
 *
 * ```ts
 * test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
 * ```
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
  const page = await ctx.newPage()
  try {
    await loginAsTestUser(page, user)
    await deleteTestUser(page.request)
  } finally {
    await ctx.close()
  }
}
