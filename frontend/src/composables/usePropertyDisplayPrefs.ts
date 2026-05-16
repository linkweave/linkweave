import { ref, watch, type Ref } from 'vue'

// Lightweight localStorage-backed booleans. Two preferences are exposed (badges
// on cards, sidebar section) but the helper is generic so future prefs can
// reuse it. Each call to `useShow*` returns the same module-level ref so the
// modal and the consumers stay in sync without an extra store.
function usePersistedBoolean(key: string, defaultValue: boolean): Ref<boolean> {
  const stored = typeof localStorage !== 'undefined' ? localStorage.getItem(key) : null
  const state = ref<boolean>(stored !== null ? stored === 'true' : defaultValue)
  watch(state, value => {
    if (typeof localStorage !== 'undefined') localStorage.setItem(key, String(value))
  })
  return state
}

// Module-level singletons so every consumer mutates the same reactive ref.
const showPropertyBadgesRef = usePersistedBoolean('chainlink:showPropertyBadges', false)
const showPropertiesSidebarRef = usePersistedBoolean('chainlink:showPropertiesSidebar', true)

export function useShowPropertyBadges() {
  return showPropertyBadgesRef
}

export function useShowPropertiesSidebar() {
  return showPropertiesSidebarRef
}
