import {
  AuthResourceApi,
  BookmarkResourceApi,
  CollectionResourceApi,
  ResponseError,
} from '@/api/generated'
import type {
  BookmarkJson,
  BookmarkSaveJson,
  CollectionInfoJson,
  CollectionSummaryJson,
  UserInfoJson,
} from '@/api/generated'
import { loadExtensionConfig, createApiConfig } from '../api/client'
import type { ExtensionConfig } from '../api/client'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { parseSearchQuery, bookmarkMatchesTerms } from '@/utils/search'

export const useExtensionStore = defineStore('extension', () => {
  // --- Config (loaded async from chrome.storage.sync) ---
  let config: ExtensionConfig | null = null
  let authApi: AuthResourceApi
  let collectionApi: CollectionResourceApi
  let bookmarkApi: BookmarkResourceApi
  const webAppUrl = ref('')

  // --- Current browser tab ---
  const currentTabUrl = ref('')

  // --- Auth ---
  const user = ref<UserInfoJson | null>(null)
  const isAuthenticated = computed(() => user.value !== null)

  // --- Collections ---
  const collections = ref<CollectionSummaryJson[]>([])
  const currentCollectionId = ref<string | null>(null)
  const collectionInfo = ref<CollectionInfoJson | null>(null)

  // --- Filter state ---
  const searchQuery = ref('')
  const selectedTagIds = ref<Set<string>>(new Set())
  const selectedFolderId = ref<string | null>(null)

  // --- Loading / error ---
  const loading = ref(false)
  const collectionLoading = ref(false)
  const error = ref<string | null>(null)

  // --- Derived ---
  const tags = computed(() => collectionInfo.value?.tags ?? [])
  const folders = computed(() => collectionInfo.value?.folders ?? [])

  const alreadySavedBookmark = computed(() => {
    if (!currentTabUrl.value) return null
    const normalise = (url: string) => url.replace(/\/$/, '').toLowerCase()
    const target = normalise(currentTabUrl.value)
    return (collectionInfo.value?.bookmarks ?? []).find(
      (b) => b.data.url != null && normalise(b.data.url) === target,
    ) ?? null
  })

  const filteredBookmarks = computed(() => {
    let result: BookmarkJson[] = collectionInfo.value?.bookmarks ?? []

    if (selectedFolderId.value !== null) {
      // Include the selected folder and all its descendants
      const descendantIds = getDescendantFolderIds(selectedFolderId.value)
      result = result.filter(
        (b) => b.data.folderId != null && descendantIds.has(b.data.folderId),
      )
    }

    if (selectedTagIds.value.size > 0) {
      result = result.filter((b) => {
        if (!b.data.tagIds) return false
        for (const id of selectedTagIds.value) {
          if (b.data.tagIds.has(id)) return true
        }
        return false
      })
    }

    if (searchQuery.value.length >= 2) {
      const terms = parseSearchQuery(searchQuery.value)
      if (terms.length > 0) {
        const tagNamesById = new Map(tags.value.map((t) => [t.id, t.data.name.toLowerCase()]))
        result = result.filter(b => bookmarkMatchesTerms(b, terms, tagNamesById))
      }
    }

    return result
  })

  function getDescendantFolderIds(rootId: string): Set<string> {
    const result = new Set<string>()
    const queue = [rootId]
    while (queue.length > 0) {
      const id = queue.pop()!
      result.add(id)
      for (const f of folders.value) {
        if (f.data.parentId === id) queue.push(f.id)
      }
    }
    return result
  }

  // --- Actions ---

  function setError(e: unknown): void {
    if (e instanceof ResponseError) {
      error.value = `HTTP ${e.response.status} – ${e.response.url}`
      console.error('[chainlink] error', e.response.status, e.response.url)
    } else {
      error.value = e instanceof Error ? e.message : String(e)
      console.error('[chainlink] error', e)
    }
  }

  /** Load config from chrome.storage.sync (can be called before initialize). */
  async function loadConfig(): Promise<ExtensionConfig> {
    if (config) return config
    config = await loadExtensionConfig()
    const apiConfig = createApiConfig(config)
    authApi = new AuthResourceApi(apiConfig)
    collectionApi = new CollectionResourceApi(apiConfig)
    bookmarkApi = new BookmarkResourceApi(apiConfig)
    webAppUrl.value = config.webAppUrl
    return config
  }

  async function initialize(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      await loadConfig()
      user.value = await authApi.apiAuthMeGet()
      const defaultId = user.value.defaultCollectionId
      await Promise.all([
        loadCollections(),
        loadCollection(defaultId),
      ])
      currentCollectionId.value = defaultId
    } catch (e) {
      user.value = null
      setError(e)
    } finally {
      loading.value = false
    }
  }

  async function loadCollections(): Promise<void> {
    collections.value = await collectionApi.apiCollectionsGet()
  }

  async function loadCollection(id: string): Promise<void> {
    collectionLoading.value = true
    error.value = null
    try {
      collectionInfo.value = await collectionApi.apiCollectionsIdGet({ id })
      currentCollectionId.value = id
      // Reset filters when switching collections
      selectedTagIds.value = new Set()
      selectedFolderId.value = null
      searchQuery.value = ''
    } catch (e) {
      setError(e)
    } finally {
      collectionLoading.value = false
    }
  }

  async function createBookmark(data: BookmarkSaveJson): Promise<BookmarkJson> {
    const bookmark = await bookmarkApi.apiBookmarksPost({ bookmarkSaveJson: data })
    // Prepend to local state so it shows immediately in the browse view
    if (collectionInfo.value) {
      collectionInfo.value.bookmarks = [bookmark, ...(collectionInfo.value.bookmarks ?? [])]
    }
    return bookmark
  }

  function trackClick(bookmarkId: string): void {
    if (!config) return
    // Fire-and-forget — keepalive ensures it completes even if popup closes
    fetch(`${config.apiUrl}/api/bookmarks/${bookmarkId}/track-click`, {
      method: 'POST',
      credentials: 'include',
      keepalive: true,
    }).catch(() => {})
  }

  function toggleTag(tagId: string): void {
    const next = new Set(selectedTagIds.value)
    if (next.has(tagId)) next.delete(tagId)
    else next.add(tagId)
    selectedTagIds.value = next
  }

  function clearFilters(): void {
    selectedTagIds.value = new Set()
    selectedFolderId.value = null
    searchQuery.value = ''
  }

  return {
    user,
    isAuthenticated,
    webAppUrl,
    currentTabUrl,
    alreadySavedBookmark,
    collections,
    currentCollectionId,
    collectionInfo,
    searchQuery,
    selectedTagIds,
    selectedFolderId,
    loading,
    collectionLoading,
    error,
    tags,
    folders,
    filteredBookmarks,
    loadConfig,
    initialize,
    loadCollections,
    loadCollection,
    createBookmark,
    trackClick,
    toggleTag,
    clearFilters,
  }
})
