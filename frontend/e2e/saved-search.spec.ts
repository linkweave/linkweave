import { expect, type Page, test } from '@playwright/test'
import { gotoCollection, useTestCollectionWithCleanup } from './helpers/testCollection'

test.describe.configure({ mode: 'serial' })

const ts = Date.now()

async function createBookmark(page: Page, title: string, url: string) {
  await page.getByRole('button', { name: /add bookmark/i }).click()
  const dialog = page.locator('[role="dialog"]')
  await expect(dialog).toBeVisible()
  await dialog.locator('#create-bookmark-title').fill(title)
  await dialog.locator('#create-bookmark-url').fill(url)
  await dialog.locator('button[type="submit"]').click()
  await expect(dialog).not.toBeVisible()
}

async function search(page: Page, query: string) {
  const input = page.locator('input[type="text"]').first()
  await input.fill(query)
}

test.describe('Saved Searches & Smart Collections (v2)', () => {
  const collection = useTestCollectionWithCleanup('savedsearch')
  test.use({ storageState: async ({}, use) => { await use(collection.storageState!) } })

  test('should set up test data', async ({ page }) => {
    await gotoCollection(page, collection)
    await createBookmark(page, `Production API ${ts}`, 'https://api.prod.example.com')
    await createBookmark(page, `Dev API ${ts}`, 'https://api.dev.example.com')
  })

  test('save trigger hidden when query is empty; visible after typing', async ({ page }) => {
    await gotoCollection(page, collection)
    await expect(page.getByTestId('filter-strip')).toBeHidden()
    await expect(page.getByTestId('saved-search-save-trigger')).toBeHidden()

    await search(page, 'Production')
    await expect(page.getByTestId('filter-strip')).toBeVisible()
    await expect(page.getByTestId('saved-search-save-trigger')).toBeVisible()
    // No pill yet — nothing active.
    await expect(page.getByTestId('saved-search-pill')).toHaveCount(0)
  })

  test('popover saves a new search and the pill replaces the save trigger', async ({ page }) => {
    const name = `prod-search-${ts}`

    await gotoCollection(page, collection)
    await search(page, 'Production')

    await page.getByTestId('saved-search-save-trigger').click()
    const popover = page.getByTestId('saved-search-popover')
    await expect(popover).toBeVisible()
    await expect(popover).toContainText('Save search')

    await page.getByTestId('saved-search-name-input').fill(name)
    await page.getByTestId('saved-search-submit').click()
    await expect(popover).toBeHidden()

    // Sidebar row exists and is active.
    const row = page.getByTestId(`smart-collection-row-${name}`)
    await expect(row).toBeVisible()
    await expect(row).toHaveAttribute('data-active', 'true')

    // Pill replaces the save trigger and is in Matched state.
    await expect(page.getByTestId('saved-search-save-trigger')).toHaveCount(0)
    const pill = page.getByTestId('saved-search-pill')
    await expect(pill).toBeVisible()
    await expect(pill).toHaveAttribute('data-state', 'matched')
    await expect(pill).toContainText(name)
  })

  test('modifying the query flips the pill to Dirty and inline Update saves it', async ({
    page,
  }) => {
    const name = `prod-search-${ts}`

    await gotoCollection(page, collection)
    await page.getByTestId(`smart-collection-row-${name}`).click()
    await expect(page.getByTestId('saved-search-pill')).toHaveAttribute('data-state', 'matched')

    const input = page.locator('input[type="text"]').first()
    await input.fill('Production API')

    const pill = page.getByTestId('saved-search-pill')
    await expect(pill).toHaveAttribute('data-state', 'dirty')

    await page.getByTestId('saved-search-pill-update').click()
    await expect(pill).toHaveAttribute('data-state', 'matched')

    // After reload the saved query reflects the new one.
    await page.reload()
    await page.getByTestId(`smart-collection-row-${name}`).click()
    await expect(input).toHaveValue('Production API')
  })

  test('× on the pill deselects without clearing the query', async ({ page }) => {
    const name = `prod-search-${ts}`

    await gotoCollection(page, collection)
    await page.getByTestId(`smart-collection-row-${name}`).click()
    const input = page.locator('input[type="text"]').first()
    await expect(input).toHaveValue('Production API')

    await page.getByTestId('saved-search-pill-deselect').click()
    await expect(page.getByTestId('saved-search-pill')).toHaveCount(0)
    // Query intact, save trigger reappears.
    await expect(input).toHaveValue('Production API')
    await expect(page.getByTestId('saved-search-save-trigger')).toBeVisible()
    await expect(page.getByTestId(`smart-collection-row-${name}`)).toHaveAttribute(
      'data-active',
      'false',
    )
  })

  test('clicking the active sidebar row toggles it off', async ({ page }) => {
    const name = `prod-search-${ts}`

    await gotoCollection(page, collection)
    await page.getByTestId(`smart-collection-row-${name}`).click()
    await expect(page.getByTestId(`smart-collection-row-${name}`)).toHaveAttribute(
      'data-active',
      'true',
    )

    // Second click on the active row → deselect.
    await page.getByTestId(`smart-collection-row-${name}`).click()
    await expect(page.getByTestId(`smart-collection-row-${name}`)).toHaveAttribute(
      'data-active',
      'false',
    )
    await expect(page.getByTestId('saved-search-pill')).toHaveCount(0)
  })

  test('rename via ellipsis opens a popover and commits on Enter', async ({ page }) => {
    const name = `prod-search-${ts}`
    const renamed = `${name}-renamed`

    await gotoCollection(page, collection)
    await page.getByTestId(`smart-collection-row-${name}`).hover()
    await page.getByTestId(`smart-collection-more-${name}`).click()
    await page.getByRole('menuitem', { name: 'Rename' }).click()

    const dialog = page.getByTestId('saved-search-rename-dialog')
    await expect(dialog).toBeVisible()
    // Query preview shown for context.
    await expect(dialog).toContainText('Production API')

    const input = page.getByTestId('saved-search-name-input')
    await expect(input).toHaveValue(name)
    await input.fill(renamed)
    await input.press('Enter')

    await expect(dialog).toBeHidden()
    await expect(page.getByTestId(`smart-collection-row-${renamed}`)).toBeVisible()
    await expect(page.getByTestId(`smart-collection-row-${name}`)).toHaveCount(0)
  })

  test('delete via ellipsis removes the row', async ({ page }) => {
    const renamed = `prod-search-${ts}-renamed`

    await gotoCollection(page, collection)
    await page.getByTestId(`smart-collection-row-${renamed}`).hover()
    await page.getByTestId(`smart-collection-more-${renamed}`).click()
    await page.getByRole('menuitem', { name: 'Delete' }).click()

    await expect(page.getByTestId(`smart-collection-row-${renamed}`)).toHaveCount(0)
  })

  test('Smart Collections section header collapses and persists', async ({ page }) => {
    await gotoCollection(page, collection)
    const toggle = page.getByTestId('smart-collections-toggle')
    await expect(toggle).toHaveAttribute('aria-expanded', 'true')
    await toggle.click()
    await expect(toggle).toHaveAttribute('aria-expanded', 'false')

    await page.reload()
    await expect(page.getByTestId('smart-collections-toggle')).toHaveAttribute(
      'aria-expanded',
      'false',
    )
  })

  test('Tags section header collapses and persists', async ({ page }) => {
    await gotoCollection(page, collection)
    const toggle = page.getByTestId('tags-toggle')
    await expect(toggle).toHaveAttribute('aria-expanded', 'true')
    await toggle.click()
    await expect(toggle).toHaveAttribute('aria-expanded', 'false')

    await page.reload()
    await expect(page.getByTestId('tags-toggle')).toHaveAttribute('aria-expanded', 'false')
  })
})
