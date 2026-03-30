import { createRouter, createWebHistory } from 'vue-router'
import CollectionView from '@/views/CollectionView.vue'
import LoginView from '@/views/LoginView.vue'
import { useAuthStore } from '@/stores/auth'
import { useCollectionStore } from '@/stores/collection'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      redirect: () => {
        const collection = useCollectionStore()
        if (collection.currentCollectionId) {
          return { name: 'collection', params: { id: collection.currentCollectionId } }
        }
        return { name: 'login' }
      }
    },
    {
      path: '/collections/:id',
      name: 'collection',
      component: CollectionView
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView
    }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  const collection = useCollectionStore()

  if (!auth.initialized) {
    const authenticated = await auth.fetchCurrentUser()
    if (!authenticated && to.name !== 'login') {
      return { name: 'login' }
    }
    if (authenticated && auth.user?.defaultCollectionId) {
      collection.setCurrentCollectionId(auth.user.defaultCollectionId)
    }
  }

  if (!auth.isAuthenticated && to.name !== 'login') {
    return { name: 'login' }
  }
})

export default router
