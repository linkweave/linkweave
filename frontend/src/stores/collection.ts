import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { CollectionResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { CollectionInfoJson } from '@/api/generated'
import { useNotificationStore } from '@/stores/notification'

const collectionApi = new CollectionResourceApi(config)

export const useCollectionStore = defineStore('collection', () => {
  const currentCollectionId = ref<string | null>(null)
  const collectionInfo = ref<CollectionInfoJson | null>(null)
  const loading = ref(false)

  const collectionName = computed(() => collectionInfo.value?.name ?? null)

  function setCurrentCollectionId(id: string | null) {
    currentCollectionId.value = id
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

  watch(currentCollectionId, (id) => {
    if (id) {
      fetchCollectionInfo(id)
    } else {
      collectionInfo.value = null
    }
  }, { immediate: true })

  return {
    currentCollectionId,
    collectionInfo,
    loading,
    collectionName,
    setCurrentCollectionId,
    fetchCollectionInfo,
  }
})
