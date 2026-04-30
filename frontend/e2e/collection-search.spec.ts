import { expect, test } from '@playwright/test'
import { CollectionManagePageObject } from './models/CollectionManagePageObject'

test.describe.configure({ mode: 'serial' })

test.describe('Collection Search', () => {
  const ts = Date.now()
  const alpha = `Alpha Collection ${ts}`
  const beta = `Beta Project ${ts}`
  const gamma = `Gamma Archive ${ts}`

  test.beforeEach(async ({ page }) => {
    const manage = new CollectionManagePageObject(page)
    await manage.loginAndNavigate()
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
    await expect(page.locator('[data-testid^="collection-row-"]', { hasText: beta })).not.toBeVisible()
    await expect(page.locator('[data-testid^="collection-row-"]', { hasText: gamma })).not.toBeVisible()
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
    await expect(page.locator('[data-testid^="collection-row-"]', { hasText: alpha })).not.toBeVisible()
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

  test('should clean up test data', async ({ page }) => {
    const manage = new CollectionManagePageObject(page)

    for (const name of [alpha, beta, gamma]) {
      const collectionId = await manage.getCollectionIdByName(name)
      await manage.deleteCollection(collectionId, name)
    }

    await manage.expectCollectionNotVisible(alpha)
    await manage.expectCollectionNotVisible(beta)
    await manage.expectCollectionNotVisible(gamma)
  })
})
