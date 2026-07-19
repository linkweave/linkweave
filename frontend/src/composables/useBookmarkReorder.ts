import { computed, ref, watch, type ComputedRef } from 'vue'
import type { BookmarkJson, BookmarkPositionJson } from '@/api/generated'
import { Placement, SortField } from '@/api/generated'
import {
  DRAG_TYPE_BOOKMARK,
  getDraggingBookmarkId,
  isDraggingBookmark,
} from '@/composables/useDragState'
import { useBookmarkStore } from '@/stores/bookmark'
import { useCollectionStore } from '@/stores/collection'
import { useSearchQueryStore } from '@/stores/searchQuery'
import { useDndMove } from '@/composables/useDndMove'
import { byManualBookmarkOrder } from '@/utils/bookmarkSort'

// Module-level state for the bookmark insertion indicator (UC-103). Bookmark
// rows live in different component instances (list cards, grouped-layout
// rows); sharing the active target here guarantees exactly one insertion line
// is visible at a time.

/** A drop on a row's upper/lower edge: insert relative to that anchor row. */
interface BookmarkDropTarget {
  anchorBookmarkId: string
  placement: Placement
  /** Target folder group = the anchor's folder ({@code undefined} = unfiled). */
  folderId: string | undefined
}

const activeTarget = ref<BookmarkDropTarget | null>(null)

// Clear the indicator when a drag ends anywhere (drop elsewhere or Esc).
watch(isDraggingBookmark, (dragging) => {
  if (!dragging) activeTarget.value = null
})

/**
 * Whether drag-reorder is currently possible, and if not, why: reordering
 * needs the Manual sort mode (BR-194) and a folder-scoped view (BR-197) — the
 * sidebar folder selection is an `under:` token; any other filter (search
 * text, tags, negations) shows a subset whose neighbors aren't the real
 * neighbors, so a drop there would be misleading. Drives both the drop-target
 * gating and the drag-time hint in the toolbar (A3).
 */
export type ReorderAvailability = 'available' | 'requiresManualSort' | 'requiresUnfilteredView'

export function useReorderAvailability(): ComputedRef<ReorderAvailability> {
  const collectionStore = useCollectionStore()
  const searchQueryStore = useSearchQueryStore()
  return computed(() => {
    if (collectionStore.sortField !== SortField.Manual) return 'requiresManualSort'
    const folderScoped = searchQueryStore.queryTokens.every(
      (t) => t.kind === 'operator' && t.key === 'under' && !t.neg,
    )
    return folderScoped ? 'available' : 'requiresUnfilteredView'
  })
}

/**
 * Edge-zone reorder targets on bookmark rows (UC-103): hovering the upper or
 * lower half of a row while dragging a bookmark shows an insertion line before
 * or after it — only while reordering is available (see
 * {@link useReorderAvailability}). Rows of another folder's group accept the
 * drop too and move the bookmark there at the drop position (A2/A2b).
 */
export function useBookmarkReorder() {
  const bookmarkStore = useBookmarkStore()
  const availability = useReorderAvailability()
  const { moveBookmarkWithUndo } = useDndMove()

  // True when the drop would land the bookmark exactly where it already is —
  // skipped silently so the user doesn't get a "moved" notification for a no-op.
  function isSamePosition(bookmarkId: string, target: BookmarkDropTarget): boolean {
    const bookmark = bookmarkStore.bookmarks.find(b => b.id === bookmarkId)
    if (bookmark?.data.folderId !== target.folderId) return false
    const siblings = bookmarkStore.bookmarks
      .filter(b => b.data.folderId === target.folderId)
      .sort(byManualBookmarkOrder)
    const anchorIndex = siblings.findIndex(b => b.id === target.anchorBookmarkId)
    const selfIndex = siblings.findIndex(b => b.id === bookmarkId)
    if (anchorIndex < 0 || selfIndex < 0) return false
    return target.placement === Placement.Before
      ? selfIndex === anchorIndex - 1
      : selfIndex === anchorIndex + 1
  }

  function isDropAllowed(draggingId: string, target: BookmarkDropTarget): boolean {
    if (target.anchorBookmarkId === draggingId) return false
    return !isSamePosition(draggingId, target)
  }

  function targetFor(event: DragEvent, anchor: BookmarkJson): BookmarkDropTarget {
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
    const placement =
      event.clientY < rect.top + rect.height / 2 ? Placement.Before : Placement.After
    return {
      anchorBookmarkId: anchor.id,
      placement,
      folderId: anchor.data.folderId,
    }
  }

  function onRowDragOver(event: DragEvent, anchor: BookmarkJson) {
    if (availability.value !== 'available' || !isDraggingBookmark.value) return
    const draggingId = getDraggingBookmarkId()
    if (!draggingId) return
    const target = targetFor(event, anchor)
    if (!isDropAllowed(draggingId, target)) {
      clearIfAnchored(anchor.id)
      return
    }
    event.preventDefault()
    event.stopPropagation()
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
    const current = activeTarget.value
    // Skip the no-op writes so rows don't re-render on every dragover (~60/s).
    if (
      current?.anchorBookmarkId === target.anchorBookmarkId &&
      current.placement === target.placement
    ) {
      return
    }
    activeTarget.value = target
  }

  function clearIfAnchored(bookmarkId: string) {
    if (activeTarget.value?.anchorBookmarkId === bookmarkId) activeTarget.value = null
  }

  function onRowDragLeave(event: DragEvent, anchor: BookmarkJson) {
    // Only react when leaving the element entirely, not entering a child
    const related = event.relatedTarget as Node | null
    const el = event.currentTarget as HTMLElement
    if (related && el.contains(related)) return
    clearIfAnchored(anchor.id)
  }

  async function onRowDrop(event: DragEvent, anchor: BookmarkJson) {
    if (availability.value !== 'available') return
    const types = event.dataTransfer?.types ?? []
    if (!types.includes(DRAG_TYPE_BOOKMARK)) return
    event.preventDefault()
    event.stopPropagation()
    activeTarget.value = null
    const bookmarkId = event.dataTransfer!.getData(DRAG_TYPE_BOOKMARK)
    if (!bookmarkId) return
    const target = targetFor(event, anchor)
    if (!isDropAllowed(bookmarkId, target)) return
    const position: BookmarkPositionJson = {
      anchorBookmarkId: target.anchorBookmarkId,
      placement: target.placement,
    }
    await moveBookmarkWithUndo(bookmarkId, target.folderId, position)
  }

  /** The insertion line this row should draw, if any. */
  function lineFor(bookmarkId: string): 'above' | 'below' | null {
    const target = activeTarget.value
    if (target?.anchorBookmarkId !== bookmarkId) return null
    return target.placement === Placement.Before ? 'above' : 'below'
  }

  return { onRowDragOver, onRowDragLeave, onRowDrop, lineFor }
}
