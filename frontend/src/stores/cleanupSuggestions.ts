import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { CleanupSuggestionResourceApi } from '@/api/generated'
import type { CleanupSuggestionJson } from '@/api/generated'
import { config } from '@/api'

const cleanupApi = new CleanupSuggestionResourceApi(config)

export const useCleanupSuggestionsStore = defineStore('cleanupSuggestions', () => {
  const suggestions = ref<CleanupSuggestionJson[]>([])
  const thresholds = ref<number[]>([3, 6, 12])
  const selectedIds = ref<Set<string>>(new Set())
  const loading = ref(false)
  const thresholdMonths = ref(6)

  const isEmpty = computed(() => suggestions.value.length === 0)
  const selectedCount = computed(() => selectedIds.value.size)
  const allSelected = computed(() =>
    suggestions.value.length > 0 && selectedIds.value.size === suggestions.value.length
  )

  async function refresh(collectionId: string): Promise<void> {
    loading.value = true
    try {
      const result = await cleanupApi.apiCleanupSuggestionsGet({
        collectionId,
        thresholdMonths: thresholdMonths.value,
      })
      suggestions.value = result.suggestions ?? []
      selectedIds.value = new Set()
    } finally {
      loading.value = false
    }
  }

  async function fetchThresholds(): Promise<void> {
    try {
      thresholds.value = await cleanupApi.apiCleanupSuggestionsThresholdsGet()
    } catch {
      // keep defaults
    }
  }

  async function dismissSuggestion(bookmarkId: string): Promise<void> {
    await cleanupApi.apiCleanupSuggestionsBookmarkIdDismissPost({
      bookmarkId,
    })
    suggestions.value = suggestions.value.filter(s => s.id !== bookmarkId)
    selectedIds.value.delete(bookmarkId)
  }

  async function moveSelectedToTrash(collectionId: string): Promise<void> {
    if (selectedIds.value.size === 0) return
    await cleanupApi.apiCleanupSuggestionsMoveToTrashPost({
      moveToTrashJson: {
        collectionId,
        bookmarkIds: Array.from(selectedIds.value),
      },
    })
    const removedIds = selectedIds.value
    suggestions.value = suggestions.value.filter(s => !removedIds.has(s.id))
    selectedIds.value = new Set()
  }

  function toggleSelection(id: string) {
    const next = new Set(selectedIds.value)
    if (next.has(id)) {
      next.delete(id)
    } else {
      next.add(id)
    }
    selectedIds.value = next
  }

  function selectAll() {
    selectedIds.value = new Set(suggestions.value.map(s => s.id))
  }

  function clearSelection() {
    selectedIds.value = new Set()
  }

  return {
    suggestions,
    thresholds,
    selectedIds,
    loading,
    thresholdMonths,
    isEmpty,
    selectedCount,
    allSelected,
    refresh,
    fetchThresholds,
    dismissSuggestion,
    moveSelectedToTrash,
    toggleSelection,
    selectAll,
    clearSelection,
  }
})
