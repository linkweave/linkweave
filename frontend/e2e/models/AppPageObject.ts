import { expect, type Locator, type Page } from '@playwright/test'

export class AppPageObject {
  readonly page: Page
  readonly userMenuButton: Locator
  readonly signOutButton: Locator
  readonly allBookmarksLabel: Locator

  constructor(page: Page) {
    this.page = page
    this.userMenuButton = page.getByTestId('user-menu-trigger')
    this.signOutButton = page.getByRole('menuitem', { name: 'Sign out' })
    this.allBookmarksLabel = page.getByTestId('sidebar-all-bookmarks')
  }

  async goto() {
    await this.page.goto('/')
  }

  async logout() {
    await this.userMenuButton.click()
    await this.signOutButton.click()
  }

  async expectAuthenticated() {
    // Wait for the SPA to actually load the current collection — under
    // parallel e2e load the post-login /api/collections GET can lag, and the
    // sidebar's `sidebar-all-bookmarks` span renders empty text (i18n key
    // `sidebar.allBookmarks` is just `"{name}"`) until the collection name
    // arrives, which Playwright treats as hidden. The "Add Bookmark" header
    // button is always rendered as soon as the authed layout mounts.
    await expect(
      this.page.getByRole('button', { name: /add bookmark/i }),
    ).toBeVisible({ timeout: 30000 })
  }
}
