import { ref, watch } from 'vue'
import { safeGetItem, safeSetItem } from '@/lib/safeStorage'
import { useAuthStore } from '@/stores/auth'

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
