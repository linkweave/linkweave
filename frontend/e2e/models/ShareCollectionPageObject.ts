import {expect, type Locator, type Page} from '@playwright/test'

export class ShareCollectionPageObject {
  readonly page: Page
  readonly emailInput: Locator
  readonly inviteBtn: Locator
  readonly inviteRoleSelect: Locator

  constructor(page: Page) {
    this.page = page
    this.emailInput = page.getByTestId('share-email-input')
    this.inviteBtn = page.getByTestId('share-invite-btn')
    this.inviteRoleSelect = page.getByTestId('share-invite-role-select')
  }

  async openShareDialog(collectionId: string) {
    const btn = this.page.getByTestId(`collection-share-btn-${collectionId}`)
    // Wait explicitly for the share button — the manage page might have
    // rendered the default-collection row first, and the row for this spec's
    // newly-created collection can lag under parallel e2e load.
    await expect(btn).toBeVisible({ timeout: 30000 })
    await btn.scrollIntoViewIfNeeded()
    await btn.click()
    await expect(this.emailInput).toBeVisible({ timeout: 15000 })
  }

  async inviteUser(email: string) {
    await this.emailInput.fill(email)
    await this.inviteBtn.click()
  }

  async expectOwnerVisible(displayName: string) {
    await expect(
      this.page.locator('.bg-muted\\/40', { hasText: displayName })
    ).toBeVisible()
  }

  async expectNoMembersMessage() {
    await expect(
      this.page.getByText(/Only you have access/)
    ).toBeVisible()
  }

  async closeDialog() {
    await this.page.keyboard.press('Escape')
    await expect(this.emailInput).not.toBeVisible()
  }
}
