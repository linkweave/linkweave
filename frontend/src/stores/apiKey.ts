import { config } from '@/api'
import { ApiKeyResourceApi } from '@/api/generated'
import type { ApiKeyCreateJson, ApiKeyJson } from '@/api/generated'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

const api = new ApiKeyResourceApi(config)

/** Maximum number of active (non-revoked, non-expired) keys a user may hold. */
export const MAX_ACTIVE_API_KEYS = 10

/**
 * API keys are user-scoped (not collection-scoped). Callers in the view layer
 * trigger a load on mount and are responsible for surfacing errors
 * (notifications/i18n); the store just throws.
 */
export const useApiKeyStore = defineStore('apiKey', () => {
  const keys = ref<ApiKeyJson[]>([])
  const loading = ref(false)

  function isExpired(key: ApiKeyJson): boolean {
    return !!key.expiresAt && new Date(key.expiresAt) <= new Date()
  }

  // Hide already-revoked keys; the backend keeps them but the UI removes them on revocation.
  const visibleKeys = computed(() => keys.value.filter((k) => !k.revokedAt))

  const activeCount = computed(() => visibleKeys.value.filter((k) => !isExpired(k)).length)

  const maxReached = computed(() => activeCount.value >= MAX_ACTIVE_API_KEYS)

  async function load(): Promise<void> {
    loading.value = true
    try {
      const result = await api.apiAuthApiKeysGet()
      keys.value = result.apiKeys
    } finally {
      loading.value = false
    }
  }

  /**
   * Create a key and return the one-time raw key string. Reloads the list so
   * cached entries carry properly typed `Date` fields (the POST response is
   * untyped and would otherwise leave ISO strings in the cache).
   */
  async function create(data: ApiKeyCreateJson): Promise<string> {
    const result = await api.apiAuthApiKeysPost({ apiKeyCreateJson: data })
    await load()
    return result.key
  }

  async function revoke(id: string): Promise<void> {
    await api.apiAuthApiKeysApiKeyIdDelete({ apiKeyId: id })
    // Optimistically mark revoked; visibleKeys drops it immediately.
    keys.value = keys.value.map((k) => (k.id === id ? { ...k, revokedAt: new Date() } : k))
  }

  return {
    keys,
    loading,
    visibleKeys,
    activeCount,
    maxReached,
    isExpired,
    load,
    create,
    revoke,
  }
})
