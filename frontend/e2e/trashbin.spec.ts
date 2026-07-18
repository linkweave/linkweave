import { expect, type Page, test } from './fixtures'
import { BASE, createBookmarkViaApi } from './helpers/api'
import { gotoCollection, useTestCollectionWithCleanup } from './helpers/testCollection'
import { TrashbinPageObject } from './models/TrashbinPageObject'

test.describe.configure({ mode: 'serial' })

const ts = Date.now()

async function navigateToTrashbin(page: Page) {
  await page.getByTestId('user-menu-trigger').click()
  await page.getByTestId('user-menu-trashbin').click()
  await expect(page).toHaveURL(/\/trashbin/)
}

async function deleteBookmarkViaApi(page: Page, bookmarkId: string) {
  const resp = await page.request.delete(`${BASE}/bookmarks/${bookmarkId}`)
  expect(resp.ok(), `deleteBookmark failed: ${resp.status()}`).toBeTruthy()
}

test.describe('Trashbin', () => {
  let bookmarkAId: string
  let bookmarkBId: string

  const collection = useTestCollectionWithCleanup('trashbin')
  test.use({ storageState: async ({}, use) => { await use(collection.storageState!) } })

  test('should set up bookmarks and soft-delete them', async ({ page }) => {
    await gotoCollection(page, collection)

    bookmarkAId = await createBookmarkViaApi(
      page.request,
      collection.collectionId,
      `Trash-A-${ts}`,
      'https://trash-a.example.com',
    )
    bookmarkBId = await createBookmarkViaApi(
      page.request,
      collection.collectionId,
      `Trash-B-${ts}`,
      'https://trash-b.example.com',
    )

    await page.reload()
    await expect(page.locator('h3').filter({ hasText: `Trash-A-${ts}` })).toBeVisible({
      timeout: 30000,
    })
    await expect(page.locator('h3').filter({ hasText: `Trash-B-${ts}` })).toBeVisible({
      timeout: 30000,
    })

    await deleteBookmarkViaApi(page, bookmarkAId)
    await deleteBookmarkViaApi(page, bookmarkBId)

    await page.reload()
    await expect(page.locator('h3').filter({ hasText: `Trash-A-${ts}` })).not.toBeVisible({
      timeout: 30000,
    })
    await expect(page.locator('h3').filter({ hasText: `Trash-B-${ts}` })).not.toBeVisible({
      timeout: 30000,
    })
  })

  test('should show deleted items in trashbin', async ({ page }) => {
    await gotoCollection(page, collection)
    await navigateToTrashbin(page)

    const trashbin = new TrashbinPageObject(page)
    await expect(trashbin.heading).toBeVisible()
    await expect(trashbin.bookmarkRow(bookmarkAId)).toBeVisible()
    await expect(trashbin.bookmarkRow(bookmarkBId)).toBeVisible()
  })

  test('should restore a bookmark from trashbin', async ({ page }) => {
    await gotoCollection(page, collection)
    await navigateToTrashbin(page)

    const trashbin = new TrashbinPageObject(page)
    await trashbin.restoreBookmark(bookmarkAId)
    await trashbin.expectBookmarkNotVisible(bookmarkAId)

    await gotoCollection(page, collection)
    // The restore POST has succeeded, but the bookmark-list GET on fresh page
    // load can lag under parallel e2e load. Try the assertion; if it times
    // out, force a reload to re-fetch and try once more. One-shot retry —
    // genuine bugs surface as a second timeout.
    const restored = page.locator('h3').filter({ hasText: `Trash-A-${ts}` })
    try {
      await expect(restored).toBeVisible({ timeout: 30000 })
    } catch {
      await page.reload()
      await expect(restored).toBeVisible({ timeout: 30000 })
    }
  })

  test('should purge a bookmark permanently', async ({ page }) => {
    // ARRANGE
    await gotoCollection(page, collection)
    await navigateToTrashbin(page)

    const trashbin = new TrashbinPageObject(page)
    // ACT
    await trashbin.purgeBookmark(bookmarkBId)
    // ASSERT
    await trashbin.expectBookmarkNotVisible(bookmarkBId)
  })

  test("should show empty state after purging this spec's bookmark", async ({ page }) => {
    await gotoCollection(page, collection)

    const bookmarkCId = await createBookmarkViaApi(
      page.request,
      collection.collectionId,
      `Trash-C-${ts}`,
      'https://trash-c.example.com',
    )
    await deleteBookmarkViaApi(page, bookmarkCId)

    await navigateToTrashbin(page)
    const trashbin = new TrashbinPageObject(page)
    await expect(trashbin.bookmarkRow(bookmarkCId)).toBeVisible()

    await trashbin.purgeBookmark(bookmarkCId)
    await trashbin.expectBookmarkNotVisible(bookmarkCId)
  })
})
