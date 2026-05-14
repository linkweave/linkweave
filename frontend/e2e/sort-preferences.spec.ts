import { type APIRequestContext, expect, test } from '@playwright/test'
import {
  deleteTestUserCleanup,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

test.describe.configure({ mode: 'serial' })

const BASE = '/api'

let user: TestUser
let collectionId: string
let storageState: StorageState

async function createBookmarkViaApi(
  request: APIRequestContext,
  cid: string,
  title: string,
  url: string,
): Promise<{ id: string }> {
  const resp = await request.post(`${BASE}/bookmarks`, {
    data: { collectionId: cid, title, url },
  })
  expect(resp.ok(), `createBookmark failed: ${resp.status()}`).toBeTruthy()
  return (await resp.json()) as { id: string }
}

test.describe('Sort preferences', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState, collectionId } = await registerAndCaptureStorageState(
      browser,
      'sortprefs',
    ))

    // Seed three bookmarks with predictable titles. They are created in
    // chronological order, so created_at gives us: Apple oldest, Cherry middle,
    // Banana newest. With the default sort (DATE_ADDED DESC) the visible order
    // should be Banana → Cherry → Apple.
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, storageState })
    try {
      await createBookmarkViaApi(ctx.request, collectionId, 'Apple', 'https://example.com/a')
      await createBookmarkViaApi(ctx.request, collectionId, 'Cherry', 'https://example.com/c')
      await createBookmarkViaApi(ctx.request, collectionId, 'Banana', 'https://example.com/b')
    } finally {
      await ctx.close()
    }
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  async function visibleTitles(page: import('@playwright/test').Page): Promise<string[]> {
    // Wait for at least one bookmark card to render before reading the list,
    // otherwise we race the SPA bootstrap on freshly-loaded pages.
    await page.locator('h3').first().waitFor({ state: 'visible' })
    const titles = await page.locator('h3').allInnerTexts()
    return titles.map((t) => t.trim())
  }

  test('default sort is newest first', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)
    await expect(page.getByTestId('bookmark-sort-trigger')).toBeVisible()

    const titles = await visibleTitles(page)
    // The first three rendered titles must be the seeded bookmarks in
    // creation-date-desc order.
    expect(titles.slice(0, 3)).toEqual(['Banana', 'Cherry', 'Apple'])
  })

  test('selecting Title A→Z reorders bookmarks alphabetically and persists', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)

    await page.getByTestId('bookmark-sort-trigger').click()
    await page.getByTestId('bookmark-sort-option-TITLE').click()

    // Default direction is DESC; flip to ASC so we get A→Z.
    const ascButton = page.getByRole('button', { name: /A → Z/i }).first()
    await ascButton.click()

    // Close the menu.
    await page.keyboard.press('Escape')

    // Wait for the debounced PUT to land.
    await page.waitForResponse(
      (r) =>
        r.url().includes(`/api/collections/${collectionId}/settings`) &&
        r.request().method() === 'PUT' &&
        r.ok(),
    )

    const titles = await visibleTitles(page)
    expect(titles.slice(0, 3)).toEqual(['Apple', 'Banana', 'Cherry'])

    // Persists across reload.
    await page.reload()
    const reloaded = await visibleTitles(page)
    expect(reloaded.slice(0, 3)).toEqual(['Apple', 'Banana', 'Cherry'])
  })

  test('reset clears the per-collection override and falls back to newest first', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)

    await page.getByTestId('bookmark-sort-trigger').click()
    await page.getByTestId('bookmark-sort-reset').click()

    await page.waitForResponse(
      (r) =>
        r.url().includes(`/api/collections/${collectionId}/settings/sort`) &&
        r.request().method() === 'DELETE' &&
        r.ok(),
    )

    await page.reload()
    const titles = await visibleTitles(page)
    expect(titles.slice(0, 3)).toEqual(['Banana', 'Cherry', 'Apple'])
  })

  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
})
