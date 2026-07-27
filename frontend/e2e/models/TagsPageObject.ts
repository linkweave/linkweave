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
    await this.createTagButton.click()
    await this.createTagNameInput.fill(name)
    await this.createSubmitButton.click()
  }

  async editTag(oldName: string, newName: string) {
    const tagRow = this.page.getByTestId(`tag-row-${oldName}`)
    await tagRow.hover()
    await tagRow.getByTestId('tag-edit-btn').click()

    await this.editTagNameInput.fill(newName)
    await this.editSubmitButton.click()
  }

  async deleteTag(name: string) {
    const tagRow = this.page.getByTestId(`tag-row-${name}`)
    await tagRow.hover()
    await tagRow.getByTestId('tag-delete-btn').click()

    await this.confirmDeleteButton.click()
  }

  async expectTagVisible(name: string) {
    await expect(this.page.getByTestId(`tag-row-${name}`)).toBeVisible()
  }

  async expectTagNotVisible(name: string) {
    await expect(this.page.getByTestId(`tag-row-${name}`)).not.toBeVisible()
  }
}
