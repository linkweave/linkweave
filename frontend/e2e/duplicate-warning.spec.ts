import { type APIRequestContext, type Browser, expect, test } from '@playwright/test'
import { BASE, createCollectionViaApi } from './helpers/api'
import { login, loginAndNavigateToCollection } from './helpers/auth'

test.describe.configure({ mode: 'serial' })

const ts = Date.now()
const collectionName = `Duplicate Check ${ts}`
const existingBookmarkTitle = `Existing-${ts}`
const duplicateUrl = `https://example.com/page-${ts}`

let collectionId: string

async function createBookmarkViaApi(
  request: APIRequestContext,
  cid: string,
  title: string,
  url: string,
) {
  const resp = await request.post(`${BASE}/bookmarks`, {
    data: { collectionId: cid, title, url },
  })
  expect(resp.ok(), `createBookmark failed: ${resp.status()}`).toBeTruthy()
  return (await resp.json()) as { id: string }
}

test.describe('Duplicate Bookmark Warning', () => {
  test.beforeAll(async ({ browser }) => {
    const context = await browser.newContext({ ignoreHTTPSErrors: true })
    const page = await context.newPage()
    try {
      await login(page)
      collectionId = await createCollectionViaApi(page.request, collectionName)
      await createBookmarkViaApi(page.request, collectionId, existingBookmarkTitle, duplicateUrl)
    } finally {
      await context.close()
    }
  })

  test('shows duplicate warning when creating a bookmark with an existing URL', async ({
    page,
  }) => {
    await loginAndNavigateToCollection(page, collectionId)

    await page.getByRole('button', { name: /add bookmark/i }).click()
    const dialog = page.locator('[role="dialog"]')
    await expect(dialog).toBeVisible()

    await dialog.locator('#create-bookmark-url').fill(duplicateUrl)
    await dialog.locator('#create-bookmark-title').fill('Duplicate Attempt')

    const warning = dialog.getByTestId('duplicate-warning')
    await expect(warning).toBeVisible()
    await expect(warning).toContainText(existingBookmarkTitle)

    await dialog.getByRole('button', { name: /cancel/i }).click()
    await expect(dialog).not.toBeVisible()
  })

  test('does not show duplicate warning for a new URL', async ({ page }) => {
    await loginAndNavigateToCollection(page, collectionId)

    await page.getByRole('button', { name: /add bookmark/i }).click()
    const dialog = page.locator('[role="dialog"]')
    await expect(dialog).toBeVisible()

    await dialog.locator('#create-bookmark-url').fill('https://new-unique-url.example.com')
    await dialog.locator('#create-bookmark-title').fill('Unique Bookmark')

    await expect(dialog.getByTestId('duplicate-warning')).not.toBeVisible()

    await dialog.getByRole('button', { name: /cancel/i }).click()
    await expect(dialog).not.toBeVisible()
  })

  test('allows creating a duplicate despite the warning', async ({ page }) => {
    await loginAndNavigateToCollection(page, collectionId)

    await page.getByRole('button', { name: /add bookmark/i }).click()
    const dialog = page.locator('[role="dialog"]')
    await expect(dialog).toBeVisible()

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
    await loginAndNavigateToCollection(page, collectionId)

    await page.getByRole('button', { name: /add bookmark/i }).click()
    let dialog = page.locator('[role="dialog"]')
    await expect(dialog).toBeVisible()
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
    await loginAndNavigateToCollection(page, collectionId)

    await page.getByRole('button', { name: /add bookmark/i }).click()
    let dialog = page.locator('[role="dialog"]')
    await expect(dialog).toBeVisible()
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

  test.afterAll(async ({ browser }: { browser: Browser }) => {
    if (!collectionId) return
    const context = await browser.newContext({ ignoreHTTPSErrors: true })
    const page = await context.newPage()
    try {
      await login(page)
      await page.request.delete(`${BASE}/collections/${collectionId}`).catch(() => undefined)
    } finally {
      await context.close()
    }
  })
})
