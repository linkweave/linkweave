import { type Page, expect } from '@playwright/test'
import { LoginPageObject } from './LoginPageObject'

export class BookmarksPageObject {
  readonly page: Page

  constructor(page: Page) {
    this.page = page
  }

  async loginAndWaitForCollection() {
    const loginPage = new LoginPageObject(this.page)
    await loginPage.goto()
    await loginPage.login()
    await expect(this.page).toHaveURL(/\/collections\//)
  }

  async createBookmark(title: string, url: string) {
    await this.page.getByTestId('add-bookmark-btn').click()
    await this.page.locator('[role="dialog"]').getByTestId('create-bookmark-title').fill(title)
    await this.page.locator('[role="dialog"]').getByTestId('create-bookmark-url').fill(url)
    await this.page.locator('[role="dialog"]').getByTestId('create-bookmark-submit').click()
    await expect(this.page.locator('[role="dialog"]')).not.toBeVisible({ timeout: 10000 })
  }

  async search(query: string) {
    await this.page.getByTestId('search-input').fill(query)
  }

  async clearSearch() {
    await this.page.getByTestId('search-input').clear()
  }

  async expectBookmarkVisible(title: string) {
    await expect(this.page.getByTestId(`bookmark-card-${title}`)).toBeVisible({ timeout: 10000 })
  }

  async expectBookmarkNotVisible(title: string) {
    await expect(this.page.getByTestId(`bookmark-card-${title}`)).not.toBeVisible()
  }

  async openBookmarkMenu(title: string) {
    const card = this.page.getByTestId(`bookmark-card-${title}`)
    await card.hover()
    await card.locator('button').last().click({ force: true })
    await expect(this.page.getByTestId('bookmark-edit-btn')).toBeVisible({ timeout: 5000 })
  }

  async deleteBookmark(title: string) {
    await this.openBookmarkMenu(title)
    await this.page.getByTestId('bookmark-delete-btn').click()
    await this.page.getByTestId('confirm-dialog-submit').click()
    await expect(this.page.locator('[role="dialog"]')).not.toBeVisible({ timeout: 10000 })
  }

  async selectTagInSidebar(tagName: string) {
    await this.page.getByTestId(`tag-row-${tagName}`).click()
  }

  async clearTagFilter() {
    await this.page.getByText(/clear filter/i).click()
  }

  async selectFolderInSidebar(folderName: string) {
    await this.page.getByTestId(`folder-node-${folderName}`).click()
  }

  async selectAllBookmarks() {
    await this.page.getByTestId('all-bookmarks-btn').click()
  }
}
