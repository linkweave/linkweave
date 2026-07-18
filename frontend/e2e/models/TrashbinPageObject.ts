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
    await new LoginPageObject(this.page).loginAndLand(email, password)

    await this.page.getByTestId('user-menu-trigger').click()
    await this.page.getByTestId('user-menu-trashbin').click()
    await expect(this.page).toHaveURL(/\/trashbin/)
    await expect(this.heading).toBeVisible()
  }

  async expectBookmarkVisible(id: string) {
    await expect(this.bookmarkRow(id)).toBeVisible({ timeout: 15000 })
  }

  async expectBookmarkNotVisible(id: string) {
    await expect(this.bookmarkRow(id)).not.toBeVisible({ timeout: 15000 })
  }

  async expectFolderVisible(id: string) {
    await expect(this.folderRow(id)).toBeVisible({ timeout: 15000 })
  }

  async expectFolderNotVisible(id: string) {
    await expect(this.folderRow(id)).not.toBeVisible({ timeout: 15000 })
  }

  async expectEmpty() {
    await expect(this.emptyState).toBeVisible({ timeout: 15000 })
    await expect(this.emptyButton).toBeDisabled()
  }

  async restoreBookmark(id: string) {
    // Wait for the POST restore so callers can immediately navigate back and
    // expect the bookmark to be there. Without this, under parallel load the
    // click can fire and the trashbin row can disappear (UI optimistic update)
    // before the backend has actually restored the bookmark.
    const restored = this.page.waitForResponse(
      (r) => r.url().includes(`/api/trashbin/bookmarks/${id}/restore`) && r.request().method() === 'POST' && r.ok(),
      { timeout: 15000 },
    )
    await this.restoreBookmarkButton(id).click()
    await restored
  }

  async purgeBookmark(id: string) {
    const purged = this.page.waitForResponse(
      (r) => r.url().includes(`/api/trashbin/bookmarks/${id}`) && r.request().method() === 'DELETE' && r.ok(),
      { timeout: 15000 },
    )
    await this.purgeBookmarkButton(id).click()
    await this.confirmDialogSubmit.click()
    await purged
  }

  async restoreFolder(id: string) {
    const restored = this.page.waitForResponse(
      (r) => r.url().includes(`/api/trashbin/folders/${id}/restore`) && r.request().method() === 'POST' && r.ok(),
      { timeout: 15000 },
    )
    await this.restoreFolderButton(id).click()
    await restored
  }

  async purgeFolder(id: string) {
    const purged = this.page.waitForResponse(
      (r) => r.url().includes(`/api/trashbin/folders/${id}`) && r.request().method() === 'DELETE' && r.ok(),
      { timeout: 15000 },
    )
    await this.purgeFolderButton(id).click()
    await this.confirmDialogSubmit.click()
    await purged
  }

  async emptyTrashbin() {
    const emptied = this.page.waitForResponse(
      (r) => r.url().includes('/api/trashbin') && r.request().method() === 'DELETE' && r.ok(),
      { timeout: 15000 },
    )
    await this.emptyButton.click()
    await this.confirmDialogSubmit.click()
    await emptied
  }
}
