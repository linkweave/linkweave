import { expect, test, type Browser, type Page } from './fixtures'
import { api, type Created } from './helpers/api'
import {
  deleteTestUserCleanup,
  loginViaApi,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

// E2E coverage for the exact-URL search operator (`url:`, UC-070).
//
// The world is a set of near-identical URLs (mixed-case host, trailing slash,
// deeper path, tracking parameter, shuffled query) plus one unrelated
// bookmark, seeded over HTTP. Each test then exercises one flow through the
// header search bar: substring paste, exact match, negation, invalid-syntax
// flagging, the autocomplete conversion, the saved-search round-trip, and
// the empty-result substring fallback.

test.describe.configure({ mode: 'serial' })

const ts = Date.now()

const titleExact = `Exact A ${ts}`
const titleSubPath = `Sub Path ${ts}`
const titleTracked = `Tracked ${ts}`
const titleSorted = `Sorted Query ${ts}`
const titleUnrelated = `Unrelated ${ts}`
const titleDeep = `Deep Page ${ts}`

let user: TestUser
let storageState: StorageState
let collectionId: string

async function seedWorld(browser: Browser): Promise<void> {
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true })
  try {
    await loginViaApi(ctx.request, user)
    const bookmarks: Array<{ title: string; url: string }> = [
      { title: titleExact, url: 'https://Example.com/a/' },
      { title: titleSubPath, url: 'https://example.com/a/b' },
      { title: titleTracked, url: 'https://example.com/a?utm_source=x' },
      { title: titleSorted, url: 'https://example.com/a?a=1&b=2' },
      { title: titleUnrelated, url: 'https://other.example.org/z' },
      { title: titleDeep, url: 'https://example.com/deep/page' },
    ]
    for (const bm of bookmarks) {
      await api<Created>(ctx.request, 'POST', '/api/bookmarks', {
        collectionId,
        title: bm.title,
        url: bm.url,
      })
    }
  } finally {
    await ctx.close()
  }
}

async function gotoCollection(page: Page) {
  await page.goto(`/collections/${collectionId}`)
  await expect(page).toHaveURL(new RegExp(`/collections/${collectionId}`))
  await expect(page.getByTestId(/^bookmark-card-/).first()).toBeVisible()
}

const headerInput = (page: Page) => page.locator('header [data-search-input]')
const dropdown = (page: Page) => page.getByTestId('search-autocomplete')

// Card order depends on the collection's sort settings — compare as sorted
// sets so the assertions stay about *which* bookmarks match, not their order.
function visibleCardTitles(page: Page) {
  return page
    .locator('[data-bookmark-title]')
    .evaluateAll((els) =>
      els.map((el) => el.getAttribute('data-bookmark-title') ?? '').sort(),
    )
}

function sorted(titles: string[]): string[] {
  return [...titles].sort()
}

async function search(page: Page, query: string) {
  const input = headerInput(page)
  await input.click()
  await input.fill(query)
}

test.describe('Exact-URL Search (url: operator)', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState, collectionId } = await registerAndCaptureStorageState(
      browser,
      'urlsearch',
    ))
    await seedWorld(browser)
  })

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  test.beforeEach(async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await gotoCollection(page)
  })

  test('pasting a URL filters by substring and never returns everything', async ({
    page,
  }) => {
    // ACT
    await search(page, 'https://example.com/a')
    // ASSERT — substring matches: exact (lowercased), deeper path, tracking
    // param, and shuffled query; the unrelated bookmark is filtered out.
    await expect.poll(() => visibleCardTitles(page)).toEqual(
      sorted([titleExact, titleSubPath, titleTracked, titleSorted]),
    )
  })

  test('url: matches exactly, through the normalization contract', async ({ page }) => {
    // ACT
    await search(page, 'url:https://example.com/a ')
    // ASSERT — the stored mixed-case/trailing-slash URL matches; deeper path
    // and tracking-parameter variants do not.
    await expect.poll(() => visibleCardTitles(page)).toEqual(sorted([titleExact]))
    // …and per UC-070 steps 5–6 the operator is rendered as a token pill
    // with the result count shown.
    const pill = page.locator('[data-testid="filter-pill"][data-token-key="url"]')
    await expect(pill).toBeVisible()
    await expect(pill).toHaveAttribute('data-token-value', 'https://example.com/a')
    await expect(page.getByTestId('filter-strip')).toContainText('1 result')
  })

  test('url: sorts query parameters before comparing', async ({ page }) => {
    // ACT
    await search(page, 'url:https://example.com/a?b=2&a=1 ')
    // ASSERT
    await expect.poll(() => visibleCardTitles(page)).toEqual(sorted([titleSorted]))
  })

  test('-url: returns the complement of the exact match', async ({ page }) => {
    // ACT
    await search(page, '-url:https://example.com/a ')
    // ASSERT — everything except the exact match remains.
    await expect.poll(() => visibleCardTitles(page)).toEqual(
      sorted([titleSubPath, titleTracked, titleSorted, titleUnrelated, titleDeep]),
    )
  })

  test('an unknown operator matches nothing and is flagged invalid', async ({ page }) => {
    // ACT
    await search(page, 'bogus:value ')
    // ASSERT — empty result, never the unfiltered list…
    await expect.poll(() => visibleCardTitles(page)).toEqual([])
    // …and the invalid token is flagged in the search bar.
    const flag = page.getByTestId('search-invalid-operators')
    await expect(flag).toBeVisible()
    await expect(flag).toContainText('bogus:value')
  })

  test('an unparseable url: value matches nothing and is flagged invalid', async ({
    page,
  }) => {
    // ACT
    await search(page, 'url:??? ')
    // ASSERT — invalid syntax filters to nothing (AND-combined with any other
    // tokens), never to everything, and the token is flagged in the search bar.
    await expect.poll(() => visibleCardTitles(page)).toEqual([])
    const flag = page.getByTestId('search-invalid-operators')
    await expect(flag).toBeVisible()
    await expect(flag).toContainText('url:???')
  })

  test('pasting a URL offers the url: conversion; accepting it finds the bookmark', async ({
    page,
  }) => {
    // ARRANGE — paste (fill) the stored mixed-case URL.
    await search(page, 'https://Example.com/a/')
    // ASSERT (pre-acceptance) — the conversion is offered but NOT
    // applied: the query still runs with substring semantics. The pasted
    // value (trailing slash included) is a substring of the exact bookmark
    // and the deeper path, but not of the ?query variants.
    await expect(dropdown(page)).toBeVisible()
    await expect(page.getByTestId('ac-item').filter({ hasText: 'url:' })).toBeVisible()
    await expect(headerInput(page)).toHaveValue('https://Example.com/a/')
    await expect.poll(() => visibleCardTitles(page)).toEqual(sorted([titleExact, titleSubPath]))
    // ACT — accept the offered exact-URL conversion.
    await page.keyboard.press('Enter')
    // ASSERT
    await expect(headerInput(page)).toHaveValue('url:https://Example.com/a/ ')
    await expect.poll(() => visibleCardTitles(page)).toEqual(sorted([titleExact]))
  })

  test('copy URL from a bookmark → paste → convert → finds exactly that bookmark', async ({
    page,
    context,
  }) => {
    // ARRANGE — copy the bookmark's URL via the row menu (UC-106).
    await context.grantPermissions(['clipboard-read', 'clipboard-write'])
    const card = page.getByTestId(/^bookmark-card-/).filter({ hasText: titleExact })
    await card.getByTestId('bookmark-menu-button').click()
    await page.getByTestId('bookmark-menu-copy-url').click()
    await expect
      .poll(() => page.evaluate(() => navigator.clipboard.readText()))
      .toBe('https://Example.com/a/')

    // ACT — paste into the search bar and accept the url: conversion.
    const input = headerInput(page)
    await input.click()
    await input.press('ControlOrMeta+v')
    await expect(input).toHaveValue('https://Example.com/a/')
    await expect(dropdown(page)).toBeVisible()
    await page.keyboard.press('Enter')

    // ASSERT — exactly the bookmark the URL was copied from.
    await expect(input).toHaveValue('url:https://Example.com/a/ ')
    await expect.poll(() => visibleCardTitles(page)).toEqual(sorted([titleExact]))
  })

  test('a url: query survives save → reload → re-run with the same result set', async ({
    page,
  }) => {
    const name = `url-exact-${ts}`
    const query = 'url:https://example.com/a?b=2&a=1'

    // ARRANGE — run the query and save it (UC-071).
    await search(page, query)
    await expect.poll(() => visibleCardTitles(page)).toEqual(sorted([titleSorted]))
    await page.getByTestId('saved-search-save-trigger').click()
    const popover = page.getByTestId('saved-search-popover')
    await expect(popover).toBeVisible()
    await page.getByTestId('saved-search-name-input').fill(name)
    await page.getByTestId('saved-search-submit').click()
    await expect(popover).toBeHidden()
    await expect(page.getByTestId(`smart-collection-row-${name}`)).toHaveAttribute(
      'data-active',
      'true',
    )

    // ACT — clear, reload the app, and re-run the saved query.
    await headerInput(page).fill('')
    await expect.poll(() => visibleCardTitles(page)).not.toEqual(sorted([titleSorted]))
    await page.reload()
    await expect(page.getByTestId(/^bookmark-card-/).first()).toBeVisible()
    await page.getByTestId(`smart-collection-row-${name}`).click()

    // ASSERT — identical query and identical result set.
    await expect(headerInput(page)).toHaveValue(query)
    await expect.poll(() => visibleCardTitles(page)).toEqual(sorted([titleSorted]))
  })

  test('an empty exact-URL result offers the substring fallback', async ({ page }) => {
    // ARRANGE — `…/deep` is no bookmark's exact URL, but it is a substring of
    // the stored `…/deep/page`.
    await search(page, 'url:https://example.com/deep ')
    await expect.poll(() => visibleCardTitles(page)).toEqual([])
    // ACT — one-click fallback to the substring interpretation.
    await page.getByTestId('url-search-anywhere').click()
    // ASSERT
    await expect(headerInput(page)).toHaveValue('https://example.com/deep')
    await expect.poll(() => visibleCardTitles(page)).toEqual(sorted([titleDeep]))
  })

  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))
})
