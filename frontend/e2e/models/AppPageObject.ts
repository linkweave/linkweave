import { type Locator, type Page, expect } from '@playwright/test'

export class AppPageObject {
  readonly page: Page
  readonly userMenuButton: Locator
  readonly signOutButton: Locator
  readonly allBookmarksLabel: Locator

  constructor(page: Page) {
    this.page = page
    this.userMenuButton = page.getByRole('button', { name: /alice user/i })
    this.signOutButton = page.getByRole('menuitem', { name: /sign out/i })
    this.allBookmarksLabel = page.getByTestId('all-bookmarks-btn')
  }

  async goto() {
    await this.page.goto('/')
  }

  async logout() {
    await this.userMenuButton.click()
    await this.signOutButton.click()
  }

  async expectAuthenticated() {
    await expect(this.allBookmarksLabel).toBeVisible()
  }
}
