import {useAuthStore} from '@/stores/auth'
import {useCollectionStore} from '@/stores/collection'

export async function initializeSession() {
  const auth = useAuthStore()
  const collection = useCollectionStore()

  if (auth.initialized) {
    return
  }

  const authenticated = await auth.fetchCurrentUser()
  if (authenticated && auth.user?.defaultCollectionId) {
    collection.setCurrentCollectionId(auth.user.defaultCollectionId)
  }
}
