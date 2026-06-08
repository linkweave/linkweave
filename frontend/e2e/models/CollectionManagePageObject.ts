import { expect, type Locator, type Page } from '@playwright/test'
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
    await new LoginPageObject(this.page).loginAndLand(email, password)
    await this.navigate()
  }

  async navigate() {
    await this.page.goto('/manage/collections')
    await expect(this.page).toHaveURL(/\/manage\/collections/, { timeout: 15000 })
    await this.page
      .locator('[data-testid^="collection-row-"], :text("noCollections")')
      .waitFor({ state: 'visible' })
      .catch(() => {})
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
    const submitBtn = this.page.getByTestId('collection-create-submit-btn')
    await expect(submitBtn).toBeEnabled()
    const createResp = this.page.waitForResponse(
      (r) => r.url().endsWith('/api/collections') && r.request().method() === 'POST',
      { timeout: 30000 },
    )
    await submitBtn.click()
    const resp = await createResp.catch(() => null)
    if (resp && !resp.ok()) {
      throw new Error(`createCollection POST failed: ${resp.status()}`)
    }
    // The dialog only closes after createCollection's follow-up fetchCollections
    // completes. Under heavy parallel load the list-fetch can be slow, so use a
    // generous timeout instead of the default 5s.
    await expect(this.createNameInput).not.toBeVisible({ timeout: 30000 })
  }

  async editCollection(collectionId: string, newName: string) {
    await this.openEditDialog(collectionId)
    await this.editNameInput.clear()
    await this.editNameInput.fill(newName)
    await this.submitEditDialog()
  }

  async openEditDialog(collectionId: string) {
    // The dialog opens, then asynchronously fetches the collection and resets
    // the form. Wait for that GET so user input isn't clobbered by the late
    // resetForm.
    const collectionGet = this.page.waitForResponse(
      (r) => r.url().includes(`/api/collections/${collectionId}`) && r.request().method() === 'GET',
    )
    await this.page.getByTestId(`collection-edit-btn-${collectionId}`).click()
    await expect(this.editNameInput).toBeVisible()
    await collectionGet.catch(() => undefined)
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
    }
  }

  async submitEditDialog() {
    await this.page.getByTestId('collection-edit-submit-btn').click()
    await expect(this.editNameInput).not.toBeVisible({ timeout: 15000 })
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
    const submitBtn = this.page.getByTestId('collection-delete-submit-btn')
    await expect(submitBtn).toBeEnabled()
    const deleteResp = this.page.waitForResponse(
      (r) =>
        r.url().includes(`/api/collections/${collectionId}`) && r.request().method() === 'DELETE',
    )
    await submitBtn.click()
    await deleteResp.catch(() => undefined)
    await expect(this.deleteConfirmInput).not.toBeVisible({ timeout: 15000 })
  }

  async setAsDefault(collectionId: string) {
    await this.page.getByTestId(`collection-set-default-btn-${collectionId}`).click()
  }

  collectionSetDefaultBtn(collectionId: string): Locator {
    return this.page.getByTestId(`collection-set-default-btn-${collectionId}`)
  }

  async expectCollectionVisible(name: string) {
    await expect(
      this.page.locator('[data-testid^="collection-row-"]', { hasText: name }),
    ).toBeVisible()
  }

  async expectCollectionNotVisible(name: string) {
    await expect(
      this.page.locator('[data-testid^="collection-row-"]', { hasText: name }),
    ).not.toBeVisible()
  }

  async goBack() {
    await this.backButton.click()
    await expect(this.page).toHaveURL(/\/collections\//, { timeout: 15000 })
  }
}
