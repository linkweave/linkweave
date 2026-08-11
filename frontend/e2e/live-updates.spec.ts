import type { APIRequestContext } from '@playwright/test'
import { expect, test } from './fixtures'
import { createBookmarkViaUi } from './helpers/bookmarks'
import { expectToastSeen, recordToasts, seenToasts } from './helpers/toasts'
import { CollectionManagePageObject } from './models/CollectionManagePageObject'
import {
  deleteTestUserCleanup,
  loginViaApi,
  registerAndCaptureStorageState,
  registerTestUser,
  type StorageState,
  type TestUser,
} from './models/TestUser'

/**
 * UC-104 main success scenario, end to end: a bookmark added by *another member*
 * appears in an already-open collection without the viewer reloading.
 *
 * <p>This is the only test in the suite that exercises the whole channel —
 * EventSource through the dev proxy, the per-tab id that stops a client hearing
 * its own writes (BR-205), the commit-gated publish (BR-203), and the client
 * re-reading through the normal path (BR-202). Everything below it is a unit or
 * integration test of one link in that chain.
 */
test.describe.configure({ mode: 'serial' })

let viewer: TestUser
let viewerState: StorageState
let collaborator: TestUser

test.describe('Live collection updates', () => {
  let collectionId: string
  const collectionName = `Live ${Date.now()}-${Math.random().toString(36).slice(2, 6)}`

  // The collaborator's session, established once. Logging in per test tripped
  // the suite-wide auth serialization; one session is also closer to the real
  // thing — a second person with a browser open, not a fresh login per action.
  let collaboratorApi: APIRequestContext

  test.beforeAll(async ({ browser, request, playwright }) => {
    ;({ user: viewer, storageState: viewerState } = await registerAndCaptureStorageState(
      browser,
      'liveviewer',
    ))
    collaborator = await registerTestUser(request, 'livecollab')
    // Owned by this suite rather than the `request` fixture, which Playwright
    // refuses to let a test reuse from beforeAll.
    collaboratorApi = await playwright.request.newContext({
      baseURL: test.info().project.use.baseURL,
      ignoreHTTPSErrors: true,
    })
    await loginViaApi(collaboratorApi, collaborator)
  })

  test.use({
    storageState: async ({}, use) => {
      await use(viewerState)
    },
  })

  test('sets up a shared collection the viewer is watching', async ({ page }) => {
    // ARRANGE — the viewer owns a collection and shares it with the collaborator
    const manage = new CollectionManagePageObject(page)
    await manage.navigate()
    await manage.createCollection(collectionName)
    collectionId = await manage.getCollectionIdByName(collectionName)

    const shareResponse = await page.request.post(`/api/collections/${collectionId}/members`, {
      data: { email: collaborator.email, role: 'MEMBER' },
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    })
    expect(shareResponse.ok()).toBe(true)
  })

  test("shows a collaborator's new bookmark, attributed, without a reload", async ({ page }) => {
    // ARRANGE — the viewer sits on the collection with the stream open. Waiting
    // for the toolbar is what guarantees the subscription exists before the
    // change happens: nothing is replayed for a client that was not listening
    // (BR-204), so subscribing late would make this test lie.
    await page.goto(`/collections/${collectionId}`)
    await expect(page.getByTestId('bookmark-list-toolbar')).toBeVisible()
    await recordToasts(page)

    // ACT — a different client entirely: another session, so another
    // X-Client-Id, exactly like a second person at a second machine
    const title = `From the collaborator ${Date.now()}`
    const created = await collaboratorApi.post('/api/bookmarks', {
      data: { collectionId, title, url: 'https://example.com/collaborator' },
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    })
    expect(created.ok()).toBe(true)

    // ASSERT — the list updates in place, with no navigation and no reload
    // anywhere in this test, and the indicator says who did it (BR-209)
    await expect(page.getByText(title)).toBeVisible({ timeout: 15_000 })
    await expectToastSeen(page, 'E2E livecollab')
  })

  test('removes a bookmark the collaborator deleted, without a reload', async ({ page }) => {
    // ARRANGE — a bookmark both of them can see
    const title = `Doomed ${Date.now()}`
    const created = await collaboratorApi.post('/api/bookmarks', {
      data: { collectionId, title, url: 'https://example.com/doomed' },
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    })
    const bookmarkId = (await created.json()).id as string

    await page.goto(`/collections/${collectionId}`)
    await expect(page.getByText(title)).toBeVisible()

    // ACT
    const deleted = await collaboratorApi.delete(`/api/bookmarks/${bookmarkId}`, {
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    })
    expect(deleted.ok()).toBe(true)

    // ASSERT — the removal kind travels the same channel
    await expect(page.getByText(title)).toBeHidden({ timeout: 15_000 })
  })

  test('tells the acting tab nothing about its own change', async ({ page }) => {
    // ARRANGE
    await page.goto(`/collections/${collectionId}`)
    await expect(page.getByTestId('bookmark-list-toolbar')).toBeVisible()
    await recordToasts(page)

    // ACT — through the UI, deliberately: the write has to come from the app's
    // own API client for the tab id to be on it. `page.request` would bypass the
    // page's JavaScript entirely and send no X-Client-Id, which would make this
    // test pass against a server that had never heard of BR-205.
    const ownTitle = `My own bookmark ${Date.now()}`
    await createBookmarkViaUi(page, ownTitle, 'https://example.com/mine')
    await expect(page.getByText(ownTitle)).toBeVisible()

    // ...then a change from the collaborator, whose notification *must* arrive.
    // Both events travel the same ordered stream, so once theirs has been seen,
    // ours would have been too — if the server had sent it.
    const theirTitle = `Theirs ${Date.now()}`
    const created = await collaboratorApi.post('/api/bookmarks', {
      data: { collectionId, title: theirTitle, url: 'https://example.com/theirs' },
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    })
    expect(created.ok()).toBe(true)
    await expectToastSeen(page, 'E2E livecollab')

    // ASSERT — BR-205: their change was announced, ours never was. Two things
    // this assertion deliberately does not do: look at the live DOM (a
    // self-toast auto-dismisses within seconds, so `toHaveCount(0)` would pass
    // whether or not it ever appeared), and lean on an asymmetric matcher inside
    // a deep-equality check (`not.toContainEqual(expect.stringContaining(...))`
    // reads as if it works, but whether the matcher is honoured there is a
    // framework detail — and a negative assertion that silently degrades to a
    // no-op is the exact bug this whole helper exists to prevent).
    const seen = await seenToasts(page)
    expect(seen.some((toast) => toast.includes('E2E liveviewer'))).toBe(false)
  })

  test.afterAll(async ({ browser }) => {
    await collaboratorApi.dispose()
    await deleteTestUserCleanup(browser, () => viewer)
    await deleteTestUserCleanup(browser, () => collaborator)
  })
})
