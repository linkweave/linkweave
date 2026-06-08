import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { syncDarkModeWithSystem } from './darkMode'
import '@/assets/main.css'
import './extension-theme.css'

syncDarkModeWithSystem()

const app = createApp(App)
app.use(createPinia())
app.mount('#app')
