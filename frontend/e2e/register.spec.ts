import { test } from '@playwright/test'
import { AppPageObject } from './models/AppPageObject'
import { LoginPageObject } from './models/LoginPageObject'
import { RegisterPageObject } from './models/RegisterPageObject'

test.describe('Registration Flow', () => {
  test('should display registration form with all fields', async ({ page }) => {
    const registerPage = new RegisterPageObject(page)
    await registerPage.goto()
    await registerPage.expectOnRegisterPage()
  })

  test('should show password mismatch error', async ({ page }) => {
    const registerPage = new RegisterPageObject(page)
    await registerPage.goto()

    await registerPage.firstNameInput.fill('Test')
    await registerPage.lastNameInput.fill('User')
    await registerPage.emailInput.fill('mismatch@example.com')
    await registerPage.passwordInput.fill('password123')
    await registerPage.confirmPasswordInput.fill('different456')

    await expect(registerPage.passwordMismatchError).toBeVisible()
  })

  test('should navigate to login page via sign in link', async ({ page }) => {
    const registerPage = new RegisterPageObject(page)
    await registerPage.goto()

    await registerPage.signInLink.click()

    const loginPage = new LoginPageObject(page)
    await loginPage.expectOnLoginPage()
  })

  test('should register and then login successfully', async ({ page }) => {
    const registerPage = new RegisterPageObject(page)
    const loginPage = new LoginPageObject(page)
    const appPage = new AppPageObject(page)

    const email = `e2e-${Date.now()}@example.com`
    const password = 'test-password-123'

    await registerPage.goto()
    await registerPage.register('E2E', 'Tester', email, password)

    await registerPage.expectOnLoginPage()

    await loginPage.login(email, password)

    await loginPage.expectNotOnLoginPage()
    await appPage.expectAuthenticated()
  })

  test('should register, logout, and login again', async ({ page }) => {
    const registerPage = new RegisterPageObject(page)
    const loginPage = new LoginPageObject(page)
    const appPage = new AppPageObject(page)

    const email = `e2e-relogin-${Date.now()}@example.com`
    const password = 'test-password-123'

    await registerPage.goto()
    await registerPage.register('E2E', 'Relogin', email, password)

    await registerPage.expectOnLoginPage()
    await loginPage.login(email, password)
    await appPage.expectAuthenticated()

    await appPage.logout()
    await loginPage.expectOnLoginPage()

    await loginPage.login(email, password)
    await appPage.expectAuthenticated()
  })
})
