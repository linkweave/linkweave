import { defineStore } from 'pinia'
import { ref, watchEffect } from 'vue'

export type Theme = 'light' | 'dark' | 'system'

function loadTheme(): Theme {
  const stored = localStorage.getItem('theme')
  if (stored === 'light' || stored === 'dark' || stored === 'system') return stored
  return 'system'
}

function applyTheme(theme: Theme) {
  const isDark =
    theme === 'dark' || (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)
  document.documentElement.classList.toggle('dark', isDark)
}

export const useUiStore = defineStore('ui', () => {
  const sidebarOpen = ref(false)
  const theme = ref<Theme>(loadTheme())

  watchEffect(() => {
    localStorage.setItem('theme', theme.value)
    applyTheme(theme.value)
  })

  const toggleSidebar = () => {
    sidebarOpen.value = !sidebarOpen.value
  }

  const setTheme = (newTheme: Theme) => {
    theme.value = newTheme
  }

  return {
    sidebarOpen,
    theme,
    toggleSidebar,
    setTheme
  }
})
