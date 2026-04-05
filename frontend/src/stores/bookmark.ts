import { defineStore } from 'pinia'
import { computed } from 'vue'
import { BookmarkResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { BookmarkJson, BookmarkSaveJson, BookmarkMoveJson } from '@/api/generated'
import { useCollectionStore } from '@/stores/collection'
import { useFolderStore } from '@/stores/folder'
import { useTagStore } from '@/stores/tag'

const bookmarkApi = new BookmarkResourceApi(config)

export const useBookmarkStore = defineStore('bookmark', () => {
  const collectionStore = useCollectionStore()

  const bookmarks = computed<BookmarkJson[]>(() =>
    collectionStore.collectionInfo?.bookmarks ?? []
  )

  const loading = computed(() => collectionStore.loading)

  const filteredBookmarks = computed(() => {
    const folderStore = useFolderStore()
    const tagStore = useTagStore()
    let result = bookmarks.value

    if (folderStore.selectedFolderId !== null) {
      result = result.filter(b => b.data.folderId === folderStore.selectedFolderId)
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

    return result
  })

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

  return {
    bookmarks,
    loading,
    filteredBookmarks,
    createBookmark,
    updateBookmark,
    deleteBookmark,
    moveBookmarkToFolder,
  }
})
