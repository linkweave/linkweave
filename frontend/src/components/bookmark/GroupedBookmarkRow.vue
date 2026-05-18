<script setup lang="ts">
import { ref } from 'vue'
import { MoreHorizontal } from '@lucide/vue'
import { DropdownMenuRoot, DropdownMenuTrigger } from 'radix-vue'
import { DropdownMenuContentCl, DropdownMenuItemCl } from '@/components/ui'
import BookmarkFavicon from './BookmarkFavicon.vue'
import type { BookmarkJson } from '@/api/generated'
import { DRAG_TYPE_BOOKMARK, setDraggingBookmark } from '@/composables/useDragState'
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

// Lazy radix mount, same pattern as BookmarkCard: a plain trigger button
// until first click, then the full DropdownMenuRoot with default-open. With
// many rows in the grouped layout, this is where most of the mount cost lives.
const menuActivated = ref(false)

function onDragStart(event: DragEvent) {
  if (!event.dataTransfer) return
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData(DRAG_TYPE_BOOKMARK, props.bookmark.id)
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

    <button
      v-if="!menuActivated"
      class="shrink-0 h-8 w-8 inline-flex items-center justify-center rounded transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover/row:opacity-100 hover:bg-primary hover:text-primary-foreground"
      @click.stop="menuActivated = true"
    >
      <MoreHorizontal class="h-3.5 w-3.5" />
    </button>
    <DropdownMenuRoot v-else :default-open="true">
      <DropdownMenuTrigger as-child>
        <button
          class="shrink-0 h-8 w-8 inline-flex items-center justify-center rounded transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover/row:opacity-100 hover:bg-primary hover:text-primary-foreground"
          @click.stop
        >
          <MoreHorizontal class="h-3.5 w-3.5" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContentCl class="min-w-[160px] z-50">
        <DropdownMenuItemCl @select="emit('edit', bookmark)">
          {{ $t('common.edit') }}
        </DropdownMenuItemCl>
        <DropdownMenuItemCl @select="emit('move', bookmark)">
          {{ $t('bookmark.moveToFolder') }}
        </DropdownMenuItemCl>
        <DropdownMenuItemCl variant="destructive" @select="emit('delete', bookmark)">
          {{ $t('common.delete') }}
        </DropdownMenuItemCl>
      </DropdownMenuContentCl>
    </DropdownMenuRoot>
  </div>
</template>
