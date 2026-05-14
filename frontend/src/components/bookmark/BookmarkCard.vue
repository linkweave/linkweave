<script setup lang="ts">
import { ExternalLink, MoreHorizontal, MousePointerClick, Clock } from 'lucide-vue-next'
import {
  DropdownMenuRoot,
  DropdownMenuTrigger,
  DropdownMenuPortal,
  DropdownMenuContent,
  DropdownMenuItem,
} from 'radix-vue'
import type { BookmarkJson } from '@/api/generated'
import { useTagStore } from '@/stores/tag'
import { useFolderStore } from '@/stores/folder'
import { useBookmarkStore } from '@/stores/bookmark'
import { DRAG_TYPE_BOOKMARK, setDraggingBookmark } from '@/composables/useDragState'
import { useMediaQuery } from '@/composables/useMediaQuery'
import { useRelativeTime } from '@/composables/useRelativeTime'
import BookmarkFavicon from '@/components/bookmark/BookmarkFavicon.vue'

const tagStore = useTagStore()
const folderStore = useFolderStore()
const bookmarkStore = useBookmarkStore()
const isTouch = useMediaQuery('(hover: none) and (pointer: coarse)')
const { formatRelativeTime } = useRelativeTime()

const props = defineProps<{
  bookmark: BookmarkJson
  showStats?: boolean
}>()

const emit = defineEmits<{
  edit: [bookmark: BookmarkJson]
  delete: [bookmark: BookmarkJson]
  move: [bookmark: BookmarkJson]
}>()

function getTagById(tagId: string) {
  return tagStore.tags.find(t => t.id === tagId)
}

function onBookmarkDragStart(event: DragEvent) {
  if (!event.dataTransfer) return
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData(DRAG_TYPE_BOOKMARK, props.bookmark.id)
  setDraggingBookmark(true)
}

function onBookmarkDragEnd() {
  setDraggingBookmark(false)
}

function getFolderName(): string | null {
  const folderId = props.bookmark.data.folderId
  if (!folderId) return null
  const folder = folderStore.folders.find(f => f.id === folderId)
  return folder?.data.name ?? null
}
</script>

<template>
  <div
    :draggable="!isTouch"
    class="group relative rounded-lg border border-border bg-card p-4 hover:ring-2 hover:ring-primary/50 hover:border-primary/30 transition-all text-muted-foreground hover:text-accent-foreground cursor-grab active:cursor-grabbing"
    @dragstart="onBookmarkDragStart"
    @dragend="onBookmarkDragEnd"
  >
    <DropdownMenuRoot>
      <div class="flex items-start gap-3">
        <BookmarkFavicon
          :bookmark-id="props.bookmark.id"
          :url="props.bookmark.data.url"
          :size="20"
          class="mt-0.5"
        />
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <h3 class="font-medium text-foreground truncate">
              {{ props.bookmark.data.title }}
            </h3>
            <DropdownMenuTrigger as-child>
              <button
                class="ml-auto h-8 w-8 shrink-0 inline-flex items-center justify-center rounded-md transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 hover:bg-primary hover:text-primary-foreground"
                @click.stop
              >
                <MoreHorizontal class="h-4 w-4" />
              </button>
            </DropdownMenuTrigger>
          </div>

          <a
            :href="props.bookmark.data.url"
            target="_blank"
            rel="noopener noreferrer"
            class="flex items-center gap-1 text-sm text-muted-foreground hover:text-primary transition-colors mt-0.5"
            @click="bookmarkStore.trackClick(props.bookmark.id)"
          >
            <span class="truncate">{{ props.bookmark.data.url }}</span>
            <ExternalLink class="h-3 w-3 shrink-0" />
          </a>

          <p
            v-if="props.bookmark.data.description"
            class="text-sm text-muted-foreground mt-2 line-clamp-2"
          >
            {{ props.bookmark.data.description }}
          </p>

          <div v-if="props.bookmark.data.tagIds && props.bookmark.data.tagIds.size > 0 || getFolderName()" class="flex flex-wrap items-center gap-1 mt-2">
            <span v-if="getFolderName()" class="text-xs text-muted-foreground">
              in {{ getFolderName() }}
            </span>
            <span
              v-for="tagId in props.bookmark.data.tagIds"
              :key="tagId"
              class="inline-flex items-center rounded-full px-2 py-0.5 text-xs text-white"
              :style="{ backgroundColor: getTagById(tagId)?.data.color ?? '#64748b' }"
            >
              {{ getTagById(tagId)?.data.name ?? tagId.substring(0, 8) }}
            </span>
          </div>
        </div>
      </div>
      <DropdownMenuPortal>
        <DropdownMenuContent
          class="min-w-[160px] z-50 rounded-md border border-border bg-popover p-1 text-popover-foreground shadow-xl ring-1 ring-black/5 dark:ring-white/10 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95"
          align="end"
          :side-offset="4"
        >
          <DropdownMenuItem
            class="relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
            @select="emit('edit', props.bookmark)"
          >
            {{ $t('common.edit') }}
          </DropdownMenuItem>
          <DropdownMenuItem
            class="relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
            @select="emit('move', props.bookmark)"
          >
            {{ $t('bookmark.moveToFolder') }}
          </DropdownMenuItem>
          <DropdownMenuItem
            class="relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors text-destructive focus:text-destructive data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
            @select="emit('delete', props.bookmark)"
          >
            {{ $t('common.delete') }}
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenuPortal>
    </DropdownMenuRoot>

    <div
      v-if="showStats"
      class="absolute bottom-3 right-4 hidden xl:flex flex-col items-end gap-0.5 text-xs text-muted-foreground/40"
    >
      <span class="group/clicks inline-flex items-center gap-1">
        <span class="max-w-0 overflow-hidden whitespace-nowrap transition-all duration-200 group-hover/clicks:max-w-20 group-hover/clicks:opacity-100 opacity-0">{{ $t('bookmark.statClicks') }}</span>
        <MousePointerClick class="h-3 w-3 shrink-0" />
        {{ props.bookmark.clickCount }}
      </span>
      <span v-if="props.bookmark.lastClickedAt" class="group/lastClicked inline-flex items-center gap-1">
        <span class="max-w-0 overflow-hidden whitespace-nowrap transition-all duration-200 group-hover/lastClicked:max-w-28 group-hover/lastClicked:opacity-100 opacity-0">{{ $t('bookmark.statLastClicked') }}</span>
        <Clock class="h-3 w-3 shrink-0" />
        {{ formatRelativeTime(props.bookmark.lastClickedAt) }}
      </span>
    </div>
  </div>
</template>
