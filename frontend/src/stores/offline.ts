import * as offlineCache from '@/lib/offline-cache'
import { useAuthStore } from '@/stores/auth'
import { useCollectionStore } from '@/stores/collection'
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useOfflineStore = defineStore('offline', () => {
  const isOffline = ref(!navigator.onLine)
  const lastSyncedAt = ref<number | null>(null)

  const timeSinceSync = computed(() => {
    if (!lastSyncedAt.value) return null
    const seconds = Math.floor((Date.now() - lastSyncedAt.value) / 1000)
    if (seconds < 60) return `${seconds}s ago`
    const minutes = Math.floor(seconds / 60)
    if (minutes < 60) return `${minutes}m ago`
    const hours = Math.floor(minutes / 60)
    return `${hours}h ago`
  })

  async function loadLastSyncTime() {
    const auth = useAuthStore()
    if (!auth.user?.email) return
    try {
      lastSyncedAt.value = await offlineCache.getLastSyncedAt(auth.user.email)
    } catch {
      lastSyncedAt.value = null
    }
  }

  function setupListeners() {
    const debouncedSetOffline = debounce((value: boolean) => {
      isOffline.value = value
      if (!value) {
        onBackOnline()
      }
    }, 2000)

    window.addEventListener('online', () => debouncedSetOffline(false))
    window.addEventListener('offline', () => debouncedSetOffline(true))
  }

  async function onBackOnline() {
    const collection = useCollectionStore()
    if (collection.currentCollectionId) {
      await collection.fetchCollectionInfo(collection.currentCollectionId)
    }
    await collection.fetchCollections()
    lastSyncedAt.value = Date.now()
  }

  return {
    isOffline,
    lastSyncedAt,
    timeSinceSync,
    loadLastSyncTime,
    setupListeners,
    onBackOnline,
  }
})

function debounce(fn: (value: boolean) => void, ms: number): (value: boolean) => void {
  let timer: ReturnType<typeof setTimeout> | null = null
  return (value: boolean) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn(value), ms)
  }
}
