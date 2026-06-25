import { config } from '@/api'
import type { ImportCommitResultJson, ImportNodeJson } from '@/api/generated'
import { ImportResourceApi } from '@/api/generated'
import { registerStoreReset } from '@/lib/storeReset'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

const importApi = new ImportResourceApi(config)

/**
 * Transient state for the import review surface (UC-096): the parsed manifest,
 * the destination, and the duplicate set. Holds no selection — that lives in
 * {@link useImportSelection}, instantiated by the view. Everything is cleared
 * on leave (BR-184); nothing is persisted until commit.
 */
export const useImportStore = defineStore('import', () => {
  // A file handed off from the settings dialog's "Import" entry point, to be
  // previewed once the review view mounts. In-memory only; lost on reload (the
  // view falls back to its own dropzone).
  const pendingFile = ref<File | null>(null)

  const fileName = ref<string | null>(null)
  const tree = ref<ImportNodeJson[]>([])
  const totalBookmarks = ref(0)
  const totalFolders = ref(0)
  // Bookmarks dropped during preview because their URL can't be stored
  // (e.g. chrome://…); surfaced as a note so the count reconciles.
  const unsupportedCount = ref(0)

  const destinationFolderId = ref<string | undefined>(undefined)

  const parsing = ref(false)
  const committing = ref(false)
  const error = ref<string | null>(null)

  const hasManifest = computed(() => tree.value.length > 0)
  const isEmpty = computed(() => !parsing.value && !error.value && tree.value.length === 0)

  async function preview(collectionId: string, file: File): Promise<void> {
    parsing.value = true
    error.value = null
    fileName.value = file.name
    try {
      const result = await importApi.apiCollectionsCollectionIdImportPreviewPost({
        collectionId,
        file,
      })
      tree.value = result.tree
      totalBookmarks.value = result.totalBookmarks
      totalFolders.value = result.totalFolders
      unsupportedCount.value = result.unsupportedCount
    } catch (err) {
      tree.value = []
      error.value = err instanceof Error ? err.message : 'parse-failed'
      throw err
    } finally {
      parsing.value = false
    }
  }

  async function commit(
    collectionId: string,
    nodes: ImportNodeJson[],
    skipDuplicates: boolean,
  ): Promise<ImportCommitResultJson> {
    committing.value = true
    try {
      return await importApi.apiCollectionsCollectionIdImportCommitPost({
        collectionId,
        importCommitRequestJson: {
          destinationFolderId: destinationFolderId.value,
          skipDuplicates,
          fileName: fileName.value ?? undefined,
          nodes,
        },
      })
    } finally {
      committing.value = false
    }
  }

  /** Consume the handed-off file exactly once. */
  function takePendingFile(): File | null {
    const file = pendingFile.value
    pendingFile.value = null
    return file
  }

  function clear(): void {
    pendingFile.value = null
    fileName.value = null
    tree.value = []
    totalBookmarks.value = 0
    totalFolders.value = 0
    unsupportedCount.value = 0
    destinationFolderId.value = undefined
    parsing.value = false
    committing.value = false
    error.value = null
  }

  registerStoreReset(clear)

  return {
    pendingFile,
    takePendingFile,
    fileName,
    tree,
    totalBookmarks,
    totalFolders,
    unsupportedCount,
    destinationFolderId,
    parsing,
    committing,
    error,
    hasManifest,
    isEmpty,
    preview,
    commit,
    clear,
  }
})
