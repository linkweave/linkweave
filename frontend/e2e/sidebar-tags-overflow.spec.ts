import { expect, test, type APIRequestContext, type Browser } from './fixtures'
import {
  deleteTestUserCleanup,
  loginViaApi,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

// Regression guard for the sidebar Tags section overflow bug.
//
// The Tags section is capped at `max-height: 50%` and is meant to scroll once
// the tag list is taller than that. A `display:grid` collapse wrapper
// (CollapsibleLw) sat between the capped section and the scroll container; its
// `1fr` track has an implicit min-content floor, so the row refused to shrink,
// the inner `overflow-y-auto` never engaged, and the list spilled out the
// bottom and painted over the build-version footer.
//
// This spec seeds enough tags to exceed the cap on a short viewport and asserts
// the two properties that broke: (1) the tag list actually scrolls, and (2) it
// stays within the section — it does not overlap the footer.

const TAG_COUNT = 40

let user: TestUser
let storageState: StorageState
let collectionId: string

async function seedManyTags(browser: Browser): Promise<void> {
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true })
  try {
    await loginViaApi(ctx.request, user)
    for (let i = 0; i < TAG_COUNT; i++) {
      await createTag(ctx.request, `overflow-tag-${String(i).padStart(2, '0')}`)
    }
  } finally {
    await ctx.close()
  }
}

async function createTag(request: APIRequestContext, name: string): Promise<void> {
  let last = 0
  let lastBody = ''
  for (let attempt = 0; attempt < 3; attempt++) {
    const resp = await request.post('/api/tags', {
      data: { collectionId, name },
    })
    last = resp.status()
    if (resp.ok()) return
    lastBody = await resp.text().catch(() => '')
    if (last < 500) break
    await new Promise((r) => setTimeout(r, 300))
  }
  throw new Error(`create tag "${name}" failed: ${last} ${lastBody}`)
}

test.describe('sidebar Tags section overflow', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState, collectionId } = await registerAndCaptureStorageState(
      browser,
      'sidebar-tags-overflow',
    ))
    await seedManyTags(browser)
  })

  test.afterAll(async ({ browser }) => {
    await deleteTestUserCleanup(browser, () => user)
  })

  test.use({
    // A short viewport guarantees the tag list (40 rows) is taller than 50% of
    // the column, forcing the scroll path the bug lived in. Also wide enough
    // that the sidebar is the persistent `lg:` layout, not the mobile drawer.
    viewport: { width: 1280, height: 720 },
    storageState: async ({}, use) => {
      await use(storageState)
    },
  })

  test('a tall tag list scrolls inside the section instead of overflowing the footer', async ({
    page,
  }) => {
    await page.goto(`/collections/${collectionId}`)

    const section = page.getByTestId('sidebar-tags-section')
    await expect(section).toBeVisible()

    // Ensure the section is expanded (default is open, but be explicit so the
    // test does not depend on persisted prefs).
    const toggle = page.getByTestId('tags-toggle')
    if ((await toggle.getAttribute('aria-expanded')) !== 'true') {
      await toggle.click()
    }
    await expect(toggle).toHaveAttribute('aria-expanded', 'true')

    // Wait for the list to render and the open-collapse transition to settle.
    await expect(page.getByTestId('tag-row-overflow-tag-00')).toBeVisible()

    const geometry = await section.evaluate((sectionEl: HTMLElement) => {
      const scroller = sectionEl.querySelector<HTMLElement>('[class*="overflow-y-auto"]')!
      const footer = document.querySelector<HTMLElement>('[data-testid="sidebar-footer"]')!
      return {
        scrollHeight: scroller.scrollHeight,
        clientHeight: scroller.clientHeight,
        scrollerBottom: scroller.getBoundingClientRect().bottom,
        footerTop: footer.getBoundingClientRect().top,
        sectionBottom: sectionEl.getBoundingClientRect().bottom,
        viewportHeight: window.innerHeight,
      }
    })

    // (1) The list is taller than its clamped viewport — i.e. it actually
    //     scrolls. In the regressed layout the scroll container grew to the full
    //     content height, so scrollHeight === clientHeight (not scrollable).
    expect(
      geometry.scrollHeight,
      'tag list should overflow its scroll container (be scrollable)',
    ).toBeGreaterThan(geometry.clientHeight + 8)

    // (2) The clamped viewport honours the ~50% cap (and is nowhere near the
    //     full content height), confirming the section bounded it.
    expect(geometry.clientHeight).toBeLessThan(geometry.viewportHeight * 0.55)

    // (3) The scroll area does not spill past the footer — the visible symptom
    //     of the bug was the list painting over "version: …". Allow 1px slack
    //     for sub-pixel rounding.
    expect(
      geometry.scrollerBottom,
      'tag list bottom should not overlap the footer',
    ).toBeLessThanOrEqual(geometry.footerTop + 1)
  })
})
