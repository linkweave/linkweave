import { expect, type Page, test } from '@playwright/test'
import { BASE } from './helpers/api'
import {
  deleteTestUserCleanup,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'
import { CleanupSuggestionsPageObject } from './models/CleanupSuggestionsPageObject'

test.describe.configure({ mode: 'serial' })

const ts = Date.now()

let user: TestUser
let collectionId: string
let storageState: StorageState

async function gotoCollection(page: Page) {
  await page.goto(`/collections/${collectionId}`)
  await expect(page).toHaveURL(new RegExp(`/collections/${collectionId}`))
}

async function navigateToCleanupSuggestions(page: Page) {
  await page.getByTestId('user-menu-trigger').click()
  await page.getByTestId('user-menu-cleanup-suggestions').click()
  await expect(page).toHaveURL(/\/cleanup-suggestions/)
}

async function createBookmarkViaApi(
  page: Page,
  collId: string,
  title: string,
  url: string,
): Promise<string> {
  const resp = await page.request.post(`${BASE}/bookmarks`, {
    data: { collectionId: collId, title, url },
  })
  expect(resp.ok(), `createBookmark failed: ${resp.status()}`).toBeTruthy()
  const body = await resp.json()
  return body.id
}

async function trackClickViaApi(page: Page, bookmarkId: string) {
  const resp = await page.request.post(`${BASE}/bookmarks/${bookmarkId}/track-click`)
  expect(resp.ok(), `trackClick failed: ${resp.status()}`).toBeTruthy()
}

async function travelTo(page: Page, isoInstant: string) {
  const resp = await page.request.post(`${BASE}/dev/time-travel`, {
    data: { instant: isoInstant },
  })
  expect(resp.ok(), `time-travel failed: ${resp.status()}`).toBeTruthy()
}

async function resetClock(page: Page) {
  const resp = await page.request.delete(`${BASE}/dev/time-travel`)
  expect(resp.ok(), `reset clock failed: ${resp.status()}`).toBeTruthy()
}

function isoMonthsAgo(months: number): string {
  const d = new Date()
  d.setMonth(d.getMonth() - months)
  return d.toISOString()
}

test.describe('Cleanup Suggestions', () => {
  let staleAId: string
  let staleBId: string
  let neverClickedId: string

  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState, collectionId } = await registerAndCaptureStorageState(
      browser,
      'cleanup',
    ))
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  test('should show empty state when no stale bookmarks exist', async ({ page }) => {
    await gotoCollection(page)
    await navigateToCleanupSuggestions(page)

    const cleanup = new CleanupSuggestionsPageObject(page)
    await expect(cleanup.heading).toBeVisible()
    await expect(cleanup.emptyState).toBeVisible()
  })

  test('should set up stale bookmarks and show them as suggestions', async ({ page }) => {
    await gotoCollection(page)

    // Time-travel the backend so newly created bookmarks land in the past.
    // staleA/B are created and clicked 7-8 months ago (clicked-but-stale).
    // neverClickedId is created 7 months ago and never clicked.
    try {
      await travelTo(page, isoMonthsAgo(8))
      staleBId = await createBookmarkViaApi(
        page,
        collectionId,
        `Stale-B-${ts}`,
        'https://stale-b.example.com',
      )
      await trackClickViaApi(page, staleBId)

      await travelTo(page, isoMonthsAgo(7))
      staleAId = await createBookmarkViaApi(
        page,
        collectionId,
        `Stale-A-${ts}`,
        'https://stale-a.example.com',
      )
      await trackClickViaApi(page, staleAId)

      neverClickedId = await createBookmarkViaApi(
        page,
        collectionId,
        `Never-Clicked-${ts}`,
        'https://never-clicked.example.com',
      )
    } finally {
      await resetClock(page)
    }

    await navigateToCleanupSuggestions(page)

    const cleanup = new CleanupSuggestionsPageObject(page)
    await expect(cleanup.heading).toBeVisible()
    await cleanup.expectSuggestionVisible(staleAId)
    await cleanup.expectSuggestionVisible(staleBId)
    await cleanup.expectSuggestionVisible(neverClickedId)

    await expect(cleanup.suggestionRow(neverClickedId)).toContainText('Never clicked')
  })

  test('should dismiss a suggestion', async ({ page }) => {
    await gotoCollection(page)
    await navigateToCleanupSuggestions(page)

    const cleanup = new CleanupSuggestionsPageObject(page)
    await cleanup.expectSuggestionVisible(staleBId)
    await cleanup.dismiss(staleBId)
    await cleanup.expectSuggestionNotVisible(staleBId)
  })

  test('should move selected bookmarks to trash', async ({ page }) => {
    await gotoCollection(page)
    await navigateToCleanupSuggestions(page)

    const cleanup = new CleanupSuggestionsPageObject(page)
    await cleanup.selectSuggestion(staleAId)
    await cleanup.moveToTrash()
    await cleanup.expectSuggestionNotVisible(staleAId)

    await gotoCollection(page)
    await expect(page.locator('h3').filter({ hasText: `Stale-A-${ts}` })).not.toBeVisible()
  })

  test('should select all and move to trash', async ({ page }) => {
    await gotoCollection(page)
    await navigateToCleanupSuggestions(page)

    const cleanup = new CleanupSuggestionsPageObject(page)
    await cleanup.selectAllAndMoveToTrash()
    await expect(cleanup.emptyState).toBeVisible()
  })

  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
})
