import { expect, test } from './fixtures'
import { gotoCollection, useTestCollectionWithCleanup } from './helpers/testCollection'
import { createBookmarkViaApi } from './helpers/api'
import { loginViaApi } from './models/TestUser'

// Copy URL in the shared row-action menu (BookmarkRowMenu): the item writes
// the bookmark's URL to the clipboard and confirms via toast. Because the
// action lives in the shared component, all three surfaces (card, grouped
// row, preview popup footer) expose it; this spec pins the wiring on the
// default card layout.

const collection = useTestCollectionWithCleanup('copy-url-menu')

const TARGET_URL = 'https://example.com/copy-url-target'

test.describe('bookmark row menu Copy URL', () => {
  test.use({
    storageState: async ({}, use) => {
      await use(collection.storageState!)
    },
  })

  test.beforeAll(async ({ browser }) => {
    const ctx = await browser.newContext({ ignoreHTTPSErrors: true })
    try {
      await loginViaApi(ctx.request, collection.user!)
      await createBookmarkViaApi(ctx.request, collection.collectionId, 'Copy URL target', TARGET_URL)
    } finally {
      await ctx.close()
    }
  })

  test('copies the bookmark URL to the clipboard and confirms via toast', async ({ page, context }) => {
    // ARRANGE
    await context.grantPermissions(['clipboard-read', 'clipboard-write'])
    await gotoCollection(page, collection)
    const card = page.getByTestId(/^bookmark-card-/).first()
    await expect(card).toBeVisible({ timeout: 10000 })

    // ACT
    await card.getByTestId('bookmark-menu-button').click()
    await page.getByTestId('bookmark-menu-copy-url').click()

    // ASSERT
    await expect(page.getByText('URL copied to clipboard.')).toBeVisible()
    await expect
      .poll(() => page.evaluate(() => navigator.clipboard.readText()))
      .toBe(TARGET_URL)
  })
})
