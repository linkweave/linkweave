<script setup lang="ts">
import BookmarkFavicon from './BookmarkFavicon.vue'
import BookmarkRowMenu from './BookmarkRowMenu.vue'
import type { BookmarkJson } from '@/api/generated'
import { DRAG_TYPE_BOOKMARK, setDraggingBookmarkId } from '@/composables/useDragState'
import { setCompactDragImage } from '@/lib/dragImage'
import { useBookmarkReorder } from '@/composables/useBookmarkReorder'
import { useBookmarkStore } from '@/stores/bookmark'
import { computed } from 'vue'

const props = defineProps<{
  bookmark: BookmarkJson
  isTouch: boolean
}>()

const emit = defineEmits<{
  edit: [bookmark: BookmarkJson]
  delete: [bookmark: BookmarkJson]
  move: [bookmark: BookmarkJson]
}>()

const bookmarkStore = useBookmarkStore()

function onDragStart(event: DragEvent) {
  if (!event.dataTransfer) return
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData(DRAG_TYPE_BOOKMARK, props.bookmark.id)
  setCompactDragImage(event, props.bookmark.data.title, 'bookmark')
  setDraggingBookmarkId(props.bookmark.id)
}

function onDragEnd() {
  setDraggingBookmarkId(null)
}

// Reorder targets between compact rows (UC-103 A2b); the composable gates on
// Manual sort mode and an active bookmark drag. Drops on another section's
// rows move the bookmark into that folder at the drop position.
const reorder = useBookmarkReorder()
const dropLine = computed(() => reorder.lineFor(props.bookmark.id))
</script>

<template>
  <div
    :draggable="!isTouch"
    :data-testid="`grouped-row-${bookmark.id}`"
    class="group/row relative flex items-center gap-2 rounded-md px-1.5 py-1 hover:bg-accent/50 min-w-0 cursor-grab active:cursor-grabbing"
    @dragstart="onDragStart"
    @dragend="onDragEnd"
    @dragenter="reorder.onRowDragOver($event, bookmark)"
    @dragover="reorder.onRowDragOver($event, bookmark)"
    @dragleave="reorder.onRowDragLeave($event, bookmark)"
    @drop="reorder.onRowDrop($event, bookmark)"
  >
    <!-- Insertion line for a Manual-mode reorder drop (UC-103). Compact rows
         sit flush, so the line centers on the row boundary itself. -->
    <div
      v-if="dropLine"
      class="bm-drop-line"
      :class="`bm-drop-line-${dropLine}`"
      style="--bm-drop-offset: -1.25px"
      aria-hidden="true"
    />
    <BookmarkFavicon :bookmark-id="bookmark.id" :url="bookmark.data.url" :size="16" />
    <a
      :href="bookmark.data.url"
      target="_blank"
      rel="noopener noreferrer"
      class="flex-1 text-sm truncate text-foreground hover:text-primary transition-colors min-w-0"
      @click="bookmarkStore.trackClick(bookmark.id)"
    >
      {{ bookmark.data.title }}
    </a>

    <BookmarkRowMenu
      :bookmark="bookmark"
      trigger-class="shrink-0 h-10 w-10 inline-flex items-center justify-center rounded transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover/row:opacity-100 hover:bg-primary hover:text-primary-foreground"
      icon-class="h-3.5 w-3.5"
      @edit="emit('edit', $event)"
      @move="emit('move', $event)"
      @delete="emit('delete', $event)"
    />
  </div>
</template>
