import { type Locator, type Page, expect } from '@playwright/test'
import { LoginPageObject } from './LoginPageObject'

export class TrashbinPageObject {
  readonly page: Page
  readonly emptyButton: Locator
  readonly confirmDialogSubmit: Locator
  readonly restoreBookmarkButton: (id: string) => Locator
  readonly purgeBookmarkButton: (id: string) => Locator
  readonly restoreFolderButton: (id: string) => Locator
  readonly purgeFolderButton: (id: string) => Locator
  readonly folderRow: (id: string) => Locator
  readonly bookmarkRow: (id: string) => Locator
  readonly emptyState: Locator
  readonly heading: Locator
  readonly backButton: Locator

  constructor(page: Page) {
    this.page = page
    this.emptyButton = page.getByTestId('trashbin-empty-btn')
    this.confirmDialogSubmit = page.getByTestId('confirm-dialog-submit')
    this.restoreBookmarkButton = (id: string) => page.getByTestId(`trashbin-item-${id}`).getByTestId('trashbin-restore-btn')
    this.purgeBookmarkButton = (id: string) => page.getByTestId(`trashbin-item-${id}`).getByTestId('trashbin-purge-btn')
    this.restoreFolderButton = (id: string) => page.getByTestId(`trashbin-folder-${id}`).getByTestId('trashbin-restore-folder-btn')
    this.purgeFolderButton = (id: string) => page.getByTestId(`trashbin-folder-${id}`).getByTestId('trashbin-purge-folder-btn')
    this.folderRow = (id: string) => page.getByTestId(`trashbin-folder-${id}`)
    this.bookmarkRow = (id: string) => page.getByTestId(`trashbin-item-${id}`)
    this.emptyState = page.getByText('The trashbin is empty.')
    this.heading = page.getByRole('heading', { name: 'Trashbin' })
    this.backButton = page.getByRole('button', { name: 'Back' })
  }

  async loginAndNavigate(email = 'alice@example.com', password = 'alice') {
    const loginPage = new LoginPageObject(this.page)
    await loginPage.goto()
    await loginPage.login(email, password)
    await expect(this.page).toHaveURL(/\/collections\//)

    await this.page.getByTestId('user-menu-trigger').click()
    await this.page.getByTestId('user-menu-trashbin').click()
    await expect(this.page).toHaveURL(/\/trashbin/)
    await expect(this.heading).toBeVisible()
  }

  async expectBookmarkVisible(id: string) {
    await expect(this.bookmarkRow(id)).toBeVisible()
  }

  async expectBookmarkNotVisible(id: string) {
    await expect(this.bookmarkRow(id)).not.toBeVisible()
  }

  async expectFolderVisible(id: string) {
    await expect(this.folderRow(id)).toBeVisible()
  }

  async expectFolderNotVisible(id: string) {
    await expect(this.folderRow(id)).not.toBeVisible()
  }

  async expectEmpty() {
    await expect(this.emptyState).toBeVisible()
    await expect(this.emptyButton).toBeDisabled()
  }

  async restoreBookmark(id: string) {
    await this.restoreBookmarkButton(id).click()
  }

  async purgeBookmark(id: string) {
    await this.purgeBookmarkButton(id).click()
    await this.confirmDialogSubmit.click()
  }

  async restoreFolder(id: string) {
    await this.restoreFolderButton(id).click()
  }

  async purgeFolder(id: string) {
    await this.purgeFolderButton(id).click()
    await this.confirmDialogSubmit.click()
  }

  async emptyTrashbin() {
    await this.emptyButton.click()
    await this.confirmDialogSubmit.click()
  }
}
