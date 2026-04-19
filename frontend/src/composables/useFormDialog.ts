import type { Ref } from 'vue'
import { watch } from 'vue'

/**
 * Standardizes the lifecycle of dialog-based forms.
 * Calls the reset function whenever the dialog opens.
 */
export function useFormDialog(open: Ref<boolean> | (() => boolean), resetFn: () => void) {
  watch(() => typeof open === 'function' ? open() : open.value, (val) => {
    if (val) {
      resetFn()
    }
  })
}
