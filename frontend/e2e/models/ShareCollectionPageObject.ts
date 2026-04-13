import {expect, type Locator, type Page} from '@playwright/test'

export class ShareCollectionPageObject {
  readonly page: Page
  readonly emailInput: Locator
  readonly inviteBtn: Locator

  constructor(page: Page) {
    this.page = page
    this.emailInput = page.getByTestId('share-email-input')
    this.inviteBtn = page.getByTestId('share-invite-btn')
  }

  async openShareDialog(collectionId: string) {
    const btn = this.page.getByTestId(`collection-share-btn-${collectionId}`)
    await btn.scrollIntoViewIfNeeded()
    await btn.click()
    await expect(this.emailInput).toBeVisible({ timeout: 10000 })
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
