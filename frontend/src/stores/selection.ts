import { registerStoreReset } from '@/lib/storeReset'
import { useBookmarkStore } from '@/stores/bookmark'
import { useCollectionStore } from '@/stores/collection'
import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

/**
 * Transient multi-select state for the bookmark view (UC-074). Never
 * persisted; cleared on collection switch, route change, Esc, and successful
 * batch actions. Selection is a set of bookmark ids so it survives grid⇄list
 * and previews on/off switches.
 */
export const useSelectionStore = defineStore('selection', () => {
  const bookmarkStore = useBookmarkStore()
  const collectionStore = useCollectionStore()

  // May be true with 0 selected (toolbar button toggled).
  const selectMode = ref(false)
  const selectedIds = ref(new Set<string>())
  // Shift-click extends from this bookmark. Anchoring by id (not render
  // index) keeps the anchor on the same bookmark even when the list
  // re-sorts or re-filters between the anchor click and the shift-click.
  const anchorId = ref<string | null>(null)

  const selecting = computed(() => selectMode.value || selectedIds.value.size > 0)
  const count = computed(() => selectedIds.value.size)
  const totalCount = computed(() => bookmarkStore.filteredBookmarks.length)
  // every() rather than a size comparison: selectedIds may hold ids of
  // bookmarks that have since been filtered out of the view.
  const allSelected = computed(
    () =>
      totalCount.value > 0 &&
      bookmarkStore.filteredBookmarks.every((b) => selectedIds.value.has(b.id)),
  )

  const selectedBookmarks = computed(() =>
    bookmarkStore.bookmarks.filter((b) => selectedIds.value.has(b.id)),
  )

  watch(
    () => collectionStore.currentCollectionId,
    () => clearAndExit(),
  )
  registerStoreReset(clearAndExit)

  function isSelected(id: string): boolean {
    return selectedIds.value.has(id)
  }

  function enterMode() {
    selectMode.value = true
  }

  function clearAndExit() {
    selectMode.value = false
    selectedIds.value = new Set()
    anchorId.value = null
  }

  /** Every toggle moves the anchor to that item (also implicitly enters the mode, e.g. ⌘-click). */
  function toggle(id: string) {
    const nextSelectionState = new Set(selectedIds.value) // copy current state
    if (nextSelectionState.has(id)) {
      nextSelectionState.delete(id)
    } else {
      nextSelectionState.add(id)
    }
    selectedIds.value = nextSelectionState
    anchorId.value = id
    selectMode.value = true
  }

  /**
   * Shift-click: adds the range from the anchor to the clicked item — never
   * removes — and does NOT move the anchor. Without an anchor (or with one
   * no longer in the rendered list) it degrades to a plain toggle.
   */
  function rangeSelectTo(id: string) {
    const list = bookmarkStore.filteredBookmarks
    const from = anchorId.value === null ? -1 : list.findIndex((b) => b.id === anchorId.value)
    const to = list.findIndex((b) => b.id === id)
    if (from === -1 || to === -1) {
      toggle(id)
      return
    }
    const nextSelectionState = new Set(selectedIds.value)
    for (let i = Math.min(from, to); i <= Math.max(from, to); i++) {
      nextSelectionState.add(list[i]!.id)
    }
    selectedIds.value = nextSelectionState
  }

  function selectAll() {
    selectedIds.value = new Set(bookmarkStore.filteredBookmarks.map((b) => b.id))
    selectMode.value = true
  }

  return {
    selectMode,
    selectedIds,
    anchorId,
    selecting,
    count,
    totalCount,
    allSelected,
    selectedBookmarks,
    isSelected,
    enterMode,
    clearAndExit,
    toggle,
    rangeSelectTo,
    selectAll,
  }
})
