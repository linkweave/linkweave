import { expect, test } from '@playwright/test'
import { LoginPageObject } from './models/LoginPageObject'

test('homepage has title', async ({ page }) => {
  await page.goto('/')

  await expect(page).toHaveTitle(/Chainlink/)
})

test('redirects to login when unauthenticated', async ({ page }) => {
  await page.goto('/')

  await expect(page).toHaveURL(/\/login/)
})

test.describe('Authenticated', () => {
  test.beforeEach(async ({ page }) => {
    const loginPage = new LoginPageObject(page)
    await loginPage.goto()
    await loginPage.login('alice@example.com', 'alice')
    await expect(page).toHaveURL(/\/collections\//)
  })

  test('homepage shows empty state', async ({ page }) => {
    await page.goto('/')

    await expect(page.getByText('No bookmarks yet')).toBeVisible()
  })

  test('sidebar is visible on desktop', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto('/')

    await expect(page.getByText('All Bookmarks')).toBeVisible()
    await expect(page.locator('span').filter({ hasText: 'Tags' })).toBeVisible()
  })

  test('sidebar is off-screen on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 })
    await page.goto('/')

    const sidebar = page.locator('aside')
    await expect(sidebar).toBeVisible()
  })

  test('can click add bookmark button', async ({ page }) => {
    await page.goto('/')

    const button = page.getByRole('button', { name: /add bookmark/i })
    await expect(button).toBeVisible()
  })
})
