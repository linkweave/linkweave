import { expect, test } from '@playwright/test'
import { LoginPageObject } from './models/LoginPageObject'

test.describe('App Shell', () => {
  test('should redirect to login page when unauthenticated', async ({ page }) => {
    await page.goto('/')
    await expect(page).toHaveURL(/\/login/)
  })

  test('should show app title on login page', async ({ page }) => {
    const loginPage = new LoginPageObject(page)
    await loginPage.goto()
    await expect(loginPage.appTitle).toBeVisible()
  })

  test('should show sidebar after login', async ({ page }) => {
    const loginPage = new LoginPageObject(page)
    await loginPage.goto()
    await loginPage.login('alice@example.com', 'alice')

    await expect(page).toHaveURL(/\/collections\//)
    await expect(page.getByTestId('all-bookmarks-btn')).toBeVisible()
  })
})
