import { defineStore } from 'pinia'
import { ref } from 'vue'
import { FolderResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { FolderJson, FolderSaveJson } from '@/api/generated'

const folderApi = new FolderResourceApi(config)

export const useFolderStore = defineStore('folder', () => {
  const folders = ref<FolderJson[]>([])
  const loading = ref(false)

  async function fetchFolders() {
    loading.value = true
    try {
      const result = await folderApi.apiFoldersGet()
      folders.value = result.folderList ?? []
    } catch {
      folders.value = []
    } finally {
      loading.value = false
    }
  }

  async function createFolder(data: FolderSaveJson): Promise<FolderJson> {
    const folder = await folderApi.apiFoldersPost({ folderSaveJson: data })
    folders.value.push(folder)
    return folder
  }

  return {
    folders,
    loading,
    fetchFolders,
    createFolder,
  }
})
