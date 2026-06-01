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
  dismiss: () => void
}

const KEY: InjectionKey<PreviewHoverController> = Symbol('bookmarkPreviewHover')

export function provideBookmarkPreviewHover(): PreviewHoverController {
  const active = shallowRef<PreviewHoverActive | null>(null)
  // `warm` lives outside the ref system: nothing in the template depends on it.
  let warm = false
  let showT: ReturnType<typeof setTimeout> | undefined
  let hideT: ReturnType<typeof setTimeout> | undefined
  let coolT: ReturnType<typeof setTimeout> | undefined

  function clearTimers() {
    if (showT) { clearTimeout(showT); showT = undefined }
    if (hideT) { clearTimeout(hideT); hideT = undefined }
    if (coolT) { clearTimeout(coolT); coolT = undefined }
  }

  function onRowEnter(bookmark: BookmarkJson, row: HTMLElement) {
    if (hideT) { clearTimeout(hideT); hideT = undefined }
    if (showT) { clearTimeout(showT); showT = undefined }
    if (coolT) { clearTimeout(coolT); coolT = undefined }
    const delay = warm ? 0 : DWELL_MS
    showT = setTimeout(() => {
      active.value = { bookmark, row }
      warm = true
    }, delay)
  }

  function onRowLeave() {
    if (showT) { clearTimeout(showT); showT = undefined }
    if (hideT) clearTimeout(hideT)
    hideT = setTimeout(() => {
      active.value = null
      if (coolT) clearTimeout(coolT)
      coolT = setTimeout(() => { warm = false }, WARM_MS)
    }, GRACE_MS)
  }

  function dismiss() {
    clearTimers()
    active.value = null
    warm = false
  }

  // Scrolling = navigating, not previewing. Capture-phase so we catch any
  // scroll container, not just window. Reset to cold so the next preview
  // needs the full dwell again.
  function onScroll() { dismiss() }
  window.addEventListener('scroll', onScroll, true)

  onScopeDispose(() => {
    clearTimers()
    window.removeEventListener('scroll', onScroll, true)
  })

  const controller: PreviewHoverController = { active, onRowEnter, onRowLeave, dismiss }
  provide(KEY, controller)
  return controller
}

export function useBookmarkPreviewHover(): PreviewHoverController | null {
  return inject(KEY, null)
}

// Re-exported so the popup can mirror the same dwell/warm values if needed.
export const PREVIEW_HOVER_TIMINGS = { DWELL_MS, WARM_MS, GRACE_MS }

// Silences "unused" warnings on the type re-export above when bundlers prune.
export type { Ref }
