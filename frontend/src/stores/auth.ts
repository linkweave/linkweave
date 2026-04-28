import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { AuthResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { UserInfoJson } from '@/api/generated'
import { useCollectionStore } from '@/stores/collection'
import { useBookmarkStore } from '@/stores/bookmark'
import { useFolderStore } from '@/stores/folder'
import { useTagStore } from '@/stores/tag'
import * as offlineCache from '@/lib/offline-cache'
import router from '@/router'

const authApi = new AuthResourceApi(config)

function resetAllStores() {
  const collection = useCollectionStore()
  const bookmark = useBookmarkStore()
  const folder = useFolderStore()
  const tag = useTagStore()

  collection.currentCollectionId = null
  collection.collectionInfo = null
  collection.collections = []
  collection.collectionsFetched = false
  bookmark.searchQuery = ''
  folder.selectedFolderId = null
  tag.selectedTagIds = new Set()
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfoJson | null>(null)
  const loading = ref(true)
  const initialized = ref(false)

  const isAuthenticated = computed(() => user.value !== null)
  const displayName = computed(() => {
    if (!user.value) return ''
    return `${user.value.firstName} ${user.value.lastName}`
  })

  let fetchPromise: Promise<boolean> | null = null

  async function fetchCurrentUser(): Promise<boolean> {
    if (fetchPromise) {
      return fetchPromise
    }
    fetchPromise = (async () => {
      try {
        user.value = await authApi.apiAuthMeGet()
        if (user.value) {
          offlineCache.saveUserInfo(user.value.email, user.value).catch(err => console.error('Failed to cache user info for offline use:', err))
        }
        return true
      } catch {
        user.value = null
        return false
      } finally {
        loading.value = false
        initialized.value = true
        fetchPromise = null
      }
    })()
    return fetchPromise
  }

  async function logout() {
    try {
      await authApi.apiAuthLogoutPost()
    } catch {
      // cookie is cleared by server, ignore errors
    } finally {
      const email = user.value?.email
      resetAllStores()
      user.value = null
      loading.value = true
      initialized.value = false
      fetchPromise = null
      if (email) {
        offlineCache.purgeForUser(email).catch(err => console.error('Failed to purge offline cache on logout:', err))
      }
      router.push({ name: 'login' })
    }
  }

  function updateDefaultCollectionId(id: string) {
    if (user.value) {
      user.value = { ...user.value, defaultCollectionId: id }
    }
  }

  return {
    user,
    loading,
    initialized,
    isAuthenticated,
    displayName,
    fetchCurrentUser,
    logout,
    updateDefaultCollectionId
  }
})
