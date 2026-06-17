import { inject, provide, shallowRef } from 'vue'
import type { InjectionKey, Ref } from 'vue'

// Shares the sticky list toolbar's root element with the teleported preview
// popup. The popup (UC-093 BR-093-6) is a solid pointer-events:auto overlay and
// must clamp below the toolbar so it never covers and blocks the toolbar's
// controls. The toolbar and the popup live in different layout slots, so they
// can't reach one another through the row-level preview-hover provide; they
// share the element through this provide/inject, anchored in CollectionView
// (which renders both the toolbar slot and the bookmark list).
const KEY: InjectionKey<Ref<HTMLElement | null>> = Symbol('stickyToolbarEl')

// Call in the component that renders both the toolbar and the popup. The
// toolbar self-registers into the returned ref; callers don't need to use it.
export function provideStickyToolbar(): Ref<HTMLElement | null> {
  const el = shallowRef<HTMLElement | null>(null)
  provide(KEY, el)
  return el
}

// Returns the shared ref, or null when no provider is mounted above (e.g. the
// toolbar isn't part of the current view).
export function useStickyToolbar(): Ref<HTMLElement | null> | null {
  return inject(KEY, null)
}
