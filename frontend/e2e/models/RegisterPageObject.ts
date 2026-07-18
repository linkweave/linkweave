import { type Locator, type Page, expect } from '@playwright/test'

export class RegisterPageObject {
  private readonly page: Page
  readonly firstNameInput: Locator
  readonly lastNameInput: Locator
  readonly emailInput: Locator
  readonly passwordInput: Locator
  readonly confirmPasswordInput: Locator
  readonly submitButton: Locator
  readonly passwordMismatchError: Locator
  readonly signInLink: Locator

  constructor(page: Page) {
    this.page = page
    this.firstNameInput = page.getByLabel('First Name')
    this.lastNameInput = page.getByLabel('Last Name')
    this.emailInput = page.getByLabel('Email')
    this.passwordInput = page.getByRole('textbox', { name: 'Password', exact: true })
    this.confirmPasswordInput = page.getByRole('textbox', { name: 'Confirm Password' })
    this.submitButton = page.getByRole('button', { name: /create account/i })
    this.passwordMismatchError = page.getByText('Passwords do not match.')
    this.signInLink = page.getByRole('link', { name: 'Sign in' })
  }

  async goto() {
    await this.page.goto('/register')
  }

  async register(
    firstName = 'Test',
    lastName = 'User',
    email = `test-${Date.now()}@example.com`,
    password = 'test-password-123',
  ) {
    await this.firstNameInput.fill(firstName)
    await this.lastNameInput.fill(lastName)
    await this.emailInput.fill(email)
    await this.passwordInput.fill(password)
    await this.confirmPasswordInput.fill(password)
    await this.submitButton.click()
  }

  async expectOnRegisterPage() {
    await expect(this.page).toHaveURL(/\/register/, { timeout: 15000 })
    await expect(this.firstNameInput).toBeVisible({ timeout: 10000 })
    await expect(this.emailInput).toBeVisible({ timeout: 10000 })
    await expect(this.submitButton).toBeVisible({ timeout: 10000 })
  }

  async expectOnLoginPage() {
    // After successful registration the SPA redirects to /login. Under
    // parallel e2e load the backend's registration write + redirect can lag,
    // so give the URL change a generous budget.
    await expect(this.page).toHaveURL(/\/login/, { timeout: 30000 })
  }
}
