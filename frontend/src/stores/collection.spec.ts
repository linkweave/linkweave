// @vitest-environment happy-dom
import { useCollectionStore } from '@/stores/collection'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

/**
 * Answers `GET /api/collections/{id}` (and its settings sibling) with a delay
 * per collection id, so a slow first request can be made to land after a fast
 * second one.
 */
function stubApi(delaysById: Record<string, number>) {
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL) => {
      const url = String(input)
      const match = url.match(/\/api\/collections\/([^/?]+)/)
      const id = match?.[1] ?? ''
      const body = url.includes('/settings')
        ? {}
        : {
            id,
            name: `Collection ${id}`,
            screenshotEnabled: false,
            bookmarks: [],
            tags: [],
            folders: [],
            autoTagRules: [],
            propertyDefinitions: [],
          }
      return new Promise((resolve) =>
        setTimeout(
          () =>
            resolve(
              new Response(JSON.stringify(body), {
                status: 200,
                headers: { 'Content-Type': 'application/json' },
              }),
            ),
          delaysById[id] ?? 0,
        ),
      )
    }),
  )
}

describe('collection store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('should ignore a stale collection response that arrives after a newer one', async () => {
    // ARRANGE: switching away from the slow collection A to the fast B
    stubApi({ 'col-a': 50, 'col-b': 0 })
    const store = useCollectionStore()

    // ACT
    const slow = store.fetchCollectionInfo('col-a')
    const fast = store.fetchCollectionInfo('col-b')
    await Promise.all([fast, slow])

    // ASSERT: A's late response must not overwrite the collection now shown
    expect(store.collectionInfo?.id).toBe('col-b')
    expect(store.loading).toBe(false)
  })

  it('should not repopulate the collection after it was cleared', async () => {
    // ARRANGE
    stubApi({ 'col-a': 20 })
    const store = useCollectionStore()

    // ACT: the in-flight fetch is still running when the selection is cleared
    const pending = store.fetchCollectionInfo('col-a')
    await store.fetchCollectionInfo('')
    await pending

    // ASSERT: clearing is terminal — nothing is left to switch the spinner off,
    // and BookmarkList/FolderTree would spin forever on a stuck flag.
    expect(store.collectionInfo).toBeNull()
    expect(store.loading).toBe(false)
  })

  it('should stop loading when the collection is deselected mid-fetch', async () => {
    // ARRANGE
    stubApi({ 'col-a': 20 })
    const store = useCollectionStore()
    store.setCurrentCollectionId('col-a')
    await nextTick()

    // ACT: deleting the last collection deselects it while its fetch is running
    store.setCurrentCollectionId(null)
    await nextTick()
    await new Promise((resolve) => setTimeout(resolve, 40))

    // ASSERT
    expect(store.collectionInfo).toBeNull()
    expect(store.loading).toBe(false)
  })
})
