import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { api, type UserInfo } from '@/api/client'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | null>(null)
  const loading = ref(true)
  const initialized = ref(false)

  const isAuthenticated = computed(() => user.value !== null)
  const displayName = computed(() => {
    if (!user.value) return ''
    return `${user.value.firstName} ${user.value.lastName}`
  })

  async function fetchCurrentUser(): Promise<boolean> {
    try {
      const response = await fetch('/api/auth/me', {
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' }
      })

      if (response.status === 401) {
        user.value = null
        return false
      }

      if (!response.ok) {
        user.value = null
        return false
      }

      user.value = await response.json()
      return true
    } catch {
      user.value = null
      return false
    } finally {
      loading.value = false
      initialized.value = true
    }
  }

  async function logout() {
    try {
      await api.post('/auth/logout')
    } catch {
      // cookie is cleared by server, ignore errors
    } finally {
      user.value = null
      router.push({ name: 'login' })
    }
  }

  return {
    user,
    loading,
    initialized,
    isAuthenticated,
    displayName,
    fetchCurrentUser,
    logout
  }
})
