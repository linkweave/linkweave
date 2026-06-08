import { expect, test, type APIRequestContext, type Browser } from '@playwright/test'
import { BASE, createCollectionViaApi } from './helpers/api'
import { login } from './helpers/auth'
import { openAddBookmarkDialog } from './helpers/bookmarks'

test.describe.configure({ mode: 'serial' })

const ts = Date.now()
const collectionName = `Auto-Tag Test ${ts}`
const bookmarkTitle = `AutoTag-${ts}`
const bookmarkUrl = `https://dev.acme-${ts}.example.com`

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
    const context = await browser.newContext({ ignoreHTTPSErrors: true })
    const page = await context.newPage()
    try {
      await login(page)
      collectionId = await createCollectionViaApi(page.request, collectionName)
      // Seed the auto-tag rules this spec relies on, in a freshly-created
      // collection so we don't depend on dev-DB state.
      await createAutoTagRuleViaApi(page.request, collectionId, 'dev\\.', 'dev')
      await createAutoTagRuleViaApi(page.request, collectionId, 'uat', 'uat')
    } finally {
      await context.close()
    }
  })

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
    // Section reserves space (always rendered) but contains no chips when no rule matches.
    await expect(dialog.locator('[data-testid^="suggested-tag-"]')).toHaveCount(0)
    await expect(dialog.getByTestId('accept-suggestions-btn')).toHaveCount(0)

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

  test.afterAll(async ({ browser }: { browser: Browser }) => {
    if (!collectionId) return
    const context = await browser.newContext({ ignoreHTTPSErrors: true })
    const page = await context.newPage()
    try {
      await login(page)
      await page.request.delete(`${BASE}/collections/${collectionId}`).catch(() => undefined)
    } finally {
      await context.close()
    }
  })
})
