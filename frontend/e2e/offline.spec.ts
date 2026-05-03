import { expect, test } from '@playwright/test'
import { AppPageObject } from './models/AppPageObject'
import { LoginPageObject } from './models/LoginPageObject'

test.describe('Offline Mode', () => {
  test('should show offline banner and disable write actions when offline', async ({ page, context }) => {
    const loginPage = new LoginPageObject(page)
    const appPage = new AppPageObject(page)

    await loginPage.goto()
    await loginPage.login('alice@example.com', 'alice')
    await expect(page).toHaveURL(/\/collections\//)
    await appPage.expectAuthenticated()

    await context.setOffline(true)

    // Trigger the window 'offline' event manually — context.setOffline blocks
    // network but does not always dispatch the event in the page. Reloading
    // while offline races against service worker install and is unreliable.
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
})
