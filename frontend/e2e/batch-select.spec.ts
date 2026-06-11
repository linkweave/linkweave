import { expect, test, type Page } from '@playwright/test'
import { api, createBookmarkViaApi, type Created } from './helpers/api'
import { loginViaApi } from './models/TestUser'
import { useTestCollectionWithCleanup } from './helpers/testCollection'

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

  test('should batch add a tag to the selection', async ({ page }) => {
    await selectByTitles(page, collection.collectionId, bm.one, bm.two)

    await page.getByTestId('batch-add-tag').click()
    await page.getByTestId(`batch-tag-option-${tagName}`).click()

    await expect(page.getByText(`Tagged 2 bookmarks with "${tagName}".`)).toBeVisible()
    // Success clears the selection and exits the mode.
    await expect(batchBar(page)).not.toHaveClass(/is-open/)
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
    await context.grantPermissions(['clipboard-read', 'clipboard-write'])
    await selectByTitles(page, collection.collectionId, bm.one, bm.two)

    await page.getByTestId('batch-copy-urls').click()
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
    await page.getByRole('button', { name: 'List view' }).click()
    await expect(batchCount(page)).toHaveText('1 selected')
    await expect(cardByTitle(page, bm.one)).toHaveAttribute('aria-selected', 'true')

    await page.getByRole('button', { name: 'Grid view' }).click()
    await expect(batchCount(page)).toHaveText('1 selected')
  })
})
