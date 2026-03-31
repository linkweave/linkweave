import { defineStore } from 'pinia'
import { ref } from 'vue'
import { BookmarkResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { BookmarkJson, BookmarkSaveJson } from '@/api/generated'

const bookmarkApi = new BookmarkResourceApi(config)

export const useBookmarkStore = defineStore('bookmark', () => {
  const bookmarks = ref<BookmarkJson[]>([])
  const loading = ref(false)

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
    bookmarks.value.push(bookmark)
    return bookmark
  }

  return {
    bookmarks,
    loading,
    fetchBookmarks,
    createBookmark,
  }
})
