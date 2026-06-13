import { expect, type Page, test } from './fixtures'
import { createBookmarkViaUi } from './helpers/bookmarks'
import { gotoCollection, useTestCollectionWithCleanup } from './helpers/testCollection'

test.describe.configure({ mode: 'serial' })

const ts = Date.now()

async function openSettingsDialog(page: Page) {
  await page.getByTestId('user-menu-trigger').click()
  await page.getByRole('menuitem', { name: /settings/i }).click()
  await expect(page.getByTestId('toggle-saved-searches')).toBeVisible()
}

async function closeSettingsDialog(page: Page) {
  // Dialog closes on Escape (radix-vue default).
  await page.keyboard.press('Escape')
  await expect(page.getByTestId('toggle-saved-searches')).toBeHidden()
}

async function createSavedSearch(page: Page, name: string, query: string) {
  const input = page.locator('input[type="text"]').first()
  await input.fill(query)
  await page.getByTestId('saved-search-save-trigger').click()
  await page.getByTestId('saved-search-name-input').fill(name)
  await page.getByTestId('saved-search-submit').click()
  await expect(page.getByTestId(`smart-collection-row-${name}`)).toBeVisible()
}

test.describe('Saved Searches feature flag', () => {
  const collection = useTestCollectionWithCleanup('savedsearchsetting')
  test.use({ storageState: async ({}, use) => { await use(collection.storageState!) } })

  test('disabling the setting hides smart collections and save trigger', async ({ page }) => {
    const name = `ff-search-${ts}`
    await gotoCollection(page, collection)
    await createBookmarkViaUi(page,`Production API ${ts}`, 'https://api.prod.example.com')
    await createSavedSearch(page, name, 'Production')

    // Baseline: feature is on, sidebar section visible.
    await expect(page.getByTestId('smart-collections-toggle')).toBeVisible()
    await expect(page.getByTestId('saved-search-pill')).toBeVisible()

    // Toggle off.
    await openSettingsDialog(page)
    await page.getByTestId('toggle-saved-searches').click()
    await closeSettingsDialog(page)

    // Smart Collections section vanishes from the sidebar; save trigger is hidden too.
    await expect(page.getByTestId('smart-collections-toggle')).toBeHidden()
    await expect(page.getByTestId('saved-search-save-trigger')).toBeHidden()
    await expect(page.getByTestId('saved-search-pill')).toBeHidden()

    // Re-enable: UI comes back, the saved search still exists (the flag is a
    // gate, not a destructive op).
    await openSettingsDialog(page)
    await page.getByTestId('toggle-saved-searches').click()
    await closeSettingsDialog(page)
    await expect(page.getByTestId('smart-collections-toggle')).toBeVisible()
    await expect(page.getByTestId(`smart-collection-row-${name}`)).toBeVisible()
  })

  test('setting persists across reload', async ({ page }) => {
    await gotoCollection(page, collection)
    await openSettingsDialog(page)
    await page.getByTestId('toggle-saved-searches').click()
    await closeSettingsDialog(page)
    await expect(page.getByTestId('smart-collections-toggle')).toBeHidden()

    await page.reload()
    await expect(page.getByTestId('smart-collections-toggle')).toBeHidden()

    // Restore default for any later tests / cleanup symmetry.
    await openSettingsDialog(page)
    await page.getByTestId('toggle-saved-searches').click()
    await closeSettingsDialog(page)
    await expect(page.getByTestId('smart-collections-toggle')).toBeVisible()
  })
})

test.describe('Saved Searches offline cache', () => {
  const offlineCollection = useTestCollectionWithCleanup('savedsearchoffline')
  const name = `offline-search-${ts}`
  test.use({ storageState: async ({}, use) => { await use(offlineCollection.storageState!) } })

  test('serves saved searches from IndexedDB when the server returns 503', async ({ page }) => {
    // First online pass: create bookmark + saved search so the GET response
    // gets cached by the offline middleware.
    await page.goto(`/collections/${offlineCollection.collectionId}`)
    await page.getByRole('button', { name: /add bookmark/i }).click()
    const dialog = page.locator('[role="dialog"]')
    await dialog.locator('#create-bookmark-title').fill(`Production API ${ts}`)
    await dialog.locator('#create-bookmark-url').fill('https://api.prod.example.com')
    await dialog.locator('button[type="submit"]').click()
    await expect(dialog).not.toBeVisible()
    await createSavedSearch(page, name, 'Production')

    // The create path mutates the store in memory but does not write to the
    // offline cache. Reload once online so the GET response (which now
    // includes the new row) is persisted to IndexedDB.
    await page.reload()
    await expect(page.getByTestId(`smart-collection-row-${name}`)).toBeVisible()

    // Force the next saved-searches GET to look like the backend is down. The
    // middleware should fall back to the IndexedDB cache and the row should
    // still render.
    await page.route('**/api/saved-searches?**', (route) =>
      route.fulfill({ status: 503, contentType: 'text/plain', body: 'down' }),
    )
    await page.reload()

    const row = page.getByTestId(`smart-collection-row-${name}`)
    await expect(row).toBeVisible()
  })
})
