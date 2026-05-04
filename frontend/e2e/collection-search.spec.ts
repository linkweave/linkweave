import { expect, test } from '@playwright/test'
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

    await manage.expectCollectionVisible(alpha)
    await manage.expectCollectionVisible(beta)
    await manage.expectCollectionVisible(gamma)
  })

  test('should filter collections by search query', async ({ page }) => {
    const searchInput = page.locator('input[placeholder*="Search collections"]')
    await searchInput.fill('Alpha')

    await expect(page.locator('[data-testid^="collection-row-"]', { hasText: alpha })).toBeVisible()
    await expect(
      page.locator('[data-testid^="collection-row-"]', { hasText: beta }),
    ).not.toBeVisible()
    await expect(
      page.locator('[data-testid^="collection-row-"]', { hasText: gamma }),
    ).not.toBeVisible()
  })

  test('should show no results message when nothing matches', async ({ page }) => {
    const searchInput = page.locator('input[placeholder*="Search collections"]')
    await searchInput.fill('NonexistentCollectionXYZ')

    await expect(page.getByText('No collections match your search')).toBeVisible()
  })

  test('should be case-insensitive', async ({ page }) => {
    const searchInput = page.locator('input[placeholder*="Search collections"]')
    await searchInput.fill('gamma archive')

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

  // Cleanup is handled by deleting the test user — no explicit per-collection
  // delete test needed.
  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
})
