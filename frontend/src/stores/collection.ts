import {config} from '@/api'
import type {CollectionInfoJson, CollectionMemberJson, CollectionSettingsJson, CollectionSummaryJson} from '@/api/generated'
import {CollectionResourceApi} from '@/api/generated'
import * as offlineCache from '@/lib/offline-cache'
import { toSerializable } from '@/lib/to-serializable'
import router from '@/router'
import {useAuthStore} from '@/stores/auth'
import {useNotificationStore} from '@/stores/notification'
import {defineStore} from 'pinia'
import {computed, ref, watch} from 'vue'

const collectionApi = new CollectionResourceApi(config)

export const useCollectionStore = defineStore('collection', () => {
  const currentCollectionId = ref<string | null>(null)
  const collectionInfo = ref<CollectionInfoJson | null>(null)
  const collections = ref<CollectionSummaryJson[]>([])
  const settings = ref<CollectionSettingsJson | null>(null)
  const loading = ref(false)
  const searchQuery = ref('')

  const collectionName = computed(() => collectionInfo.value?.name ?? null)

  const settingsLayout = computed<'list' | 'grid' | 'grouped' | null>(() => {
    const v = settings.value?.layout
    return v === 'list' || v === 'grid' || v === 'grouped' ? v : null
  })
  const defaultCollectionId = computed(() =>
    collections.value.find(c => c.isDefault)?.id ?? null
  )

  const filteredCollections = computed(() => {
    if (!searchQuery.value) return collections.value
    const query = searchQuery.value.toLowerCase()
    return collections.value.filter(c =>
      c.name?.toLowerCase().includes(query)
    )
  })

  const collectionsFetched = ref(false)

  function setCurrentCollectionId(id: string | null) {
    currentCollectionId.value = id
  }

  async function fetchCollections() {
    try {
      collections.value = await collectionApi.apiCollectionsGet()
      const auth = useAuthStore()
      if (auth.user?.email) {
        offlineCache.saveCollections(auth.user.email, toSerializable(collections.value)).catch(err => console.error('Failed to cache collections for offline use:', err))
      }
    } catch (err) {
      console.error('Failed to fetch collections:', err)
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to load collections')
    }
  }

  async function fetchCollectionInfo(collectionId: string) {
    if (!collectionId) {
      collectionInfo.value = null
      settings.value = null
      return
    }

    loading.value = true
    try {
      const [info, fetchedSettings] = await Promise.all([
        collectionApi.apiCollectionsIdGet({ id: collectionId }),
        collectionApi.apiCollectionsIdSettingsGet({ id: collectionId }).catch(() => null),
      ])
      collectionInfo.value = info
      settings.value = fetchedSettings
      const auth = useAuthStore()
      if (auth.user?.email && collectionInfo.value) {
        offlineCache.saveCollectionInfo(auth.user.email, toSerializable(collectionInfo.value)).catch(err => console.error('Failed to cache collection info for offline use:', err))
      }
    } catch (err) {
      console.error('Failed to fetch collection info:', err)
      collectionInfo.value = null
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to load collection')
    } finally {
      loading.value = false
    }
  }

  async function setDefaultCollection(collectionId: string) {
    try {
      await collectionApi.apiCollectionsIdDefaultPut({ id: collectionId })
      collections.value = collections.value.map(c => ({
        ...c,
        isDefault: c.id === collectionId
      }))

      const auth = useAuthStore()
      auth.updateDefaultCollectionId(collectionId)
    } catch (err) {
      console.error('Failed to set default collection:', err)
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to set default collection')
    }
  }

  async function createCollection(name: string): Promise<CollectionSummaryJson | null> {
    try {
      const result = await collectionApi.apiCollectionsPost({ collectionCreateJson: { name } })
      await fetchCollections()
      return result
    } catch (err) {
      console.error('Failed to create collection:', err)
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to create collection')
      return null
    }
  }

  async function updateCollection(
    collectionId: string,
    name: string,
    faviconAllowlist?: string,
  ): Promise<boolean> {
    try {
      await collectionApi.apiCollectionsIdPut({
        id: collectionId,
        collectionUpdateJson: { name, faviconAllowlist },
      })
      await fetchCollections()
      if (currentCollectionId.value === collectionId) {
        await fetchCollectionInfo(collectionId)
      }
      return true
    } catch (err) {
      console.error('Failed to update collection:', err)
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to update collection')
      return false
    }
  }

  async function deleteCollection(collectionId: string): Promise<boolean> {
    try {
      await collectionApi.apiCollectionsIdDelete({ id: collectionId })
      const wasCurrentCollection = currentCollectionId.value === collectionId
      if (wasCurrentCollection) {
        currentCollectionId.value = null
      }
      await fetchCollections()
      if (wasCurrentCollection) {
        const fallback = collections.value.find(c => c.isDefault) ?? collections.value[0]
        if (fallback?.id) {
          switchCollection(fallback.id)
        }
      }
      return true
    } catch (err) {
      console.error('Failed to delete collection:', err)
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to delete collection')
      return false
    }
  }

  function switchCollection(collectionId: string) {
    setCurrentCollectionId(collectionId)
    router.push({ name: 'collection', params: { id: collectionId } })
  }

  watch(currentCollectionId, (id, prevId) => {
    if (prevId && pendingPatch) {
      if (settingsFlushTimer) {
        clearTimeout(settingsFlushTimer)
        settingsFlushTimer = null
      }
      void flushSettings()
    }
    if (id) {
      fetchCollectionInfo(id)
      if (!collectionsFetched.value) {
        collectionsFetched.value = true
        fetchCollections()
      }
    } else {
      collectionInfo.value = null
    }
  }, { immediate: true })

  async function fetchMembers(collectionId: string): Promise<CollectionMemberJson[]> {
    try {
      return await collectionApi.apiCollectionsIdMembersGet({ id: collectionId })
    } catch (err) {
      console.error('Failed to fetch members:', err)
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to load members')
      return []
    }
  }

  async function shareWithUser(collectionId: string, email: string): Promise<CollectionMemberJson | null> {
    try {
      return await collectionApi.apiCollectionsIdMembersPost({
        id: collectionId,
        collectionShareJson: { email },
      })
    } catch (err) {
      console.error('Failed to share collection:', err)
      throw err
    }
  }

  const SETTINGS_DEBOUNCE_MS = 400
  let settingsFlushTimer: ReturnType<typeof setTimeout> | null = null
  let pendingPatch: { collectionId: string; patch: CollectionSettingsJson } | null = null
  let inFlight = false

  async function flushSettings(): Promise<void> {
    if (!pendingPatch || inFlight) return
    const { collectionId, patch } = pendingPatch
    pendingPatch = null
    inFlight = true
    try {
      const result = await collectionApi.apiCollectionsIdSettingsPut({
        id: collectionId,
        collectionSettingsJson: patch,
      })
      // Only adopt the server response if the user hasn't queued another change.
      if (!pendingPatch) {
        settings.value = result
      }
    } catch (err) {
      console.error('Failed to update collection settings:', err)
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to update collection settings')
    } finally {
      inFlight = false
      if (pendingPatch) {
        void flushSettings()
      }
    }
  }

  function updateSettings(collectionId: string, patch: CollectionSettingsJson): void {
    settings.value = { ...(settings.value ?? {}), ...patch }
    pendingPatch = {
      collectionId,
      patch: { ...(pendingPatch?.collectionId === collectionId ? pendingPatch.patch : {}), ...patch },
    }
    if (settingsFlushTimer) clearTimeout(settingsFlushTimer)
    settingsFlushTimer = setTimeout(() => {
      settingsFlushTimer = null
      void flushSettings()
    }, SETTINGS_DEBOUNCE_MS)
  }

  async function revokeAccess(collectionId: string, userId: string): Promise<void> {
    try {
      await collectionApi.apiCollectionsIdMembersUserIdDelete({ id: collectionId, userId })
    } catch (err) {
      console.error('Failed to revoke access:', err)
      throw err
    }
  }

  return {
    currentCollectionId,
    collectionInfo,
    collections,
    settings,
    settingsLayout,
    updateSettings,
    loading,
    searchQuery,
    collectionName,
    defaultCollectionId,
    filteredCollections,
    setCurrentCollectionId,
    fetchCollectionInfo,
    fetchCollections,
    setDefaultCollection,
    createCollection,
    updateCollection,
    deleteCollection,
    switchCollection,
    fetchMembers,
    shareWithUser,
    revokeAccess,
    collectionsFetched,
  }
})
