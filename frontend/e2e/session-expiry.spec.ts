import { expect, test } from './fixtures'
import { gotoCollection, useTestCollectionWithCleanup } from './helpers/testCollection'

test.describe('Session expiry', () => {
  const collection = useTestCollectionWithCleanup('session-expiry')
  test.use({ storageState: async ({}, use) => { await use(collection.storageState!) } })

  test('should send an in-app navigation with an expired session to login', async ({ page }) => {
    // ARRANGE
    await gotoCollection(page, collection)
    await page.context().clearCookies()

    // ACT — the session dies while the SPA is still running
    await page.getByTestId('user-menu-trigger').click()
    await page.getByTestId('user-menu-trashbin').click()

    // ASSERT
    await expect(page).toHaveURL(/\/login/)
  })

  test('should send a dropped live stream with an expired session to login', async ({ page }) => {
    // ARRANGE — every stream attempt fails, so the client keeps reconnecting
    await gotoCollection(page, collection)
    await page.route('**/api/collections/*/events*', (route) => route.abort())
    await page.reload()
    await page.waitForRequest(/\/api\/collections\/[^/]+\/events\?/)

    // ACT — the session expires before the next reconnect attempt
    await page.context().clearCookies()

    // ASSERT — the reconnect checks the session and routes to login rather than
    // retrying silently (the first retry is ~1s after the drop, backoff grows)
    await expect(page).toHaveURL(/\/login/, { timeout: 30_000 })
  })
})
