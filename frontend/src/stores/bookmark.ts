import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { BookmarkResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { BookmarkJson, BookmarkSaveJson, BookmarkMoveJson } from '@/api/generated'
import { useCollectionStore } from '@/stores/collection'
import { useFolderStore } from '@/stores/folder'
import { useTagStore } from '@/stores/tag'
import { sortBookmarks } from '@/utils/bookmarkSort'
import {
  tokenize,
  stringifyTokens,
  toggleToken,
  matchesTokens,
  type QueryToken,
  type MatchContext,
} from '@/lib/searchQuery'

const bookmarkApi = new BookmarkResourceApi(config)

export const useBookmarkStore = defineStore('bookmark', () => {
  const collectionStore = useCollectionStore()

  // ---------------------------------------------------------------------------
  // State + base getters
  // ---------------------------------------------------------------------------

  const bookmarks = computed<BookmarkJson[]>(() =>
    collectionStore.collectionInfo?.bookmarks ?? []
  )

  const loading = computed(() => collectionStore.loading)

  // ---------------------------------------------------------------------------
  // Search query (string + parsed tokens, UC-070 lite)
  // ---------------------------------------------------------------------------

  const searchQuery = ref('')

  function setSearchQuery(query: string) {
    searchQuery.value = query
  }

  function clearSearchQuery() {
    searchQuery.value = ''
  }

  const queryTokens = computed<QueryToken[]>(() => tokenize(searchQuery.value))

  function toggleQueryToken(token: QueryToken, modifier?: 'exclude') {
    const next = toggleToken(queryTokens.value, token, modifier)
    searchQuery.value = stringifyTokens(next)
  }

  function removeQueryTokenAt(idx: number) {
    const next = queryTokens.value.filter((_, i) => i !== idx)
    searchQuery.value = stringifyTokens(next)
  }

  function isTagActive(name: string): boolean {
    const lower = name.toLowerCase()
    return queryTokens.value.some(t => t.kind === 'tag' && !t.neg && t.value.toLowerCase() === lower)
  }

  function isTagExcluded(name: string): boolean {
    const lower = name.toLowerCase()
    return queryTokens.value.some(t => t.kind === 'tag' && t.neg && t.value.toLowerCase() === lower)
  }

  function isFolderActive(name: string): boolean {
    const lower = name.toLowerCase()
    return queryTokens.value.some(
      t => t.kind === 'op' && t.key === 'folder' && !t.neg && t.value.toLowerCase() === lower,
    )
  }

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
    let result = bookmarks.value

    if (folderStore.selectedFolderId !== null) {
      result = result.filter(b => b.data.folderId != null && folderStore.selectedFolderIds.has(b.data.folderId))
    }

    // Sidebar tag selection is now merged into the search query (see tag store's
    // `selectedTagIds`/`toggleTag`); the token filter below handles it.
    const tokens = queryTokens.value
    if (tokens.length > 0) {
      const tagNamesById = new Map(tagStore.tags.map(t => [t.id, t.data.name.toLowerCase()]))
      const folderNamesById = new Map(folderStore.folders.map(f => [f.id, f.data.name.toLowerCase()]))
      result = result.filter(b => {
        const ctx: MatchContext = {
          tagNamesById,
          folderName: b.data.folderId ? folderNamesById.get(b.data.folderId) ?? null : null,
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

  async function createBookmark(data: BookmarkSaveJson): Promise<BookmarkJson> {
    const bookmark = await bookmarkApi.apiBookmarksPost({ bookmarkSaveJson: data })
    patchBookmarks(list => [bookmark, ...list])
    return bookmark
  }

  async function updateBookmark(bookmarkId: string, data: BookmarkSaveJson): Promise<BookmarkJson> {
    const updated = await bookmarkApi.apiBookmarksBookmarkIdPut({
      bookmarkId,
      bookmarkSaveJson: data,
    })
    patchBookmarks(list => {
      const idx = list.findIndex(b => b.id === bookmarkId)
      if (idx !== -1) list[idx] = updated
      return list
    })
    return updated
  }

  async function deleteBookmark(bookmarkId: string): Promise<void> {
    await bookmarkApi.apiBookmarksBookmarkIdDelete({ bookmarkId })
    patchBookmarks(list => list.filter(b => b.id !== bookmarkId))
    const { useTrashbinStore } = await import('@/stores/trashbin')
    useTrashbinStore().refreshCount().catch(() => {})
  }

  async function moveBookmarkToFolder(bookmarkId: string, data: BookmarkMoveJson): Promise<BookmarkJson> {
    const updated = await bookmarkApi.apiBookmarksBookmarkIdMovePatch({
      bookmarkId,
      bookmarkMoveJson: data,
    })
    patchBookmarks(list => {
      const idx = list.findIndex(b => b.id === bookmarkId)
      if (idx !== -1) list[idx] = updated
      return list
    })
    return updated
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
    // search
    searchQuery,
    setSearchQuery,
    clearSearchQuery,
    queryTokens,
    toggleQueryToken,
    removeQueryTokenAt,
    isTagActive,
    isTagExcluded,
    isFolderActive,
    // filtered list
    filteredBookmarks,
    neverOpenedCount,
    // CRUD
    createBookmark,
    updateBookmark,
    deleteBookmark,
    moveBookmarkToFolder,
    // telemetry
    trackClick,
  }
})
