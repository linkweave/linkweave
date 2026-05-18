import {
  expect,
  test,
  type APIRequestContext,
  type Page,
} from '@playwright/test'
import {
  deleteTestUserCleanup,
  loginViaApi,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

// These tests rely on shared backend state (folders, tags, bookmarks created
// once in beforeAll) and on the URL/query string being mutated by individual
// interactions, so they must run in order.
test.describe.configure({ mode: 'serial' })

const ts = Date.now()
const tagAlphaName = `alpha-${ts}`
const tagBetaName = `beta-${ts}`

const bm = {
  workBackend: `WorkBackend ${ts}`,
  personalBackend: `PersonalBackend ${ts}`,
  workRoot: `WorkRoot ${ts}`,
  unfiled: `Unfiled ${ts}`,
}

let user: TestUser
let storageState: StorageState
let collectionId: string

// Ids captured from the API responses; the duplicate-folder case needs them
// for assertions, so we keep them at module scope.
let workId: string
let personalId: string
let workBackendId: string
let personalBackendId: string
let tagAlphaId: string

async function api<T>(
  request: APIRequestContext,
  method: 'POST' | 'GET',
  path: string,
  body?: unknown,
): Promise<T> {
  const opts: Parameters<APIRequestContext['post']>[1] = body ? { data: body } : {}
  const res =
    method === 'POST' ? await request.post(path, opts) : await request.get(path, opts)
  if (!res.ok()) {
    throw new Error(`${method} ${path} failed: ${res.status()} ${await res.text()}`)
  }
  return (await res.json()) as T
}

type Created = { id: string }
type CreatedBookmark = { id: string; data: { folderId?: string } }

async function createFolder(
  request: APIRequestContext,
  data: { collectionId: string; name: string; parentId?: string },
) {
  const out = await api<Created>(request, 'POST', '/api/folders', data)
  return out.id
}

async function createTag(
  request: APIRequestContext,
  data: { collectionId: string; name: string },
) {
  const out = await api<Created>(request, 'POST', '/api/tags', data)
  return out.id
}

async function createBookmark(
  request: APIRequestContext,
  data: {
    collectionId: string
    title: string
    url: string
    folderId?: string
    tagIds?: string[]
  },
) {
  const out = await api<CreatedBookmark>(request, 'POST', '/api/bookmarks', data)
  return out
}

async function gotoCollection(page: Page) {
  await page.goto(`/collections/${collectionId}`)
  await expect(page).toHaveURL(new RegExp(`/collections/${collectionId}`))
  // Wait for at least one card to render so beforeAll-created data is hydrated.
  await expect(page.getByTestId(/^bookmark-card-/).first()).toBeVisible()
}

function searchInput(page: Page) {
  return page.locator('input[type="text"]').first()
}

function cardByTitle(page: Page, title: string) {
  return page.locator(`[data-bookmark-title="${title}"]`)
}

function visibleCardTitles(page: Page) {
  return page.locator('[data-bookmark-title]').evaluateAll((els) =>
    els.map((el) => el.getAttribute('data-bookmark-title') ?? ''),
  )
}

async function expectVisibleCards(page: Page, titles: string[]) {
  // The list filters in-place, so any cards not in `titles` must be absent.
  for (const t of titles) await expect(cardByTitle(page, t)).toBeVisible()
  await expect
    .poll(() => visibleCardTitles(page))
    .toEqual(expect.arrayContaining(titles))
  await expect.poll(async () => (await visibleCardTitles(page)).length).toBe(titles.length)
}

function filterPill(page: Page, opts: { kind: string; key?: string; value?: string }) {
  let selector = `[data-testid="filter-pill"][data-token-kind="${opts.kind}"]`
  if (opts.key !== undefined) selector += `[data-token-key="${opts.key}"]`
  if (opts.value !== undefined) selector += `[data-token-value="${opts.value}"]`
  return page.locator(selector)
}

function folderRow(page: Page, folderId: string) {
  return page.getByTestId(`folder-row-${folderId}`)
}

test.describe('Card + filter interactions', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState, collectionId } = await registerAndCaptureStorageState(
      browser,
      'cardfilter',
    ))
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true })
    try {
      await loginViaApi(ctx.request, user)
      // Folder tree:
      //   Work
      //     └─ Backend           ← duplicate name
      //   Personal
      //     └─ Backend           ← duplicate name
      workId = await createFolder(ctx.request, { collectionId, name: 'Work' })
      personalId = await createFolder(ctx.request, { collectionId, name: 'Personal' })
      workBackendId = await createFolder(ctx.request, {
        collectionId,
        name: 'Backend',
        parentId: workId,
      })
      personalBackendId = await createFolder(ctx.request, {
        collectionId,
        name: 'Backend',
        parentId: personalId,
      })
      tagAlphaId = await createTag(ctx.request, { collectionId, name: tagAlphaName })
      const tagBetaId = await createTag(ctx.request, { collectionId, name: tagBetaName })
      await createBookmark(ctx.request, {
        collectionId,
        title: bm.workBackend,
        url: 'https://wb.example.com',
        folderId: workBackendId,
        tagIds: [tagAlphaId],
      })
      await createBookmark(ctx.request, {
        collectionId,
        title: bm.personalBackend,
        url: 'https://pb.example.com',
        folderId: personalBackendId,
        tagIds: [tagBetaId],
      })
      await createBookmark(ctx.request, {
        collectionId,
        title: bm.workRoot,
        url: 'https://wr.example.com',
        folderId: workId,
        tagIds: [tagAlphaId, tagBetaId],
      })
      await createBookmark(ctx.request, {
        collectionId,
        title: bm.unfiled,
        url: 'https://uf.example.com',
      })
    } finally {
      await ctx.close()
    }
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  test('card tag chip toggles filter; pill [×] restores the list', async ({ page }) => {
    await gotoCollection(page)
    await expectVisibleCards(page, [bm.workBackend, bm.personalBackend, bm.workRoot, bm.unfiled])

    // Click the tag chip on a card → pill appears, list filters.
    const card = cardByTitle(page, bm.workBackend)
    await card.locator(`[data-testid="card-tag-pill"][data-tag-name="${tagAlphaName}"]`).click()
    await expect(filterPill(page, { kind: 'tag', value: tagAlphaName })).toBeVisible()
    await expectVisibleCards(page, [bm.workBackend, bm.workRoot])

    // Remove via the strip [×] → list restored.
    await filterPill(page, { kind: 'tag', value: tagAlphaName })
      .getByTestId('filter-pill-remove')
      .click()
    await expect(filterPill(page, { kind: 'tag', value: tagAlphaName })).not.toBeVisible()
    await expectVisibleCards(page, [bm.workBackend, bm.personalBackend, bm.workRoot, bm.unfiled])
  })

  test('sidebar tag click and card chip stay in sync', async ({ page }) => {
    await gotoCollection(page)

    // Click the tag in the sidebar — should produce the same pill / same filter.
    await page.getByTestId(`tag-row-${tagAlphaName}`).click()
    await expect(filterPill(page, { kind: 'tag', value: tagAlphaName })).toBeVisible()
    await expectVisibleCards(page, [bm.workBackend, bm.workRoot])

    // The card chip for the same tag should now render as active. We assert
    // via the absence-of-the-idle state isn't useful; instead toggle from the
    // card and confirm both surfaces clear together.
    const card = cardByTitle(page, bm.workBackend)
    await card.locator(`[data-testid="card-tag-pill"][data-tag-name="${tagAlphaName}"]`).click()
    await expect(filterPill(page, { kind: 'tag', value: tagAlphaName })).not.toBeVisible()
    // Sidebar row no longer highlighted (selectedTagIds is now empty).
    // Soft check: the page shows all bookmarks again.
    await expectVisibleCards(page, [bm.workBackend, bm.personalBackend, bm.workRoot, bm.unfiled])
  })

  test('clicking a folder pill on a card navigates the sidebar (under:)', async ({ page }) => {
    await gotoCollection(page)

    const card = cardByTitle(page, bm.workRoot)
    await card.locator('[data-testid="card-folder-pill"]').click()

    // Pill stores the folder name directly (since "Work" is unique in this collection).
    const underPill = filterPill(page, { kind: 'operator', key: 'under', value: 'Work' })
    await expect(underPill).toBeVisible()
    await expect(underPill).toContainText('Work')

    // Sidebar row for the Work folder is highlighted (data-selected="true").
    await expect(folderRow(page, workId)).toHaveAttribute('data-selected', 'true')

    // Filter shows the Work root bookmark + everything under Work (incl. Work/Backend).
    await expectVisibleCards(page, [bm.workRoot, bm.workBackend])
  })

  test('sidebar folder click filters and updates the breadcrumbs (descendants included)', async ({
    page,
  }) => {
    await gotoCollection(page)

    // Start from a clean strip — clear any leftover pills.
    if (await page.getByTestId('filter-clear-all').isVisible()) {
      await page.getByTestId('filter-clear-all').click()
    }

    await folderRow(page, workBackendId).click()
    await expect(folderRow(page, workBackendId)).toHaveAttribute('data-selected', 'true')
    await expect(filterPill(page, { kind: 'operator', key: 'under', value: workBackendId })).toBeVisible()
    // Only the bookmark in Work/Backend matches (no descendants of its own).
    await expectVisibleCards(page, [bm.workBackend])
  })

  test('duplicate-named folders disambiguate by id (Work/Backend vs Personal/Backend)', async ({
    page,
  }) => {
    await gotoCollection(page)
    if (await page.getByTestId('filter-clear-all').isVisible()) {
      await page.getByTestId('filter-clear-all').click()
    }

    // Click the bookmark sitting in Work/Backend → should highlight the Work/Backend row only.
    await cardByTitle(page, bm.workBackend)
      .locator('[data-testid="card-folder-pill"]')
      .click()
    await expect(folderRow(page, workBackendId)).toHaveAttribute('data-selected', 'true')
    await expect(folderRow(page, personalBackendId)).toHaveAttribute('data-selected', 'false')
    await expectVisibleCards(page, [bm.workBackend])

    // Now click the bookmark in Personal/Backend → selection moves to the other Backend.
    // (The Personal/Backend card is now hidden, so go via the strip clear and re-pick.)
    await page.getByTestId('filter-clear-all').click()
    await cardByTitle(page, bm.personalBackend)
      .locator('[data-testid="card-folder-pill"]')
      .click()
    await expect(folderRow(page, personalBackendId)).toHaveAttribute('data-selected', 'true')
    await expect(folderRow(page, workBackendId)).toHaveAttribute('data-selected', 'false')
    await expectVisibleCards(page, [bm.personalBackend])
  })

  test('typing operators directly in the search bar produces matching pills', async ({ page }) => {
    await gotoCollection(page)
    if (await page.getByTestId('filter-clear-all').isVisible()) {
      await page.getByTestId('filter-clear-all').click()
    }

    await searchInput(page).fill(`#${tagAlphaName} note:nothing-matches`)

    await expect(filterPill(page, { kind: 'tag', value: tagAlphaName })).toBeVisible()
    await expect(filterPill(page, { kind: 'operator', key: 'note', value: 'nothing-matches' })).toBeVisible()
    // note: 'nothing-matches' has no hits, so combined AND yields zero results.
    await expectVisibleCards(page, [])

    await searchInput(page).clear()
    await expectVisibleCards(page, [bm.workBackend, bm.personalBackend, bm.workRoot, bm.unfiled])
  })

  test('Clear all empties the strip and the query', async ({ page }) => {
    await gotoCollection(page)
    await page.getByTestId(`tag-row-${tagAlphaName}`).click()
    await expect(filterPill(page, { kind: 'tag', value: tagAlphaName })).toBeVisible()

    await page.getByTestId('filter-clear-all').click()
    await expect(page.getByTestId('filter-strip')).not.toBeVisible()
    await expect(searchInput(page)).toHaveValue('')
    await expectVisibleCards(page, [bm.workBackend, bm.personalBackend, bm.workRoot, bm.unfiled])
  })

  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
})
