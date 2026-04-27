import {useAuthStore} from '@/stores/auth'
import {useCollectionStore} from '@/stores/collection'

export async function initializeSession(to?: { name?: string | symbol | null; params?: Record<string, string | string[]> }) {
  const auth = useAuthStore()
  const collection = useCollectionStore()

  if (auth.initialized) {
    return
  }

  const authenticated = await auth.fetchCurrentUser()
  if (!authenticated) {
    return
  }

  if (to?.name === 'collection' && to.params?.id && typeof to.params.id === 'string') {
    collection.setCurrentCollectionId(to.params.id)
  } else if (auth.user?.defaultCollectionId) {
    collection.setCurrentCollectionId(auth.user.defaultCollectionId)
  }
}
