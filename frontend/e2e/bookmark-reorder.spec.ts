import { type APIRequestContext, expect, test, type Page } from './fixtures'
import { BASE } from './helpers/api'
import {
  deleteTestUserCleanup,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

// UC-103: reorder bookmarks within a folder group by dragging a row onto the
// upper/lower edge of another row (insertion line), in Manual sort mode only.
// The grouped (compact) layout offers the same targets between its rows and
// follows the manual folder order for its cards (BR-199/BR-200).

test.describe.configure({ mode: 'serial' })

let user: TestUser
let collectionId: string
let storageState: StorageState
const bookmarkIds: Record<string, string> = {}
let workFolderId: string

async function createBookmarkViaApi(
  request: APIRequestContext,
  cid: string,
  title: string,
  folderId?: string,
): Promise<{ id: string }> {
  const resp = await request.post(`${BASE}/bookmarks`, {
    data: { collectionId: cid, title, url: `https://example.com/${title}`, folderId },
  })
  expect(resp.ok(), `createBookmark failed: ${resp.status()}`).toBeTruthy()
  return (await resp.json()) as { id: string }
}

test.describe('Bookmark reorder (UC-103)', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState, collectionId } = await registerAndCaptureStorageState(
      browser,
      'bmreorder',
    ))

    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, storageState })
    try {
      const folderResp = await ctx.request.post(`${BASE}/folders`, {
        data: { collectionId, name: 'Work' },
      })
      expect(folderResp.ok()).toBeTruthy()
      workFolderId = ((await folderResp.json()) as { id: string }).id

      // Creation order is the initial manual order (BR-192): Work holds
      // WorkA, WorkB; Alpha, Beta, Gamma are unfiled.
      for (const [title, folderId] of [
        ['WorkA', workFolderId],
        ['WorkB', workFolderId],
        ['Alpha', undefined],
        ['Beta', undefined],
        ['Gamma', undefined],
      ] as const) {
        const { id } = await createBookmarkViaApi(ctx.request, collectionId, title, folderId)
        bookmarkIds[title] = id
      }
    } finally {
      await ctx.close()
    }
  })

  test.afterAll(async ({ browser }) => {
    await deleteTestUserCleanup(browser, () => user)
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  async function visibleTitles(page: Page): Promise<string[]> {
    await page.locator('h3').first().waitFor({ state: 'visible' })
    const titles = await page.locator('h3').allInnerTexts()
    return titles.map((t) => t.trim())
  }

  /** Waits for the debounced settings PUT so the choice outlives this page. */
  function settingsSaved(page: Page): Promise<unknown> {
    return page.waitForResponse(
      (r) =>
        r.url().includes(`/api/collections/${collectionId}/settings`) &&
        r.request().method() === 'PUT' &&
        r.ok(),
    )
  }

  async function selectSort(page: Page, option: 'MANUAL' | 'TITLE') {
    await page.getByTestId('bookmark-sort-trigger').click()
    const saved = settingsSaved(page)
    await page.getByTestId(`bookmark-sort-option-${option}`).click()
    await saved
    await page.keyboard.press('Escape')
  }

  /**
   * Switches to the list layout (insertion targets live there, BR-199) after
   * the collection has rendered — clicking earlier would write the choice to
   * localStorage instead of the collection settings and not persist.
   */
  async function ensureListLayout(page: Page) {
    await page.locator('h3').first().waitFor({ state: 'visible' })
    const listButton = page.getByRole('button', { name: 'List view' })
    if ((await listButton.getAttribute('aria-pressed')) === 'true') return
    const saved = settingsSaved(page)
    await listButton.click()
    await expect(listButton).toHaveAttribute('aria-pressed', 'true')
    await saved
  }

  /**
   * Simulates an HTML5 drag of one bookmark row onto the upper or lower edge
   * of another. Playwright's high-level dragTo is unreliable for native DnD,
   * so the drag events are dispatched directly with a shared DataTransfer.
   */
  async function dragBookmark(
    page: Page,
    sourceTitle: string,
    targetTitle: string,
    edge: 'above' | 'below',
    rowTestId: (title: string) => string = (t) => `bookmark-card-${bookmarkIds[t]}`,
  ) {
    const source = page.getByTestId(rowTestId(sourceTitle))
    const target = page.getByTestId(rowTestId(targetTitle))
    const box = await target.boundingBox()
    if (!box) throw new Error(`target row ${targetTitle} is not visible`)
    const clientX = box.x + box.width / 2
    const clientY = edge === 'above' ? box.y + box.height * 0.25 : box.y + box.height * 0.75

    const dataTransfer = await page.evaluateHandle(() => new DataTransfer())
    await source.dispatchEvent('dragstart', { dataTransfer })
    await target.dispatchEvent('dragover', { dataTransfer, clientX, clientY })
    await target.dispatchEvent('drop', { dataTransfer, clientX, clientY })
    await source.dispatchEvent('dragend', { dataTransfer })
  }

  test('Manual mode lists folder groups first, each in creation order', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)
    await ensureListLayout(page)
    await selectSort(page, 'MANUAL')
    // BR-197: Work's bookmarks group before the unfiled ones.
    await expect
      .poll(() => visibleTitles(page))
      .toEqual(['WorkA', 'WorkB', 'Alpha', 'Beta', 'Gamma'])
  })

  test('dragging over a row edge shows the insertion line in Manual mode', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)
    await ensureListLayout(page)
    const source = page.getByTestId(`bookmark-card-${bookmarkIds['Gamma']}`)
    const target = page.getByTestId(`bookmark-card-${bookmarkIds['Alpha']}`)
    await expect(target).toBeVisible()
    const box = await target.boundingBox()
    if (!box) throw new Error('target row is not visible')

    const dataTransfer = await page.evaluateHandle(() => new DataTransfer())
    await source.dispatchEvent('dragstart', { dataTransfer })
    await target.dispatchEvent('dragover', {
      dataTransfer,
      clientX: box.x + box.width / 2,
      clientY: box.y + box.height * 0.25,
    })
    await expect(target.locator('.bm-drop-line')).toBeVisible()

    // Cancel the drag: the indicator disappears, no order change.
    await source.dispatchEvent('dragend', { dataTransfer })
    await expect(target.locator('.bm-drop-line')).toHaveCount(0)
    expect(await visibleTitles(page)).toEqual(['WorkA', 'WorkB', 'Alpha', 'Beta', 'Gamma'])
  })

  test('dropping above another bookmark inserts it there and persists', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)
    await ensureListLayout(page)
    await expect(page.getByTestId(`bookmark-card-${bookmarkIds['Gamma']}`)).toBeVisible()

    await dragBookmark(page, 'Gamma', 'Alpha', 'above')
    await expect
      .poll(() => visibleTitles(page))
      .toEqual(['WorkA', 'WorkB', 'Gamma', 'Alpha', 'Beta'])

    // The new order is shared server state, not view state: it survives a reload.
    await page.reload()
    await expect
      .poll(() => visibleTitles(page))
      .toEqual(['WorkA', 'WorkB', 'Gamma', 'Alpha', 'Beta'])
  })

  test('undo restores the previous order', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)
    await ensureListLayout(page)
    await expect(page.getByTestId(`bookmark-card-${bookmarkIds['Beta']}`)).toBeVisible()

    // Current order: Gamma, Alpha, Beta → move Beta above Gamma…
    await dragBookmark(page, 'Beta', 'Gamma', 'above')
    await expect
      .poll(() => visibleTitles(page))
      .toEqual(['WorkA', 'WorkB', 'Beta', 'Gamma', 'Alpha'])

    // …and undo brings back the old position.
    await page.getByRole('button', { name: 'Undo' }).click()
    await expect
      .poll(() => visibleTitles(page))
      .toEqual(['WorkA', 'WorkB', 'Gamma', 'Alpha', 'Beta'])
  })

  test('no insertion targets outside Manual mode (BR-194)', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)
    await ensureListLayout(page)
    await selectSort(page, 'TITLE')
    const source = page.getByTestId(`bookmark-card-${bookmarkIds['Gamma']}`)
    const target = page.getByTestId(`bookmark-card-${bookmarkIds['Alpha']}`)
    await expect(target).toBeVisible()
    const box = await target.boundingBox()
    if (!box) throw new Error('target row is not visible')

    const dataTransfer = await page.evaluateHandle(() => new DataTransfer())
    await source.dispatchEvent('dragstart', { dataTransfer })
    await target.dispatchEvent('dragover', {
      dataTransfer,
      clientX: box.x + box.width / 2,
      clientY: box.y + box.height * 0.25,
    })
    await expect(target.locator('.bm-drop-line')).toHaveCount(0)
    // The toolbar explains why there are no insertion targets (A3) — but only
    // while the drag is in flight.
    await expect(page.getByTestId('reorder-hint')).toContainText('Manual')
    await source.dispatchEvent('dragend', { dataTransfer })
    await expect(page.getByTestId('reorder-hint')).toHaveCount(0)

    // Back to Manual for the remaining tests.
    await selectSort(page, 'MANUAL')
  })

  test('no insertion targets while a search filter is active (BR-197)', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)
    await ensureListLayout(page)
    // The filtered list hides rows between the visible ones, so its neighbors
    // aren't the real neighbors — reordering is disabled.
    await page.getByPlaceholder(/Search bookmarks/).fill('a')
    const source = page.getByTestId(`bookmark-card-${bookmarkIds['Gamma']}`)
    const target = page.getByTestId(`bookmark-card-${bookmarkIds['Alpha']}`)
    await expect(target).toBeVisible()
    const box = await target.boundingBox()
    if (!box) throw new Error('target row is not visible')

    const dataTransfer = await page.evaluateHandle(() => new DataTransfer())
    await source.dispatchEvent('dragstart', { dataTransfer })
    await target.dispatchEvent('dragover', {
      dataTransfer,
      clientX: box.x + box.width / 2,
      clientY: box.y + box.height * 0.25,
    })
    await expect(target.locator('.bm-drop-line')).toHaveCount(0)
    // The drag-time hint names the filter as the reason (BR-197).
    await expect(page.getByTestId('reorder-hint')).toContainText('filters')
    await source.dispatchEvent('dragend', { dataTransfer })
  })

  test('grouped layout: cards precede unfiled, drop into another section moves at position (A2b)', async ({
    page,
  }) => {
    await page.goto(`/collections/${collectionId}`)
    await page.getByRole('button', { name: 'Grouped view' }).click()
    await expect(page.getByTestId(`grouped-row-${bookmarkIds['WorkA']}`)).toBeVisible()

    // Drop unfiled Alpha onto the lower edge of WorkA: it moves into Work,
    // slotted between WorkA and WorkB.
    await dragBookmark(page, 'Alpha', 'WorkA', 'below', (t) => `grouped-row-${bookmarkIds[t]}`)

    // Verify against the list layout, whose flat order shows the folder
    // grouping: Work now holds WorkA, Alpha, WorkB.
    await page.getByRole('button', { name: 'List view' }).click()
    await expect
      .poll(() => visibleTitles(page))
      .toEqual(['WorkA', 'Alpha', 'WorkB', 'Gamma', 'Beta'])
  })
})
