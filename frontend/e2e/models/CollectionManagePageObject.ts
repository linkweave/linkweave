import { type Locator, type Page, expect } from '@playwright/test'
import { LoginPageObject } from './LoginPageObject'

export class CollectionManagePageObject {
  readonly page: Page
  readonly backButton: Locator
  readonly createButton: Locator
  readonly createNameInput: Locator
  readonly editNameInput: Locator
  readonly editFaviconAllowlistInput: Locator
  readonly deleteConfirmInput: Locator

  constructor(page: Page) {
    this.page = page
    this.backButton = page.getByTestId('collection-manage-back-btn')
    this.createButton = page.getByTestId('collection-manage-create-btn')
    this.createNameInput = page.getByTestId('create-collection-name-input')
    this.editNameInput = page.getByTestId('edit-collection-name-input')
    this.editFaviconAllowlistInput = page.getByTestId('edit-collection-favicon-allowlist-input')
    this.deleteConfirmInput = page.getByTestId('delete-confirm-name-input')
  }

  async loginAndNavigate(email = 'alice@example.com', password = 'alice') {
    const loginPage = new LoginPageObject(this.page)
    await loginPage.goto()
    await loginPage.login(email, password)
    await expect(this.page).toHaveURL(/\/collections\//)

    await this.page.goto('/manage/collections')
    await expect(this.page).toHaveURL(/\/manage\/collections/)
    await this.page.locator('[data-testid^="collection-row-"], :text("noCollections")').waitFor({ state: 'visible' }).catch(() => {})
    await expect(this.createButton).toBeVisible()
  }

  async getCollectionIdByName(name: string): Promise<string> {
    const row = this.page.locator('[data-testid^="collection-row-"]', { hasText: name })
    const testId = await row.getAttribute('data-testid')
    return testId!.replace('collection-row-', '')
  }

  async createCollection(name: string) {
    await this.createButton.click()
    await expect(this.createNameInput).toBeVisible()
    await this.createNameInput.fill(name)
    await this.page.getByTestId('collection-create-submit-btn').click()
    await expect(this.createNameInput).not.toBeVisible()
  }

  async editCollection(collectionId: string, newName: string) {
    await this.page.getByTestId(`collection-edit-btn-${collectionId}`).click()
    await expect(this.editNameInput).toBeVisible()
    await this.editNameInput.clear()
    await this.editNameInput.fill(newName)
    await this.page.getByTestId('collection-edit-submit-btn').click()
    await expect(this.editNameInput).not.toBeVisible()
  }

  async openEditDialog(collectionId: string) {
    await this.page.getByTestId(`collection-edit-btn-${collectionId}`).click()
    await expect(this.editNameInput).toBeVisible()
  }

  /**
   * Opens the edit dialog and waits for the async collection-info fetch
   * to complete so the favicon-allowlist textarea reflects the persisted value.
   */
  async openEditDialogAndWaitForAllowlist(collectionId: string, expectedValue?: string) {
    await this.openEditDialog(collectionId)
    await expect(this.editFaviconAllowlistInput).toBeVisible()
    if (expectedValue !== undefined) {
      await expect(this.editFaviconAllowlistInput).toHaveValue(expectedValue, { timeout: 10000 })
    } else {
      // Wait for the textarea value to stabilise (the dialog first resets to ''
      // then populates from the API). We wait for it to become non-empty or
      // confirm it stays empty after the API round-trip.
      await this.page.waitForTimeout(500)
    }
  }

  async submitEditDialog() {
    await this.page.getByTestId('collection-edit-submit-btn').click()
    await expect(this.editNameInput).not.toBeVisible()
  }

  async editFaviconAllowlist(collectionId: string, allowlist: string) {
    await this.openEditDialog(collectionId)
    await expect(this.editFaviconAllowlistInput).toBeVisible()
    await this.editFaviconAllowlistInput.clear()
    if (allowlist) {
      await this.editFaviconAllowlistInput.fill(allowlist)
    }
    await this.submitEditDialog()
  }

  async deleteCollection(collectionId: string, collectionName: string) {
    await this.page.getByTestId(`collection-delete-btn-${collectionId}`).click()
    await expect(this.deleteConfirmInput).toBeVisible()
    await this.deleteConfirmInput.fill(collectionName)
    await this.page.getByTestId('collection-delete-submit-btn').click()
    await expect(this.deleteConfirmInput).not.toBeVisible()
  }

  async setAsDefault(collectionId: string) {
    await this.page.getByTestId(`collection-set-default-btn-${collectionId}`).click()
  }

  collectionSetDefaultBtn(collectionId: string): Locator {
    return this.page.getByTestId(`collection-set-default-btn-${collectionId}`)
  }

  async expectCollectionVisible(name: string) {
    await expect(this.page.locator('[data-testid^="collection-row-"]', { hasText: name })).toBeVisible()
  }

  async expectCollectionNotVisible(name: string) {
    await expect(this.page.locator('[data-testid^="collection-row-"]', { hasText: name })).not.toBeVisible()
  }

  async goBack() {
    await this.backButton.click()
    await expect(this.page).toHaveURL(/\/collections\//)
  }
}
