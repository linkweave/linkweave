import { useSelectionStore } from '@/stores/selection'
import { onBeforeUnmount, onMounted } from 'vue'

function isEditableTarget(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null
  if (!el) return false
  return el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable
}

/**
 * Window-level keyboard shortcuts for batch selection (UC-074), mounted by
 * the view that shows selectable bookmarks: Esc clears and exits (unless a
 * dialog/menu is open — those catch Esc themselves and must not also clear
 * the selection underneath); ⌘/Ctrl-A selects all in the current view, only
 * intercepted while selecting.
 *
 * A composable rather than store-internal wiring: stores never unmount, so a
 * store-owned window listener would outlive the views it serves.
 */
export function useSelectionShortcuts(): void {
  const selection = useSelectionStore()

  function onKeydown(event: KeyboardEvent) {
    if (!selection.selecting) return
    if (event.key === 'Escape') {
      if (event.defaultPrevented) return
      // An open radix dialog/menu lives in a portal with role=dialog/menu;
      // Esc precedence belongs to it (UC-074 edge case).
      if (document.querySelector('[role="dialog"], [role="menu"]')) return
      if (isEditableTarget(event.target)) return
      selection.clearAndExit()
    } else if ((event.key === 'a' || event.key === 'A') && (event.metaKey || event.ctrlKey)) {
      if (isEditableTarget(event.target)) return
      event.preventDefault()
      selection.selectAll()
    }
  }

  onMounted(() => globalThis.addEventListener('keydown', onKeydown))
  onBeforeUnmount(() => globalThis.removeEventListener('keydown', onKeydown))
}
