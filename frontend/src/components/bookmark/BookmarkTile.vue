<script setup lang="ts">
import type { BookmarkJson } from '@/api/generated'
import BookmarkFavicon from '@/components/bookmark/BookmarkFavicon.vue'
import { DropdownMenuContentCl, DropdownMenuItemCl } from '@/components/ui'
import { DRAG_TYPE_BOOKMARK, setDraggingBookmark } from '@/composables/useDragState'
import { useMediaQuery } from '@/composables/useMediaQuery'
import { useBookmarkStore } from '@/stores/bookmark'
import { useCollectionStore } from '@/stores/collection'
import { MoreHorizontal } from '@lucide/vue'
import { DropdownMenuRoot, DropdownMenuTrigger } from 'radix-vue'
import { computed, ref } from 'vue'

const collectionStore = useCollectionStore()
const bookmarkStore = useBookmarkStore()
const isTouch = useMediaQuery('(hover: none) and (pointer: coarse)')

const props = defineProps<{
  bookmark: BookmarkJson
}>()

const emit = defineEmits<{
  edit: [bookmark: BookmarkJson]
  delete: [bookmark: BookmarkJson]
  move: [bookmark: BookmarkJson]
}>()

const failed = ref(false)
const menuActivated = ref(false)

const screenshotSrc = computed<string | null>(() => {
  const cid = collectionStore.currentCollectionId
  if (!cid) return null
  return `/api/collections/${encodeURIComponent(cid)}/bookmarks/${encodeURIComponent(props.bookmark.id)}/screenshot`
})

let didDrag = false

function onDragStart(event: DragEvent) {
  if (!event.dataTransfer) return
  didDrag = true
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData(DRAG_TYPE_BOOKMARK, props.bookmark.id)
  setDraggingBookmark(true)
}

function onDragEnd() {
  setDraggingBookmark(false)
  setTimeout(() => { didDrag = false }, 0)
}

function onLinkClick(event: MouseEvent) {
  if (didDrag) {
    event.preventDefault()
    return
  }
  bookmarkStore.trackClick(props.bookmark.id)
}
</script>

<template>
  <div
    :draggable="!isTouch"
    :data-testid="`bookmark-tile-${props.bookmark.id}`"
    :data-bookmark-title="props.bookmark.data.title"
    class="group relative rounded-lg border border-border bg-card overflow-hidden hover:ring-2 hover:ring-primary/50 hover:border-primary/30 focus-within:ring-2 focus-within:ring-primary transition-[box-shadow,border-color] duration-150 cursor-grab active:cursor-grabbing"
    @dragstart="onDragStart"
    @dragend="onDragEnd"
  >
    <a
      :href="props.bookmark.data.url"
      target="_blank"
      rel="noopener noreferrer"
      :aria-label="props.bookmark.data.title"
      draggable="false"
      class="absolute inset-0 rounded-[inherit] z-0 outline-none"
      @click="onLinkClick"
    />

    <div class="relative aspect-[16/10] bg-secondary/40 overflow-hidden pointer-events-none">
      <img
        v-if="screenshotSrc && !failed"
        :src="screenshotSrc"
        alt=""
        loading="lazy"
        decoding="async"
        class="absolute inset-0 w-full h-full object-cover object-top"
        @error="failed = true"
      />
      <div
        v-else
        class="absolute inset-0 flex items-center justify-center bg-gradient-to-br from-primary/10 via-secondary/40 to-muted/60"
      >
        <BookmarkFavicon
          :bookmark-id="props.bookmark.id"
          :url="props.bookmark.data.url"
          :size="36"
        />
      </div>
    </div>

    <div class="relative flex items-start gap-2 p-3">
      <BookmarkFavicon
        :bookmark-id="props.bookmark.id"
        :url="props.bookmark.data.url"
        :size="16"
        class="mt-0.5 pointer-events-none"
      />
      <div class="flex-1 min-w-0 pointer-events-none">
        <h3 class="font-medium text-foreground text-sm truncate">
          {{ props.bookmark.data.title }}
        </h3>
        <p class="text-xs text-muted-foreground truncate mt-0.5">
          {{ props.bookmark.data.url }}
        </p>
      </div>
      <button
        v-if="!menuActivated"
        class="ml-auto h-7 w-7 shrink-0 inline-flex items-center justify-center rounded-md transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 hover:bg-primary hover:text-primary-foreground pointer-events-auto relative z-10"
        @click.stop="menuActivated = true"
      >
        <MoreHorizontal class="h-4 w-4" />
      </button>
      <DropdownMenuRoot v-else :default-open="true">
        <DropdownMenuTrigger as-child>
          <button
            class="ml-auto h-7 w-7 shrink-0 inline-flex items-center justify-center rounded-md transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 hover:bg-primary hover:text-primary-foreground pointer-events-auto relative z-10"
            @click.stop
          >
            <MoreHorizontal class="h-4 w-4" />
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContentCl class="min-w-[160px] z-50">
          <DropdownMenuItemCl @select="emit('edit', props.bookmark)">
            {{ $t('common.edit') }}
          </DropdownMenuItemCl>
          <DropdownMenuItemCl @select="emit('move', props.bookmark)">
            {{ $t('bookmark.moveToFolder') }}
          </DropdownMenuItemCl>
          <DropdownMenuItemCl variant="destructive" @select="emit('delete', props.bookmark)">
            {{ $t('common.delete') }}
          </DropdownMenuItemCl>
        </DropdownMenuContentCl>
      </DropdownMenuRoot>
    </div>
  </div>
</template>
