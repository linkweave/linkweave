import { createApp } from 'vue'
import OptionsView from './views/OptionsView.vue'
import '@/assets/main.css'
import './extension-theme.css'

// Apply dark mode class based on system preference
function applyDarkMode(dark: boolean) {
  document.documentElement.classList.toggle('dark', dark)
}
const mq = window.matchMedia('(prefers-color-scheme: dark)')
applyDarkMode(mq.matches)
mq.addEventListener('change', (e) => applyDarkMode(e.matches))

const app = createApp(OptionsView)
app.mount('#app')
