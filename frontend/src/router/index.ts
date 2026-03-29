import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView
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

  if (!auth.initialized) {
    const authenticated = await auth.fetchCurrentUser()
    if (!authenticated && to.name !== 'login') {
      return { name: 'login' }
    }
  }

  if (!auth.isAuthenticated && to.name !== 'login') {
    return { name: 'login' }
  }
})

export default router
