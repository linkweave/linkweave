import { expect, test, type Page } from './fixtures'
import { api, BASE, type Created } from './helpers/api'
import {
  deleteTestUserCleanup,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from './models/TestUser'

// UC-110: group cards are content-sized and flow in a masonry (multi-column)
// layout — no per-card height cap, no scrolling inside a card.

test.describe.configure({ mode: 'serial' })

let user: TestUser
let collectionId: string
let storageState: StorageState

// The cap the masonry replaced was `max-h-96` on the card body (24rem = 384px).
const OLD_CARD_BODY_CAP_PX = 384

/** Folder name → how many bookmarks it gets. "Big" clears the old cap on its own. */
const FOLDERS: Record<string, number> = { Big: 30, Small: 2, Medium: 8 }

async function openGrouped(page: Page) {
  await page.goto(`/collections/${collectionId}`)
  const group = page.getByRole('group', { name: /bookmark layout/i })
  await group.getByRole('button', { name: 'Grouped' }).click()
  await expect(page.getByTestId('grouped-masonry')).toBeVisible()
  await expect(page.getByTestId('grouped-card')).toHaveCount(Object.keys(FOLDERS).length)
}

test.describe('Grouped layout masonry (UC-110)', () => {
  test.beforeAll(async ({ browser }) => {
    ;({ user, storageState, collectionId } = await registerAndCaptureStorageState(
      browser,
      'groupedmasonry',
    ))

    const ctx = await browser.newContext({ ignoreHTTPSErrors: true, storageState })
    try {
      for (const [name, count] of Object.entries(FOLDERS)) {
        const folder = await api<Created>(ctx.request, 'POST', `${BASE}/folders`, {
          collectionId,
          name,
        })
        for (let i = 1; i <= count; i++) {
          await api<Created>(ctx.request, 'POST', `${BASE}/bookmarks`, {
            collectionId,
            title: `${name} ${i}`,
            url: `https://example.com/${name}/${i}`,
            folderId: folder.id,
          })
        }
      }
    } finally {
      await ctx.close()
    }
  })

  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => user))

  test.use({ storageState: async ({}, use) => { await use(storageState) } })

  test('cards grow past the old height cap and never scroll internally', async ({ page }) => {
    // ARRANGE
    await page.setViewportSize({ width: 1440, height: 900 })
    await openGrouped(page)

    // ACT
    const cards = await page.getByTestId('grouped-card').evaluateAll((els) =>
      els.map((el) => ({
        name: el.querySelector('span')?.textContent?.trim() ?? '',
        height: el.getBoundingClientRect().height,
        // A card that still capped its body would clip its rows away.
        overflowing: [el, ...el.querySelectorAll('*')].some(
          (n) => n.scrollHeight - n.clientHeight > 1,
        ),
      })),
    )

    // ASSERT
    const big = cards.find((c) => c.name === 'Big')!
    const small = cards.find((c) => c.name === 'Small')!
    expect(big.height).toBeGreaterThan(OLD_CARD_BODY_CAP_PX)
    expect(small.height).toBeLessThan(OLD_CARD_BODY_CAP_PX)
    expect(cards.map((c) => c.overflowing)).toEqual(cards.map(() => false))
  })

  test('cards flow into multiple columns on a wide viewport, one column on mobile', async ({
    page,
  }) => {
    // ARRANGE
    await page.setViewportSize({ width: 1440, height: 900 })
    await openGrouped(page)
    const grid = page.getByTestId('grouped-masonry')

    // ACT
    const wide = await grid.evaluate((el) => getComputedStyle(el).columnCount)
    await page.setViewportSize({ width: 480, height: 900 })
    const narrow = await grid.evaluate((el) => getComputedStyle(el).columnCount)

    // ASSERT — UC-057 / BR-110-5: mobile stays single-column.
    expect(wide).toBe('3')
    expect(narrow).toBe('1')
  })

  test('a card is never split across two columns', async ({ page }) => {
    // ARRANGE
    await page.setViewportSize({ width: 1440, height: 900 })
    await openGrouped(page)

    // ACT — each card's own rows must all share the card's horizontal band.
    const split = await page.getByTestId('grouped-card').evaluateAll((els) =>
      els.filter((el) => {
        const card = el.getBoundingClientRect()
        return [...el.querySelectorAll('[data-testid^="grouped-row-"]')].some((row) => {
          const r = row.getBoundingClientRect()
          return r.left < card.left - 1 || r.right > card.right + 1
        })
      }).length,
    )

    // ASSERT
    expect(split).toBe(0)
  })
})
