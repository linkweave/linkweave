import { defineStore } from 'pinia'
import { computed } from 'vue'
import { FolderResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { FolderJson, FolderSaveJson, FolderMoveJson } from '@/api/generated'
import { useCollectionStore } from '@/stores/collection'
import { useBookmarkStore } from '@/stores/bookmark'
import { useTrashbinStore } from '@/stores/trashbin'

const folderApi = new FolderResourceApi(config)

export const useFolderStore = defineStore('folder', () => {
  const collectionStore = useCollectionStore()

  const folders = computed<FolderJson[]>(() =>
    collectionStore.collectionInfo?.folders ?? []
  )

  const loading = computed(() => collectionStore.loading)

  // Sidebar folder selection is a derived view over the bookmark search query:
  // the "selected" folder is whichever folder is referenced by the first active
  // `under:` token. Sidebar / card clicks write the folder *id* into the token
  // (so duplicate folder names don't disambiguate ambiguously); typed queries
  // can still use names, resolved as a fallback below.
  const selectedFolderId = computed<string | null>(() => {
    const bookmarkStore = useBookmarkStore()
    for (const t of bookmarkStore.queryTokens) {
      if (t.kind === 'operator' && t.key === 'under' && !t.neg) {
        // Exact id first (click-path encoding) — case-sensitive.
        const byId = folders.value.find(f => f.id === t.value)
        if (byId) return byId.id
        // Fallback: case-insensitive name match. Inherits duplicate-name
        // ambiguity by design — typed queries are name-based by convention.
        const target = t.value.toLowerCase()
        const byName = folders.value.find(f => f.data.name.toLowerCase() === target)
        if (byName) return byName.id
      }
    }
    return null
  })

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
    const bookmarkStore = useBookmarkStore()
    // Strip any existing `under:` tokens — folder selection is exclusive in the
    // sidebar, so we never want two active at once. Leaves `folder:` tokens
    // (the flat substring variant emitted by card labels) alone.
    bookmarkStore.removeTokensWhere(t => t.kind === 'operator' && t.key === 'under')
    if (folderId === null) return
    // Use the folder *id* as the token value: unambiguous across duplicate
    // folder names. The pill renders the resolved name (see FilterPill.vue).
    bookmarkStore.toggleQueryToken({ kind: 'operator', key: 'under', value: folderId, neg: false })
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
    // check if the folder was selected, and if so, unselect it to remove pills in query
    const wasSelected = selectedFolderId.value === folderId
    await folderApi.apiFoldersFolderIdDelete({ folderId })
    patchFolders(list => list.filter(f => f.id !== folderId))
    if (wasSelected) selectFolder(null)
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
