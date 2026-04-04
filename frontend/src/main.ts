import './assets/main.css'
import { useAuthStore } from '@/stores/auth'
import { useCollectionStore } from '@/stores/collection'
import { createPinia } from 'pinia'

import { createApp } from 'vue'

import App from './App.vue'
import i18n from './i18n'
import router from './router'

async function initializeApp() {
  const app = createApp(App)

  app.use(createPinia())
  app.use(router)
  app.use(i18n)

  const auth = useAuthStore()
  const collection = useCollectionStore()

  // Wait for auth to be initialized before mounting
  // The router guard will also handle this if the router navigates early
  if (!auth.initialized) {
    const authenticated = await auth.fetchCurrentUser()
    if (authenticated && auth.user?.defaultCollectionId) {
      collection.setCurrentCollectionId(auth.user.defaultCollectionId)
    }
  }

  app.mount('#app')
}

try {
  await initializeApp()
} catch (e) {
  console.error('Error initializing app:', e)
}
