import { type Locator, type Page, expect } from '@playwright/test'
import { LoginPageObject } from './LoginPageObject'

export class CollectionSwitcherPageObject {
  private readonly page: Page
  private readonly triggerButton: Locator
  private readonly setDefaultButton: Locator
  private readonly manageButton: Locator

  constructor(page: Page) {
    this.page = page
    this.triggerButton = page.getByTestId('collection-switcher-trigger')
    this.setDefaultButton = page.getByTestId('collection-set-default-btn')
    this.manageButton = page.getByText('Manage collections')
  }

  async loginAndWaitForPage(email = 'alice@example.com', password = 'alice') {
    const loginPage = new LoginPageObject(this.page)
    await loginPage.goto()
    await loginPage.login(email, password)
    await expect(this.page).toHaveURL(/\/collections\//)
  }

  async openSwitcher() {
    await this.triggerButton.click()
  }

  async expectTriggerText(text: string) {
    await expect(this.triggerButton).toContainText(text)
  }

  async expectDropdownVisible() {
    await expect(this.setDefaultButton).toBeVisible()
  }

  async expectDropdownHidden() {
    await expect(this.setDefaultButton).not.toBeVisible()
  }

  async selectCollection(name: string) {
    await this.page.getByRole('button', { name: new RegExp(name) }).click()
  }

  async expectCollectionActive(name: string) {
    await this.openSwitcher()
    const item = this.page.locator('button[class*="bg-primary"]', { hasText: name })
    await expect(item).toBeVisible()
  }

  async expectDefaultBadgeVisible(collectionId: string) {
    await expect(this.page.getByTestId(`collection-default-badge-${collectionId}`)).toBeVisible()
  }

  async expectDefaultBadgeNotVisible(collectionId: string) {
    await expect(this.page.getByTestId(`collection-default-badge-${collectionId}`)).not.toBeVisible()
  }

  async setAsDefault() {
    await this.setDefaultButton.click()
  }

  async expectSetAsDefaultDisabled() {
    await expect(this.setDefaultButton).toBeDisabled()
  }

  async expectShowsThisIsDefault() {
    await expect(this.setDefaultButton).toContainText('This is the default')
  }

  async expectShowsSetAsDefault() {
    await expect(this.setDefaultButton).toContainText('Set as default')
  }

  async closeByClickingOutside() {
    await this.page.keyboard.press('Escape')
  }

  getTriggerButton(): Locator {
    return this.triggerButton
  }
}
