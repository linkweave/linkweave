import { expect, type Page, test } from '@playwright/test'
import {
  deleteTestUserCleanup,
  registerAndCaptureStorageState,
  type StorageState,
  type TestUser,
} from '../models/TestUser'

export type TestCollection = {
  user?: TestUser
  storageState?: StorageState
  collectionId: string
}

/**
 * Provisions a fresh authenticated user + their default collection for the
 * enclosing `describe`, and hard-deletes the user (and everything they own) in
 * `afterAll`. Returns a handle whose fields are filled in `beforeAll`.
 *
 * IMPORTANT: read `handle.collectionId` / `handle.storageState` only inside a
 * test or hook — at module-load time they are still empty (`''` / `undefined`).
 * A hard runtime guard is deliberately avoided: Playwright probes the
 * `storageState` override before `beforeAll` runs, where an empty value is
 * expected and harmless (the real value is applied when the test context is
 * created).
 *
 * Pair with a storageState override so every test boots authenticated:
 *   const collection = useTestCollectionWithCleanup('myslug')
 *   test.use({ storageState: async ({}, use) => { await use(collection.storageState!) } })
 */
export function useTestCollectionWithCleanup(slug: string): TestCollection {
  const handle: TestCollection = { collectionId: '' }

  test.beforeAll(async ({ browser }) => {
    Object.assign(handle, await registerAndCaptureStorageState(browser, slug))
  })

  test.afterAll(({ browser }) => deleteTestUserCleanup(browser, () => handle.user))

  return handle
}

/**
 * Navigates to the provisioned collection and waits for the URL to settle,
 * plus a render signal so callers don't race the SPA boot. The
 * `collection-settings-open` gear button renders as part of the toolbar on
 * every collection page, independent of the bookmarks GET — a reliable
 * "the view has mounted" signal. Generous timeout: under parallel e2e load
 * the SPA boot can lag the URL change by several seconds.
 */
export async function gotoCollection(page: Page, collection: TestCollection): Promise<void> {
  await page.goto(`/collections/${collection.collectionId}`)
  await expect(page).toHaveURL(new RegExp(`/collections/${collection.collectionId}`))
  await expect(page.getByTestId('collection-settings-open')).toBeVisible({ timeout: 30000 })
}
