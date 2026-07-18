import { expect, type Locator, type Page } from '@playwright/test'
import { LoginPageObject } from './LoginPageObject'

export class TagsPageObject {
  private readonly page: Page
  private readonly createTagButton: Locator
  private readonly createTagNameInput: Locator
  private readonly createSubmitButton: Locator
  private readonly editTagNameInput: Locator
  private readonly editSubmitButton: Locator
  private readonly confirmDeleteButton: Locator

  constructor(page: Page) {
    this.page = page
    this.createTagButton = page.getByTestId('new-tag-btn')
    this.createTagNameInput = page.getByTestId('create-tag-name-input')
    this.createSubmitButton = page.getByTestId('create-tag-submit')
    this.editTagNameInput = page.getByTestId('edit-tag-name-input')
    this.editSubmitButton = page.getByTestId('edit-tag-submit')
    this.confirmDeleteButton = page.getByTestId('confirm-dialog-submit')
  }

  async loginAndWaitForPage(email = 'alice@example.com', password = 'alice') {
    await new LoginPageObject(this.page).loginAndLand(email, password)
    await expect(this.createTagButton).toBeVisible()
  }

  async createTag(name: string) {
    // Wait for the POST so we know the backend has accepted the tag before we
    // poll for it in the list — otherwise the re-fetch can lag under parallel
    // load and the immediate expectTagVisible() fails the 5s default budget.
    const created = this.page.waitForResponse(
      (r) => r.url().endsWith('/api/tags') && r.request().method() === 'POST' && r.ok(),
      { timeout: 15000 },
    )
    await this.createTagButton.click()
    await this.createTagNameInput.fill(name)
    await this.createSubmitButton.click()
    await created
  }

  async editTag(oldName: string, newName: string) {
    const tagRow = this.page.getByTestId(`tag-row-${oldName}`)
    await tagRow.hover()
    const updated = this.page.waitForResponse(
      (r) => r.url().includes('/api/tags/') && r.request().method() === 'PUT' && r.ok(),
      { timeout: 15000 },
    )
    await tagRow.getByTestId('tag-edit-btn').click()

    await this.editTagNameInput.fill(newName)
    await this.editSubmitButton.click()
    await updated
  }

  async deleteTag(name: string) {
    const tagRow = this.page.getByTestId(`tag-row-${name}`)
    await tagRow.hover()
    const deleted = this.page.waitForResponse(
      (r) => r.url().includes('/api/tags/') && r.request().method() === 'DELETE' && r.ok(),
      { timeout: 15000 },
    )
    await tagRow.getByTestId('tag-delete-btn').click()

    await this.confirmDeleteButton.click()
    await deleted
  }

  async expectTagVisible(name: string) {
    // Under parallel e2e load the sidebar's tags GET can lag the create-POST
    // by tens of seconds. Use a generous budget.
    await expect(this.page.getByTestId(`tag-row-${name}`)).toBeVisible({ timeout: 30000 })
  }

  async expectTagNotVisible(name: string) {
    await expect(this.page.getByTestId(`tag-row-${name}`)).not.toBeVisible({ timeout: 30000 })
  }
}
