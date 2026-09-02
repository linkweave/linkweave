import { expect, test, type APIRequestContext } from './fixtures'
import { BASE, createCollectionViaApi } from './helpers/api'
import { openAddBookmarkDialog } from './helpers/bookmarks'
import {
  deleteTestUserCleanup,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

test.describe.configure({ mode: 'serial' })

const ts = Date.now()
const collectionName = `Auto-Tag Test ${ts}`
const bookmarkTitle = `AutoTag-${ts}`
const bookmarkUrl = `https://dev.acme-${ts}.example.com`

let user: TestUser
let storageState: StorageState
let collectionId: string

async function createAutoTagRuleViaApi(
  request: APIRequestContext,
  collectionId: string,
  pattern: string,
  tagNames: string,
) {
  const resp = await request.post(`${BASE}/auto-tag-rules`, {
    data: { collectionId, pattern, tagNames, enabled: true },
  })
  expect(resp.ok(), `createAutoTagRule failed: ${resp.status()}`).toBeTruthy()
}

async function listTags(request: APIRequestContext, collectionId: string) {
  const resp = await request.get(`${BASE}/tags`, { params: { collectionId } })
  expect(resp.ok(), `listTags failed: ${resp.status()}`).toBeTruthy()
  const body = (await resp.json()) as { tagList: Array<{ id: string; data: { name: string } }> }
  return body.tagList
}

async function findBookmarkByTitle(
  request: APIRequestContext,
  collectionId: string,
  title: string,
) {
  const resp = await request.get(`${BASE}/bookmarks`, { params: { collectionId } })
  if (!resp.ok()) return null
  const body = (await resp.json()) as {
    bookmarkList: Array<{ id: string; data: { title: string; tagIds?: string[] } }>
  }
  return body.bookmarkList.find((b) => b.data.title === title) ?? null
}

test.describe('Auto-Tag Bookmark by URL Pattern', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState } = await registerAndCaptureStorageState(browser, 'autotag'))
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, storageState })
    try {
      collectionId = await createCollectionViaApi(ctx.request, collectionName)
      // Seed the auto-tag rules this spec relies on, in a freshly-created
      // collection so we don't depend on dev-DB state.
      await createAutoTagRuleViaApi(ctx.request, collectionId, 'dev\\.', 'dev')
      await createAutoTagRuleViaApi(ctx.request, collectionId, 'uat', 'uat')
    } finally {
      await ctx.close()
    }
  })

  test.afterAll(async ({ browser }) => {
    await deleteTestUserCleanup(browser, () => user)
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  test('shows suggestions, accepts them, and creates bookmark with the new tag', async ({ page }) => {
    const dialog = await openAddBookmarkDialog(page, collectionId)

    await dialog.locator('#create-bookmark-title').fill(bookmarkTitle)
    await dialog.locator('#create-bookmark-url').fill(bookmarkUrl)

    const suggestionsSection = dialog.getByTestId('suggested-tags-section')
    await expect(suggestionsSection).toBeVisible()
    const devChip = suggestionsSection.getByTestId('suggested-tag-dev')
    await expect(devChip).toBeVisible()

    await dialog.getByTestId('accept-suggestions-btn').click()
    // accept-suggestions auto-creates the missing tag via POST /tags before
    // adding it to the form's tagIds. Wait for the suggestions to clear
    // (they hide once accepted) before submitting.
    await expect(devChip).toHaveCount(0)

    await dialog.locator('button[type="submit"]').click()
    await expect(dialog).not.toBeVisible({ timeout: 15000 })

    await expect(page.locator('h3').filter({ hasText: bookmarkTitle })).toBeVisible()

    const tags = await listTags(page.request, collectionId)
    const devTag = tags.find((t) => t.data.name === 'dev')
    expect(devTag, 'dev tag should exist (auto-created)').toBeTruthy()

    const bookmark = await findBookmarkByTitle(page.request, collectionId, bookmarkTitle)
    expect(bookmark, 'bookmark should exist after creation').toBeTruthy()

    const assignedTagIds = bookmark?.data.tagIds ?? []
    expect(assignedTagIds, 'bookmark should have the auto-created dev tag').toContain(devTag!.id)
  })

  test('hides suggestions section for non-matching URL', async ({ page }) => {
    const dialog = await openAddBookmarkDialog(page, collectionId)

    await dialog.locator('#create-bookmark-url').fill('https://www.example.com')
    // No rule matches, so there are no rule chips and nothing to accept. The AI
    // group may still be present -- it is the collection's AI setting that
    // governs that (UC-112), not whether a rule happened to match.
    await expect(dialog.locator('[data-testid^="suggested-tag-"]')).toHaveCount(0)
    await expect(dialog.getByTestId('accept-suggestions-btn')).toHaveCount(0)

    await dialog.getByRole('button', { name: /cancel/i }).click()
    await expect(dialog).not.toBeVisible()
  })

  test('hides the AI group entirely when the collection has opted out', async ({ page, request }) => {
    // ARRANGE — a collection of its own, so toggling the setting cannot affect
    // the other tests in this serial file. UC-112 BR-112-5: any member with
    // access may flip it, so the owning user's own session can.
    const optedOutId = await createCollectionViaApi(request, `Auto-Tag OptOut ${ts}`)
    await createAutoTagRuleViaApi(request, optedOutId, 'dev\\.', 'dev')

    const withAi = await openAddBookmarkDialog(page, optedOutId)
    await withAi.locator('#create-bookmark-url').fill('https://dev.example.com')
    await expect(withAi.getByTestId('ai-suggestions-group'))
      .toBeVisible()
    await withAi.getByRole('button', { name: /cancel/i }).click()

    // ACT
    const resp = await request.put(`${BASE}/collections/${optedOutId}/ai-tagging`, {
      data: { enabled: false },
    })
    expect(resp.ok(), `toggle failed: ${resp.status()}`).toBeTruthy()

    // ASSERT — the AI group is gone entirely (BR-112-7): no heading, no
    // placeholder, no "unavailable" note. Rule suggestions are untouched.
    const dialog = await openAddBookmarkDialog(page, optedOutId)
    await dialog.locator('#create-bookmark-url').fill('https://dev.example.com')
    await expect(dialog.getByTestId('suggested-tag-dev')).toBeVisible()
    await expect(dialog.getByTestId('ai-suggestions-group')).toHaveCount(0)
    await expect(dialog.getByTestId('ai-suggest-btn')).toHaveCount(0)

    await dialog.getByRole('button', { name: /cancel/i }).click()
    await expect(dialog).not.toBeVisible()
  })

  test('disables accept button when all suggestions are deselected', async ({ page }) => {
    const dialog = await openAddBookmarkDialog(page, collectionId)

    await dialog.locator('#create-bookmark-url').fill('https://uat-api.example.com')
    const uatChip = dialog.getByTestId('suggested-tag-uat')
    await expect(uatChip).toBeVisible()

    await uatChip.click() // deselect

    const acceptBtn = dialog.getByTestId('accept-suggestions-btn')
    await expect(acceptBtn).toBeDisabled()

    await dialog.getByRole('button', { name: /cancel/i }).click()
    await expect(dialog).not.toBeVisible()
  })
})
