import { safeGetItem, safeSetItem } from '@/lib/safeStorage'
import { useAuthStore } from '@/stores/auth'
import { ref, watch, type Ref } from 'vue'

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
