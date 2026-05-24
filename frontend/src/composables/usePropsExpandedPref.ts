import { useAuthStore } from '@/stores/auth'
import { ref, watch, type Ref } from 'vue'

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
    console.warn('localStorage unavailable')
    // localStorage may be unavailable (private mode, quota exceeded) — fail silently.
  }
}

export function usePropsExpandedPref(collectionId: Ref<string>) {
  const auth = useAuthStore()
  const keyFor = (id: string) =>
    `${auth.user?.email ?? 'anon'}:propsExpanded:${id}`

  const state = ref<boolean>(safeGetItem(keyFor(collectionId.value)) === 'true')

  let suppressWrite = false

  watch(collectionId, (id) => {
    suppressWrite = true
    state.value = safeGetItem(keyFor(id)) === 'true'
    suppressWrite = false
  })

  watch(state, (value) => {
    if (suppressWrite) return
    safeSetItem(keyFor(collectionId.value), String(value))
  })

  return state
}
