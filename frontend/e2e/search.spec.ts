import { expect, test, type Browser, type Page } from '@playwright/test'
import { LoginPageObject } from './models/LoginPageObject'

test.describe.configure({ mode: 'serial' })

const BASE = '/api'
const ts = Date.now()
const collectionName = `Search Test ${ts}`
const tagProd = `prod-${ts}`
const tagDev = `dev-${ts}`

const bookmarks = [
  { title: `Production API ${ts}`, url: 'https://api.prod.example.com', tag: tagProd },
  { title: `Production Frontend ${ts}`, url: 'https://frontend.prod.example.com', tag: tagProd },
  { title: `Dev API ${ts}`, url: 'https://api.dev.example.com', tag: tagDev },
  { title: `Standalone Page ${ts}`, url: 'https://standalone.example.com', tag: null },
]

let collectionId: string

async function loginAndNavigateToCollection(page: Page, collectionId: string) {
  const loginPage = new LoginPageObject(page)
  await loginPage.goto()
  await loginPage.login('alice@example.com', 'alice')
  await expect(page).toHaveURL(/\/collections\//)
  await page.goto(`/collections/${collectionId}`)
  await expect(page).toHaveURL(new RegExp(`/collections/${collectionId}`))
}

async function createCollectionViaApi(page: Page, name: string): Promise<string> {
  const resp = await page.request.post(`${BASE}/collections`, { data: { name } })
  expect(resp.ok(), `createCollection failed: ${resp.status()}`).toBeTruthy()
  const body = await resp.json()
  return body.id
}

async function deleteCollectionViaApi(page: Page, id: string) {
  await page.request.delete(`${BASE}/collections/${id}`).catch(() => undefined)
}

async function createTag(page: Page, name: string) {
  await page.getByTestId('new-tag-btn').click()
  await page.getByTestId('create-tag-name-input').fill(name)
  await page.getByTestId('create-tag-submit').click()
}

async function createBookmark(page: Page, title: string, url: string, tagNames: string[] = []) {
  await page.getByRole('button', { name: /add bookmark/i }).click()
  const dialog = page.locator('[role="dialog"]')
  await expect(dialog).toBeVisible()
  await dialog.locator('#create-bookmark-title').fill(title)
  await dialog.locator('#create-bookmark-url').fill(url)
  for (const tagName of tagNames) {
    await dialog.locator('button').filter({ hasText: tagName }).click()
  }
  await dialog.locator('button[type="submit"]').click()
  await expect(dialog).not.toBeVisible()
}

async function search(page: Page, query: string) {
  await page.locator('input[type="text"]').first().fill(query)
}

async function expectBookmarkVisible(page: Page, title: string) {
  await expect(page.locator('h3').filter({ hasText: title })).toBeVisible()
}

async function expectBookmarkNotVisible(page: Page, title: string) {
  await expect(page.locator('h3').filter({ hasText: title })).not.toBeVisible()
}

test.describe('Multi-Term Search', () => {
  test('should set up test data', async ({ page }) => {
    const loginPage = new LoginPageObject(page)
    await loginPage.goto()
    await loginPage.login('alice@example.com', 'alice')
    await expect(page).toHaveURL(/\/collections\//)

    collectionId = await createCollectionViaApi(page, collectionName)
    await page.goto(`/collections/${collectionId}`)
    await expect(page).toHaveURL(new RegExp(`/collections/${collectionId}`))

    await createTag(page, tagProd)
    await createTag(page, tagDev)

    for (const bm of bookmarks) {
      await createBookmark(page, bm.title, bm.url, bm.tag ? [bm.tag] : [])
    }

    for (const bm of bookmarks) {
      await expectBookmarkVisible(page, bm.title)
    }
  })

  test('should filter by single term', async ({ page }) => {
    await loginAndNavigateToCollection(page, collectionId)
    await search(page, 'Production')

    await expectBookmarkVisible(page, bookmarks[0].title)
    await expectBookmarkVisible(page, bookmarks[1].title)
    await expectBookmarkNotVisible(page, bookmarks[2].title)
    await expectBookmarkNotVisible(page, bookmarks[3].title)
  })

  test('should combine multiple terms with AND logic', async ({ page }) => {
    await loginAndNavigateToCollection(page, collectionId)
    await search(page, `Production API`)

    await expectBookmarkVisible(page, bookmarks[0].title)
    await expectBookmarkNotVisible(page, bookmarks[1].title)
    await expectBookmarkNotVisible(page, bookmarks[2].title)
    await expectBookmarkNotVisible(page, bookmarks[3].title)
  })

  test('should match term against tag name', async ({ page }) => {
    await loginAndNavigateToCollection(page, collectionId)
    await search(page, `API ${tagDev}`)

    await expectBookmarkVisible(page, bookmarks[2].title)
    await expectBookmarkNotVisible(page, bookmarks[0].title)
    await expectBookmarkNotVisible(page, bookmarks[1].title)
    await expectBookmarkNotVisible(page, bookmarks[3].title)
  })

  test('should treat quoted phrase as single term', async ({ page }) => {
    await loginAndNavigateToCollection(page, collectionId)
    await search(page, `'Production Frontend'`)

    await expectBookmarkVisible(page, bookmarks[1].title)
    await expectBookmarkNotVisible(page, bookmarks[0].title)
    await expectBookmarkNotVisible(page, bookmarks[2].title)
    await expectBookmarkNotVisible(page, bookmarks[3].title)
  })

  test('should combine quoted phrase with another term', async ({ page }) => {
    await loginAndNavigateToCollection(page, collectionId)
    await search(page, `'Production Frontend' ${tagProd}`)

    await expectBookmarkVisible(page, bookmarks[1].title)
    await expectBookmarkNotVisible(page, bookmarks[0].title)
    await expectBookmarkNotVisible(page, bookmarks[2].title)
    await expectBookmarkNotVisible(page, bookmarks[3].title)
  })

  test('should show empty results when terms do not all match', async ({ page }) => {
    await loginAndNavigateToCollection(page, collectionId)
    await search(page, `Standalone ${tagProd}`)

    await expectBookmarkNotVisible(page, bookmarks[0].title)
    await expectBookmarkNotVisible(page, bookmarks[1].title)
    await expectBookmarkNotVisible(page, bookmarks[2].title)
    await expectBookmarkNotVisible(page, bookmarks[3].title)
  })

  test('should show all bookmarks when search is cleared', async ({ page }) => {
    await loginAndNavigateToCollection(page, collectionId)
    await search(page, 'Production')
    await expect(page.locator('h3').filter({ hasText: `Production` }).first()).toBeVisible()

    await page.locator('input[type="text"]').first().clear()
    await expectBookmarkVisible(page, bookmarks[0].title)
    await expectBookmarkVisible(page, bookmarks[1].title)
    await expectBookmarkVisible(page, bookmarks[2].title)
    await expectBookmarkVisible(page, bookmarks[3].title)
  })

  test('should match term against URL', async ({ page }) => {
    await loginAndNavigateToCollection(page, collectionId)
    await search(page, `standalone.example.com`)

    await expectBookmarkVisible(page, bookmarks[3].title)
    await expectBookmarkNotVisible(page, bookmarks[0].title)
    await expectBookmarkNotVisible(page, bookmarks[1].title)
    await expectBookmarkNotVisible(page, bookmarks[2].title)
  })

  test.afterAll(async ({ browser }: { browser: Browser }) => {
    if (!collectionId) return
    const context = await browser.newContext({ ignoreHTTPSErrors: true })
    const page = await context.newPage()
    try {
      const loginPage = new LoginPageObject(page)
      await loginPage.goto()
      await loginPage.login('alice@example.com', 'alice')
      await expect(page).toHaveURL(/\/collections\//)
      await deleteCollectionViaApi(page, collectionId)
    } finally {
      await context.close()
    }
  })
})
