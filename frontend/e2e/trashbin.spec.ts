import { expect, type Page, test } from '@playwright/test'
import {
  deleteTestUserCleanup,
  loginAsTestUser,
  registerTestUser,
  type TestUser,
} from './models/TestUser'
import { TrashbinPageObject } from './models/TrashbinPageObject'

test.describe.configure({ mode: 'serial' })

const ts = Date.now()
const BASE = '/api'

let user: TestUser
let collectionId: string

async function navigateToTrashbin(page: Page) {
  await page.getByTestId('user-menu-trigger').click()
  await page.getByTestId('user-menu-trashbin').click()
  await expect(page).toHaveURL(/\/trashbin/)
}

async function createBookmarkViaApi(
  page: Page,
  collectionId: string,
  title: string,
  url: string,
): Promise<string> {
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
  let bookmarkAId: string
  let bookmarkBId: string

  test.beforeAll(async ({ browser }) => {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true })
    try {
      user = await registerTestUser(ctx.request, 'trashbin')
    } finally {
      await ctx.close()
    }
  })

  test('should set up bookmarks and soft-delete them', async ({ page }) => {
    collectionId = await loginAsTestUser(page, user)

    bookmarkAId = await createBookmarkViaApi(
      page,
      collectionId,
      `Trash-A-${ts}`,
      'https://trash-a.example.com',
    )
    bookmarkBId = await createBookmarkViaApi(
      page,
      collectionId,
      `Trash-B-${ts}`,
      'https://trash-b.example.com',
    )

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
    await loginAsTestUser(page, user)
    await navigateToTrashbin(page)

    const trashbin = new TrashbinPageObject(page)
    await expect(trashbin.heading).toBeVisible()
    await expect(trashbin.bookmarkRow(bookmarkAId)).toBeVisible()
    await expect(trashbin.bookmarkRow(bookmarkBId)).toBeVisible()
  })

  test('should restore a bookmark from trashbin', async ({ page }) => {
    await loginAsTestUser(page, user)
    await navigateToTrashbin(page)

    const trashbin = new TrashbinPageObject(page)
    await trashbin.restoreBookmark(bookmarkAId)
    await trashbin.expectBookmarkNotVisible(bookmarkAId)

    await page.goto(`/collections/${collectionId}`)
    await expect(page).toHaveURL(new RegExp(`/collections/${collectionId}`))
    await expect(page.locator('h3').filter({ hasText: `Trash-A-${ts}` })).toBeVisible()
  })

  test('should purge a bookmark permanently', async ({ page }) => {
    await loginAsTestUser(page, user)
    await navigateToTrashbin(page)

    const trashbin = new TrashbinPageObject(page)
    await trashbin.purgeBookmark(bookmarkBId)
    await trashbin.expectBookmarkNotVisible(bookmarkBId)
  })

  test("should show empty state after purging this spec's bookmark", async ({ page }) => {
    await loginAsTestUser(page, user)

    const bookmarkCId = await createBookmarkViaApi(
      page,
      collectionId,
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

  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
})
