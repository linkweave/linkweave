<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFolderStore } from '@/stores/folder'
import type { BookmarkJson, FolderJson } from '@/api/generated'
import { useBookmarkStore } from '@/stores/bookmark'
import { Folder, FolderOpen, MoreHorizontal } from 'lucide-vue-next'
import { DRAG_TYPE_BOOKMARK, isDraggingBookmark, setDraggingBookmark } from '@/composables/useDragState'
import { useDndMove } from '@/composables/useDndMove'
import {
  DropdownMenuRoot,
  DropdownMenuTrigger,
  DropdownMenuPortal,
  DropdownMenuContent,
  DropdownMenuItem,
} from 'radix-vue'

const { t } = useI18n()
const folderStore = useFolderStore()
const bookmarkStore = useBookmarkStore()
const { moveBookmarkWithUndo } = useDndMove()

const props = defineProps<{
  bookmarks: BookmarkJson[]
}>()

const emit = defineEmits<{
  edit: [bookmark: BookmarkJson]
  delete: [bookmark: BookmarkJson]
  move: [bookmark: BookmarkJson]
}>()

// A section is one folder's direct bookmarks within a group card.
// isSubfolder=true means it should be visually separated from the section above it.
interface Section {
  folder: FolderJson | null
  bookmarks: BookmarkJson[]
  isSubfolder: boolean
}

interface GroupCard {
  rootFolder: FolderJson | null
  sections: Section[]
  totalBookmarks: number
}

function buildMaps() {
  // bookmarksByFolder: folderId (null = unfiled) → bookmarks placed directly in that folder
  const bookmarksByFolder = new Map<string | null, BookmarkJson[]>()
  bookmarksByFolder.set(null, [])
  for (const folder of folderStore.folders) {
    bookmarksByFolder.set(folder.id, [])
  }
  for (const bookmark of props.bookmarks) {
    const folderId = bookmark.data.folderId ?? null
    // Fall back to unfiled if folder is no longer in the store
    const key = bookmarksByFolder.has(folderId) ? folderId : null
    bookmarksByFolder.get(key)!.push(bookmark)
  }

  // foldersByParent: parentId (null = root level) → child folders
  const foldersByParent = new Map<string | null, FolderJson[]>()
  foldersByParent.set(null, [])
  for (const folder of folderStore.folders) {
    const parentId = folder.data.parentId ?? null
    if (!foldersByParent.has(parentId)) {
      foldersByParent.set(parentId, [])
    }
    foldersByParent.get(parentId)!.push(folder)
  }

  return { bookmarksByFolder, foldersByParent }
}

function buildSections(
  folder: FolderJson | null,
  bookmarksByFolder: Map<string | null, BookmarkJson[]>,
  foldersByParent: Map<string | null, FolderJson[]>,
  isSubfolder: boolean,
): Section[] {
  const folderId = folder?.id ?? null
  const direct = bookmarksByFolder.get(folderId) ?? []
  const children = (foldersByParent.get(folderId) ?? [])
    .slice()
    .sort((a, b) => a.data.name.localeCompare(b.data.name))

  const sections: Section[] = []
  if (direct.length > 0) {
    sections.push({ folder, bookmarks: direct, isSubfolder })
  }
  for (const child of children) {
    sections.push(...buildSections(child, bookmarksByFolder, foldersByParent, true))
  }
  return sections
}

const groups = computed<GroupCard[]>(() => {
  const { bookmarksByFolder, foldersByParent } = buildMaps()

  const rootFolders = (foldersByParent.get(null) ?? [])
    .slice()
    .sort((a, b) => a.data.name.localeCompare(b.data.name))

  const cards: GroupCard[] = []

  for (const folder of rootFolders) {
    const sections = buildSections(folder, bookmarksByFolder, foldersByParent, false)
    const totalBookmarks = sections.reduce((sum, s) => sum + s.bookmarks.length, 0)
    if (totalBookmarks > 0) {
      cards.push({ rootFolder: folder, sections, totalBookmarks })
    }
  }

  // Unfiled: only bookmarks with folderId === null, no folder hierarchy
  const unfiledBookmarks = bookmarksByFolder.get(null) ?? []
  if (unfiledBookmarks.length > 0) {
    cards.push({
      rootFolder: null,
      sections: [{ folder: null, bookmarks: unfiledBookmarks, isSubfolder: false }],
      totalBookmarks: unfiledBookmarks.length,
    })
  }

  return cards
})

function onBookmarkDragStart(event: DragEvent, bookmark: BookmarkJson) {
  if (!event.dataTransfer) return
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData(DRAG_TYPE_BOOKMARK, bookmark.id)
  setDraggingBookmark(true)
}

function onBookmarkDragEnd() {
  setDraggingBookmark(false)
}

// ── Card header drop targets ─────────────────────────────────────────────────

const dragOverCardId = ref<string | null>(null) // rootFolder.id or 'unfiled'

function cardKey(group: GroupCard): string {
  return group.rootFolder?.id ?? 'unfiled'
}

function onHeaderDragOver(event: DragEvent, group: GroupCard) {
  const types = event.dataTransfer?.types ?? []
  if (!types.includes(DRAG_TYPE_BOOKMARK)) return
  event.preventDefault()
  event.stopPropagation()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dragOverCardId.value = cardKey(group)
}

function onHeaderDragLeave(event: DragEvent, group: GroupCard) {
  const related = event.relatedTarget as Node | null
  const el = event.currentTarget as HTMLElement
  if (related && el.contains(related)) return
  if (dragOverCardId.value === cardKey(group)) dragOverCardId.value = null
}

async function onHeaderDrop(event: DragEvent, group: GroupCard) {
  event.preventDefault()
  dragOverCardId.value = null

  const bookmarkId = event.dataTransfer?.getData(DRAG_TYPE_BOOKMARK)
  if (bookmarkId) await moveBookmarkWithUndo(bookmarkId, group.rootFolder?.id)
}

function faviconUrl(url: string): string {
  try {
    const hostname = new URL(url).hostname
    return hostname ? `https://icons.duckduckgo.com/ip3/${hostname}.ico` : ''
  } catch {
    return ''
  }
}
</script>

<template>
  <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 items-start">
    <div
      v-for="group in groups"
      :key="group.rootFolder?.id ?? 'unfiled'"
      class="rounded-lg border border-border bg-card overflow-hidden flex flex-col"
    >
      <!-- Card header (drop target) -->
      <div
        class="px-4 py-3 border-b border-border bg-muted/30 flex items-center gap-2 shrink-0 transition-colors"
        :class="dragOverCardId === cardKey(group)
          ? 'bg-primary/15 border-primary/40 ring-2 ring-primary/40 ring-inset'
          : isDraggingBookmark ? 'bg-primary/5 border-primary/20' : ''"
        @dragover="onHeaderDragOver($event, group)"
        @dragleave="onHeaderDragLeave($event, group)"
        @drop="onHeaderDrop($event, group)"
      >
        <Folder class="h-4 w-4 text-primary shrink-0" />
        <span class="font-medium text-sm text-foreground truncate flex-1">
          {{ group.rootFolder?.data.name ?? t('bookmarkList.unfiled') }}
        </span>
        <span class="text-xs text-muted-foreground shrink-0">{{ group.totalBookmarks }}</span>
      </div>

      <!-- Sections -->
      <div class="p-2 overflow-y-auto max-h-96">
        <template
          v-for="(section, sectionIndex) in group.sections"
          :key="section.folder?.id ?? ('unfiled-' + sectionIndex)"
        >
          <!-- Subfolder heading, with divider only when preceded by another section -->
          <div
            v-if="section.isSubfolder"
            class="flex items-center gap-1.5 px-1 py-1"
            :class="sectionIndex > 0 ? 'mt-1 border-t border-border/50 pt-2' : ''"
          >
            <FolderOpen class="h-3 w-3 text-muted-foreground shrink-0" />
            <span class="text-xs text-muted-foreground font-medium truncate">{{ section.folder?.data.name }}</span>
          </div>

          <!-- Compact bookmark rows -->
          <div
            v-for="bookmark in section.bookmarks"
            :key="bookmark.id"
            draggable="true"
            class="group/row flex items-center gap-2 rounded-md px-1.5 py-1 hover:bg-accent/50 min-w-0 cursor-grab active:cursor-grabbing"
            @dragstart="onBookmarkDragStart($event, bookmark)"
            @dragend="onBookmarkDragEnd"
          >
            <img
              v-if="faviconUrl(bookmark.data.url)"
              :src="faviconUrl(bookmark.data.url)"
              alt=""
              class="w-4 h-4 shrink-0 rounded-sm"
              loading="lazy"
              @error="($event.target as HTMLImageElement).style.display = 'none'"
            />
            <a
              :href="bookmark.data.url"
              target="_blank"
              rel="noopener noreferrer"
              class="flex-1 text-sm truncate text-foreground hover:text-primary transition-colors min-w-0"
              @click="bookmarkStore.trackClick(bookmark.id)"
            >
              {{ bookmark.data.title }}
            </a>

            <DropdownMenuRoot>
              <DropdownMenuTrigger as-child>
                <button
                  class="shrink-0 h-6 w-6 inline-flex items-center justify-center rounded transition-opacity [@media(hover:hover)]:opacity-0 [@media(hover:hover)]:group-hover/row:opacity-100 hover:bg-primary hover:text-primary-foreground"
                  @click.stop
                >
                  <MoreHorizontal class="h-3.5 w-3.5" />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuPortal>
                <DropdownMenuContent
                  class="min-w-[160px] z-50 rounded-md border border-border bg-popover p-1 text-popover-foreground shadow-md data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95"
                  align="end"
                  :side-offset="4"
                >
                  <DropdownMenuItem
                    class="relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
                    @select="emit('edit', bookmark)"
                  >
                    {{ $t('common.edit') }}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    class="relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
                    @select="emit('move', bookmark)"
                  >
                    {{ $t('bookmark.moveToFolder') }}
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    class="relative flex cursor-pointer select-none items-center rounded-sm px-2 py-1.5 text-sm outline-none transition-colors text-destructive focus:text-destructive data-[disabled]:pointer-events-none data-[disabled]:opacity-50"
                    @select="emit('delete', bookmark)"
                  >
                    {{ $t('common.delete') }}
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenuPortal>
            </DropdownMenuRoot>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>
