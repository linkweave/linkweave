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
    this.loginSubmitButton = page.getByRole('button', { name: /sign in/i })
    this.errorMessage = page.getByText('Invalid email or password.')
    this.appTitle = page.getByRole('heading', { name: 'Chainlink' })
  }

  async goto() {
    await this.page.goto('/login')
  }

  async login(email = 'alice@example.com', password = 'alice') {
    await this.emailInput.fill(email)
    await this.passwordInput.fill(password)
    await this.loginSubmitButton.click()
  }

  async expectOnLoginPage() {
    await expect(this.page).toHaveURL(/\/login/)
    await expect(this.appTitle).toBeVisible()
    await expect(this.emailInput).toBeVisible()
    await expect(this.passwordInput).toBeVisible()
    await expect(this.loginSubmitButton).toBeVisible()
  }

  async expectNotOnLoginPage() {
    await expect(this.page).not.toHaveURL(/\/login/)
  }

  async expectErrorVisible() {
    await expect(this.errorMessage).toBeVisible()
  }
}
