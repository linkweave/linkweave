import { expect, test } from '@playwright/test'
import {
  deleteTestUserCleanup,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

test.describe.configure({ mode: 'serial' })

let user: TestUser
let collectionId: string
let storageState: StorageState

test.describe('Header Search', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState, collectionId } = await registerAndCaptureStorageState(
      browser,
      'headersearch',
    ))
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  test('desktop: search input is visible in the header', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(`/collections/${collectionId}`)

    const header = page.locator('header')
    const searchInput = header.locator('[data-search-input]')
    await expect(searchInput).toBeVisible()

    // No search input should appear in the main content body
    const main = page.locator('main')
    await expect(main.locator('[data-search-input]')).not.toBeVisible()
  })

  test('desktop: Cmd+K focuses the header search input', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(`/collections/${collectionId}`)

    const searchInput = page.locator('header [data-search-input]')
    await expect(searchInput).toBeVisible()

    await page.keyboard.press('Meta+k')
    await expect(searchInput).toBeFocused()
  })

  test('desktop: / key focuses the header search input', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(`/collections/${collectionId}`)

    const searchInput = page.locator('header [data-search-input]')
    await expect(searchInput).toBeVisible()

    // Click the main content area to give the document keyboard focus
    // (not on an input element, so the / shortcut handler fires)
    await page.locator('main').click({ position: { x: 100, y: 200 } })
    await page.keyboard.press('/')
    await expect(searchInput).toBeFocused()
  })

  test('tablet (640–1023px): search input is visible in the header with short placeholder', async ({ page }) => {
    await page.setViewportSize({ width: 768, height: 1024 })
    await page.goto(`/collections/${collectionId}`)

    const searchInput = page.locator('header [data-search-input]')
    await expect(searchInput).toBeVisible()

    // Short placeholder on tablet
    await expect(searchInput).toHaveAttribute('placeholder', 'Search…')

    // Mobile icon button must not be visible at this width
    const searchIconButton = page.getByTestId('mobile-search-trigger')
    await expect(searchIconButton).not.toBeVisible()
  })

  test('mobile (<640px): header shows search icon button instead of inline input', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 })
    await page.goto(`/collections/${collectionId}`)

    // The header inline search container is hidden on mobile (sm:flex means visible from 640px)
    const headerInlineSearch = page.locator('header .hidden.sm\\:flex [data-search-input]')
    await expect(headerInlineSearch).not.toBeVisible()

    // The icon button is visible below 640px
    const searchIconButton = page.getByTestId('mobile-search-trigger')
    await expect(searchIconButton).toBeVisible()
  })

  test('mobile: tapping the search icon opens the overlay with a focused input', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 })
    await page.goto(`/collections/${collectionId}`)

    const searchIconButton = page.getByTestId('mobile-search-trigger')
    await searchIconButton.click()

    // Overlay input is now visible and focused
    const overlayInput = page.locator('[data-search-input]').last()
    await expect(overlayInput).toBeVisible()
    await expect(overlayInput).toBeFocused()
  })

  test('mobile: Escape closes the search overlay', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 })
    await page.goto(`/collections/${collectionId}`)

    await page.getByTestId('mobile-search-trigger').click()
    const overlayInput = page.locator('[data-search-input]').last()
    await expect(overlayInput).toBeVisible()

    await page.keyboard.press('Escape')
    await expect(overlayInput).not.toBeVisible()
  })

  test('mobile: clicking the backdrop closes the search overlay', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 })
    await page.goto(`/collections/${collectionId}`)

    await page.getByTestId('mobile-search-trigger').click()
    const overlayInput = page.locator('[data-search-input]').last()
    await expect(overlayInput).toBeVisible()

    // Click the backdrop (the blurred background, not the panel)
    await page.getByTestId('mobile-search-backdrop').click({ position: { x: 10, y: 300 } })
    await expect(overlayInput).not.toBeVisible()
  })

  test('mobile: typing in the overlay filters bookmarks', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 })

    const ts = Date.now()
    const title = `Mobile Search Test ${ts}`
    await page.request.post('/api/bookmarks', {
      data: { collectionId, title, url: `https://mobile-search-${ts}.example.com` },
    })

    await page.goto(`/collections/${collectionId}`)
    await page.getByTestId('mobile-search-trigger').click()

    const overlayInput = page.locator('[data-search-input]').last()
    await overlayInput.fill(`Mobile Search Test ${ts}`)

    await expect(page.locator('h3').filter({ hasText: title })).toBeVisible()
  })

  test('desktop: pill strip shows one pill per token and clears via "Clear all"', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })

    const ts = Date.now()
    const title = `Chip Test ${ts}`
    await page.request.post('/api/bookmarks', {
      data: { collectionId, title, url: `https://chip-test-${ts}.example.com` },
    })

    await page.goto(`/collections/${collectionId}`)
    const searchInput = page.locator('header [data-search-input]')
    await searchInput.fill(`Chip Test ${ts}`)

    // Strip is visible with one pill per whitespace-delimited token.
    const strip = page.getByTestId('filter-strip')
    await expect(strip).toBeVisible()
    await expect(strip.getByTestId('filter-pill')).toHaveCount(3)
    await expect(strip.locator('[data-token-kind="text"][data-token-value="Chip"]')).toBeVisible()
    await expect(strip.locator('[data-token-kind="text"][data-token-value="Test"]')).toBeVisible()
    await expect(strip.locator(`[data-token-kind="text"][data-token-value="${ts}"]`)).toBeVisible()

    // "Clear all" empties the strip and the input.
    await page.getByTestId('filter-clear-all').click()
    await expect(strip).not.toBeVisible()
    await expect(searchInput).toHaveValue('')
  })

  test('mobile: icon button shows active state when search is non-empty', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 })
    await page.goto(`/collections/${collectionId}`)

    // Open overlay and type a query
    const iconButton = page.getByTestId('mobile-search-trigger')
    await iconButton.click()
    const overlayInput = page.locator('[data-search-input]').last()
    await overlayInput.fill('active')

    // Close the overlay
    await page.keyboard.press('Escape')

    // Button should now have the active bg class
    await expect(iconButton).toHaveClass(/bg-primary/)

    // Dot badge should be visible
    const badge = iconButton.locator('span[aria-hidden="true"]')
    await expect(badge).toBeVisible()
  })

  test('mobile: pill strip appears below the header after entering a query', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 })
    await page.goto(`/collections/${collectionId}`)

    const iconButton = page.getByTestId('mobile-search-trigger')
    await iconButton.click()
    const overlayInput = page.locator('[data-search-input]').last()
    await overlayInput.fill('active')
    await page.keyboard.press('Escape')

    // Strip is visible below the header with the typed token rendered as a pill.
    const strip = page.getByTestId('filter-strip')
    await expect(strip).toBeVisible()
    await expect(strip.locator('[data-token-kind="text"][data-token-value="active"]')).toBeVisible()
  })

  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
})
