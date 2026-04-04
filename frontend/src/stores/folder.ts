import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { FolderResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { FolderJson, FolderSaveJson, FolderMoveJson } from '@/api/generated'
import { useNotificationStore } from '@/stores/notification'

const folderApi = new FolderResourceApi(config)

export const useFolderStore = defineStore('folder', () => {
  const folders = ref<FolderJson[]>([])
  const loading = ref(false)
  const selectedFolderId = ref<string | null>(null)

  const selectedFolderPath = computed<FolderJson[]>(() => {
    if (selectedFolderId.value === null) return []
    const path: FolderJson[] = []
    let currentId: string | null | undefined = selectedFolderId.value
    while (currentId) {
      const folder = folders.value.find(f => f.id === currentId)
      if (folder) {
        path.unshift(folder)
        currentId = folder.data.parentId
      } else {
        break
      }
    }
    return path
  })

  function selectFolder(folderId: string | null) {
    selectedFolderId.value = folderId
  }

  async function fetchFolders(collectionId?: string) {
    loading.value = true
    try {
      const result = await folderApi.apiFoldersGet(
        collectionId ? { collectionId } : undefined,
      )
      folders.value = result.folderList ?? []
    } catch (err) {
      folders.value = []
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to load folders')
    } finally {
      loading.value = false
    }
  }

  async function createFolder(data: FolderSaveJson): Promise<FolderJson> {
    const folder = await folderApi.apiFoldersPost({ folderSaveJson: data })
    folders.value.push(folder)
    return folder
  }

  async function renameFolder(folderId: string, data: FolderSaveJson): Promise<FolderJson> {
    const updated = await folderApi.apiFoldersFolderIdPut({
      folderId,
      folderSaveJson: data,
    })
    const idx = folders.value.findIndex(f => f.id === folderId)
    if (idx !== -1) folders.value[idx] = updated
    return updated
  }

  async function moveFolder(folderId: string, data: FolderMoveJson): Promise<FolderJson> {
    const updated = await folderApi.apiFoldersFolderIdMovePatch({
      folderId,
      folderMoveJson: data,
    })
    const idx = folders.value.findIndex(f => f.id === folderId)
    if (idx !== -1) folders.value[idx] = updated
    return updated
  }

  async function deleteFolder(folderId: string): Promise<void> {
    await folderApi.apiFoldersFolderIdDelete({ folderId })
    folders.value = folders.value.filter(f => f.id !== folderId)
    if (selectedFolderId.value === folderId) {
      selectedFolderId.value = null
    }
  }

  return {
    folders,
    loading,
    selectedFolderId,
    selectedFolderPath,
    fetchFolders,
    createFolder,
    renameFolder,
    moveFolder,
    deleteFolder,
    selectFolder,
  }
})
