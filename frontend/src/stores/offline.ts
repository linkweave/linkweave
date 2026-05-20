import * as offlineCache from '@/lib/offline-cache'
import {
  installNetworkStatusListeners,
  isOffline,
  offlineReason,
  setBrowserOffline,
} from '@/lib/network-status'
import { useAuthStore } from '@/stores/auth'
import { useCollectionStore } from '@/stores/collection'
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useOfflineStore = defineStore('offline', () => {
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
    const debouncedSetBrowserOffline = debounce((value: boolean) => {
      setBrowserOffline(value)
      if (!value) onBackOnline()
    }, 2000)

    window.addEventListener('online', () => debouncedSetBrowserOffline(false))
    window.addEventListener('offline', () => debouncedSetBrowserOffline(true))

    installNetworkStatusListeners({ onServerBack: onBackOnline })
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
    offlineReason,
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
