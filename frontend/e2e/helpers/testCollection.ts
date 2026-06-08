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
 * `afterAll`. Returns a handle whose fields are filled in `beforeAll`, so read
 * `handle.collectionId` / `handle.storageState` inside tests and hooks — not at
 * module load, where they are still empty.
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

/** Navigates to the provisioned collection and waits for the URL to settle. */
export async function gotoCollection(page: Page, collection: TestCollection): Promise<void> {
  await page.goto(`/collections/${collection.collectionId}`)
  await expect(page).toHaveURL(new RegExp(`/collections/${collection.collectionId}`))
}
