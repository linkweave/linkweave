import {config} from '@/api'
import type {CollectionInfoJson, CollectionMemberJson, CollectionSummaryJson} from '@/api/generated'
import {CollectionResourceApi} from '@/api/generated'
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
    fetchMembers,
    shareWithUser,
    revokeAccess,
  }
})
