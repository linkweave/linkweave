import { ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'

function safeGetItem(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function safeSetItem(key: string, value: string): void {
  try {
    localStorage.setItem(key, value)
  } catch {
    // localStorage may be unavailable (private mode, quota exceeded) — fail silently.
  }
}

/**
 * Persists a sidebar section's expanded/collapsed state to localStorage,
 * keyed by user. Defaults to `true` (open) on first visit.
 *
 * Example key: `user@example.com:smartCollectionsExpanded`
 */
export function useSidebarSectionExpandedPref(sectionKey: string) {
  const auth = useAuthStore()
  const key = `${auth.user?.email ?? 'anon'}:${sectionKey}`

  const stored = safeGetItem(key)
  const state = ref<boolean>(stored !== null ? stored === 'true' : true)

  watch(state, (value) => {
    safeSetItem(key, String(value))
  })

  return state
}
