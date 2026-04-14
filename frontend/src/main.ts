import './assets/main.css'
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

  // Auth and collection initialization is handled by the router guard
  // before any navigation proceeds, so we can mount immediately
  app.mount('#app')
}

try {
  await initializeApp()
} catch (e) {
  console.error('Error initializing app:', e)
}
