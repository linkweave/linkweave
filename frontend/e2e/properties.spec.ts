import { expect, test, type APIRequestContext, type Browser, type Page } from '@playwright/test'
import {
  deleteTestUserCleanup,
  loginViaApi,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

// End-to-end coverage for UC-067 property filtering:
//   - sidebar property row → bare `property:<name>` (existence) token
//   - card property badge   → `property:<name>=<value>` (equality) token
//
// We set up the world (property definitions + bookmarks + values) via HTTP
// against the same Quarkus instance the dev server proxies to. This keeps
// the test fast and focused on the UI behaviour we just shipped — the
// definition + value-editing dialogs already have unit coverage.

let user: TestUser
let storageState: StorageState
let collectionId: string

// Captured during beforeAll so the tests can target a specific badge directly.
let bookmarkWithStatusDraftId: string
let bookmarkWithStatusPublishedId: string
let bookmarkWithPriorityOnlyId: string

type PropDefResp = { id: string; data: { name: string } }
type BookmarkResp = { id: string }

// Parallel workers hammer the dev SQLite DB, which occasionally answers 500 on
// concurrent writes. Same retry pattern as TestUser.ts — only retry transient
// 5xx; let 4xx fail fast so a real bug surfaces.
async function postJsonWithRetry(
  request: APIRequestContext,
  path: string,
  body: unknown,
): Promise<unknown> {
  let lastStatus = 0
  let lastBody = ''
  for (let attempt = 0; attempt < 3; attempt++) {
    const resp = await request.post(path, { data: body })
    lastStatus = resp.status()
    if (resp.ok()) return await resp.json()
    lastBody = await resp.text().catch(() => '')
    if (lastStatus < 500) break
    await new Promise((r) => setTimeout(r, 300))
  }
  throw new Error(`POST ${path} failed: ${lastStatus} ${lastBody}`)
}

async function putJsonWithRetry(
  request: APIRequestContext,
  path: string,
  body: unknown,
): Promise<void> {
  let lastStatus = 0
  let lastBody = ''
  for (let attempt = 0; attempt < 3; attempt++) {
    const resp = await request.put(path, { data: body })
    lastStatus = resp.status()
    if (resp.ok()) return
    lastBody = await resp.text().catch(() => '')
    if (lastStatus < 500) break
    await new Promise((r) => setTimeout(r, 300))
  }
  throw new Error(`PUT ${path} failed: ${lastStatus} ${lastBody}`)
}

async function createPropertyDef(
  request: APIRequestContext,
  body: {
    collectionId: string
    name: string
    type: 'TEXT' | 'SELECT' | 'NUMBER'
    allowedValues?: string
    sortOrder: number
  },
): Promise<PropDefResp> {
  return (await postJsonWithRetry(request, '/api/property-definitions', body)) as PropDefResp
}

async function createBookmark(
  request: APIRequestContext,
  title: string,
  url: string,
): Promise<BookmarkResp> {
  return (await postJsonWithRetry(request, '/api/bookmarks', {
    collectionId,
    title,
    url,
  })) as BookmarkResp
}

async function setBookmarkProperties(
  request: APIRequestContext,
  bookmarkId: string,
  values: Array<{ definitionId: string; valueText?: string; valueNumber?: number }>,
): Promise<void> {
  await putJsonWithRetry(request, `/api/bookmarks/${bookmarkId}/properties`, {
    propertyValues: values,
  })
}

async function seedWorld(browser: Browser): Promise<void> {
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true })
  try {
    await loginViaApi(ctx.request, user)

    const statusDef = await createPropertyDef(ctx.request, {
      collectionId,
      name: 'status',
      type: 'SELECT',
      allowedValues: 'draft,review,published',
      sortOrder: 0,
    })
    const priorityDef = await createPropertyDef(ctx.request, {
      collectionId,
      name: 'priority',
      type: 'NUMBER',
      sortOrder: 1,
    })

    const draft = await createBookmark(ctx.request, 'Draft bookmark', 'https://example.com/a')
    const published = await createBookmark(ctx.request, 'Published bookmark', 'https://example.com/b')
    const priorityOnly = await createBookmark(ctx.request, 'Priority-only bookmark', 'https://example.com/c')
    // Fourth bookmark intentionally left without any property values — confirms
    // the existence filter excludes it.
    await createBookmark(ctx.request, 'Plain bookmark', 'https://example.com/d')

    bookmarkWithStatusDraftId = draft.id
    bookmarkWithStatusPublishedId = published.id
    bookmarkWithPriorityOnlyId = priorityOnly.id

    await setBookmarkProperties(ctx.request, draft.id, [
      { definitionId: statusDef.id, valueText: 'draft' },
      { definitionId: priorityDef.id, valueNumber: 5 },
    ])
    await setBookmarkProperties(ctx.request, published.id, [
      { definitionId: statusDef.id, valueText: 'published' },
    ])
    await setBookmarkProperties(ctx.request, priorityOnly.id, [
      { definitionId: priorityDef.id, valueNumber: 2 },
    ])
  } finally {
    await ctx.close()
  }
}

async function visibleCardCount(page: Page): Promise<number> {
  return page.locator('[data-testid^="bookmark-card-"]').count()
}

async function searchQueryValue(page: Page): Promise<string> {
  return page.locator('[data-search-input]').first().inputValue()
}

test.describe('UC-067 property filtering', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState, collectionId } = await registerAndCaptureStorageState(
      browser,
      'properties',
    ))
    await seedWorld(browser)
  })

  test.use({
    storageState: async ({}, use) => {
      await use(storageState)
    },
  })

  test('sidebar row toggles existence filter on/off', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)
    // 4 bookmarks total, no filter yet
    await expect.poll(() => visibleCardCount(page)).toBe(4)

    const statusRow = page.locator('[data-testid="sidebar-property-row-status"]')
    await expect(statusRow).toBeVisible()
    await statusRow.click()

    // Only the two with status set should remain; query is bare-key
    await expect.poll(() => searchQueryValue(page)).toBe('property:status')
    await expect.poll(() => visibleCardCount(page)).toBe(2)
    await expect(statusRow).toHaveAttribute('data-active', 'true')

    // Sanity: the bookmark with only `priority` set is hidden.
    await expect(page.locator(`[data-testid="bookmark-card-${bookmarkWithPriorityOnlyId}"]`)).toHaveCount(0)
    await expect(page.locator(`[data-testid="bookmark-card-${bookmarkWithStatusDraftId}"]`)).toHaveCount(1)

    // Click again → cleared
    await statusRow.click()
    await expect.poll(() => searchQueryValue(page)).toBe('')
    await expect.poll(() => visibleCardCount(page)).toBe(4)
    await expect(statusRow).toHaveAttribute('data-active', 'false')
  })

  test('card badge click filters by equality and lights up purple', async ({ page }) => {
    // Enable badges on cards before the SPA boots so the row renders on first paint.
    await page.addInitScript(() => {
      localStorage.setItem('chainlink:showPropertyBadges', 'true')
    })
    await page.goto(`/collections/${collectionId}`)
    await expect.poll(() => visibleCardCount(page)).toBe(4)

    const draftCard = page.locator(`[data-testid="bookmark-card-${bookmarkWithStatusDraftId}"]`)
    const statusBadge = draftCard.locator('[data-testid="card-property-badge-status"]')
    await expect(statusBadge).toBeVisible()
    await expect(statusBadge).toHaveAttribute('data-active', 'false')

    await statusBadge.click()

    // Only the draft bookmark should remain.
    await expect.poll(() => searchQueryValue(page)).toBe('property:status=draft')
    await expect.poll(() => visibleCardCount(page)).toBe(1)
    await expect(statusBadge).toHaveAttribute('data-active', 'true')

    // Purple border on the active badge — computed value differs per theme but
    // is never the default grey `--color-border` (rgb(229, 231, 235)).
    const activeBorder = await statusBadge.evaluate((el) => getComputedStyle(el).borderColor)
    expect(activeBorder).not.toBe('rgb(229, 231, 235)')

    // A different status (published) is not active.
    const publishedCard = page.locator(
      `[data-testid="bookmark-card-${bookmarkWithStatusPublishedId}"]`,
    )
    await expect(publishedCard).toHaveCount(0)

    // Click the badge again → clear.
    await statusBadge.click()
    await expect.poll(() => searchQueryValue(page)).toBe('')
    await expect.poll(() => visibleCardCount(page)).toBe(4)
  })

  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
})
