import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { CollectionResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { CollectionInfoJson, CollectionSummaryJson } from '@/api/generated'
import { useNotificationStore } from '@/stores/notification'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const collectionApi = new CollectionResourceApi(config)

export const useCollectionStore = defineStore('collection', () => {
  const currentCollectionId = ref<string | null>(null)
  const collectionInfo = ref<CollectionInfoJson | null>(null)
  const collections = ref<CollectionSummaryJson[]>([])
  const loading = ref(false)

  const collectionName = computed(() => collectionInfo.value?.name ?? null)
  const defaultCollectionId = computed(() =>
    collections.value.find(c => c.isDefault)?.id ?? null
  )

  const collectionsFetched = ref(false)

  function setCurrentCollectionId(id: string | null) {
    currentCollectionId.value = id
  }

  async function fetchCollections() {
    try {
      collections.value = await collectionApi.apiCollectionsGet()
    } catch (err) {
      console.error('Failed to fetch collections:', err)
      const notification = useNotificationStore()
      notification.handleApiError(err, 'Failed to load collections')
    }
  }

  async function fetchCollectionInfo(collectionId: string) {
    if (!collectionId) {
      collectionInfo.value = null
      return
    }

    loading.value = true
    try {
      collectionInfo.value = await collectionApi.apiCollectionsIdGet({ id: collectionId })
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

  async function updateCollection(collectionId: string, name: string): Promise<boolean> {
    try {
      await collectionApi.apiCollectionsIdPut({ id: collectionId, collectionUpdateJson: { name } })
      await fetchCollections()
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
      if (currentCollectionId.value === collectionId) {
        currentCollectionId.value = null
      }
      await fetchCollections()
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

  watch(currentCollectionId, (id) => {
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

  return {
    currentCollectionId,
    collectionInfo,
    collections,
    loading,
    collectionName,
    defaultCollectionId,
    setCurrentCollectionId,
    fetchCollectionInfo,
    fetchCollections,
    setDefaultCollection,
    createCollection,
    updateCollection,
    deleteCollection,
    switchCollection,
  }
})
