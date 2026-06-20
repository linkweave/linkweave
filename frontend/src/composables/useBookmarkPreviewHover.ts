import { inject, onScopeDispose, provide, shallowRef } from 'vue'
import type { InjectionKey, Ref } from 'vue'
import type { BookmarkJson } from '@/api/generated'

// Hover-intent gating for the list-row preview popup. A bare `:hover` would
// cascade popups as the pointer sweeps the list; this enforces a dwell on
// cold start and a warm window once the user is clearly browsing previews.
const DWELL_MS = 450
const WARM_MS = 600
const GRACE_MS = 80

export type PreviewHoverActive = {
  bookmark: BookmarkJson
  // Live row element — the popup re-measures on each animation frame so it
  // stays glued to the row while transitions run.
  row: HTMLElement
}

export type PreviewHoverController = {
  active: Ref<PreviewHoverActive | null>
  onRowEnter: (bookmark: BookmarkJson, row: HTMLElement) => void
  onRowLeave: () => void
  // Popup keepalive (UC-093): the popup is a solid pointer-events:auto overlay
  // teleported to <body>, so moving the pointer from the row onto the popup
  // fires a row mouseleave. The popup's own mouseenter/leave are wired to these
  // to keep it alive while the cursor is anywhere over it (capture or footer)
  // and hide it once it leaves. Because the overlay captures the pointer, the
  // rows beneath never fire mouseenter while it's up, so the preview can't get
  // hijacked by an adjacent row as the user travels down to the footer.
  onPopupEnter: () => void
  onPopupLeave: () => void
  // Pin the popup open while the footer's dropdown menu is open — moving from
  // the trigger onto the teleported menu content would otherwise dismiss it
  // mid-interaction. Unpin re-enables hiding (the next leave schedules it).
  pin: () => void
  unpin: () => void
}

const KEY: InjectionKey<PreviewHoverController> = Symbol('bookmarkPreviewHover')

export function provideBookmarkPreviewHover(): PreviewHoverController {
  const active = shallowRef<PreviewHoverActive | null>(null)
  // `warm` lives outside the ref system: nothing in the template depends on it.
  let warm = false
  let pinned = false
  let showT: ReturnType<typeof setTimeout> | undefined
  let hideT: ReturnType<typeof setTimeout> | undefined
  let coolT: ReturnType<typeof setTimeout> | undefined

  function clearTimers() {
    if (showT) { clearTimeout(showT); showT = undefined }
    if (hideT) { clearTimeout(hideT); hideT = undefined }
    if (coolT) { clearTimeout(coolT); coolT = undefined }
  }

  function clearHide() {
    if (hideT) { clearTimeout(hideT); hideT = undefined }
  }

  function onRowEnter(bookmark: BookmarkJson, row: HTMLElement) {
    // While pinned, the footer dropdown is open against the current bookmark.
    // Radix dropdowns stay open on mouse-leave, so a pointer drifting onto an
    // adjacent row must NOT retarget `active` — otherwise the still-open menu
    // would silently list (and act on) a different bookmark than the one it was
    // opened for (UC-093: wrong-item delete). Treat the open menu as a stable
    // focus and ignore row enters until it closes (unpin).
    if (pinned) return
    clearHide()
    if (showT) { clearTimeout(showT); showT = undefined }
    if (coolT) { clearTimeout(coolT); coolT = undefined }
    const delay = warm ? 0 : DWELL_MS
    showT = setTimeout(() => {
      active.value = { bookmark, row }
      warm = true
    }, delay)
  }

  // Schedule the hide after the grace window. No-op while pinned (footer menu
  // open) so the popup stays put for an active dropdown interaction.
  function scheduleHide() {
    if (pinned) return
    clearHide()
    hideT = setTimeout(() => {
      active.value = null
      if (coolT) clearTimeout(coolT)
      coolT = setTimeout(() => { warm = false }, WARM_MS)
    }, GRACE_MS)
  }

  function onRowLeave() {
    if (showT) { clearTimeout(showT); showT = undefined }
    scheduleHide()
  }

  // Popup-side keepalive. The popup is a solid pointer-events:auto overlay, so
  // its root mouseenter/leave are wired to these. Entering the popup cancels a
  // hide scheduled by the row mouseleave; leaving it schedules one.
  function onPopupEnter() {
    clearHide()
  }

  function onPopupLeave() {
    scheduleHide()
  }

  function pin() {
    pinned = true
    clearHide()
  }

  function unpin() {
    pinned = false
  }

  // Scrolling = navigating, not previewing. Capture-phase so we catch any
  // scroll container, not just window. Reset to cold so the next preview
  // needs the full dwell again.
  function onScroll() {
    pinned = false
    clearTimers()
    active.value = null
    warm = false
  }
  window.addEventListener('scroll', onScroll, true)

  onScopeDispose(() => {
    clearTimers()
    window.removeEventListener('scroll', onScroll, true)
  })

  const controller: PreviewHoverController = {
    active,
    onRowEnter,
    onRowLeave,
    onPopupEnter,
    onPopupLeave,
    pin,
    unpin,
  }
  provide(KEY, controller)
  return controller
}

export function useBookmarkPreviewHover(): PreviewHoverController | null {
  return inject(KEY, null)
}
