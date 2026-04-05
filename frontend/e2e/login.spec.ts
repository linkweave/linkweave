import { expect, test } from '@playwright/test'
import { AppPageObject } from './models/AppPageObject'
import { LoginPageObject } from './models/LoginPageObject'

test.describe('UC-027: Logout', () => {
  test('should redirect to login page when unauthenticated', async ({ page }) => {
    const loginPage = new LoginPageObject(page)
    const appPage = new AppPageObject(page)

    await appPage.goto()

    await loginPage.expectOnLoginPage()
  })

  test('should show error message with invalid credentials', async ({ page }) => {
    const loginPage = new LoginPageObject(page)
    await loginPage.goto()

    await loginPage.login('wrong@example.com', 'wrong-password')

    await loginPage.expectErrorVisible()
  })

  test('should login successfully and redirect to collection', async ({ page }) => {
    const loginPage = new LoginPageObject(page)
    const appPage = new AppPageObject(page)

    await loginPage.goto()
    await loginPage.login('alice@example.com', 'alice')

    await loginPage.expectNotOnLoginPage()
    await appPage.expectAuthenticated()
    await expect(page).toHaveURL(/\/collections\//)
  })

  test('should be able to logout', async ({ page }) => {
    const loginPage = new LoginPageObject(page)
    const appPage = new AppPageObject(page)

    await loginPage.goto()
    await loginPage.login('alice@example.com', 'alice')
    await appPage.expectAuthenticated()

    await appPage.logout()

    await loginPage.expectOnLoginPage()
  })
})
