import { initializeSession } from '@/composables/useSessionInit'
import { useAuthStore } from '@/stores/auth'
import { useCollectionStore } from '@/stores/collection'
import CollectionView from '@/views/CollectionView.vue'
import LoginView from '@/views/LoginView.vue'
import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: CollectionView,
    },
    {
      path: '/collections/:id',
      name: 'collection',
      component: CollectionView,
    },
    {
      path: '/collections/:id/import',
      name: 'import-review',
      component: () => import('@/views/ImportReviewView.vue'),
    },
    {
      path: '/manage/collections',
      name: 'manage-collections',
      component: () => import('@/views/CollectionManageView.vue'),
    },
    {
      path: '/trashbin',
      name: 'trashbin',
      component: () => import('@/views/TrashbinView.vue'),
    },
    {
      path: '/cleanup-suggestions',
      name: 'cleanup-suggestions',
      component: () => import('@/views/CleanupSuggestionsView.vue'),
    },
    {
      path: '/login',
      name: 'login',
      meta: { public: true },
      component: LoginView,
    },
    {
      path: '/register',
      name: 'register',
      meta: { public: true },
      component: () => import('@/views/RegisterView.vue'),
    },
    {
      path: '/privacy',
      name: 'privacy',
      meta: { public: true },
      component: () => import('@/views/PrivacyPolicyView.vue'),
    },
  ],
})

router.beforeEach(async (to) => {
  await initializeSession(to)

  const auth = useAuthStore()
  const collection = useCollectionStore()

  if (!auth.isAuthenticated && !to.meta.public) {
    return { name: 'login' }
  }

  if (auth.isAuthenticated && to.meta.public) {
    return { name: 'home' }
  }

  if (auth.isAuthenticated && to.name === 'home') {
    if (collection.currentCollectionId) {
      return { name: 'collection', params: { id: collection.currentCollectionId } }
    }
    return { name: 'manage-collections' }
  }
})

export default router
