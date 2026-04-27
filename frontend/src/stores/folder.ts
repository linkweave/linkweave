import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { FolderResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { FolderJson, FolderSaveJson, FolderMoveJson } from '@/api/generated'
import { useCollectionStore } from '@/stores/collection'
import { useTrashbinStore } from '@/stores/trashbin'

const folderApi = new FolderResourceApi(config)

export const useFolderStore = defineStore('folder', () => {
  const collectionStore = useCollectionStore()

  const folders = computed<FolderJson[]>(() =>
    collectionStore.collectionInfo?.folders ?? []
  )

  const loading = computed(() => collectionStore.loading)

  const selectedFolderId = ref<string | null>(null)

  const selectedFolderIds = computed<Set<string>>(() => {
    if (selectedFolderId.value === null) return new Set()
    const result = new Set<string>()
    const queue = [selectedFolderId.value]
    while (queue.length > 0) {
      const id = queue.pop()!
      result.add(id)
      for (const f of folders.value) {
        if (f.data.parentId === id) queue.push(f.id)
      }
    }
    return result
  })

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

  function patchFolders(updater: (list: FolderJson[]) => FolderJson[]) {
    const info = collectionStore.collectionInfo
    if (info) {
      info.folders = updater(info.folders ?? [])
    }
  }

  async function createFolder(data: FolderSaveJson): Promise<FolderJson> {
    const folder = await folderApi.apiFoldersPost({ folderSaveJson: data })
    patchFolders(list => [...list, folder])
    return folder
  }

  async function renameFolder(folderId: string, data: FolderSaveJson): Promise<FolderJson> {
    const updated = await folderApi.apiFoldersFolderIdPut({
      folderId,
      folderSaveJson: data,
    })
    patchFolders(list => {
      const idx = list.findIndex(f => f.id === folderId)
      if (idx !== -1) list[idx] = updated
      return list
    })
    return updated
  }

  async function moveFolder(folderId: string, data: FolderMoveJson): Promise<FolderJson> {
    const updated = await folderApi.apiFoldersFolderIdMovePatch({
      folderId,
      folderMoveJson: data,
    })
    patchFolders(list => {
      const idx = list.findIndex(f => f.id === folderId)
      if (idx !== -1) list[idx] = updated
      return list
    })
    return updated
  }

  async function deleteFolder(folderId: string): Promise<void> {
    await folderApi.apiFoldersFolderIdDelete({ folderId })
    patchFolders(list => list.filter(f => f.id !== folderId))
    if (selectedFolderId.value === folderId) {
      selectedFolderId.value = null
    }
    void useTrashbinStore().refreshCount()
    if (collectionStore.currentCollectionId) {
      void collectionStore.fetchCollectionInfo(collectionStore.currentCollectionId)
    }
  }

  return {
    folders,
    loading,
    selectedFolderId,
    selectedFolderIds,
    selectedFolderPath,
    createFolder,
    renameFolder,
    moveFolder,
    deleteFolder,
    selectFolder,
  }
})
