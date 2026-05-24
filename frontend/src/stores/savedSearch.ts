import { isNullish } from '@/lib/nullish.ts'
import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { SavedSearchResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { SavedSearchJson, SavedSearchSaveJson } from '@/api/generated'
import { useCollectionStore } from '@/stores/collection'
import { useBookmarkStore } from '@/stores/bookmark'
import { stringifyTokens, tokenize } from '@/lib/searchQuery'

const api = new SavedSearchResourceApi(config)

function normalize(query: string): string {
  return stringifyTokens(tokenize(query))
}

export const useSavedSearchStore = defineStore('savedSearch', () => {
  const collectionStore = useCollectionStore()
  const bookmarkStore = useBookmarkStore()

  const savedSearches = ref<SavedSearchJson[]>([])
  const loading = ref(false)
  const activeSavedSearchId = ref<string | null>(null)

  async function loadForCurrentCollection(): Promise<void> {
    const collectionId = collectionStore.currentCollectionId
    if (isNullish(collectionId)) {
      savedSearches.value = []
      activeSavedSearchId.value = null
      return
    }
    loading.value = true
    try {
      const list = await api.apiSavedSearchesGet({ collectionId })
      savedSearches.value = list.savedSearchList
    } finally {
      loading.value = false
    }
  }

  // Reload whenever the current collection changes (or becomes available).
  watch(
    () => collectionStore.currentCollectionId,
    () => {
      activeSavedSearchId.value = null
      void loadForCurrentCollection()
    },
    { immediate: true },
  )

  // Deactivate when the user clears the query entirely.
  watch(
    () => bookmarkStore.searchQuery,
    (q) => {
      if (q.trim() === '') activeSavedSearchId.value = null
    },
  )

  const normalizedCurrentQuery = computed(() => normalize(bookmarkStore.searchQuery))

  /** Whether the current query exactly matches the active saved search. */
  const currentMatchesActive = computed(() => {
    const active = savedSearches.value.find((s) => s.id === activeSavedSearchId.value)
    if (!active) return false
    return normalize(active.data.query) === normalizedCurrentQuery.value
  })


  /**
   * Toggling behavior per FR-076 v2: clicking an inactive row loads its query
   * and activates it; clicking the active row deactivates it without touching
   * the query.
   */
  function toggleSavedSearch(savedSearch: SavedSearchJson) {
    if (activeSavedSearchId.value === savedSearch.id) {
      activeSavedSearchId.value = null
      return
    }
    bookmarkStore.setSearchQuery(savedSearch.data.query)
    activeSavedSearchId.value = savedSearch.id
  }

  /** Drop the saved-search context without modifying the search query. */
  function deactivate() {
    activeSavedSearchId.value = null
  }

  /**
   * Persist the current query into the active saved search (Dirty → Matched
   * transition driven by the pill's inline Update action; no popover).
   */
  async function updateActiveQuery(): Promise<SavedSearchJson | null> {
    const id = activeSavedSearchId.value
    if (!id) return null
    return updateSavedSearch(id, { query: bookmarkStore.searchQuery })
  }

  async function createSavedSearch(name: string, query: string): Promise<SavedSearchJson> {
    const collectionId = collectionStore.currentCollectionId
    if (!collectionId) throw new Error('No current collection')
    const data: SavedSearchSaveJson = { collectionId, name, query }
    const created = await api.apiSavedSearchesPost({ savedSearchSaveJson: data })
    savedSearches.value = [...savedSearches.value, created].sort((a, b) =>
      a.data.name.toLowerCase().localeCompare(b.data.name.toLowerCase()),
    )
    activeSavedSearchId.value = created.id
    return created
  }

  async function updateSavedSearch(
    id: string,
    patch: { name?: string; query?: string },
  ): Promise<SavedSearchJson> {
    const existing = savedSearches.value.find((s) => s.id === id)
    if (!existing) throw new Error('Saved search not found')
    const data: SavedSearchSaveJson = {
      collectionId: existing.data.collectionId,
      name: patch.name ?? existing.data.name,
      query: patch.query ?? existing.data.query,
    }
    const updated = await api.apiSavedSearchesSavedSearchIdPut({
      savedSearchId: id,
      savedSearchSaveJson: data,
    })
    savedSearches.value = savedSearches.value
      .map((s) => (s.id === id ? updated : s))
      .sort((a, b) =>
        a.data.name.toLowerCase().localeCompare(b.data.name.toLowerCase()),
      )
    return updated
  }

  async function deleteSavedSearch(id: string): Promise<void> {
    await api.apiSavedSearchesSavedSearchIdDelete({ savedSearchId: id })
    savedSearches.value = savedSearches.value.filter((s) => s.id !== id)
    if (activeSavedSearchId.value === id) activeSavedSearchId.value = null
  }

  return {
    savedSearches,
    loading,
    activeSavedSearchId,
    currentMatchesActive,

    loadForCurrentCollection,
    toggleSavedSearch,
    deactivate,
    updateActiveQuery,
    createSavedSearch,
    updateSavedSearch,
    deleteSavedSearch,
  }
})
