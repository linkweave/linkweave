import { test } from './fixtures'
import { AppPageObject } from './models/AppPageObject'
import { LoginPageObject } from './models/LoginPageObject'

test.describe('Login Flow', () => {
  test('should redirect to login page when unauthenticated', async ({ page }) => {
    const loginPage = new LoginPageObject(page)
    const appPage = new AppPageObject(page)

    // Navigate to the home page
    await appPage.goto()

    // Should be redirected to /login
    await loginPage.expectOnLoginPage()
  })

  test('should show error message with invalid credentials', async ({ page }) => {
    // ARRANGE
    const loginPage = new LoginPageObject(page)
    await loginPage.goto()

    // ACT
    await loginPage.login('wrong@example.com', 'wrong-password')

    // ASSERT
    // Error message should be visible
    await loginPage.expectErrorVisible()
  })

  test('should login successfully and redirect to home', async ({ page }) => {
    // ARRANGE
    const loginPage = new LoginPageObject(page)
    const appPage = new AppPageObject(page)

    await loginPage.goto()

    // ACT
    // Use test credentials from application.properties
    await loginPage.login('alice@example.com', 'alice')

    // ASSERT
    // Should be redirected to home (which might then redirect to a collection)
    // We check if we are no longer on the login page
    await loginPage.expectNotOnLoginPage()

    // Check if the sidebar or some authenticated element is visible
    await appPage.expectAuthenticated()
  })

  test('should be able to logout', async ({ page }) => {
    const loginPage = new LoginPageObject(page)
    const appPage = new AppPageObject(page)

    // First login
    await loginPage.goto()
    await loginPage.login('alice@example.com', 'alice')

    await appPage.expectAuthenticated()

    // Open User Menu by clicking on display name (Alice User)
    await appPage.logout()

    // Should be redirected back to login
    await loginPage.expectOnLoginPage()
  })
})
