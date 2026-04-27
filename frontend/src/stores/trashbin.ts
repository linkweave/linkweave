import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { TrashbinResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { BookmarkJson, FolderJson } from '@/api/generated'

const trashbinApi = new TrashbinResourceApi(config)

export const useTrashbinStore = defineStore('trashbin', () => {
  const bookmarks = ref<BookmarkJson[]>([])
  const folders = ref<FolderJson[]>([])
  const count = ref(0)
  const loading = ref(false)

  const isEmpty = computed(() => bookmarks.value.length === 0 && folders.value.length === 0)

  async function refresh(): Promise<void> {
    loading.value = true
    try {
      const result = await trashbinApi.apiTrashbinGet()
      bookmarks.value = result.bookmarks ?? []
      folders.value = result.folders ?? []
      count.value = bookmarks.value.length + folders.value.length
    } finally {
      loading.value = false
    }
  }

  async function refreshCount(): Promise<void> {
    const c = await trashbinApi.apiTrashbinCountGet()
    count.value = c.count
  }

  async function restoreBookmark(bookmarkId: string): Promise<void> {
    await trashbinApi.apiTrashbinBookmarksBookmarkIdRestorePost({ bookmarkId })
    bookmarks.value = bookmarks.value.filter(b => b.id !== bookmarkId)
    count.value = bookmarks.value.length + folders.value.length
  }

  async function purgeBookmark(bookmarkId: string): Promise<void> {
    await trashbinApi.apiTrashbinBookmarksBookmarkIdDelete({ bookmarkId })
    bookmarks.value = bookmarks.value.filter(b => b.id !== bookmarkId)
    count.value = bookmarks.value.length + folders.value.length
  }

  async function restoreFolder(folderId: string): Promise<FolderJson> {
    const restored = await trashbinApi.apiTrashbinFoldersFolderIdRestorePost({ folderId })
    await refresh()
    return restored
  }

  async function purgeFolder(folderId: string): Promise<void> {
    await trashbinApi.apiTrashbinFoldersFolderIdDelete({ folderId })
    await refresh()
  }

  async function empty(): Promise<void> {
    await trashbinApi.apiTrashbinDelete()
    bookmarks.value = []
    folders.value = []
    count.value = 0
  }

  return {
    bookmarks,
    folders,
    count,
    loading,
    isEmpty,
    refresh,
    refreshCount,
    restoreBookmark,
    purgeBookmark,
    restoreFolder,
    purgeFolder,
    empty,
  }
})
