import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import '@/assets/main.css'
import './extension-theme.css'

// Apply dark mode class based on system preference, keep in sync if it changes
function applyDarkMode(dark: boolean) {
  document.documentElement.classList.toggle('dark', dark)
}
const mq = window.matchMedia('(prefers-color-scheme: dark)')
applyDarkMode(mq.matches)
mq.addEventListener('change', (e) => applyDarkMode(e.matches))

const app = createApp(App)
app.use(createPinia())
app.mount('#app')
