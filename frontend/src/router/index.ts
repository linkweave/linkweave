import {useAuthStore} from '@/stores/auth'
import {useCollectionStore} from '@/stores/collection'
import CollectionView from '@/views/CollectionView.vue'
import LoginView from '@/views/LoginView.vue'
import {createRouter, createWebHistory} from 'vue-router'

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
      path: '/manage/collections',
      name: 'manage-collections',
      component: () => import('@/views/CollectionManageView.vue')
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/RegisterView.vue')
    }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  // Wait for auth to be initialized before checking authentication
  if (!auth.initialized) {
    await auth.fetchCurrentUser()
  }

  if (!auth.isAuthenticated && to.name !== 'login' && to.name !== 'register') {
    return { name: 'login' }
  }

  if (auth.isAuthenticated && (to.name === 'login' || to.name === 'register')) {
    return { name: 'home' }
  }
})

export default router
