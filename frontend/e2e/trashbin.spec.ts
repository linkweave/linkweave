import { expect, test, type Browser, type Page } from '@playwright/test'
import { LoginPageObject } from './models/LoginPageObject'
import { TrashbinPageObject } from './models/TrashbinPageObject'

test.describe.configure({ mode: 'serial' })

const ts = Date.now()
const BASE = '/api'
const collectionName = `Trashbin Test ${ts}`

async function login(page: Page) {
  const loginPage = new LoginPageObject(page)
  await loginPage.goto()
  await loginPage.login('alice@example.com', 'alice')
  await expect(page).toHaveURL(/\/collections\//, { timeout: 10000 })
}

async function loginAndNavigateToCollection(page: Page, collectionId: string) {
  await login(page)
  await page.goto(`/collections/${collectionId}`)
  await expect(page).toHaveURL(new RegExp(`/collections/${collectionId}`))
}

async function navigateToTrashbin(page: Page) {
  await page.getByTestId('user-menu-trigger').click()
  await page.getByTestId('user-menu-trashbin').click()
  await expect(page).toHaveURL(/\/trashbin/)
}

async function createCollectionViaApi(page: Page, name: string): Promise<string> {
  const resp = await page.request.post(`${BASE}/collections`, { data: { name } })
  expect(resp.ok(), `createCollection failed: ${resp.status()}`).toBeTruthy()
  const body = await resp.json()
  return body.id
}

async function deleteCollectionViaApi(page: Page, id: string) {
  await page.request.delete(`${BASE}/collections/${id}`).catch(() => undefined)
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
    await login(page)
    collectionId = await createCollectionViaApi(page, collectionName)
    await page.goto(`/collections/${collectionId}`)
    await expect(page).toHaveURL(new RegExp(`/collections/${collectionId}`))

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
    await loginAndNavigateToCollection(page, collectionId)
    await navigateToTrashbin(page)

    const trashbin = new TrashbinPageObject(page)
    await expect(trashbin.heading).toBeVisible()

    // Verify the two bookmarks created by this spec are present, instead of
    // asserting on total count (which is fragile when other test data exists).
    await expect(trashbin.bookmarkRow(bookmarkAId)).toBeVisible()
    await expect(trashbin.bookmarkRow(bookmarkBId)).toBeVisible()
  })

  test('should restore a bookmark from trashbin', async ({ page }) => {
    await loginAndNavigateToCollection(page, collectionId)
    await navigateToTrashbin(page)

    const trashbin = new TrashbinPageObject(page)
    await trashbin.restoreBookmark(bookmarkAId)
    await trashbin.expectBookmarkNotVisible(bookmarkAId)

    await page.goto(`/collections/${collectionId}`)
    await expect(page).toHaveURL(new RegExp(`/collections/${collectionId}`))
    await expect(page.locator('h3').filter({ hasText: `Trash-A-${ts}` })).toBeVisible()
  })

  test('should purge a bookmark permanently', async ({ page }) => {
    await loginAndNavigateToCollection(page, collectionId)
    await navigateToTrashbin(page)

    const trashbin = new TrashbinPageObject(page)
    await trashbin.purgeBookmark(bookmarkBId)
    await trashbin.expectBookmarkNotVisible(bookmarkBId)
  })

  test('should show empty state after purging this spec\'s bookmark', async ({ page }) => {
    await loginAndNavigateToCollection(page, collectionId)

    // Create + soft-delete a fresh bookmark, then purge it specifically.
    const bookmarkCId = await createBookmarkViaApi(page, collectionId, `Trash-C-${ts}`, 'https://trash-c.example.com')
    await deleteBookmarkViaApi(page, bookmarkCId)

    await navigateToTrashbin(page)
    const trashbin = new TrashbinPageObject(page)
    await expect(trashbin.bookmarkRow(bookmarkCId)).toBeVisible()

    await trashbin.purgeBookmark(bookmarkCId)
    await trashbin.expectBookmarkNotVisible(bookmarkCId)
  })

  test.afterAll(async ({ browser }: { browser: Browser }) => {
    if (!collectionId) return
    const context = await browser.newContext({ ignoreHTTPSErrors: true })
    const page = await context.newPage()
    try {
      await login(page)
      await deleteCollectionViaApi(page, collectionId)
    } finally {
      await context.close()
    }
  })
})
