import { expect, test } from './fixtures'
import { createBookmarkViaApi, createCollectionViaApi } from './helpers/api'
import { openAddBookmarkDialog } from './helpers/bookmarks'
import {
  deleteTestUserCleanup,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

test.describe.configure({ mode: 'serial' })

const ts = Date.now()
const collectionName = `Duplicate Check ${ts}`
const existingBookmarkTitle = `Existing-${ts}`
const duplicateUrl = `https://example.com/page-${ts}`

let user: TestUser
let storageState: StorageState
let collectionId: string

test.describe('Duplicate Bookmark Warning', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState } = await registerAndCaptureStorageState(browser, 'dupwarn'))
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, storageState })
    try {
      collectionId = await createCollectionViaApi(ctx.request, collectionName)
      await createBookmarkViaApi(ctx.request, collectionId, existingBookmarkTitle, duplicateUrl)
    } finally {
      await ctx.close()
    }
  })

  test.afterAll(async ({ browser }) => {
    await deleteTestUserCleanup(browser, () => user)
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  test('shows duplicate warning when creating a bookmark with an existing URL', async ({
    page,
  }) => {
    const dialog = await openAddBookmarkDialog(page, collectionId)

    await dialog.locator('#create-bookmark-url').fill(duplicateUrl)
    await dialog.locator('#create-bookmark-title').fill('Duplicate Attempt')

    const warning = dialog.getByTestId('duplicate-warning')
    await expect(warning).toBeVisible()
    await expect(warning).toContainText(existingBookmarkTitle)

    await dialog.getByRole('button', { name: /cancel/i }).click()
    await expect(dialog).not.toBeVisible()
  })

  test('does not show duplicate warning for a new URL', async ({ page }) => {
    const dialog = await openAddBookmarkDialog(page, collectionId)

    await dialog.locator('#create-bookmark-url').fill('https://new-unique-url.example.com')
    await dialog.locator('#create-bookmark-title').fill('Unique Bookmark')

    await expect(dialog.getByTestId('duplicate-warning')).not.toBeVisible()

    await dialog.getByRole('button', { name: /cancel/i }).click()
    await expect(dialog).not.toBeVisible()
  })

  test('allows creating a duplicate despite the warning', async ({ page }) => {
    const dialog = await openAddBookmarkDialog(page, collectionId)

    await dialog.locator('#create-bookmark-url').fill(duplicateUrl)
    await dialog.locator('#create-bookmark-title').fill('Second Copy')

    const warning = dialog.getByTestId('duplicate-warning')
    await expect(warning).toBeVisible()

    await dialog.locator('button[type="submit"]').click()
    await expect(dialog).not.toBeVisible({ timeout: 15000 })

    await expect(page.locator('h3').filter({ hasText: 'Second Copy' })).toBeVisible()
  })

  test('does not show warning when editing the same bookmark (no other duplicates)', async ({
    page,
  }) => {
    const uniqueUrl = `https://unique-${ts}.example.com/only-one`
    let dialog = await openAddBookmarkDialog(page, collectionId)
    await dialog.locator('#create-bookmark-url').fill(uniqueUrl)
    await dialog.locator('#create-bookmark-title').fill('Only Bookmark')
    await dialog.locator('button[type="submit"]').click()
    await expect(dialog).not.toBeVisible({ timeout: 15000 })

    const card = page.locator('.group', { hasText: 'Only Bookmark' })
    await card
      .locator('button')
      .filter({ has: page.locator('svg') })
      .click()
    await page.getByRole('menuitem', { name: /edit/i }).click()

    dialog = page.locator('[role="dialog"]')
    await expect(dialog).toBeVisible()

    await expect(dialog.getByTestId('duplicate-warning')).not.toBeVisible()

    await dialog.getByRole('button', { name: /cancel/i }).click()
    await expect(dialog).not.toBeVisible()
  })

  test('shows warning when editing a bookmark URL to match another', async ({ page }) => {
    const otherUrl = `https://other-${ts}.example.com`
    let dialog = await openAddBookmarkDialog(page, collectionId)
    await dialog.locator('#create-bookmark-url').fill(otherUrl)
    await dialog.locator('#create-bookmark-title').fill('Other Bookmark')
    await dialog.locator('button[type="submit"]').click()
    await expect(dialog).not.toBeVisible({ timeout: 15000 })

    const otherCard = page.locator('.group', { hasText: 'Other Bookmark' })
    await otherCard
      .locator('button')
      .filter({ has: page.locator('svg') })
      .click()
    await page.getByRole('menuitem', { name: /edit/i }).click()

    dialog = page.locator('[role="dialog"]')
    await expect(dialog).toBeVisible()

    const urlInput = dialog.locator('#edit-bookmark-url')
    await urlInput.clear()
    await urlInput.fill(duplicateUrl)

    const warning = dialog.getByTestId('duplicate-warning')
    await expect(warning).toBeVisible()
    await expect(warning).toContainText(existingBookmarkTitle)

    await dialog.getByRole('button', { name: /cancel/i }).click()
    await expect(dialog).not.toBeVisible()
  })
})
