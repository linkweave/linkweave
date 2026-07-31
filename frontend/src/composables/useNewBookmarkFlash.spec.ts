// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick, ref } from 'vue'
import { markNewBookmark, useNewBookmarkFlash } from './useNewBookmarkFlash'

// Mirrored from the composable (kept private there).
const FLASH_MS = 1800

/** Mounts a throwaway component so `onMounted`/`watch` run with a real instance. */
function mountRow(bookmarkId: string) {
  const el = ref<HTMLElement | null>(document.createElement('div'))
  const scrollIntoView = vi.fn()
  ;(el.value as HTMLElement).scrollIntoView = scrollIntoView

  let isNew!: { value: boolean }
  const app = createApp({
    setup() {
      isNew = useNewBookmarkFlash(el, () => bookmarkId).isNew
      return () => null
    },
  })
  app.mount(document.createElement('div'))
  return { isNew, scrollIntoView, unmount: () => app.unmount() }
}

describe('useNewBookmarkFlash', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => {
    // Drain the flash so state does not leak between tests.
    vi.advanceTimersByTime(FLASH_MS)
    vi.useRealTimers()
  })

  it('flags only the row whose bookmark was just created', () => {
    // ARRANGE
    const created = mountRow('bm-1')
    const other = mountRow('bm-2')

    // ACT
    markNewBookmark('bm-1')

    // ASSERT
    expect(created.isNew.value).toBe(true)
    expect(other.isNew.value).toBe(false)

    created.unmount()
    other.unmount()
  })

  it('scrolls a row into view when it becomes the new bookmark', async () => {
    // ARRANGE
    const row = mountRow('bm-1')
    expect(row.scrollIntoView).not.toHaveBeenCalled()

    // ACT — one tick runs the watcher, the next runs the scroll it defers
    // so the row is laid out at its final position first.
    markNewBookmark('bm-1')
    await nextTick()
    await nextTick()

    // ASSERT
    expect(row.scrollIntoView).toHaveBeenCalledWith({ block: 'nearest', behavior: 'smooth' })

    row.unmount()
  })

  it('scrolls a row that mounts after the bookmark was marked', async () => {
    // ARRANGE — the list can re-render after the create resolves
    markNewBookmark('bm-late')

    // ACT
    const row = mountRow('bm-late')
    await nextTick()

    // ASSERT
    expect(row.scrollIntoView).toHaveBeenCalledOnce()

    row.unmount()
  })

  it('clears the flag once the flash window elapses', () => {
    // ARRANGE
    const row = mountRow('bm-1')
    markNewBookmark('bm-1')

    // ACT
    vi.advanceTimersByTime(FLASH_MS)

    // ASSERT
    expect(row.isNew.value).toBe(false)

    row.unmount()
  })

  it('moves the flag when a second bookmark is created before the first flash ends', () => {
    // ARRANGE
    const first = mountRow('bm-1')
    const second = mountRow('bm-2')
    markNewBookmark('bm-1')

    // ACT
    vi.advanceTimersByTime(FLASH_MS / 2)
    markNewBookmark('bm-2')

    // ASSERT
    expect(first.isNew.value).toBe(false)
    expect(second.isNew.value).toBe(true)

    first.unmount()
    second.unmount()
  })
})
