import { expect, type Page, test } from './fixtures'
import { createBookmarkViaUi } from './helpers/bookmarks'
import { gotoCollection, useTestCollectionWithCleanup } from './helpers/testCollection'

test.describe.configure({ mode: 'serial' })

const ts = Date.now()
const tagProd = `prod-${ts}`
const tagDev = `dev-${ts}`

const bookmarks = [
  { title: `Production API ${ts}`, url: 'https://api.prod.example.com', tag: tagProd },
  { title: `Production Frontend ${ts}`, url: 'https://frontend.prod.example.com', tag: tagProd },
  { title: `Dev API ${ts}`, url: 'https://api.dev.example.com', tag: tagDev },
  { title: `Standalone Page ${ts}`, url: 'https://standalone.example.com', tag: null },
]

async function createTag(page: Page, name: string) {
  await page.getByTestId('new-tag-btn').click()
  await page.getByTestId('create-tag-name-input').fill(name)
  await page.getByTestId('create-tag-submit').click()
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
  const collection = useTestCollectionWithCleanup('search')
  test.use({ storageState: async ({}, use) => { await use(collection.storageState!) } })

  test('should set up test data', async ({ page }) => {
    await gotoCollection(page, collection)

    await createTag(page, tagProd)
    await createTag(page, tagDev)

    for (const bm of bookmarks) {
      await createBookmarkViaUi(page, bm.title, bm.url, bm.tag ? [bm.tag] : [])
    }

    for (const bm of bookmarks) {
      await expectBookmarkVisible(page, bm.title)
    }
  })

  test('should filter by single term', async ({ page }) => {
    // ARRANGE
    await gotoCollection(page, collection)
    // ACT
    await search(page, 'Production')

    // ASSERT
    await expectBookmarkVisible(page, bookmarks[0].title)
    await expectBookmarkVisible(page, bookmarks[1].title)
    await expectBookmarkNotVisible(page, bookmarks[2].title)
    await expectBookmarkNotVisible(page, bookmarks[3].title)
  })

  test('should combine multiple terms with AND logic', async ({ page }) => {
    // ARRANGE
    await gotoCollection(page, collection)
    // ACT
    await search(page, `Production API`)

    // ASSERT
    await expectBookmarkVisible(page, bookmarks[0].title)
    await expectBookmarkNotVisible(page, bookmarks[1].title)
    await expectBookmarkNotVisible(page, bookmarks[2].title)
    await expectBookmarkNotVisible(page, bookmarks[3].title)
  })

  test('should combine free-text terms with OR when match:or is present', async ({ page }) => {
    // ARRANGE — the same two terms AND to nothing: no bookmark is both.
    await gotoCollection(page, collection)
    await search(page, `Frontend Standalone`)
    await expectBookmarkNotVisible(page, bookmarks[1].title)
    await expectBookmarkNotVisible(page, bookmarks[3].title)

    // ACT — UC-070 BR-081.
    await search(page, `match:or Frontend Standalone`)

    // ASSERT — either term is now enough.
    await expectBookmarkVisible(page, bookmarks[1].title)
    await expectBookmarkVisible(page, bookmarks[3].title)
    await expectBookmarkNotVisible(page, bookmarks[0].title)
    await expectBookmarkNotVisible(page, bookmarks[2].title)
  })

  test('should keep operators ANDed and exclusions unconditional in OR mode', async ({ page }) => {
    // ARRANGE
    await gotoCollection(page, collection)
    // ACT — the tag is not part of the OR: it must still hold for every hit.
    await search(page, `#${tagProd} match:or API Standalone`)

    // ASSERT — Standalone Page matches the OR but lacks the tag, so it is out.
    await expectBookmarkVisible(page, bookmarks[0].title)
    await expectBookmarkNotVisible(page, bookmarks[3].title)
    await expectBookmarkNotVisible(page, bookmarks[2].title)

    // ACT — an exclusion is never ORed: it removes the bookmark outright even
    // though "API" matched.
    await search(page, `match:or API Frontend -${tagDev}`)

    // ASSERT
    await expectBookmarkVisible(page, bookmarks[0].title)
    await expectBookmarkVisible(page, bookmarks[1].title)
    await expectBookmarkNotVisible(page, bookmarks[2].title)
  })

  test('should flag an unrecognised match: mode instead of guessing at it', async ({ page }) => {
    // ARRANGE
    await gotoCollection(page, collection)
    // ACT
    await search(page, `match:xor Frontend `)

    // ASSERT — invalid syntax (A2): nothing matches and the token is flagged,
    // rather than silently falling back to AND or OR.
    const flag = page.locator('[data-testid="filter-pill"][data-invalid="true"]')
    await expect(flag).toBeVisible()
    await expect(flag).toContainText('match:xor')
    for (const bm of bookmarks) {
      await expectBookmarkNotVisible(page, bm.title)
    }
  })

  test('should match term against tag name', async ({ page }) => {
    // ARRANGE
    await gotoCollection(page, collection)
    // ACT
    await search(page, `API ${tagDev}`)

    // ASSERT
    await expectBookmarkVisible(page, bookmarks[2].title)
    await expectBookmarkNotVisible(page, bookmarks[0].title)
    await expectBookmarkNotVisible(page, bookmarks[1].title)
    await expectBookmarkNotVisible(page, bookmarks[3].title)
  })

  test('should treat quoted phrase as single term', async ({ page }) => {
    // ARRANGE
    await gotoCollection(page, collection)
    // ACT
    await search(page, `'Production Frontend'`)

    // ASSERT
    await expectBookmarkVisible(page, bookmarks[1].title)
    await expectBookmarkNotVisible(page, bookmarks[0].title)
    await expectBookmarkNotVisible(page, bookmarks[2].title)
    await expectBookmarkNotVisible(page, bookmarks[3].title)
  })

  test('should combine quoted phrase with another term', async ({ page }) => {
    // ARRANGE
    await gotoCollection(page, collection)
    // ACT
    await search(page, `'Production Frontend' ${tagProd}`)

    // ASSERT
    await expectBookmarkVisible(page, bookmarks[1].title)
    await expectBookmarkNotVisible(page, bookmarks[0].title)
    await expectBookmarkNotVisible(page, bookmarks[2].title)
    await expectBookmarkNotVisible(page, bookmarks[3].title)
  })

  test('should show empty results when terms do not all match', async ({ page }) => {
    // ARRANGE
    await gotoCollection(page, collection)
    // ACT
    await search(page, `Standalone ${tagProd}`)

    // ASSERT
    await expectBookmarkNotVisible(page, bookmarks[0].title)
    await expectBookmarkNotVisible(page, bookmarks[1].title)
    await expectBookmarkNotVisible(page, bookmarks[2].title)
    await expectBookmarkNotVisible(page, bookmarks[3].title)
  })

  test('should show all bookmarks when search is cleared', async ({ page }) => {
    await gotoCollection(page, collection)
    await search(page, 'Production')
    await expect(page.locator('h3').filter({ hasText: `Production` }).first()).toBeVisible()

    await page.locator('input[type="text"]').first().clear()
    await expectBookmarkVisible(page, bookmarks[0].title)
    await expectBookmarkVisible(page, bookmarks[1].title)
    await expectBookmarkVisible(page, bookmarks[2].title)
    await expectBookmarkVisible(page, bookmarks[3].title)
  })

  test('should match term against URL', async ({ page }) => {
    // ARRANGE
    await gotoCollection(page, collection)
    // ACT
    await search(page, `standalone.example.com`)

    // ASSERT
    await expectBookmarkVisible(page, bookmarks[3].title)
    await expectBookmarkNotVisible(page, bookmarks[0].title)
    await expectBookmarkNotVisible(page, bookmarks[1].title)
    await expectBookmarkNotVisible(page, bookmarks[2].title)
  })
})
