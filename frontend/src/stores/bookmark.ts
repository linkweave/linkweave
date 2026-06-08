import { config } from '@/api'
import type {
  BookmarkJson,
  BookmarkMoveJson,
  BookmarkPropertyValueJson,
  BookmarkSaveJson,
} from '@/api/generated'
import { BookmarkPropertyValueResourceApi, BookmarkResourceApi } from '@/api/generated'
import {
  type AncestorSets,
  buildAncestorSets,
  EMPTY_ANCESTORS,
  type MatchContext,
  matchesTokens,
} from '@/lib/searchQuery'
import { useCollectionStore } from '@/stores/collection'
import { useFolderStore } from '@/stores/folder'
import { usePropertyStore } from '@/stores/property'
import { useSearchQueryStore } from '@/stores/searchQuery'
import { useTagStore } from '@/stores/tag'
import { sortBookmarks } from '@/utils/bookmarkSort'
import { defineStore } from 'pinia'
import { computed } from 'vue'

const bookmarkApi = new BookmarkResourceApi(config)
const bookmarkPropertyValueApi = new BookmarkPropertyValueResourceApi(config)

export const useBookmarkStore = defineStore('bookmark', () => {
  const collectionStore = useCollectionStore()
  const searchQueryStore = useSearchQueryStore()

  // ---------------------------------------------------------------------------
  // State + base getters
  // ---------------------------------------------------------------------------

  const bookmarks = computed<BookmarkJson[]>(() => collectionStore.collectionInfo?.bookmarks ?? [])

  const loading = computed(() => collectionStore.loading)

  // ---------------------------------------------------------------------------
  // Filtered + sorted list
  // ---------------------------------------------------------------------------

  // Combined filter + sort pass. Splitting into separate `filteredBookmarks`
  // and `neverOpenedCount` computeds (each consuming the sort util) would mean
  // running the partition twice; instead we let `sortBookmarks` report both
  // and expose two cheap getter-style computeds derived from this one.
  const sortedAndFiltered = computed(() => {
    const folderStore = useFolderStore()
    const tagStore = useTagStore()
    const propertyStore = usePropertyStore()
    let result = bookmarks.value

    // Sidebar folder + tag selections both flow through query tokens now (see
    // `folderStore.selectFolder` and `tagStore.toggleTag`); the token filter
    // below handles them via `under:` and `#tag`.
    const tokens = searchQueryStore.queryTokens
    if (tokens.length > 0) {
      const tagNamesById = new Map(tagStore.tags.map((t) => [t.id, t.data.name.toLowerCase()]))
      const folderNamesById = new Map(
        folderStore.folders.map((f) => [f.id, f.data.name.toLowerCase()]),
      )
      const folderParentById = new Map(
        folderStore.folders.map((f) => [f.id, f.data.parentId ?? null]),
      )
      const propertyDefsByName = new Map(
        propertyStore.definitions.map((d) => [
          d.data.name.toLowerCase(),
          { id: d.id, type: d.data.type },
        ]),
      )

      // Memoize ancestor walks per filter pass so bookmarks sharing a folderId
      // don't re-walk the tree. The cache lives inside this computed, so it is
      // rebuilt automatically on every re-evaluation.
      const ancestorsCache = new Map<string, AncestorSets>()
      function getAncestors(id: string): AncestorSets {
        let cached = ancestorsCache.get(id)
        if (!cached) {
          cached = buildAncestorSets(id, folderNamesById, folderParentById)
          ancestorsCache.set(id, cached)
        }
        return cached
      }

      result = result.filter((b) => {
        const anc = b.data.folderId ? getAncestors(b.data.folderId) : EMPTY_ANCESTORS
        const ctx: MatchContext = {
          tagNamesById,
          folderName: b.data.folderId ? (folderNamesById.get(b.data.folderId) ?? null) : null,
          ancestorFolderNames: anc.names,
          ancestorFolderIds: anc.ids,
          propertyDefsByName,
        }
        return matchesTokens(b, tokens, ctx)
      })
    }

    return sortBookmarks(result, {
      field: collectionStore.sortField,
      direction: collectionStore.sortDirection,
    })
  })

  const filteredBookmarks = computed(() => sortedAndFiltered.value.items)
  const neverOpenedCount = computed(() => sortedAndFiltered.value.neverOpenedCount)

  // ---------------------------------------------------------------------------
  // CRUD
  // ---------------------------------------------------------------------------

  function patchBookmarks(updater: (list: BookmarkJson[]) => BookmarkJson[]) {
    const info = collectionStore.collectionInfo
    if (info) {
      info.bookmarks = updater(info.bookmarks ?? [])
    }
  }

  /** Replace a bookmark in the in-memory list with its updated version and return it. */
  function applyUpdatedBookmark(updated: BookmarkJson): BookmarkJson {
    patchBookmarks((list) => {
      const idx = list.findIndex((b) => b.id === updated.id)
      if (idx !== -1) list[idx] = updated
      return list
    })
    return updated
  }

  async function createBookmark(data: BookmarkSaveJson): Promise<BookmarkJson> {
    const bookmark = await bookmarkApi.apiBookmarksPost({ bookmarkSaveJson: data })
    patchBookmarks((list) => [bookmark, ...list])
    return bookmark
  }

  async function updateBookmark(bookmarkId: string, data: BookmarkSaveJson): Promise<BookmarkJson> {
    const updated = await bookmarkApi.apiBookmarksBookmarkIdPut({
      bookmarkId,
      bookmarkSaveJson: data,
    })
    return applyUpdatedBookmark(updated)
  }

  async function deleteBookmark(bookmarkId: string): Promise<void> {
    await bookmarkApi.apiBookmarksBookmarkIdDelete({ bookmarkId })
    patchBookmarks((list) => list.filter((b) => b.id !== bookmarkId))
    const { useTrashbinStore } = await import('@/stores/trashbin')
    useTrashbinStore()
      .refreshCount()
      .catch(() => {})
  }

  async function moveBookmarkToFolder(
    bookmarkId: string,
    data: BookmarkMoveJson,
  ): Promise<BookmarkJson> {
    const updated = await bookmarkApi.apiBookmarksBookmarkIdMovePatch({
      bookmarkId,
      bookmarkMoveJson: data,
    })
    return applyUpdatedBookmark(updated)
  }

  /**
   * Replace all property values on a bookmark. The endpoint is treated as a
   * full replace — pass exactly the values you want persisted; anything not
   * in the list is cleared.
   */
  async function updateProperties(
    bookmarkId: string,
    propertyValues: BookmarkPropertyValueJson[],
  ): Promise<BookmarkJson> {
    const updated = await bookmarkPropertyValueApi.apiBookmarksBookmarkIdPropertiesPut({
      bookmarkId,
      bookmarkPropertyValueListJson: { propertyValues },
    })
    return applyUpdatedBookmark(updated)
  }

  // ---------------------------------------------------------------------------
  // Telemetry
  // ---------------------------------------------------------------------------

  function trackClick(bookmarkId: string): void {
    fetch(`/api/bookmarks/${bookmarkId}/track-click`, {
      method: 'POST',
      keepalive: true,
      credentials: 'include',
    }).catch(() => {})
  }

  return {
    // state + base
    bookmarks,
    loading,
    // filtered list
    filteredBookmarks,
    neverOpenedCount,
    // CRUD
    createBookmark,
    updateBookmark,
    deleteBookmark,
    moveBookmarkToFolder,
    updateProperties,
    // telemetry
    trackClick,
  }
})
