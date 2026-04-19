import { readonly, ref } from 'vue'

// Module-level reactive state shared across all drag interactions in the current page.

const _draggingFolderId = ref<string | null>(null)

export const isDraggingBookmark = ref(false)
export const isDraggingFolder = ref(false)
export const draggingFolderId = readonly(_draggingFolderId)

export function setDraggingBookmark(dragging: boolean) {
  isDraggingBookmark.value = dragging
}

export function setDraggingFolderId(id: string | null) {
  _draggingFolderId.value = id
  isDraggingFolder.value = id !== null
}

// Only used during dragover (dataTransfer.getData is unavailable then)
export function getDraggingFolderId(): string | null {
  return _draggingFolderId.value
}

export const DRAG_TYPE_BOOKMARK = 'application/x-chainlink-bookmark'
export const DRAG_TYPE_FOLDER = 'application/x-chainlink-folder'
