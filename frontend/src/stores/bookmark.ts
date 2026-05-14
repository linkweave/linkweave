import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { BookmarkResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { BookmarkJson, BookmarkSaveJson, BookmarkMoveJson } from '@/api/generated'
import { useCollectionStore } from '@/stores/collection'
import { useFolderStore } from '@/stores/folder'
import { useTagStore } from '@/stores/tag'
import { sortBookmarks } from '@/utils/bookmarkSort'
import { parseSearchQuery, bookmarkMatchesTerms } from '@/utils/search'

const bookmarkApi = new BookmarkResourceApi(config)

export const useBookmarkStore = defineStore('bookmark', () => {
  const collectionStore = useCollectionStore()

  const bookmarks = computed<BookmarkJson[]>(() =>
    collectionStore.collectionInfo?.bookmarks ?? []
  )

  const loading = computed(() => collectionStore.loading)

  const searchQuery = ref('')

  function setSearchQuery(query: string) {
    searchQuery.value = query
  }

  function clearSearchQuery() {
    searchQuery.value = ''
  }

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

    if (tagStore.selectedTagIds.size > 0) {
      result = result.filter(b => {
        const tagIds = b.data.tagIds
        if (!tagIds) return false
        for (const selectedId of tagStore.selectedTagIds) {
          if (tagIds.has(selectedId)) return true
        }
        return false
      })
    }

    if (searchQuery.value.length >= 2) {
      const terms = parseSearchQuery(searchQuery.value)
      if (terms.length > 0) {
        const tagsById = new Map(tagStore.tags.map(t => [t.id, t.data.name.toLowerCase()]))
        result = result.filter(b => bookmarkMatchesTerms(b, terms, tagsById))
      }
    }

    return sortBookmarks(result, {
      field: collectionStore.sortField,
      direction: collectionStore.sortDirection,
    })
  })

  const filteredBookmarks = computed(() => sortedAndFiltered.value.items)
  const neverOpenedCount = computed(() => sortedAndFiltered.value.neverOpenedCount)

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

  function trackClick(bookmarkId: string): void {
    fetch(`/api/bookmarks/${bookmarkId}/track-click`, {
      method: 'POST',
      keepalive: true,
      credentials: 'include',
    }).catch(() => {})
  }

  return {
    bookmarks,
    loading,
    searchQuery,
    filteredBookmarks,
    neverOpenedCount,
    setSearchQuery,
    clearSearchQuery,
    createBookmark,
    updateBookmark,
    deleteBookmark,
    moveBookmarkToFolder,
    trackClick,
  }
})
