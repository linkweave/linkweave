import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { BookmarkResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { BookmarkJson, BookmarkSaveJson, BookmarkMoveJson } from '@/api/generated'
import { useFolderStore } from '@/stores/folder'
import { useTagStore } from '@/stores/tag'

const bookmarkApi = new BookmarkResourceApi(config)

export const useBookmarkStore = defineStore('bookmark', () => {
  const bookmarks = ref<BookmarkJson[]>([])
  const loading = ref(false)

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

  async function fetchBookmarks(collectionId: string) {
    loading.value = true
    try {
      const result = await bookmarkApi.apiBookmarksGet({ collectionId })
      bookmarks.value = result.bookmarkList ?? []
    } catch {
      bookmarks.value = []
    } finally {
      loading.value = false
    }
  }

  async function createBookmark(data: BookmarkSaveJson): Promise<BookmarkJson> {
    const bookmark = await bookmarkApi.apiBookmarksPost({ bookmarkSaveJson: data })
    bookmarks.value.unshift(bookmark)
    return bookmark
  }

  async function updateBookmark(bookmarkId: string, data: BookmarkSaveJson): Promise<BookmarkJson> {
    const updated = await bookmarkApi.apiBookmarksBookmarkIdPut({
      bookmarkId,
      bookmarkSaveJson: data,
    })
    const idx = bookmarks.value.findIndex(b => b.id === bookmarkId)
    if (idx !== -1) bookmarks.value[idx] = updated
    return updated
  }

  async function deleteBookmark(bookmarkId: string): Promise<void> {
    await bookmarkApi.apiBookmarksBookmarkIdDelete({ bookmarkId })
    bookmarks.value = bookmarks.value.filter(b => b.id !== bookmarkId)
  }

  async function moveBookmarkToFolder(bookmarkId: string, data: BookmarkMoveJson): Promise<BookmarkJson> {
    const updated = await bookmarkApi.apiBookmarksBookmarkIdMovePatch({
      bookmarkId,
      bookmarkMoveJson: data,
    })
    const idx = bookmarks.value.findIndex(b => b.id === bookmarkId)
    if (idx !== -1) bookmarks.value[idx] = updated
    return updated
  }

  return {
    bookmarks,
    loading,
    filteredBookmarks,
    fetchBookmarks,
    createBookmark,
    updateBookmark,
    deleteBookmark,
    moveBookmarkToFolder,
  }
})
