import { expect, test } from './fixtures'
import { CollectionManagePageObject } from './models/CollectionManagePageObject'
import {
  deleteTestUserCleanup,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

test.describe.configure({ mode: 'serial' })

let user: TestUser
let storageState: StorageState

test.describe('Collection Search', () => {
  const ts = Date.now()
  const alpha = `Alpha Collection ${ts}`
  const beta = `Beta Project ${ts}`
  const gamma = `Gamma Archive ${ts}`
  // A colon in a collection name is plain text here: this view filters by
  // substring and must not inherit the bookmark-query grammar.
  const colon = `Delta Q3:Ops ${ts}`

  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState } = await registerAndCaptureStorageState(browser, 'colsearch'))
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  test.beforeEach(async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    await manage.navigate()
  })

  test('should set up test data', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    await manage.createCollection(alpha)
    await manage.createCollection(beta)
    await manage.createCollection(gamma)
    await manage.createCollection(colon)

    await manage.expectCollectionVisible(alpha)
    await manage.expectCollectionVisible(beta)
    await manage.expectCollectionVisible(gamma)
    await manage.expectCollectionVisible(colon)
  })

  test('should filter collections by search query', async ({ page }) => {
    // ARRANGE
    const searchInput = page.locator('input[placeholder*="Search collections"]')
    // ACT
    await searchInput.fill('Alpha')

    // ASSERT
    await expect(page.locator('[data-testid^="collection-row-"]', { hasText: alpha })).toBeVisible()
    await expect(
      page.locator('[data-testid^="collection-row-"]', { hasText: beta }),
    ).not.toBeVisible()
    await expect(
      page.locator('[data-testid^="collection-row-"]', { hasText: gamma }),
    ).not.toBeVisible()
  })

  test('should show no results message when nothing matches', async ({ page }) => {
    // ARRANGE
    const searchInput = page.locator('input[placeholder*="Search collections"]')
    // ACT
    await searchInput.fill('NonexistentCollectionXYZ')

    // ASSERT
    await expect(page.getByText('No collections match your search')).toBeVisible()
  })

  test('should be case-insensitive', async ({ page }) => {
    // ARRANGE
    const searchInput = page.locator('input[placeholder*="Search collections"]')
    // ACT
    await searchInput.fill('gamma archive')

    // ASSERT
    await expect(page.locator('[data-testid^="collection-row-"]', { hasText: gamma })).toBeVisible()
    await expect(
      page.locator('[data-testid^="collection-row-"]', { hasText: alpha }),
    ).not.toBeVisible()
  })

  test('should show all collections when search is cleared', async ({ page }) => {
    const searchInput = page.locator('input[placeholder*="Search collections"]')
    await searchInput.fill('Alpha')
    await expect(page.locator('[data-testid^="collection-row-"]', { hasText: alpha })).toBeVisible()

    await searchInput.clear()

    await expect(page.locator('[data-testid^="collection-row-"]', { hasText: alpha })).toBeVisible()
    await expect(page.locator('[data-testid^="collection-row-"]', { hasText: beta })).toBeVisible()
    await expect(page.locator('[data-testid^="collection-row-"]', { hasText: gamma })).toBeVisible()
  })

  test('does not apply the bookmark-query grammar to the collection filter', async ({ page }) => {
    // ARRANGE — this SearchBar is bound to a plain substring filter, so
    // `key:value` here is a name fragment, not an operator (UC-070 is scoped
    // to the bookmark search bar).
    const searchInput = page.locator('input[placeholder*="Search collections"]')

    // ACT
    await searchInput.fill('Q3:Ops')

    // ASSERT — the substring filter works, with no invalid-syntax state…
    await expect(page.locator('[data-testid^="collection-row-"]', { hasText: colon })).toBeVisible()
    await expect(
      page.locator('[data-testid^="collection-row-"]', { hasText: alpha }),
    ).not.toBeVisible()
    await expect(searchInput).not.toHaveClass(/border-destructive/)

    // …and no bookmark autocomplete, so pressing Enter cannot rewrite a pasted
    // URL into a `url:` query that matches no collection.
    await searchInput.fill('https://example.com/a')
    await expect(page.getByTestId('search-autocomplete')).toHaveCount(0)
    await page.keyboard.press('Enter')
    await expect(searchInput).toHaveValue('https://example.com/a')
  })

  // Cleanup is handled by deleting the test user — no explicit per-collection
  // delete test needed.
  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
})
