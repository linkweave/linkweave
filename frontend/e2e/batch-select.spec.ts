import { expect, test, type Page } from './fixtures'
import { api, createBookmarkViaApi, type Created } from './helpers/api'
import { loginViaApi } from './models/TestUser'
import { useTestCollectionWithCleanup } from './helpers/testCollection'
// Generated from the OpenAPI spec — the serializer only emits schema-defined
// properties, so it doubles as the source of truth for the request contract.
import { BookmarkBatchExportJsonToJSON } from '../src/api/generated'

// Batch select — UC-074: selection model, batch action bar, move / delete /
// add-tag / copy-URLs flows. Tests mutate shared seeded state (bookmarks get
// moved/deleted), so they run in order.
test.describe.configure({ mode: 'serial' })

const ts = Date.now()
const bm = {
  one: `BatchOne ${ts}`,
  two: `BatchTwo ${ts}`,
  three: `BatchThree ${ts}`,
  four: `BatchFour ${ts}`,
}
const folderName = `Archive ${ts}`
const tagName = `batch-${ts}`

let folderId: string

function cardByTitle(page: Page, title: string) {
  return page.locator(`[data-bookmark-title="${title}"]`)
}

function selectToggle(page: Page) {
  return page.getByTestId('bookmark-select-toggle')
}

function batchBar(page: Page) {
  return page.getByTestId('batch-action-bar')
}

function batchCount(page: Page) {
  return page.getByTestId('batch-count')
}

async function gotoCollection(page: Page, collectionId: string) {
  await page.goto(`/collections/${collectionId}`)
  await expect(page).toHaveURL(new RegExp(`/collections/${collectionId}`))
  await expect(page.getByTestId(/^bookmark-card-/).first()).toBeVisible()
}

/**
 * Switches the bookmark layout. The toggle renders as a segmented button group
 * at the `sm` breakpoint (≥640px) and collapses to a "Change layout" menu below
 * it, so the control to click depends on the viewport (the Mobile projects run
 * at <640px). See BookmarkLayoutToggle.vue.
 */
async function switchLayout(page: Page, label: 'Grid view' | 'List view') {
  const width = page.viewportSize()?.width ?? 1280
  if (width >= 640) {
    await page.getByRole('button', { name: label }).click()
  } else {
    await page.getByRole('button', { name: 'Change layout' }).click()
    await page.getByRole('menuitemradio', { name: label }).click()
  }
}

/** Navigates, enters selection mode via the toolbar button, and selects the given cards. */
async function selectByTitles(page: Page, collectionId: string, ...titles: string[]) {
  await gotoCollection(page, collectionId)
  await selectToggle(page).click()
  for (const title of titles) {
    await cardByTitle(page, title).click()
  }
  await expect(batchCount(page)).toHaveText(`${titles.length} selected`)
}

test.describe('Batch select (UC-074)', () => {
  const collection = useTestCollectionWithCleanup('batchselect')

  test.use({
    storageState: async ({}, use) => {
      await use(collection.storageState!)
    },
  })

  test.beforeAll(async ({ browser }) => {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true })
    try {
      await loginViaApi(ctx.request, collection.user!)
      const collectionId = collection.collectionId
      // Enable screenshots so cards render the 16:9 cover — the selection
      // overlay sits above it and must stay pointer-transparent (regression
      // guard for the cover-click dead zone).
      const info = await api<{ name: string }>(ctx.request, 'GET', `/api/collections/${collectionId}`)
      await api(ctx.request, 'PUT', `/api/collections/${collectionId}`, {
        name: info.name,
        screenshotEnabled: true,
      })
      folderId = (
        await api<Created>(ctx.request, 'POST', '/api/folders', {
          collectionId,
          name: folderName,
        })
      ).id
      await api<Created>(ctx.request, 'POST', '/api/tags', {
        collectionId,
        name: tagName,
      })
      for (const title of [bm.one, bm.two, bm.three, bm.four]) {
        await createBookmarkViaApi(
          ctx.request,
          collectionId,
          title,
          `https://example.com/${encodeURIComponent(title)}`,
        )
      }
    } finally {
      await ctx.close()
    }
  })

  test('should enter selection mode, toggle cards and clear with Esc', async ({ page }) => {
    await gotoCollection(page, collection.collectionId)

    // Toolbar button enters the mode and relabels to Cancel.
    await selectToggle(page).click()
    await expect(selectToggle(page)).toHaveAttribute('aria-pressed', 'true')
    await expect(selectToggle(page)).toHaveText(/Cancel/)

    // Card click toggles instead of opening the link; the batch bar opens.
    await cardByTitle(page, bm.one).click()
    await expect(batchBar(page)).toHaveClass(/is-open/)
    await expect(batchCount(page)).toHaveText('1 selected')
    await expect(cardByTitle(page, bm.one)).toHaveAttribute('aria-selected', 'true')

    // Clicking the cover image region toggles too — the selection overlay
    // above the cover must not swallow the click.
    await cardByTitle(page, bm.two).click({ position: { x: 140, y: 50 } })
    await expect(batchCount(page)).toHaveText('2 selected')

    // Toggling an already-selected card removes it.
    await cardByTitle(page, bm.two).click()
    await expect(batchCount(page)).toHaveText('1 selected')

    // Esc clears the selection and exits the mode.
    await page.keyboard.press('Escape')
    await expect(batchBar(page)).not.toHaveClass(/is-open/)
    await expect(selectToggle(page)).toHaveAttribute('aria-pressed', 'false')
    await expect(selectToggle(page)).toHaveText(/Select/)
  })

  test('should support ctrl/cmd-click entry, shift-range and select all', async ({ page }) => {
    await gotoCollection(page, collection.collectionId)

    // ⌘/Ctrl-click enters selection mode implicitly — no toolbar button.
    await cardByTitle(page, bm.one).click({ modifiers: ['ControlOrMeta'] })
    await expect(batchCount(page)).toHaveText('1 selected')
    await expect(selectToggle(page)).toHaveAttribute('aria-pressed', 'true')

    // Shift-click extends the range from the anchor.
    await cardByTitle(page, bm.three).click({ modifiers: ['Shift'] })
    await expect(batchCount(page)).toHaveText('3 selected')

    // The Select-all link selects everything and then disappears.
    await page.getByTestId('batch-select-all').click()
    await expect(batchCount(page)).toHaveText('4 selected')
    await expect(page.getByTestId('batch-select-all')).toBeHidden()

    // × clears like Esc.
    await page.getByTestId('batch-clear').click()
    await expect(batchBar(page)).not.toHaveClass(/is-open/)
  })

  test('should batch add a tag via the editor and retain the selection (UC-074a)', async ({
    page,
  }) => {
    // The bar-level contract for the tag editor: it opens anchored under the
    // Tags button, applies in one batch, and — unlike Move/Delete — RETAINS the
    // selection. The full tri-state matrix lives in batch-tag-editor.spec.ts.
    await selectByTitles(page, collection.collectionId, bm.one, bm.two)

    await page.getByTestId('batch-add-tag').click()
    await expect(page.getByTestId('batch-tag-editor')).toBeVisible()
    const row = page.getByTestId(`batch-tag-row-${tagName}`)
    await expect(row).toHaveAttribute('aria-checked', 'false')
    await row.click()
    await expect(row).toHaveAttribute('aria-checked', 'true')
    await expect(page.getByTestId('batch-tag-summary')).toHaveText(`+${tagName}`)
    await page.getByTestId('batch-tag-apply').click()

    await expect(page.getByText(`Added "${tagName}" to 2.`)).toBeVisible()
    await expect(page.getByTestId('batch-tag-editor')).toHaveCount(0)
    await expect(batchCount(page)).toHaveText('2 selected')
    await expect(
      cardByTitle(page, bm.one).locator(`[data-tag-name="${tagName}"]`),
    ).toBeVisible()
    await expect(
      cardByTitle(page, bm.two).locator(`[data-tag-name="${tagName}"]`),
    ).toBeVisible()
  })

  test('should batch move the selection into a folder', async ({ page }) => {
    await selectByTitles(page, collection.collectionId, bm.one, bm.two)

    await page.getByTestId('batch-move').click()
    await expect(page.getByText('Move 2 bookmarks')).toBeVisible()
    // Pick the destination folder in the picker.
    await page.locator('#move-bookmark-folder').click()
    await page.getByRole('option', { name: folderName }).click()
    await page.getByRole('button', { name: 'Move here' }).click()

    await expect(page.getByText(`Moved 2 bookmarks to ${folderName}.`)).toBeVisible()
    await expect(batchBar(page)).not.toHaveClass(/is-open/)
    await expect(
      cardByTitle(page, bm.one).locator(`[data-folder-id="${folderId}"]`),
    ).toBeVisible()
    await expect(
      cardByTitle(page, bm.two).locator(`[data-folder-id="${folderId}"]`),
    ).toBeVisible()
  })

  test('should batch move the selection back to the collection root (BR-101)', async ({ page }) => {
    // After the previous test bm.one and bm.two are in the folder.
    await selectByTitles(page, collection.collectionId, bm.one, bm.two)

    await page.getByTestId('batch-move').click()
    await expect(page.getByText('Move 2 bookmarks')).toBeVisible()
    // Submit without picking a folder → moves to the collection root.
    await page.getByRole('button', { name: 'Move here' }).click()

    await expect(page.getByText('Moved 2 bookmarks to the collection root.')).toBeVisible()
    await expect(batchBar(page)).not.toHaveClass(/is-open/)
    // The folder chip must be gone from both cards.
    await expect(cardByTitle(page, bm.one).locator(`[data-folder-id="${folderId}"]`)).toHaveCount(0)
    await expect(cardByTitle(page, bm.two).locator(`[data-folder-id="${folderId}"]`)).toHaveCount(0)
  })

  test('should batch delete the selection into the trashbin', async ({ page }) => {
    await selectByTitles(page, collection.collectionId, bm.three, bm.four)

    await page.getByTestId('batch-delete').click()
    await expect(page.getByText('Move 2 bookmarks to trashbin?')).toBeVisible()
    // Esc on the dialog only closes the dialog — selection stays.
    await page.keyboard.press('Escape')
    await expect(batchCount(page)).toHaveText('2 selected')

    await page.getByTestId('batch-delete').click()
    await page.getByTestId('confirm-dialog-submit').click()

    await expect(page.getByText('2 bookmarks moved to trashbin.')).toBeVisible()
    await expect(cardByTitle(page, bm.three)).toHaveCount(0)
    await expect(cardByTitle(page, bm.four)).toHaveCount(0)
    await expect(batchBar(page)).not.toHaveClass(/is-open/)
  })

  test('should copy URLs without clearing the selection', async ({ page, context }) => {
    // ARRANGE
    await context.grantPermissions(['clipboard-read', 'clipboard-write'])
    await selectByTitles(page, collection.collectionId, bm.one, bm.two)

    // ACT
    await page.getByTestId('batch-copy-urls').click()

    // ASSERT
    await expect(page.getByText('Copied 2 URLs to clipboard.')).toBeVisible()
    // Non-mutating: the selection is retained.
    await expect(batchCount(page)).toHaveText('2 selected')

    const clipboard = await page.evaluate(() => navigator.clipboard.readText())
    const lines = clipboard.split('\n')
    expect(lines).toHaveLength(2)
    for (const line of lines) {
      expect(line).toMatch(/^https:\/\/example\.com\//)
    }
  })

  test('should keep the selection across layout and preview switches', async ({ page }) => {
    await selectByTitles(page, collection.collectionId, bm.one)

    // Switch grid ⇄ list — selection (a set of ids) survives.
    await switchLayout(page, 'List view')
    await expect(batchCount(page)).toHaveText('1 selected')
    await expect(cardByTitle(page, bm.one)).toHaveAttribute('aria-selected', 'true')

    await switchLayout(page, 'Grid view')
    await expect(batchCount(page)).toHaveText('1 selected')
  })

  test('should retain the selection when a batch action fails (NFR-018)', async ({ page }) => {
    await selectByTitles(page, collection.collectionId, bm.one)

    // Intercept the batch-delete call and force a 500 so we can verify the
    // selection survives the atomic rollback and the user can retry.
    await page.route('**/api/bookmarks/batch-delete', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: '{"message":"simulated failure"}',
      }),
    )

    await page.getByTestId('batch-delete').click()
    await page.getByTestId('confirm-dialog-submit').click()

    // Error toast and selection retained for retry.
    await expect(page.getByText('Failed to delete bookmarks. No changes were made.')).toBeVisible()
    await expect(batchCount(page)).toHaveText('1 selected')
    await expect(batchBar(page)).toHaveClass(/is-open/)
    await expect(cardByTitle(page, bm.one)).toBeVisible()

    await page.unroute('**/api/bookmarks/batch-delete')
  })

  test('should export the selection without clearing it', async ({ page }) => {
    await selectByTitles(page, collection.collectionId, bm.one, bm.two)

    // Intercept the partial-export call: the real handler would push a file
    // download, which Playwright cannot observe directly. Instead, assert the
    // correct payload is POSTed and the success toast + retained selection
    // behave like the other non-mutating batch action (copy URLs).
    let capturedBody: string | null = null
    await page.route('**/api/collections/*/export/partial', async (route) => {
      capturedBody = route.request().postData()
      await route.fulfill({
        status: 200,
        contentType: 'text/html',
        headers: {
          'content-disposition': 'attachment; filename="bookmarks.html"',
          'x-exported-count': '2',
        },
        body: '<!DOCTYPE NETSCAPE-Bookmark-file-1>',
      })
    })

    await page.getByTestId('batch-export').click()
    await expect(page.getByText('Exported 2 bookmarks.')).toBeVisible()
    // Non-mutating: the selection is retained.
    await expect(batchCount(page)).toHaveText('2 selected')
    await expect(batchBar(page)).toHaveClass(/is-open/)

    expect(capturedBody).not.toBeNull()
    const payload = JSON.parse(capturedBody!)
    expect(payload.bookmarkIds).toHaveLength(2)
    // Body must conform to the OpenAPI-generated contract: round-tripping through
    // the generated serializer strips any property not in the schema, so a stray
    // field (e.g. a stale client sending collectionId) would break this equality.
    expect(payload).toEqual(BookmarkBatchExportJsonToJSON(payload))

    await page.unroute('**/api/collections/*/export/partial')
  })
})
