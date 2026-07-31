import { computed, nextTick, onMounted, ref, watch, type Ref } from 'vue'

/**
 * Reveals a freshly created bookmark.
 *
 * A new bookmark is filed by sort order, not by recency: in grouped layout it
 * lands at the bottom of its folder group — for a late group that is the very
 * bottom of the page. Saving therefore changed nothing on screen, which read as
 * "it didn't save" and got the bookmark entered a second time. The new row now
 * scrolls itself into view and flashes briefly so the save is visibly located.
 */
const FLASH_MS = 1800

const newBookmarkId = ref<string | null>(null)
let flashTimer: ReturnType<typeof setTimeout> | undefined

/** Marks `id` as just-created; the matching row reveals itself when it renders. */
export function markNewBookmark(id: string) {
  clearTimeout(flashTimer)
  newBookmarkId.value = id
  flashTimer = setTimeout(() => {
    newBookmarkId.value = null
  }, FLASH_MS)
}

/**
 * Wires one rendered bookmark row. Returns `isNew`, which the row binds to its
 * flash class. Scrolling waits a tick so the row is laid out at its final
 * position before it is brought into view.
 */
export function useNewBookmarkFlash(el: Ref<HTMLElement | null>, bookmarkId: () => string) {
  const isNew = computed(() => newBookmarkId.value === bookmarkId())

  function reveal() {
    if (!isNew.value) return
    void nextTick(() => {
      el.value?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
    })
  }

  onMounted(reveal)
  watch(isNew, (nowNew) => {
    if (nowNew) reveal()
  })

  return { isNew }
}
