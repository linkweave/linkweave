import { expect, test } from '@playwright/test'
import { CollectionManagePageObject } from './models/CollectionManagePageObject'
import {
  deleteTestUserCleanup,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

test.describe.configure({ mode: 'serial' })

let user: TestUser
let storageState: StorageState

test.describe('Favicon Allowlist', () => {
  let collectionId: string
  const collectionName = `Allowlist Test ${Date.now()}-${Math.random().toString(36).slice(2, 6)}`

  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState } = await registerAndCaptureStorageState(browser, 'favicon'))
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  test('should create a test collection', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    await manage.navigate()
    await manage.createCollection(collectionName)
    await manage.expectCollectionVisible(collectionName)

    collectionId = await manage.getCollectionIdByName(collectionName)
  })

  test('should add favicon allowlist patterns', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    await manage.navigate()

    // Open edit dialog — allowlist starts empty
    await manage.openEditDialogAndWaitForAllowlist(collectionId, '')
    await manage.editFaviconAllowlistInput.fill('*.mycompany.domain\nintranet.local')
    await manage.submitEditDialog()

    // Reopen the dialog and verify the patterns persisted
    await manage.openEditDialogAndWaitForAllowlist(
      collectionId,
      '*.mycompany.domain\nintranet.local',
    )
  })

  test('should edit existing favicon allowlist patterns', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    await manage.navigate()

    // Open dialog — should show previously saved patterns
    await manage.openEditDialogAndWaitForAllowlist(
      collectionId,
      '*.mycompany.domain\nintranet.local',
    )

    // Replace with new patterns
    await manage.editFaviconAllowlistInput.clear()
    await manage.editFaviconAllowlistInput.fill('staging.internal\n*.other.domain')
    await manage.submitEditDialog()

    // Reopen and verify the updated patterns
    await manage.openEditDialogAndWaitForAllowlist(collectionId, 'staging.internal\n*.other.domain')
  })

  test('should clear the favicon allowlist', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    await manage.navigate()

    // Open dialog — should show previously saved patterns
    await manage.openEditDialogAndWaitForAllowlist(collectionId, 'staging.internal\n*.other.domain')

    // Clear the allowlist
    await manage.editFaviconAllowlistInput.clear()
    await manage.submitEditDialog()

    // Reopen and verify the allowlist is empty
    await manage.openEditDialogAndWaitForAllowlist(collectionId, '')
  })

  test('should reject invalid favicon allowlist patterns', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    await manage.navigate()

    // Use openEditDialogAndWaitForAllowlist to ensure the async API fetch completes
    // before we fill — otherwise the async resetForm overwrites our fill.
    await manage.openEditDialogAndWaitForAllowlist(collectionId, '')

    // Enter a bare IPv4 address (invalid pattern)
    await manage.editFaviconAllowlistInput.fill('192.168.1.1')

    // Submit to trigger validation
    await page.getByTestId('collection-edit-submit-btn').click()

    // Validation error should appear
    await expect(page.getByText(/invalid pattern/i)).toBeVisible()

    // The dialog should still be open (form not submitted)
    await expect(manage.editFaviconAllowlistInput).toBeVisible()
  })

  // Cleanup handled by user-delete in afterAll.
  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
})
