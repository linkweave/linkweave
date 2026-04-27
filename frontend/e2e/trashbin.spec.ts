import { expect, test, type Page } from '@playwright/test'
import { LoginPageObject } from './models/LoginPageObject'
import { TrashbinPageObject } from './models/TrashbinPageObject'

test.describe.configure({ mode: 'serial' })

const ts = Date.now()
const BASE = '/api'

async function loginAndGetCollectionId(page: Page): Promise<string> {
  const loginPage = new LoginPageObject(page)
  await loginPage.goto()
  await loginPage.login('alice@example.com', 'alice')
  await expect(page).toHaveURL(/\/collections\//, { timeout: 10000 })
  const url = page.url()
  const match = url.match(/\/collections\/([^/?#]+)/)
  return match![1]
}

async function createBookmarkViaApi(page: Page, collectionId: string, title: string, url: string): Promise<string> {
  const resp = await page.request.post(`${BASE}/bookmarks`, {
    data: { collectionId, title, url },
  })
  expect(resp.ok(), `createBookmark failed: ${resp.status()}`).toBeTruthy()
  const body = await resp.json()
  return body.id
}

async function deleteBookmarkViaApi(page: Page, bookmarkId: string) {
  const resp = await page.request.delete(`${BASE}/bookmarks/${bookmarkId}`)
  expect(resp.ok(), `deleteBookmark failed: ${resp.status()}`).toBeTruthy()
}

test.describe('Trashbin', () => {
  let collectionId: string
  let bookmarkAId: string
  let bookmarkBId: string

  test('should set up bookmarks and soft-delete them', async ({ page }) => {
    collectionId = await loginAndGetCollectionId(page)

    bookmarkAId = await createBookmarkViaApi(page, collectionId, `Trash-A-${ts}`, 'https://trash-a.example.com')
    bookmarkBId = await createBookmarkViaApi(page, collectionId, `Trash-B-${ts}`, 'https://trash-b.example.com')

    await page.reload()
    await expect(page.locator('h3').filter({ hasText: `Trash-A-${ts}` })).toBeVisible()
    await expect(page.locator('h3').filter({ hasText: `Trash-B-${ts}` })).toBeVisible()

    await deleteBookmarkViaApi(page, bookmarkAId)
    await deleteBookmarkViaApi(page, bookmarkBId)

    await page.reload()
    await expect(page.locator('h3').filter({ hasText: `Trash-A-${ts}` })).not.toBeVisible()
    await expect(page.locator('h3').filter({ hasText: `Trash-B-${ts}` })).not.toBeVisible()
  })

  test('should show deleted items in trashbin', async ({ page }) => {
    await loginAndGetCollectionId(page)

    const trashbin = new TrashbinPageObject(page)
    await trashbin.loginAndNavigate()

    await expect(trashbin.heading).toBeVisible()
    const items = page.locator('[data-testid^="trashbin-item-"]')
    await expect(items).toHaveCount(2)
  })

  test('should restore a bookmark from trashbin', async ({ page }) => {
    await loginAndGetCollectionId(page)

    const trashbin = new TrashbinPageObject(page)
    await trashbin.loginAndNavigate()

    const bookmarkItem = page.locator('[data-testid^="trashbin-item-"]').first()
    const titleEl = bookmarkItem.locator('.font-medium')
    const restoredTitle = await titleEl.textContent()
    const testId = await bookmarkItem.getAttribute('data-testid')
    const bookmarkId = testId!.replace('trashbin-item-', '')

    await trashbin.restoreBookmark(bookmarkId)
    await trashbin.expectBookmarkNotVisible(bookmarkId)

    await page.goto('/')
    await expect(page).toHaveURL(/\/collections\//, { timeout: 10000 })
    await expect(page.locator('h3').filter({ hasText: restoredTitle! })).toBeVisible()
  })

  test('should purge a bookmark permanently', async ({ page }) => {
    await loginAndGetCollectionId(page)

    const trashbin = new TrashbinPageObject(page)
    await trashbin.loginAndNavigate()

    const bookmarkItem = page.locator('[data-testid^="trashbin-item-"]').first()
    const testId = await bookmarkItem.getAttribute('data-testid')
    const bookmarkId = testId!.replace('trashbin-item-', '')

    await trashbin.purgeBookmark(bookmarkId)
    await trashbin.expectBookmarkNotVisible(bookmarkId)
  })

  test('should show empty state when trashbin is cleared', async ({ page }) => {
    collectionId = await loginAndGetCollectionId(page)

    const bookmarkCId = await createBookmarkViaApi(page, collectionId, `Trash-C-${ts}`, 'https://trash-c.example.com')
    await deleteBookmarkViaApi(page, bookmarkCId)

    const trashbin = new TrashbinPageObject(page)
    await trashbin.loginAndNavigate()

    await trashbin.emptyTrashbin()
    await trashbin.expectEmpty()
  })
})
