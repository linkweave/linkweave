import { type Page, expect } from '@playwright/test'
import { LoginPageObject } from './LoginPageObject'

export class FoldersPageObject {
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

  async createFolder(name: string) {
    await this.page.getByTestId('new-folder-btn').click()
    const dialog = this.getOpenDialog()
    await dialog.getByTestId('create-folder-name').fill(name)
    await dialog.getByTestId('create-folder-submit').click()
    await expect(dialog).not.toBeVisible({ timeout: 5000 })
  }

  async renameFolder(oldName: string, newName: string) {
    await this.openFolderMenu(oldName)
    await this.page.getByTestId('folder-rename-btn').click()
    const dialog = this.getOpenDialog()
    const input = dialog.getByTestId('rename-folder-name')
    await input.clear()
    await input.fill(newName)
    await dialog.getByTestId('rename-folder-submit').click()
    await expect(dialog).not.toBeVisible({ timeout: 5000 })
  }

  async deleteFolder(name: string) {
    await this.openFolderMenu(name)
    await this.page.getByTestId('folder-delete-btn').click()
    await this.page.getByTestId('confirm-dialog-submit').click()
    await expect(this.getOpenDialog()).not.toBeVisible({ timeout: 5000 })
  }

  async createSubfolder(parentName: string, childName: string) {
    await this.openFolderMenu(parentName)
    await this.page.getByTestId('folder-create-subfolder-btn').click()
    const dialog = this.getOpenDialog()
    await dialog.getByTestId('create-folder-name').fill(childName)
    await dialog.getByTestId('create-folder-submit').click()
    await expect(dialog).not.toBeVisible({ timeout: 5000 })
  }

  async expectFolderVisible(name: string) {
    await expect(this.page.getByTestId(`folder-node-${name}`)).toBeVisible({ timeout: 5000 })
  }

  async expectFolderNotVisible(name: string) {
    await expect(this.page.getByTestId(`folder-node-${name}`)).not.toBeVisible()
  }

  private getOpenDialog() {
    return this.page.locator('[role="dialog"][data-state="open"]').last()
  }

  private async openFolderMenu(folderName: string) {
    const folderNode = this.page.getByTestId(`folder-node-${folderName}`)
    await folderNode.scrollIntoViewIfNeeded()
    await folderNode.hover()
    await folderNode.locator('button').last().click({ force: true })
    await expect(this.page.getByTestId('folder-rename-btn')).toBeVisible({ timeout: 5000 })
  }
}
