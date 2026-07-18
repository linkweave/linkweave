import { type Locator, type Page, expect } from '@playwright/test'

export class LoginPageObject {
  private readonly page: Page
  readonly emailInput: Locator
  readonly passwordInput: Locator
  readonly loginSubmitButton: Locator
  readonly errorMessage: Locator
  readonly appTitle: Locator

  constructor(page: Page) {
    this.page = page
    this.emailInput = page.getByLabel('Email')
    this.passwordInput = page.getByLabel('Password')
    this.loginSubmitButton = page.getByRole('button', { name: 'Sign in', exact: true })
    this.errorMessage = page.getByText('Invalid email or password.')
    this.appTitle = page.getByRole('heading', { name: 'LinkWeave' })
  }

  async goto() {
    await this.page.goto('/login')
  }

  async login(email = 'alice@example.com', password = 'alice') {
    await this.emailInput.fill(email)
    await this.passwordInput.fill(password)
    await this.loginSubmitButton.click()
  }

  /**
   * Opens the login page, signs in, and waits until the app lands on a
   * collection. The generous timeout covers parallel e2e load: login is a
   * full SPA boot plus the auth round-trip, and with several workers sharing
   * one dev backend the landing can take well over the usual budget.
   */
  async loginAndLand(email = 'alice@example.com', password = 'alice') {
    await this.goto()
    await this.login(email, password)
    await expect(this.page).toHaveURL(/\/collections\//, { timeout: 30000 })
  }

  async expectOnLoginPage() {
    await expect(this.page).toHaveURL(/\/login/, { timeout: 15000 })
    await expect(this.appTitle).toBeVisible({ timeout: 15000 })
    await expect(this.emailInput).toBeVisible({ timeout: 10000 })
    await expect(this.passwordInput).toBeVisible({ timeout: 10000 })
    await expect(this.loginSubmitButton).toBeVisible({ timeout: 10000 })
  }

  async expectNotOnLoginPage() {
    await expect(this.page).not.toHaveURL(/\/login/, { timeout: 30000 })
  }

  async expectErrorVisible() {
    await expect(this.errorMessage).toBeVisible({ timeout: 15000 })
  }
}
