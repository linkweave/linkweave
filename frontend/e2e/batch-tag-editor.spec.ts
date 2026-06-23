import { expect, test, type Page } from './fixtures'
import { api, createBookmarkViaApi, type Created } from './helpers/api'
import { loginViaApi } from './models/TestUser'
import { useTestCollectionWithCleanup } from './helpers/testCollection'

// Batch tag editor — UC-074a: the tri-state add/remove editor opened from the
// batch bar's "Tags" button. Covers the full state matrix (on all / some /
// none), the click cycle, search + inline create, the live summary/Apply
// gating, the net-change toast, selection retention, and atomic failure.
//
// Several tests mutate shared seeded tag state, so they run in order: all
// read-only assertions first, the mutating apply flows last.
test.describe.configure({ mode: 'serial' })

const ts = Date.now()
const bm = {
  a: `TagA ${ts}`,
  b: `TagB ${ts}`,
}
// Tag base states are defined relative to a selection of {a, b}.
const onAll = `onall-${ts}` // applied to a + b   → base "all"
const onSome = `onsome-${ts}` // applied to a only  → base "some"
const onNone = `onnone-${ts}` // applied to neither → base "none"

let bookmarkA: string
let bookmarkB: string

function cardByTitle(page: Page, title: string) {
  return page.locator(`[data-bookmark-title="${title}"]`)
}

function editor(page: Page) {
  return page.getByTestId('batch-tag-editor')
}

function row(page: Page, name: string) {
  return page.getByTestId(`batch-tag-row-${name}`)
}

async function gotoCollection(page: Page, collectionId: string) {
  await page.goto(`/collections/${collectionId}`)
  await expect(page).toHaveURL(new RegExp(`/collections/${collectionId}`))
  await expect(page.getByTestId(/^bookmark-card-/).first()).toBeVisible()
}

/**
 * Enters selection mode via the toolbar and selects the given cards. Clicks a
 * fixed top-of-card position rather than the center: these cards have no cover,
 * so as tags accumulate across tests a center click can land on a tag chip
 * (which filters instead of toggling). The title/favicon row is always at the
 * top and always toggles in select mode.
 */
async function selectByTitles(page: Page, collectionId: string, ...titles: string[]) {
  await gotoCollection(page, collectionId)
  await page.getByTestId('bookmark-select-toggle').click()
  for (const title of titles) {
    await cardByTitle(page, title).click({ position: { x: 18, y: 12 } })
  }
  await expect(page.getByTestId('batch-count')).toHaveText(`${titles.length} selected`)
}

/** Selects the cards and opens the tag editor, waiting for it to mount. */
async function openEditor(page: Page, collectionId: string, ...titles: string[]) {
  await selectByTitles(page, collectionId, ...titles)
  await page.getByTestId('batch-add-tag').click()
  await expect(editor(page)).toBeVisible()
}

test.describe('Batch tag editor (UC-074a)', () => {
  const collection = useTestCollectionWithCleanup('batchtag')

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

      bookmarkA = await createBookmarkViaApi(ctx.request, collectionId, bm.a, 'https://example.com/a')
      bookmarkB = await createBookmarkViaApi(ctx.request, collectionId, bm.b, 'https://example.com/b')

      const tagId: Record<string, string> = {}
      for (const name of [onAll, onSome, onNone]) {
        tagId[name] = (
          await api<Created>(ctx.request, 'POST', '/api/tags', { collectionId, name })
        ).id
      }

      // Seed the base states via the (known-good) batch-tag endpoint.
      await api(ctx.request, 'POST', '/api/bookmarks/batch-tag', {
        collectionId,
        addTagIds: [tagId[onAll]],
        removeTagIds: [],
        bookmarkIds: [bookmarkA, bookmarkB],
      })
      await api(ctx.request, 'POST', '/api/bookmarks/batch-tag', {
        collectionId,
        addTagIds: [tagId[onSome]],
        removeTagIds: [],
        bookmarkIds: [bookmarkA],
      })
    } finally {
      await ctx.close()
    }
  })

  // ---- Read-only assertions (no Apply) -------------------------------------

  test('should render the correct tri-state for a mixed selection', async ({ page }) => {
    await openEditor(page, collection.collectionId, bm.a, bm.b)

    // Header reflects the selection size.
    await expect(editor(page)).toContainText('2 selected')

    // on all → checked, hint "all 2".
    await expect(row(page, onAll)).toHaveAttribute('aria-checked', 'true')
    await expect(row(page, onAll)).toContainText('all 2')

    // on some → mixed, hint "1 of 2".
    await expect(row(page, onSome)).toHaveAttribute('aria-checked', 'mixed')
    await expect(row(page, onSome)).toContainText('1 of 2')

    // on none → unchecked, no count hint.
    await expect(row(page, onNone)).toHaveAttribute('aria-checked', 'false')

    // Partial/all tags (relevant to the selection) sort above none tags.
    const ids = await page
      .locator('[data-testid^="batch-tag-row-"]')
      .evaluateAll((els) => els.map((e) => e.getAttribute('data-testid') ?? ''))
    expect(ids.indexOf(`batch-tag-row-${onNone}`)).toBeGreaterThan(
      ids.indexOf(`batch-tag-row-${onAll}`),
    )
    expect(ids.indexOf(`batch-tag-row-${onNone}`)).toBeGreaterThan(
      ids.indexOf(`batch-tag-row-${onSome}`),
    )

    // Nothing staged yet → Apply disabled, summary clean.
    await expect(page.getByTestId('batch-tag-apply')).toBeDisabled()
    await expect(page.getByTestId('batch-tag-summary')).toHaveText('No changes')
  })

  test('should cycle a partial tag leave → add → remove → leave', async ({ page }) => {
    await openEditor(page, collection.collectionId, bm.a, bm.b)
    const some = row(page, onSome)
    const apply = page.getByTestId('batch-tag-apply')
    const summary = page.getByTestId('batch-tag-summary')

    // 1st click → add to all (the 1 not yet tagged).
    await some.click()
    await expect(some).toHaveAttribute('aria-checked', 'true')
    await expect(some).toContainText('add to 1')
    await expect(summary).toHaveText(`+${onSome}`)
    await expect(apply).toBeEnabled()

    // 2nd click → remove from all (the 1 currently tagged).
    await some.click()
    await expect(some).toHaveAttribute('aria-checked', 'false')
    await expect(some).toContainText('remove from 1')
    await expect(summary).toHaveText(`−${onSome}`)
    await expect(apply).toBeEnabled()

    // 3rd click → back to leave: no net change, Apply disabled again.
    await some.click()
    await expect(some).toHaveAttribute('aria-checked', 'mixed')
    await expect(some).toContainText('1 of 2')
    await expect(summary).toHaveText('No changes')
    await expect(apply).toBeDisabled()
  })

  test('should filter on search and offer Create only for a non-matching query', async ({
    page,
  }) => {
    await openEditor(page, collection.collectionId, bm.a, bm.b)
    const search = page.getByTestId('batch-tag-search')

    // Exact existing name → that row only, and no Create row.
    await search.fill(onAll)
    await expect(row(page, onAll)).toBeVisible()
    await expect(row(page, onSome)).toHaveCount(0)
    await expect(row(page, onNone)).toHaveCount(0)
    await expect(page.getByTestId('batch-tag-create')).toHaveCount(0)

    // Novel query → no tag rows, Create row offering the typed name.
    const novel = `brand-new-${ts}`
    await search.fill(novel)
    await expect(page.locator('[data-testid^="batch-tag-row-"]')).toHaveCount(0)
    await expect(page.getByTestId('batch-tag-create')).toContainText(novel)
  })

  test('should degrade to a plain add/remove checklist for a single selection (N == 1)', async ({
    page,
  }) => {
    await openEditor(page, collection.collectionId, bm.a)

    // With one bookmark every tag is all or none — never "some".
    await expect(row(page, onSome)).toHaveAttribute('aria-checked', 'true')
    await expect(row(page, onSome)).not.toContainText('of')
    await expect(row(page, onAll)).toHaveAttribute('aria-checked', 'true')
    await expect(row(page, onNone)).toHaveAttribute('aria-checked', 'false')
  })

  test('should close on outside click without clearing the selection', async ({ page }) => {
    await openEditor(page, collection.collectionId, bm.a, bm.b)

    // Mousedown outside the popover (on the batch bar) closes it.
    await page.getByTestId('batch-count').click()
    await expect(editor(page)).toHaveCount(0)
    // Selection survives — only the popover closed.
    await expect(page.getByTestId('batch-count')).toHaveText('2 selected')
  })

  test('should make no mutation and retain the selection when Apply fails (NFR-018)', async ({
    page,
  }) => {
    await openEditor(page, collection.collectionId, bm.a)

    await page.route('**/api/bookmarks/batch-tag', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: '{"message":"simulated failure"}',
      }),
    )

    await row(page, onNone).click()
    await page.getByTestId('batch-tag-apply').click()

    await expect(page.getByText('Failed to update tags. No changes were made.')).toBeVisible()
    await expect(editor(page)).toHaveCount(0)
    await expect(page.getByTestId('batch-count')).toHaveText('1 selected')
    await expect(cardByTitle(page, bm.a).locator(`[data-tag-name="${onNone}"]`)).toHaveCount(0)

    await page.unroute('**/api/bookmarks/batch-tag')
  })

  // The NFR-018 case above stages an existing tag (no inline create), so the
  // rollback loop is a no-op. This one stages an inline-created tag that is
  // already persisted by the time the batch fails — exercising the orphan-
  // prevention branch (temp-id → real-id mapping + sequential delete on catch).
  test('should roll back an inline-created tag and retain the selection when Apply fails', async ({
    page,
  }) => {
    const created = `rollback-${ts}`
    await openEditor(page, collection.collectionId, bm.a)

    // Capture the real id the backend assigns to the inline-created tag, and
    // record any rollback DELETE so we can assert the orphan was cleaned up.
    let createdTagId = ''
    await page.route('**/api/tags', async (route) => {
      const response = await route.fetch()
      if (route.request().method() === 'POST') {
        const body = await response.json().catch(() => ({}))
        if (body.id) createdTagId = body.id
      }
      await route.fulfill({ response })
    })
    const deletedTagIds: string[] = []
    await page.route('**/api/tags/*', async (route) => {
      if (route.request().method() === 'DELETE') {
        deletedTagIds.push(route.request().url().split('/').pop() ?? '')
      }
      await route.continue()
    })

    await page.getByTestId('batch-tag-search').fill(created)
    await page.getByTestId('batch-tag-create').click()
    // The new tag is persisted and staged as add.
    await expect(row(page, created)).toHaveAttribute('aria-checked', 'true')

    // Now make the batch fail: the created tag is already persisted, so the
    // rollback path must delete it to avoid leaving an orphan.
    await page.route('**/api/bookmarks/batch-tag', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: '{"message":"simulated failure"}',
      }),
    )

    await page.getByTestId('batch-tag-apply').click()

    await expect(page.getByText('Failed to update tags. No changes were made.')).toBeVisible()
    await expect(editor(page)).toHaveCount(0)
    // Selection retained despite the failed apply.
    await expect(page.getByTestId('batch-count')).toHaveText('1 selected')

    await page.unroute('**/api/bookmarks/batch-tag')
    await page.unroute('**/api/tags')
    await page.unroute('**/api/tags/*')

    // The orphan-prevention branch ran: the created tag was deleted.
    expect(createdTagId).toBeTruthy()
    expect(deletedTagIds).toContain(createdTagId)

    // Reopening reflects the rollback: the tag is gone from the collection, so
    // its row is absent and Create is offered again for the same name.
    await page.getByTestId('batch-add-tag').click()
    await expect(row(page, created)).toHaveCount(0)
    await page.getByTestId('batch-tag-search').fill(created)
    await expect(page.getByTestId('batch-tag-create')).toContainText(created)
  })

  // ---- Mutating apply flows (run last) -------------------------------------

  test('should create a tag inline and apply it to the whole selection', async ({ page }) => {
    const created = `created-${ts}`
    await openEditor(page, collection.collectionId, bm.a, bm.b)

    await page.getByTestId('batch-tag-search').fill(created)
    await page.getByTestId('batch-tag-create').click()
    // The new tag is added to the list and immediately staged as add (checked).
    await expect(row(page, created)).toHaveAttribute('aria-checked', 'true')
    // The input clears after creating.
    await expect(page.getByTestId('batch-tag-search')).toHaveValue('')

    await page.getByTestId('batch-tag-apply').click()
    await expect(page.getByText(`Added "${created}" to 2.`)).toBeVisible()
    await expect(cardByTitle(page, bm.a).locator(`[data-tag-name="${created}"]`)).toBeVisible()
    await expect(cardByTitle(page, bm.b).locator(`[data-tag-name="${created}"]`)).toBeVisible()
  })

  test('should add and remove in one atomic apply with a combined toast', async ({ page }) => {
    await openEditor(page, collection.collectionId, bm.a, bm.b)

    // Stage an add (onNone) and a remove (onAll) together.
    await row(page, onNone).click()
    await row(page, onAll).click()
    await expect(row(page, onNone)).toHaveAttribute('aria-checked', 'true')
    await expect(row(page, onAll)).toHaveAttribute('aria-checked', 'false')
    await expect(page.getByTestId('batch-tag-summary')).toHaveText(`+${onNone}  −${onAll}`)

    await page.getByTestId('batch-tag-apply').click()

    // One toast describing the net effect of both clauses.
    await expect(page.getByText(`Added "${onNone}" to 2, removed "${onAll}" from 2.`)).toBeVisible()
    // Editor closed, selection retained.
    await expect(editor(page)).toHaveCount(0)
    await expect(page.getByTestId('batch-count')).toHaveText('2 selected')

    // Cards reflect the change: onNone added, onAll gone.
    await expect(cardByTitle(page, bm.a).locator(`[data-tag-name="${onNone}"]`)).toBeVisible()
    await expect(cardByTitle(page, bm.b).locator(`[data-tag-name="${onNone}"]`)).toBeVisible()
    await expect(cardByTitle(page, bm.a).locator(`[data-tag-name="${onAll}"]`)).toHaveCount(0)
    await expect(cardByTitle(page, bm.b).locator(`[data-tag-name="${onAll}"]`)).toHaveCount(0)

    // Re-opening reflects the new counts immediately.
    await page.getByTestId('batch-add-tag').click()
    await expect(row(page, onNone)).toHaveAttribute('aria-checked', 'true')
    await expect(row(page, onAll)).toHaveAttribute('aria-checked', 'false')
  })
})
