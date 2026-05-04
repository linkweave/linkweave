import { type Locator, type Page, expect } from '@playwright/test'

export class CleanupSuggestionsPageObject {
  readonly page: Page
  readonly heading: Locator
  readonly emptyState: Locator
  readonly moveToTrashButton: Locator
  readonly selectAllButton: Locator
  readonly confirmDialogSubmit: Locator
  readonly thresholdSelect: Locator
  readonly suggestionRow: (id: string) => Locator
  readonly dismissButton: (id: string) => Locator
  readonly checkbox: (id: string) => Locator

  constructor(page: Page) {
    this.page = page
    this.heading = page.getByRole('heading', { name: 'Cleanup Suggestions' })
    this.emptyState = page.getByText('Your collection is tidy! No stale bookmarks found.')
    this.moveToTrashButton = page.getByTestId('cleanup-move-to-trash-btn')
    this.selectAllButton = page.getByRole('button', { name: 'Select all' })
    this.confirmDialogSubmit = page.getByTestId('confirm-dialog-submit')
    this.thresholdSelect = page.getByRole('combobox', { name: 'Inactivity period' })
    this.suggestionRow = (id: string) => page.getByTestId(`cleanup-suggestion-${id}`)
    this.dismissButton = (id: string) =>
      page.getByTestId(`cleanup-suggestion-${id}`).getByRole('button', { name: 'Dismiss' })
    this.checkbox = (id: string) =>
      page.getByTestId(`cleanup-suggestion-${id}`).getByRole('checkbox')
  }

  async expectSuggestionVisible(id: string) {
    await expect(this.suggestionRow(id)).toBeVisible()
  }

  async expectSuggestionNotVisible(id: string) {
    await expect(this.suggestionRow(id)).not.toBeVisible()
  }

  async dismiss(id: string) {
    await this.dismissButton(id).click()
  }

  async selectSuggestion(id: string) {
    await this.checkbox(id).check()
  }

  async moveToTrash() {
    await this.moveToTrashButton.click()
    await this.confirmDialogSubmit.click()
  }

  async selectAllAndMoveToTrash() {
    await this.selectAllButton.click()
    await this.moveToTrashButton.click()
    await this.confirmDialogSubmit.click()
  }
}
