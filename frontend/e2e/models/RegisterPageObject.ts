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
    this.passwordInput = page.getByLabel('Password')
    this.confirmPasswordInput = page.getByLabel('Confirm Password')
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
    await expect(this.page).toHaveURL(/\/register/)
    await expect(this.firstNameInput).toBeVisible()
    await expect(this.emailInput).toBeVisible()
    await expect(this.submitButton).toBeVisible()
  }

  async expectOnLoginPage() {
    await expect(this.page).toHaveURL(/\/login/)
  }
}
