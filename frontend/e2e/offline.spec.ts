import { expect, test } from '@playwright/test'
import { AppPageObject } from './models/AppPageObject'
import {
  deleteTestUserCleanup,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

let user: TestUser
let storageState: StorageState

test.describe('Offline Mode', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState } = await registerAndCaptureStorageState(browser, 'offline'))
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  test('should show offline banner and disable write actions when offline', async ({
    page,
    context,
  }) => {
    const appPage = new AppPageObject(page)

    await page.goto('/')
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
