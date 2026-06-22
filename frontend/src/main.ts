import './assets/main.css'
import 'vue-color/style.css'
import { createPinia } from 'pinia'
import { configure } from 'vee-validate'

import * as Sentry from '@sentry/vue'
import { createApp } from 'vue'

import App from './App.vue'
import i18n from './i18n'
import { registerRouter } from './lib/routerNavigation'
import router from './router'

configure({
  validateOnBlur: true,
  validateOnChange: true,
  validateOnInput: false,
  validateOnModelUpdate: false,
})

async function initializeApp() {
  const app = createApp(App)

  Sentry.init({
    app,
    dsn: import.meta.env.VITE_SENTRY_DSN,
    tunnel: '/api/sentry-tunnel',
    enabled: !import.meta.env.VITEST && !import.meta.env.VITE_E2E,
    environment: import.meta.env.MODE,
    integrations: [Sentry.browserTracingIntegration({ router })],
    tracesSampleRate: 0.2,
    tracePropagationTargets: [
      'local-chainlink.localhost',
      'dev-linkweave.dev',
      'linkweave.dev',
    ],
    sendDefaultPii: true,
    enableLogs: true,
  })

  app.use(createPinia())
  app.use(router)
  app.use(i18n)
  registerRouter(router)

  // Auth and collection initialization is handled by the router guard
  // before any navigation proceeds, so we can mount immediately
  app.mount('#app')

  if ('serviceWorker' in navigator) {
    // When a new service worker takes control (e.g. after a deploy), reload so
    // the app picks up the new shell. Guard against re-entrancy: a single
    // controllerchange must trigger at most one reload, otherwise repeated
    // events (e.g. DevTools "Update on reload") can stack into a reload loop.
    let refreshing = false
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      if (refreshing) return
      refreshing = true
      window.location.reload()
    })
  }
}

try {
  await initializeApp()
} catch (e) {
  console.error('Error initializing app:', e)
}
