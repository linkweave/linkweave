import { expect, test, type APIRequestContext, type Browser } from '@playwright/test'
import {
  deleteTestUserCleanup,
  loginViaApi,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

let user: TestUser
let storageState: StorageState
let collectionId: string

type PropDefResp = { id: string }
type BookmarkResp = { id: string }

async function post(request: APIRequestContext, path: string, body: unknown): Promise<unknown> {
  let last = 0
  let lastBody = ''
  for (let attempt = 0; attempt < 3; attempt++) {
    const resp = await request.post(path, { data: body })
    last = resp.status()
    if (resp.ok()) return await resp.json()
    lastBody = await resp.text().catch(() => '')
    if (last < 500) break
    await new Promise((r) => setTimeout(r, 300))
  }
  throw new Error(`POST ${path} failed: ${last} ${lastBody}`)
}

async function createPropDef(
  request: APIRequestContext,
  name: string,
  sortOrder: number,
): Promise<PropDefResp> {
  return (await post(request, '/api/property-definitions', {
    collectionId,
    name,
    type: 'TEXT',
    sortOrder,
  })) as PropDefResp
}

async function createBookmark(request: APIRequestContext, title: string): Promise<BookmarkResp> {
  return (await post(request, '/api/bookmarks', {
    collectionId,
    title,
    url: `https://example.com/${title.replace(/\s+/g, '-').toLowerCase()}`,
  })) as BookmarkResp
}

async function seedSixProps(browser: Browser): Promise<BookmarkResp> {
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true })
  let bookmark!: BookmarkResp
  try {
    await loginViaApi(ctx.request, user)
    for (let i = 0; i < 6; i++) {
      await createPropDef(ctx.request, `prop${i + 1}`, i)
    }
    bookmark = await createBookmark(ctx.request, 'Test bookmark six props')
  } finally {
    await ctx.close()
  }
  return bookmark
}

test.describe('UC-091 collapsible properties section', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState, collectionId } = await registerAndCaptureStorageState(
      browser,
      'collapsible-props',
    ))
    await seedSixProps(browser)
  })

  test.afterAll(async ({ browser }) => {
    await deleteTestUserCleanup(browser, () => user)
  })

  test.use({
    storageState: async ({}, use) => {
      await use(storageState)
    },
  })

  test('properties section is collapsed by default for 6+ properties', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)
    await expect(page.locator('[data-testid^="bookmark-card-"]').first()).toBeVisible({
      timeout: 10000,
    })

    // Open edit dialog via the three-dot menu
    const card = page.locator('[data-testid^="bookmark-card-"]').first()
    await card.locator('button').click()
    await page.getByRole('menuitem', { name: /edit/i }).click()

    const dialog = page.locator('[role="dialog"]')
    await expect(dialog).toBeVisible()

    // The section header should be a button (collapsible mode)
    const toggleBtn = dialog.locator('[data-testid="properties-toggle"]')
    await expect(toggleBtn).toBeVisible()
    await expect(toggleBtn).toHaveAttribute('aria-expanded', 'false')

    // Property inputs should not be visible (section is collapsed / opacity 0 + grid 0fr)
    const collapseWrap = dialog.locator('.collapsible-wrap')
    await expect(collapseWrap).toHaveClass(/shut/)
  })

  test('clicking the header expands the section and persists the state', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)
    await expect(page.locator('[data-testid^="bookmark-card-"]').first()).toBeVisible({
      timeout: 10000,
    })

    const card = page.locator('[data-testid^="bookmark-card-"]').first()
    await card.locator('button').click()
    await page.getByRole('menuitem', { name: /edit/i }).click()

    const dialog = page.locator('[role="dialog"]')
    await expect(dialog).toBeVisible()

    const toggleBtn = dialog.locator('[data-testid="properties-toggle"]')
    await expect(toggleBtn).toHaveAttribute('aria-expanded', 'false')

    // Expand
    await toggleBtn.click()
    await expect(toggleBtn).toHaveAttribute('aria-expanded', 'true')

    const collapseWrap = dialog.locator('.collapsible-wrap')
    await expect(collapseWrap).not.toHaveClass(/shut/)

    // Close and reopen the dialog — state should be remembered
    await dialog.getByRole('button', { name: /cancel/i }).click()
    await expect(dialog).not.toBeVisible()

    await card.locator('button').click()
    await page.getByRole('menuitem', { name: /edit/i }).click()

    const dialog2 = page.locator('[role="dialog"]')
    await expect(dialog2).toBeVisible()
    const toggleBtn2 = dialog2.locator('[data-testid="properties-toggle"]')
    await expect(toggleBtn2).toHaveAttribute('aria-expanded', 'true')
    await expect(dialog2.locator('.collapsible-wrap')).not.toHaveClass(/shut/)
  })

  test('count badge is visible on the section header', async ({ page }) => {
    await page.goto(`/collections/${collectionId}`)
    await expect(page.locator('[data-testid^="bookmark-card-"]').first()).toBeVisible({
      timeout: 10000,
    })

    const card = page.locator('[data-testid^="bookmark-card-"]').first()
    await card.locator('button').click()
    await page.getByRole('menuitem', { name: /edit/i }).click()

    const dialog = page.locator('[role="dialog"]')
    await expect(dialog).toBeVisible()

    // Count badge should show 6
    const toggleBtn = dialog.locator('[data-testid="properties-toggle"]')
    await expect(toggleBtn).toContainText('6')
  })
})
