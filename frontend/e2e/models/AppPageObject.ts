import { type Locator, type Page, expect } from '@playwright/test'

export class AppPageObject {
  readonly page: Page
  readonly userMenuButton: Locator
  readonly signOutButton: Locator
  readonly allBookmarksLabel: Locator

  constructor(page: Page) {
    this.page = page
    this.userMenuButton = page.getByTestId('user-menu-trigger')
    this.signOutButton = page.getByRole('menuitem', { name: 'Sign out' })
    this.allBookmarksLabel = page.getByText('All Bookmarks')
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
