import { createApp } from 'vue'
import OptionsView from './views/OptionsView.vue'
import { syncDarkModeWithSystem } from './darkMode'
import '@/assets/main.css'
import './extension-theme.css'

syncDarkModeWithSystem()

const app = createApp(OptionsView)
app.mount('#app')
