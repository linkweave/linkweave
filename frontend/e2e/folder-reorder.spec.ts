import { expect, test, type Page } from './fixtures'
import { api, BASE, type Created } from './helpers/api'
import {
  deleteTestUserCleanup,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

// UC-102: reorder folders in the sidebar by dragging them onto the edge zones
// of other rows (insertion line), while the row middle still nests (UC-012).

test.describe.configure({ mode: 'serial' })

let user: TestUser
let collectionId: string
let storageState: StorageState
const folderIds: Record<string, string> = {}

test.describe('Folder reorder (UC-102)', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState, collectionId } = await registerAndCaptureStorageState(
      browser,
      'folderorder',
    ))

    // Seed three root folders; creation order Alpha, Beta, Gamma is the
    // initial manual order (BR-186).
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, storageState })
    try {
      for (const name of ['Alpha', 'Beta', 'Gamma']) {
        const { id } = await api<Created>(ctx.request, 'POST', `${BASE}/folders`, {
          collectionId,
          name,
        })
        folderIds[name] = id
      }
    } finally {
      await ctx.close()
    }
  })

  test.afterAll(async ({ browser }) => {
    await deleteTestUserCleanup(browser, () => user)
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  async function visibleFolderNames(page: Page): Promise<string[]> {
    const rows = page.locator('[data-testid^="folder-row-"]')
    await rows.first().waitFor({ state: 'visible' })
    return rows.evaluateAll((els) =>
      els.map((el) => el.getAttribute('data-folder-name') ?? ''),
    )
  }

  /**
   * Simulates an HTML5 drag of one folder row onto another. Playwright's
   * high-level dragTo is unreliable for native DnD, so the drag events are
   * dispatched directly with a shared DataTransfer, aiming at the requested
   * zone of the target row (top edge = before, bottom edge = after,
   * middle = nest).
   */
  async function dragFolder(
    page: Page,
    sourceName: string,
    targetName: string,
    zone: 'before' | 'after' | 'into',
  ) {
    const source = page.getByTestId(`folder-row-${folderIds[sourceName]}`)
    const target = page.getByTestId(`folder-row-${folderIds[targetName]}`)
    const dataTransfer = await page.evaluateHandle(() => new DataTransfer())
    await source.dispatchEvent('dragstart', { dataTransfer })
    const box = await target.boundingBox()
    if (!box) throw new Error(`target row ${targetName} is not visible`)
    const clientX = box.x + box.width / 2
    const clientY =
      zone === 'before'
        ? box.y + box.height * 0.1
        : zone === 'after'
          ? box.y + box.height * 0.9
          : box.y + box.height / 2
    await target.dispatchEvent('dragover', { dataTransfer, clientX, clientY })
    await target.dispatchEvent('drop', { dataTransfer, clientX, clientY })
    await source.dispatchEvent('dragend', { dataTransfer })
  }

  test('folders appear in creation order initially', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)
    expect(await visibleFolderNames(page)).toEqual(['Alpha', 'Beta', 'Gamma'])
  })

  test('hovering the edge zones shows an insertion line, the middle a nesting highlight', async ({
    page,
  }) => {
    await page.goto(`/collections/${collectionId}`)
    const source = page.getByTestId(`folder-row-${folderIds['Gamma']}`)
    const target = page.getByTestId(`folder-row-${folderIds['Alpha']}`)
    await expect(target).toBeVisible()

    const dataTransfer = await page.evaluateHandle(() => new DataTransfer())
    await source.dispatchEvent('dragstart', { dataTransfer })
    const box = await target.boundingBox()
    if (!box) throw new Error('target row is not visible')
    const clientX = box.x + box.width / 2

    await target.dispatchEvent('dragover', { dataTransfer, clientX, clientY: box.y + 2 })
    await expect(target).toHaveClass(/drop-line-before/)

    await target.dispatchEvent('dragover', { dataTransfer, clientX, clientY: box.y + box.height - 2 })
    await expect(target).toHaveClass(/drop-line-after/)

    await target.dispatchEvent('dragover', { dataTransfer, clientX, clientY: box.y + box.height / 2 })
    await expect(target).toHaveClass(/ring-2/)
    await expect(target).not.toHaveClass(/drop-line/)

    // Cancel the drag: no order change.
    await source.dispatchEvent('dragend', { dataTransfer })
    expect(await visibleFolderNames(page)).toEqual(['Alpha', 'Beta', 'Gamma'])
  })

  test('dragging a folder onto the top edge of another inserts it before and persists', async ({
    page,
  }) => {
    await page.goto(`/collections/${collectionId}`)
    await expect(page.getByTestId(`folder-row-${folderIds['Gamma']}`)).toBeVisible()

    await dragFolder(page, 'Gamma', 'Alpha', 'before')
    await expect.poll(() => visibleFolderNames(page)).toEqual(['Gamma', 'Alpha', 'Beta'])

    // The new order is shared server state, not view state: it survives a reload.
    await page.reload()
    await expect.poll(() => visibleFolderNames(page)).toEqual(['Gamma', 'Alpha', 'Beta'])
  })

  test('undo restores the previous order', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)
    await expect(page.getByTestId(`folder-row-${folderIds['Beta']}`)).toBeVisible()

    // Current order: Gamma, Alpha, Beta → move Beta to the top…
    await dragFolder(page, 'Beta', 'Gamma', 'before')
    await expect.poll(() => visibleFolderNames(page)).toEqual(['Beta', 'Gamma', 'Alpha'])

    // …and undo brings back the old position.
    await page.getByRole('button', { name: 'Undo' }).click()
    await expect.poll(() => visibleFolderNames(page)).toEqual(['Gamma', 'Alpha', 'Beta'])
  })

  test('dropping onto the middle of a row still nests the folder', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)
    await expect(page.getByTestId(`folder-row-${folderIds['Beta']}`)).toBeVisible()

    await dragFolder(page, 'Beta', 'Alpha', 'into')

    // Beta is now a child of Alpha: rendered directly after it, indented one level.
    await expect.poll(() => visibleFolderNames(page)).toEqual(['Gamma', 'Alpha', 'Beta'])
    const alphaPadding = await page
      .getByTestId(`folder-row-${folderIds['Alpha']}`)
      .evaluate((el) => parseInt(getComputedStyle(el).paddingLeft, 10))
    const betaPadding = await page
      .getByTestId(`folder-row-${folderIds['Beta']}`)
      .evaluate((el) => parseInt(getComputedStyle(el).paddingLeft, 10))
    expect(betaPadding).toBeGreaterThan(alphaPadding)
  })
})
