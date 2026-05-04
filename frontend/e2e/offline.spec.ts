import { expect, test } from '@playwright/test'
import { AppPageObject } from './models/AppPageObject'
import {
  deleteTestUserCleanup,
  loginAsTestUser,
  registerTestUser,
  type TestUser,
} from './models/TestUser'

let user: TestUser

test.describe('Offline Mode', () => {
  test.beforeAll(async ({ browser }) => {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true })
    try {
      user = await registerTestUser(ctx.request, 'offline')
    } finally {
      await ctx.close()
    }
  })

  test('should show offline banner and disable write actions when offline', async ({
    page,
    context,
  }) => {
    const appPage = new AppPageObject(page)

    await loginAsTestUser(page, user)
    await appPage.expectAuthenticated()

    await context.setOffline(true)
    // Trigger the window 'offline' event manually — context.setOffline blocks
    // network but doesn't always dispatch the event. Reloading while offline
    // races against service-worker install and is unreliable.
    await page.evaluate(() => window.dispatchEvent(new Event('offline')))

    await expect(page.getByText(/you're offline/i)).toBeVisible({ timeout: 10000 })

    const addBookmarkButton = page.getByRole('button', { name: /add bookmark/i })
    await expect(addBookmarkButton).toBeDisabled()

    const newFolderButton = page.getByRole('button', { name: /new folder/i })
    if (await newFolderButton.isVisible()) {
      await expect(newFolderButton).toBeDisabled()
    }

    await context.setOffline(false)
    await page.evaluate(() => window.dispatchEvent(new Event('online')))

    await expect(page.getByText(/you're offline/i)).not.toBeVisible({ timeout: 10000 })
  })

  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
})
