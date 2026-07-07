import { test, expect, type APIRequestContext } from './fixtures'
import { BASE } from './helpers/api'
import { login } from './helpers/auth'

test.describe('Admin user management', () => {
  test.describe.configure({ mode: 'serial' })

  const ts = Date.now()
  const victimEmail = `e2e-admin-${ts}@example.com`

  async function registerVictim(request: APIRequestContext): Promise<void> {
    const resp = await request.post(`${BASE}/auth/register`, {
      data: {
        email: victimEmail,
        password: 'original-password-123',
        vorname: 'E2E',
        nachname: 'AdminVictim',
      },
    })
    expect(resp.ok(), `register victim failed: ${resp.status()}`).toBeTruthy()
  }

  async function victimUserId(request: APIRequestContext): Promise<string> {
    const list = await request.get(`${BASE}/admin/users`)
    expect(list.ok(), `list users as alice failed: ${list.status()}`).toBeTruthy()
    const body = (await list.json()) as { users: { id: string; email: string }[] }
    const match = body.users.find((u) => u.email === victimEmail)
    expect(match, `victim ${victimEmail} should appear in /admin/users`).toBeTruthy()
    return match!.id
  }

  test('registers a victim user via the public register endpoint', async ({ request }) => {
    // ARRANGE / ACT / ASSERT
    await registerVictim(request)
  })

  test('supporter can list users and reset a password', async ({ page, request }) => {
    // ARRANGE — sign in as alice (the seeded SUPPORT user) via the UI.
    await login(page)

    // ACT — open the user menu entry that only supporters see.
    await page.getByTestId('user-menu-trigger').click()
    await page.getByTestId('user-menu-admin-users').click()
    await expect(page).toHaveURL(/\/admin\/users/)

    // The victim row should appear in the listing.
    const victimRow = page.locator('[data-testid^="admin-user-row-"]').filter({
      hasText: victimEmail,
    })
    await expect(victimRow).toBeVisible()

    // Find the row's reset button and click it.
    const victimId = await victimUserId(request)
    await page.getByTestId(`admin-reset-password-btn-${victimId}`).click()

    // Confirm dialog appears.
    const confirmBtn = page.getByTestId('admin-confirm-reset-btn')
    await expect(confirmBtn).toBeVisible()

    // ACT — confirm the reset.
    await confirmBtn.click()

    // ASSERT — the result dialog shows a 16-char generated password.
    const passwordInput = page.getByTestId('admin-generated-password')
    await expect(passwordInput).toBeVisible()
    await expect(passwordInput).toHaveValue(/^.{16}$/)

    // And the new plaintext actually works for form login.
    const newPassword = await passwordInput.inputValue()
    expect(newPassword).toHaveLength(16)
    const loginResp = await request.post(`${BASE}/j_security_check`, {
      form: { j_username: victimEmail, j_password: newPassword },
      maxRedirects: 0,
    })
    // 302 = successful Quarkus form auth redirect; 200 is also valid.
    expect([200, 302]).toContain(loginResp.status())
  })

  test('non-supporter is bounced away from /admin/users', async ({ browser, request }) => {
    // ARRANGE — register a fresh non-supporter and capture their session.
    const plainEmail = `e2e-plain-${ts}@example.com`
    const reg = await request.post(`${BASE}/auth/register`, {
      data: {
        email: plainEmail,
        password: 'plain-password-123',
        vorname: 'Plain',
        nachname: 'User',
      },
    })
    expect(reg.ok(), `register plain failed: ${reg.status()}`).toBeTruthy()

    const ctx = await browser.newContext({ ignoreHTTPSErrors: true })
    try {
      const loginResp = await ctx.request.post(`${BASE}/j_security_check`, {
        form: { j_username: plainEmail, j_password: 'plain-password-123' },
        maxRedirects: 0,
      })
      expect([200, 302]).toContain(loginResp.status())

      const page = await ctx.newPage()
      // The router guard redirects non-supporters to home (the collection view).
      await page.goto('/admin/users')
      await expect(page).toHaveURL(/\/collections\//, { timeout: 15000 })
    } finally {
      await ctx.close()
    }
  })
})
