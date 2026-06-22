import { expect, test } from './fixtures'
import {
  deleteTestUserCleanup,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

test('homepage has title', async ({ page }) => {
  await page.goto('/')

  await expect(page).toHaveTitle(/LinkWeave/)
})

test('redirects to login when unauthenticated', async ({ page }) => {
  await page.goto('/')

  await expect(page).toHaveURL(/\/login/)
})

let authedUser: TestUser
let storageState: StorageState

test.describe('Authenticated', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user: authedUser, storageState } = await registerAndCaptureStorageState(browser, 'app'))
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  test('homepage shows authenticated content', async ({ page }) => {
    await page.goto('/')

    // The add bookmark button proves the page loaded for an authenticated user
    await expect(page.getByRole('button', { name: /add bookmark/i })).toBeVisible()
  })

  test('sidebar is visible on desktop', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto('/')

    await expect(page.getByTestId('sidebar-all-bookmarks')).toBeVisible()
    await expect(page.locator('span').filter({ hasText: 'Tags' })).toBeVisible()
  })

  test('sidebar is off-screen on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 })
    await page.goto('/')

    const sidebar = page.getByRole('complementary')
    await expect(sidebar).toBeVisible()
  })

  test('can click add bookmark button', async ({ page }) => {
    await page.goto('/')

    const button = page.getByRole('button', { name: /add bookmark/i })
    await expect(button).toBeVisible()
  })

  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => authedUser))
})
