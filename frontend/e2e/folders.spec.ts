import { expect, test } from '@playwright/test'
import { LoginPageObject } from './models/LoginPageObject'

async function loginAsAlice(page: import('@playwright/test').Page) {
  const loginPage = new LoginPageObject(page)
  await loginPage.goto()
  await loginPage.login()
  await expect(page).toHaveURL(/\/collections\//)
}

test.describe('UC-009: Create Folder', () => {
  test('should create a new folder', async ({ page }) => {
    await loginAsAlice(page)

    const name = `folder-${Date.now()}`
    await page.getByTestId('new-folder-btn').click()
    await page.getByTestId('create-folder-name').fill(name)
    await page.getByTestId('create-folder-submit').click()
    await expect(page.getByTestId(`folder-node-${name}`)).toBeVisible({ timeout: 10000 })
  })
})

test.describe('UC-010: View Folders', () => {
  test('should display folders in the sidebar tree', async ({ page }) => {
    await loginAsAlice(page)

    const name = `view-${Date.now()}`
    await page.getByTestId('new-folder-btn').click()
    await page.getByTestId('create-folder-name').fill(name)
    await page.getByTestId('create-folder-submit').click()
    await expect(page.getByTestId(`folder-node-${name}`)).toBeVisible({ timeout: 10000 })
  })
})

test.describe('UC-011: Rename Folder', () => {
  test('should rename a folder', async ({ page }) => {
    await loginAsAlice(page)

    const oldName = `old-${Date.now()}`
    const newName = `${oldName}-renamed`

    await page.getByTestId('new-folder-btn').click()
    await page.getByTestId('create-folder-name').fill(oldName)
    await page.getByTestId('create-folder-submit').click()
    await expect(page.getByTestId(`folder-node-${oldName}`)).toBeVisible({ timeout: 10000 })

    const folderNode = page.getByTestId(`folder-node-${oldName}`)
    await folderNode.hover()
    await folderNode.locator('button').last().click({ force: true })
    await page.getByTestId('folder-rename-btn').click()

    await page.getByTestId('rename-folder-name').clear()
    await page.getByTestId('rename-folder-name').fill(newName)
    await page.getByTestId('rename-folder-submit').click()
    await expect(page.getByTestId(`folder-node-${newName}`)).toBeVisible({ timeout: 10000 })
  })
})

test.describe('UC-012: Nest Folders', () => {
  test('should create a subfolder inside another folder', async ({ page }) => {
    await loginAsAlice(page)

    const parentName = `parent-${Date.now()}`
    await page.getByTestId('new-folder-btn').click()
    await page.getByTestId('create-folder-name').fill(parentName)
    await page.getByTestId('create-folder-submit').click()
    await expect(page.getByTestId(`folder-node-${parentName}`)).toBeVisible({ timeout: 10000 })

    const parentFolder = page.getByTestId(`folder-node-${parentName}`)
    await parentFolder.hover()
    await parentFolder.locator('button').last().click({ force: true })
    await page.getByTestId('folder-create-subfolder-btn').click()

    const childName = `${parentName}-child`
    await page.getByTestId('create-folder-name').fill(childName)
    await page.getByTestId('create-folder-submit').click()
    await expect(page.getByTestId(`folder-node-${childName}`)).toBeVisible({ timeout: 10000 })
  })
})

test.describe('UC-014: Delete Folder', () => {
  test('should delete a folder', async ({ page }) => {
    await loginAsAlice(page)

    const name = `del-${Date.now()}`
    await page.getByTestId('new-folder-btn').click()
    await page.getByTestId('create-folder-name').fill(name)
    await page.getByTestId('create-folder-submit').click()
    await expect(page.getByTestId(`folder-node-${name}`)).toBeVisible({ timeout: 10000 })

    const folderNode = page.getByTestId(`folder-node-${name}`)
    await folderNode.hover()
    await folderNode.locator('button').last().click({ force: true })
    await page.getByTestId('folder-delete-btn').click()
    await page.getByTestId('confirm-dialog-submit').click()
    await expect(page.getByTestId(`folder-node-${name}`)).not.toBeVisible({ timeout: 10000 })
  })
})
