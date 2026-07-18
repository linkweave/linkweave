<script setup lang="ts">
import BookmarkFavicon from './BookmarkFavicon.vue'
import BookmarkRowMenu from './BookmarkRowMenu.vue'
import type { BookmarkJson } from '@/api/generated'
import { DRAG_TYPE_BOOKMARK, setDraggingBookmark } from '@/composables/useDragState'
import { setCompactDragImage } from '@/lib/dragImage'
import { useBookmarkStore } from '@/stores/bookmark'

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
  setDraggingBookmark(true)
}

function onDragEnd() {
  setDraggingBookmark(false)
}
</script>

<template>
  <div
    :draggable="!isTouch"
    class="group/row flex items-center gap-2 rounded-md px-1.5 py-1 hover:bg-accent/50 min-w-0 cursor-grab active:cursor-grabbing"
    @dragstart="onDragStart"
    @dragend="onDragEnd"
  >
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
