import { defineStore } from 'pinia'
import { ref, watchEffect } from 'vue'

export type Theme = 'light' | 'dark' | 'system'
export type BookmarkLayout = 'list' | 'grid' | 'grouped'

function loadTheme(): Theme {
  const stored = localStorage.getItem('theme')
  if (stored === 'light' || stored === 'dark' || stored === 'system') return stored
  return 'system'
}

function loadBookmarkLayout(): BookmarkLayout {
  const stored = localStorage.getItem('bookmarkLayout')
  if (stored === 'list' || stored === 'grid' || stored === 'grouped') return stored
  return 'grid'
}

function applyTheme(theme: Theme) {
  const isDark =
    theme === 'dark' || (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)
  document.documentElement.classList.toggle('dark', isDark)
}

export const useUiStore = defineStore('ui', () => {
  const sidebarOpen = ref(false)
  const theme = ref<Theme>(loadTheme())
  const bookmarkLayout = ref<BookmarkLayout>(loadBookmarkLayout())

  watchEffect(() => {
    localStorage.setItem('theme', theme.value)
    applyTheme(theme.value)
  })

  watchEffect(() => {
    localStorage.setItem('bookmarkLayout', bookmarkLayout.value)
  })

  const toggleSidebar = () => {
    sidebarOpen.value = !sidebarOpen.value
  }

  const setTheme = (newTheme: Theme) => {
    theme.value = newTheme
  }

  const setBookmarkLayout = (layout: BookmarkLayout) => {
    bookmarkLayout.value = layout
  }

  return {
    sidebarOpen,
    theme,
    bookmarkLayout,
    toggleSidebar,
    setTheme,
    setBookmarkLayout,
  }
})
