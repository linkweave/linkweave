import { readonly, ref } from 'vue'

// Module-level reactive state shared across all drag interactions in the current page.

const _draggingFolderId = ref<string | null>(null)
const _draggingBookmarkId = ref<string | null>(null)

export const isDraggingBookmark = ref(false)
export const isDraggingFolder = ref(false)
export const draggingFolderId = readonly(_draggingFolderId)

export function setDraggingBookmarkId(id: string | null) {
  _draggingBookmarkId.value = id
  isDraggingBookmark.value = id !== null
}

// Only used during dragover (dataTransfer.getData is unavailable then)
export function getDraggingBookmarkId(): string | null {
  return _draggingBookmarkId.value
}

export function setDraggingFolderId(id: string | null) {
  _draggingFolderId.value = id
  isDraggingFolder.value = id !== null
}

// Only used during dragover (dataTransfer.getData is unavailable then)
export function getDraggingFolderId(): string | null {
  return _draggingFolderId.value
}

export const DRAG_TYPE_BOOKMARK = 'application/x-linkweave-bookmark'
export const DRAG_TYPE_FOLDER = 'application/x-linkweave-folder'
