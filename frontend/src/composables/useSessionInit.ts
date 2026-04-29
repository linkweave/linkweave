import {useAuthStore} from '@/stores/auth'
import {useCollectionStore} from '@/stores/collection'
import * as offlineCache from '@/lib/offline-cache'

export async function initializeSession(to?: { name?: string | symbol | null; params?: Record<string, string | string[]> }) {
  const auth = useAuthStore()
  const collection = useCollectionStore()

  if (auth.initialized) {
    return
  }

  const authenticated = await auth.fetchCurrentUser()
  if (!authenticated) {
    const restored = await tryRestoreFromCache(auth)
    if (!restored) {
      return
    }
  }

  if (to?.name === 'collection' && to.params?.id && typeof to.params.id === 'string') {
    collection.setCurrentCollectionId(to.params.id)
  } else if (auth.user?.defaultCollectionId) {
    collection.setCurrentCollectionId(auth.user.defaultCollectionId)
  }
}

async function tryRestoreFromCache(auth: ReturnType<typeof useAuthStore>): Promise<boolean> {
  if (!('indexedDB' in window)) return false
  if (navigator.onLine) return false

  try {
    const cached = await offlineCache.loadUserInfo()
    if (!cached) return false

    auth.$patch({
      user: cached.data,
      loading: false,
      initialized: true,
    })
    return true
  } catch {
    return false
  }
}
