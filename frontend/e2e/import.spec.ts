import { expect, test } from '@playwright/test'
import { LoginPageObject } from './models/LoginPageObject'
import { AppPageObject } from './models/AppPageObject'
import { resolve } from 'node:path'

async function loginAsAlice(page: import('@playwright/test').Page) {
  const loginPage = new LoginPageObject(page)
  await loginPage.goto()
  await loginPage.login()
  await expect(page).toHaveURL(/\/collections\//)
}

test.describe('UC-031: Import Browser Bookmarks', () => {
  test('should import bookmarks from HTML file', async ({ page }) => {
    await loginAsAlice(page)

    const appPage = new AppPageObject(page)
    await appPage.userMenuButton.click()
    await page.getByRole('menuitem').filter({ hasText: /settings/i }).click()

    await page.getByTestId('import-btn').click()

    const importFile = resolve(process.cwd(), 'e2e', 'fixtures', 'test-bookmarks.html')
    await page.locator('#bookmark-file').setInputFiles(importFile)

    await page.getByTestId('import-submit-btn').click()

    await expect(page.locator('#bookmark-file')).not.toBeVisible({ timeout: 10000 })
  })
})
