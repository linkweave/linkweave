import { expect, test, type Browser, type Page } from './fixtures'
import { api, type Created } from './helpers/api'
import {
  deleteTestUserCleanup,
  loginViaApi,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

// E2E coverage for FR-072 Search Operator Autocomplete.
//
// The world (tags, folders, a property definition + values, and a few tagged
// bookmarks) is seeded over HTTP against the same Quarkus instance the dev
// server proxies to — fast, and keeps the tests focused on the dropdown UI and
// query-editing behaviour we shipped.

test.describe.configure({ mode: 'serial' })

const ts = Date.now()
const tagQuarkus = `quarkus-${ts}`
const tagDocs = `docs-${ts}`
const folderInsel = `insel-${ts}`
const folderDev = `dev-${ts}`

let user: TestUser
let storageState: StorageState
let collectionId: string

async function seedWorld(browser: Browser): Promise<void> {
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true })
  try {
    await loginViaApi(ctx.request, user)

    const quarkus = await api<Created>(ctx.request, 'POST', '/api/tags', {
      collectionId,
      name: tagQuarkus,
      color: '#dc2626',
    })
    const docs = await api<Created>(ctx.request, 'POST', '/api/tags', {
      collectionId,
      name: tagDocs,
      color: '#22c55e',
    })

    const insel = await api<Created>(ctx.request, 'POST', '/api/folders', { collectionId, name: folderInsel })
    const dev = await api<Created>(ctx.request, 'POST', '/api/folders', { collectionId, name: folderDev })

    const statusDef = await api<Created>(ctx.request, 'POST', '/api/property-definitions', {
      collectionId,
      name: 'status',
      type: 'SELECT',
      allowedValues: 'draft,review,published',
      sortOrder: 0,
    })

    const a = await api<Created>(ctx.request, 'POST', '/api/bookmarks', {
      collectionId,
      title: `Quarkus Guide ${ts}`,
      url: 'https://quarkus.io/guides',
      tagIds: [quarkus.id],
      folderId: insel.id,
    })
    await api<Created>(ctx.request, 'POST', '/api/bookmarks', {
      collectionId,
      title: `Docs Site ${ts}`,
      url: 'https://docs.example.com',
      tagIds: [docs.id],
      folderId: dev.id,
    })
    // Give the quarkus bookmark a status so property-value suggestions also
    // include values that came from real bookmark data, not just allowedValues.
    await ctx.request.put(`/api/bookmarks/${a.id}/properties`, {
      data: { propertyValues: [{ definitionId: statusDef.id, valueText: 'draft' }] },
    })
  } finally {
    await ctx.close()
  }
}

async function gotoCollection(page: Page) {
  await page.goto(`/collections/${collectionId}`)
  await expect(page).toHaveURL(new RegExp(`/collections/${collectionId}`))
  // Under parallel load the bookmarks GET can lag the page mount; give the
  // first card a generous budget + one-shot reload fallback so a transient
  // GET failure doesn't fail the whole spec.
  const firstCard = page.getByTestId(/^bookmark-card-/).first()
  try {
    await expect(firstCard).toBeVisible({ timeout: 30000 })
  } catch {
    await page.reload()
    await expect(firstCard).toBeVisible({ timeout: 30000 })
  }
}

const headerInput = (page: Page) => page.locator('header [data-search-input]')
const dropdown = (page: Page) => page.getByTestId('search-autocomplete')
const items = (page: Page) => page.getByTestId('ac-item')
const activeItem = (page: Page) => page.locator('[data-testid="ac-item"].ac-sel')

function visibleCardTitles(page: Page) {
  return page
    .locator('[data-bookmark-title]')
    .evaluateAll((els) => els.map((el) => el.getAttribute('data-bookmark-title') ?? ''))
}

test.describe('FR-072 Search Autocomplete', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState, collectionId } = await registerAndCaptureStorageState(
      browser,
      'searchac',
    ))
    await seedWorld(browser)
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await gotoCollection(page)
  })

  test('typing # opens the tag dropdown, filters, and highlights the match', async ({ page }) => {
    const input = headerInput(page)
    await input.click()
    await input.fill('#')
    await expect(dropdown(page)).toBeVisible()
    await expect(items(page).filter({ hasText: tagQuarkus })).toBeVisible()
    await expect(items(page).filter({ hasText: tagDocs })).toBeVisible()

    // Filtering narrows the list and highlights the typed substring.
    await input.fill('#quar')
    await expect(items(page).filter({ hasText: tagDocs })).toHaveCount(0)
    const row = items(page).filter({ hasText: tagQuarkus })
    await expect(row).toBeVisible()
    await expect(row.locator('mark')).toHaveText('quar')
  })

  test('a tag row shows its colour dot', async ({ page }) => {
    const input = headerInput(page)
    await input.click()
    await input.fill('#quar')
    const dot = items(page).filter({ hasText: tagQuarkus }).locator('span').first()
    await expect(dot).toHaveAttribute('style', /background/)
  })

  test('tag: alias opens the same tag dropdown', async ({ page }) => {
    const input = headerInput(page)
    await input.click()
    await input.fill('tag:')
    await expect(dropdown(page)).toBeVisible()
    await expect(items(page).filter({ hasText: tagQuarkus })).toBeVisible()
  })

  test('folder: opens the folder dropdown', async ({ page }) => {
    const input = headerInput(page)
    await input.click()
    await input.fill('folder:')
    await expect(dropdown(page)).toBeVisible()
    await expect(items(page).filter({ hasText: folderInsel })).toBeVisible()
    await expect(items(page).filter({ hasText: folderDev })).toBeVisible()
  })

  test('under: opens the folder dropdown and inserts an under: token', async ({ page }) => {
    const input = headerInput(page)
    await input.click()
    await input.fill('under:')
    await expect(dropdown(page)).toBeVisible()
    await expect(items(page).filter({ hasText: folderInsel })).toBeVisible()

    await input.fill('under:insel')
    await expect(items(page).filter({ hasText: folderInsel })).toBeVisible()
    await page.keyboard.press('Enter')
    await expect(input).toHaveValue(`under:${folderInsel} `)
  })

  test('property: opens the key dropdown and chains to the value dropdown', async ({ page }) => {
    const input = headerInput(page)
    await input.click()
    await input.fill('property:')
    await expect(dropdown(page)).toBeVisible()
    const statusRow = items(page).filter({ hasText: 'status' })
    await expect(statusRow).toBeVisible()

    // Selecting the key inserts `property:status=` and immediately surfaces the
    // value suggestions without a further keypress.
    await statusRow.click()
    await expect(input).toHaveValue('property:status=')
    await expect(dropdown(page)).toBeVisible()
    await expect(items(page).filter({ hasText: 'draft' })).toBeVisible()
    await expect(items(page).filter({ hasText: 'published' })).toBeVisible()
  })

  test('a bare operator name opens its suggestions without the colon', async ({ page }) => {
    const input = headerInput(page)
    await input.click()

    await input.fill('folder')
    await expect(dropdown(page)).toBeVisible()
    await expect(items(page).filter({ hasText: folderInsel })).toBeVisible()

    await input.fill('property')
    await expect(items(page).filter({ hasText: 'status' })).toBeVisible()

    await input.fill('tag')
    await expect(items(page).filter({ hasText: tagQuarkus })).toBeVisible()

    // Selecting replaces the bare word with the full operator token.
    await items(page).filter({ hasText: tagQuarkus }).click()
    await expect(input).toHaveValue(`tag:${tagQuarkus} `)
  })

  test('operator discovery suggests folder:/tag:/property: for a prefix', async ({ page }) => {
    const input = headerInput(page)
    await input.click()
    await input.fill('prop')
    await expect(dropdown(page)).toBeVisible()
    await expect(items(page).filter({ hasText: 'property:' })).toBeVisible()

    await input.fill('fo')
    await expect(items(page).filter({ hasText: 'folder:' })).toBeVisible()

    await input.fill('un')
    await expect(items(page).filter({ hasText: 'under:' })).toBeVisible()
  })

  test('ArrowDown moves the active selection', async ({ page }) => {
    const input = headerInput(page)
    await input.click()
    await input.fill('#')
    await expect(dropdown(page)).toBeVisible()
    const firstActive = await activeItem(page).textContent()
    await page.keyboard.press('ArrowDown')
    await expect(activeItem(page)).not.toHaveText(firstActive ?? '')
  })

  test('Enter confirms the selection, adds a trailing space, and keeps prior tokens', async ({
    page,
  }) => {
    const input = headerInput(page)
    await input.click()
    await input.fill('hello #quar')
    await expect(dropdown(page)).toBeVisible()
    await page.keyboard.press('Enter')
    await expect(input).toHaveValue(`hello #${tagQuarkus} `)
    await expect(dropdown(page)).not.toBeVisible()
  })

  test('Tab confirms the selection', async ({ page }) => {
    const input = headerInput(page)
    await input.click()
    await input.fill('#quar')
    await expect(dropdown(page)).toBeVisible()
    await page.keyboard.press('Tab')
    await expect(input).toHaveValue(`#${tagQuarkus} `)
  })

  test('clicking a suggestion inserts it and keeps focus in the input', async ({ page }) => {
    // ARRANGE
    const input = headerInput(page)
    await input.click()
    await input.fill('#quar')
    // ACT
    await items(page).filter({ hasText: tagQuarkus }).click()
    // ASSERT
    await expect(input).toHaveValue(`#${tagQuarkus} `)
    await expect(input).toBeFocused()
  })

  test('arrowing the caret back into a token reopens the dropdown', async ({ page }) => {
    const input = headerInput(page)
    await input.click()
    // Trailing space → caret sits on an empty token → no dropdown.
    await input.fill(`#${tagQuarkus} `)
    await expect(dropdown(page)).not.toBeVisible()
    // Move the caret left, back into the tag token → suggestions reopen.
    await page.keyboard.press('ArrowLeft')
    await expect(dropdown(page)).toBeVisible()
    await expect(items(page).filter({ hasText: tagQuarkus })).toBeVisible()
  })

  test('Escape closes the dropdown without changing the query', async ({ page }) => {
    const input = headerInput(page)
    await input.click()
    await input.fill('#quar')
    await expect(dropdown(page)).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(dropdown(page)).not.toBeVisible()
    await expect(input).toHaveValue('#quar')
  })

  test('clicking outside the dropdown closes it', async ({ page }) => {
    const input = headerInput(page)
    await input.click()
    await input.fill('#quar')
    await expect(dropdown(page)).toBeVisible()
    await page.locator('main').click({ position: { x: 50, y: 50 } })
    await expect(dropdown(page)).not.toBeVisible()
  })

  test('no dropdown appears for plain text', async ({ page }) => {
    const input = headerInput(page)
    await input.click()
    await input.fill('quarkus')
    await expect(dropdown(page)).not.toBeVisible()
  })

  test('tag: filters identically to # (same matching results)', async ({ page }) => {
    const input = headerInput(page)

    await input.click()
    await input.fill(`#${tagQuarkus} `)
    await expect.poll(() => visibleCardTitles(page)).toEqual([`Quarkus Guide ${ts}`])

    await input.fill(`tag:${tagQuarkus} `)
    await expect.poll(() => visibleCardTitles(page)).toEqual([`Quarkus Guide ${ts}`])
  })

  test('mobile: the dropdown renders inside the search sheet', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 800 })
    await page.goto(`/collections/${collectionId}`)
    await page.getByTestId('mobile-search-trigger').click()
    const panel = page.getByTestId('mobile-search-panel')
    const input = panel.locator('[data-search-input]')
    await expect(input).toBeFocused()
    await input.fill('#quar')
    await expect(panel.getByTestId('search-autocomplete')).toBeVisible()
    await expect(panel.getByTestId('ac-item').filter({ hasText: tagQuarkus })).toBeVisible()
  })

  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
})
