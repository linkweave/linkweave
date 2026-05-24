import { useAuthStore } from '@/stores/auth'
import { ref, watch } from 'vue'

export function usePropsExpandedPref(collectionId: string) {
  const auth = useAuthStore()
  const key = `${auth.user?.email ?? 'anon'}:propsExpanded:${collectionId}`

  const stored =
    typeof localStorage !== 'undefined' ? localStorage.getItem(key) : null
  const state = ref<boolean>(stored !== null ? stored === 'true' : false)

  watch(state, (value) => {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(key, String(value))
    }
  })

  return state
}
