<script setup lang="ts">
import { ref } from 'vue'
import { ExternalLink, MoreHorizontal, MousePointerClick, Clock, Folder } from 'lucide-vue-next'
import { DropdownMenuRoot, DropdownMenuTrigger } from 'radix-vue'
import { DropdownMenuContentCl, DropdownMenuItemCl } from '@/components/ui'
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

let didDrag = false

function onBookmarkDragStart(event: DragEvent) {
  if (!event.dataTransfer) return
  didDrag = true
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData(DRAG_TYPE_BOOKMARK, props.bookmark.id)
  setDraggingBookmark(true)
}

function onBookmarkDragEnd() {
  setDraggingBookmark(false)
  // Reset after the synthetic click that fires when a drag ends without a drop
  setTimeout(() => { didDrag = false }, 0)
}

function onCardLinkClick(event: MouseEvent) {
  if (didDrag) {
    event.preventDefault()
    return
  }
  bookmarkStore.trackClick(props.bookmark.id)
}

function getFolderName(): string | null {
  const folderId = props.bookmark.data.folderId
  if (!folderId) return null
  const folder = folderStore.folders.find(f => f.id === folderId)
  return folder?.data.name ?? null
}

// Note: we don't render an "excluded" state here. A bookmark tagged `#draft`
// is filtered out by `-#draft`, so a card chip never reaches that state. The
// excluded style lives in `FilterPill` (the strip), where it is needed.
function tagClass(tagId: string): string {
  const tag = getTagById(tagId)
  if (!tag) return ''
  if (bookmarkStore.isTagActive(tag.data.name)) {
    return 'bg-[color-mix(in_oklab,var(--tag-color)_22%,var(--color-secondary))] border-[var(--tag-color)]'
  }
  return ''
}

function tagTitle(tagId: string): string {
  const tag = getTagById(tagId)
  const name = tag?.data.name ?? ''
  if (bookmarkStore.isTagActive(name)) return `Remove filter: #${name}`
  return `Filter by tag: #${name} (⌥/⇧+click to exclude)`
}

function onTagClick(event: MouseEvent, tagId: string) {
  event.preventDefault()
  event.stopPropagation()
  const tag = getTagById(tagId)
  if (!tag) return
  const modifier = (event.altKey || event.shiftKey) ? 'exclude' : undefined
  bookmarkStore.toggleQueryToken({ kind: 'tag', value: tag.data.name, neg: false }, modifier)
}

function onToggleFolderFilter(event: MouseEvent) {
  event.preventDefault()
  event.stopPropagation()
  const name = getFolderName()
  if (!name) return
  bookmarkStore.toggleQueryToken({ kind: 'op', key: 'folder', value: name, neg: false })
}

// Lazy mount of the radix DropdownMenu. With N cards on screen, mounting an
// open-able menu per card (Portal context, popper, listeners) is the dominant
// cost of rendering the bookmark list. Most cards never have their menu
// opened, so we render a plain trigger button until first click and only then
// swap in the full radix tree (auto-opened so the user sees the menu on that
// very first click, just one tick later).
const menuActivated = ref(false)
</script>

<template>
  <div
    :draggable="!isTouch"
    class="group relative rounded-lg border border-border bg-card p-4 hover:ring-2 hover:ring-primary/50 hover:border-primary/30 focus-within:ring-2 focus-within:ring-primary transition-[box-shadow,border-color,color] duration-150 text-muted-foreground hover:text-accent-foreground cursor-grab active:cursor-grabbing"
    @dragstart="onBookmarkDragStart"
    @dragend="onBookmarkDragEnd"
  >
    <!-- Stretched link: covers the entire card so any click on the card body opens the URL.
         Sibling (not parent) of interactive children to keep HTML valid and Cmd-click working. -->
    <a
      :href="props.bookmark.data.url"
      target="_blank"
      rel="noopener noreferrer"
      :aria-label="props.bookmark.data.title"
      draggable="false"
      class="absolute inset-0 rounded-[inherit] z-0 outline-none"
      @click="onCardLinkClick"
    />

    <div class="relative flex items-start gap-3 pointer-events-none">
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
          <!-- Lazy radix mount: see `menuActivated` in script -->
          <button
            v-if="!menuActivated"
            class="ml-auto h-8 w-8 shrink-0 inline-flex items-center justify-center rounded-md transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 hover:bg-primary hover:text-primary-foreground pointer-events-auto relative z-10"
            @click.stop="menuActivated = true"
          >
            <MoreHorizontal class="h-4 w-4" />
          </button>
          <DropdownMenuRoot v-else :default-open="true">
            <DropdownMenuTrigger as-child>
              <button
                class="ml-auto h-8 w-8 shrink-0 inline-flex items-center justify-center rounded-md transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover:opacity-100 hover:bg-primary hover:text-primary-foreground pointer-events-auto relative z-10"
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

        <div class="flex items-center gap-1 text-sm text-muted-foreground mt-0.5">
          <span class="truncate">{{ props.bookmark.data.url }}</span>
          <ExternalLink class="h-3 w-3 shrink-0" />
        </div>

        <p
          v-if="props.bookmark.data.description"
          class="text-sm text-muted-foreground mt-2 line-clamp-2"
        >
          {{ props.bookmark.data.description }}
        </p>

        <div v-if="props.bookmark.data.tagIds && props.bookmark.data.tagIds.size > 0 || getFolderName()" class="flex flex-wrap items-center gap-1 mt-2">
<!--          Folder pill-->
          <button
            v-if="getFolderName()"
            type="button"
            class="pointer-events-auto relative z-10 inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs text-muted-foreground border border-dashed border-border hover:text-foreground hover:border-foreground hover:bg-secondary transition-colors"
            :class="{ 'text-foreground border-solid border-foreground bg-secondary': bookmarkStore.isFolderActive(getFolderName()!) }"
            :title="`Filter by folder: ${getFolderName()}`"
            @click="onToggleFolderFilter"
          >
            <Folder class="h-3 w-3" />
            <span>in {{ getFolderName() }}</span>
          </button>
<!--           Tag Pills-->
          <button
            v-for="tagId in props.bookmark.data.tagIds"
            :key="tagId"
            type="button"
            class="pointer-events-auto relative z-10 inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs bg-secondary text-foreground border border-transparent hover:bg-[color-mix(in_oklab,var(--tag-color)_14%,var(--color-secondary))] hover:border-[var(--tag-color)] transition-colors"
            :class="tagClass(tagId)"
            :style="{ '--tag-color': getTagById(tagId)?.data.color ?? '#64748b' }"
            :title="tagTitle(tagId)"
            @click="onTagClick($event, tagId)"
          >
            <span class="h-2 w-2 rounded-sm" :style="{ background: getTagById(tagId)?.data.color ?? '#64748b' }" />
            {{ getTagById(tagId)?.data.name ?? tagId.substring(0, 8) }}
          </button>
        </div>
      </div>
    </div>

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
