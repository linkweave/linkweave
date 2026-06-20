// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp } from 'vue'
import type { BookmarkJson } from '@/api/generated'
import { provideBookmarkPreviewHover, type PreviewHoverController } from './useBookmarkPreviewHover'

// Timings mirrored from the composable (kept private there).
const DWELL_MS = 450
const WARM_MS = 600

function bookmark(id: string): BookmarkJson {
  return { id } as unknown as BookmarkJson
}

function row(): HTMLElement {
  return document.createElement('div')
}

// Mount a throwaway component so `provide()`/`onScopeDispose()` run with a real
// instance + scope, then hand the controller back to the test.
function mountController(): { controller: PreviewHoverController; unmount: () => void } {
  let controller!: PreviewHoverController
  const app = createApp({
    setup() {
      controller = provideBookmarkPreviewHover()
      return () => null
    },
  })
  app.mount(document.createElement('div'))
  return { controller, unmount: () => app.unmount() }
}

describe('provideBookmarkPreviewHover', () => {
  let mounted: ReturnType<typeof mountController>

  beforeEach(() => {
    vi.useFakeTimers()
    mounted = mountController()
  })

  afterEach(() => {
    mounted.unmount()
    vi.useRealTimers()
  })

  it('opens the popup for the hovered row after the cold dwell, then warm-switches instantly', () => {
    const { controller } = mounted
    const a = bookmark('a')
    const b = bookmark('b')

    controller.onRowEnter(a, row())
    expect(controller.active.value).toBeNull() // still within the dwell
    vi.advanceTimersByTime(DWELL_MS)
    expect(controller.active.value?.bookmark).toBe(a)

    // Warm: a subsequent row resolves on the next tick without the full dwell.
    controller.onRowEnter(b, row())
    vi.advanceTimersByTime(0)
    expect(controller.active.value?.bookmark).toBe(b)
  })

  it('does not retarget the active bookmark while pinned (footer menu open)', () => {
    const { controller } = mounted
    const a = bookmark('a')
    const b = bookmark('b')

    // Warm the controller and open the popup for A.
    controller.onRowEnter(a, row())
    vi.advanceTimersByTime(DWELL_MS)
    expect(controller.active.value?.bookmark).toBe(a)

    // Footer ⋯ menu opens against A.
    controller.pin()

    // Pointer drifts onto an adjacent row B while the menu is still open.
    controller.onRowEnter(b, row())
    vi.advanceTimersByTime(DWELL_MS)

    // The popup (and thus the open menu's actions) must still target A.
    expect(controller.active.value?.bookmark).toBe(a)

    // Once the menu closes, enters resume retargeting normally.
    controller.unpin()
    controller.onRowEnter(b, row())
    vi.advanceTimersByTime(0)
    expect(controller.active.value?.bookmark).toBe(b)
  })

  it('keeps the pinned popup alive across a row leave, and hides after unpin', () => {
    const { controller } = mounted
    const a = bookmark('a')

    controller.onRowEnter(a, row())
    vi.advanceTimersByTime(DWELL_MS)
    controller.pin()

    // A leave while pinned must not schedule a hide.
    controller.onRowLeave()
    vi.advanceTimersByTime(WARM_MS)
    expect(controller.active.value?.bookmark).toBe(a)

    // After unpin, the next leave hides as usual.
    controller.unpin()
    controller.onRowLeave()
    vi.advanceTimersByTime(WARM_MS)
    expect(controller.active.value).toBeNull()
  })
})
