import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { AuthResourceApi } from '@/api/generated'
import { config } from '@/api'
import type { UserInfoJson } from '@/api/generated'
import router from '@/router'

const authApi = new AuthResourceApi(config)

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfoJson | null>(null)
  const loading = ref(true)
  const initialized = ref(false)

  const isAuthenticated = computed(() => user.value !== null)
  const displayName = computed(() => {
    if (!user.value) return ''
    return `${user.value.firstName} ${user.value.lastName}`
  })

  let fetchPromise: Promise<boolean> | null = null

  async function fetchCurrentUser(): Promise<boolean> {
    if (fetchPromise) {
      return fetchPromise
    }
    fetchPromise = (async () => {
      try {
        user.value = await authApi.apiAuthMeGet()
        return true
      } catch {
        user.value = null
        return false
      } finally {
        loading.value = false
        initialized.value = true
        fetchPromise = null
      }
    })()
    return fetchPromise
  }

  async function logout() {
    try {
      await authApi.apiAuthLogoutPost()
    } catch {
      // cookie is cleared by server, ignore errors
    } finally {
      user.value = null
      router.push({ name: 'login' })
    }
  }

  function updateDefaultCollectionId(id: string) {
    if (user.value) {
      user.value = { ...user.value, defaultCollectionId: id }
    }
  }

  return {
    user,
    loading,
    initialized,
    isAuthenticated,
    displayName,
    fetchCurrentUser,
    logout,
    updateDefaultCollectionId
  }
})
