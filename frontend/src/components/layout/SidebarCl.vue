<script setup lang="ts">
import { CreateFolderDialog, FolderTree } from '@/components/folder'
import TagList from '@/components/tag/TagList.vue'
import { ButtonCl } from '@/components/ui'
import BuildversionCl from '@/components/ui/BuildversionCl.vue'
import { useDndMove } from '@/composables/useDndMove'
import {
  DRAG_TYPE_BOOKMARK,
  DRAG_TYPE_FOLDER,
  getDraggingFolderId,
  isDraggingBookmark,
  isDraggingFolder,
} from '@/composables/useDragState'
import { useShowPropertiesSidebar } from '@/composables/usePropertyDisplayPrefs'
import { parsePropertyValue } from '@/lib/searchQueryProperty'
import { useBookmarkStore } from '@/stores/bookmark'
import { useCollectionStore } from '@/stores/collection'
import { useFolderStore } from '@/stores/folder'
import { useOfflineStore } from '@/stores/offline'
import { usePropertyStore } from '@/stores/property'
import { Box, Folder, Plus, Tag } from 'lucide-vue-next'
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

interface Props {
  class?: string
}

const props = defineProps<Props>()

const { t } = useI18n()
const collectionStore = useCollectionStore()
const folderStore = useFolderStore()
const offline = useOfflineStore()
const propertyStore = usePropertyStore()
const bookmarkStore = useBookmarkStore()
const showPropertiesSidebar = useShowPropertiesSidebar()
const { moveBookmarkWithUndo, moveFolderWithUndo } = useDndMove()

const collectionId = computed(() => collectionStore.currentCollectionId ?? '')
const showCreateFolder = ref(false)
const subfolderParentId = ref<string | undefined>(undefined)
const allBookmarksDragOver = ref(false)

watch(showCreateFolder, (open) => {
  if (!open) {
    subfolderParentId.value = undefined
  }
})

function handleCreateSubfolder(parentId: string) {
  subfolderParentId.value = parentId
  showCreateFolder.value = true
}

// ── Property rows ───────────────────────────────────────────────────────────
// Clicking a property row toggles its presence in the query:
//   - If no `property:<name>…` token exists yet → append `property:<name>` (a
//     bare-key existence filter) and focus the search input with the cursor at
//     the end so the user can immediately refine to a value by typing `=…`.
//   - If one (or more) already exist → remove all of them. We match by key,
//     not by full payload, so a row click also clears `property:status>3`
//     etc. — the row represents "this property dimension," not a specific
//     value, so a second click on the same dimension means "let go."

function isPropertyKeyInQuery(name: string): boolean {
  const lower = name.toLowerCase()
  return bookmarkStore.queryTokens.some((t) => {
    if (t.kind !== 'operator' || t.key !== 'property' || t.neg) return false
    const parsed = parsePropertyValue(t.value)
    return parsed?.key === lower
  })
}

function onPropertyClick(name: string) {
  const lower = name.toLowerCase()
  if (isPropertyKeyInQuery(name)) {
    bookmarkStore.removeTokensWhere((t) => {
      if (t.kind !== 'operator' || t.key !== 'property' || t.neg) return false
      return parsePropertyValue(t.value)?.key === lower
    })
    return
  }
  const current = bookmarkStore.searchQuery.trim()
  const token = `property:${name}`
  bookmarkStore.setSearchQuery(current ? `${current} ${token}` : token)
  nextTick(() => {
    const input = document.querySelector<HTMLInputElement>('[data-search-input]')
    if (input) {
      input.focus()
      const end = input.value.length
      input.setSelectionRange(end, end)
    }
  })
}

// ── "All Bookmarks" drop target ─────────────────────────────────────────────
// Accepts bookmark drops (→ unfiled) and folder drops (→ root level).

function onAllBookmarksDragOver(event: DragEvent) {
  const types = event.dataTransfer?.types ?? []
  if (types.includes(DRAG_TYPE_BOOKMARK)) {
    event.preventDefault()
    event.stopPropagation()
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
    allBookmarksDragOver.value = true
    return
  }
  if (types.includes(DRAG_TYPE_FOLDER)) {
    const draggingId = getDraggingFolderId()
    if (!draggingId) return
    const dragging = folderStore.folders.find((f) => f.id === draggingId)
    if (!dragging?.data.parentId) return // already at root, no-op
    event.preventDefault()
    event.stopPropagation()
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
    allBookmarksDragOver.value = true
  }
}

function onAllBookmarksDragLeave(event: DragEvent) {
  const related = event.relatedTarget as Node | null
  const el = event.currentTarget as HTMLElement
  if (related && el.contains(related)) return
  allBookmarksDragOver.value = false
}

async function onAllBookmarksDrop(event: DragEvent) {
  event.preventDefault()
  allBookmarksDragOver.value = false

  const types = event.dataTransfer?.types ?? []

  if (types.includes(DRAG_TYPE_BOOKMARK)) {
    const bookmarkId = event.dataTransfer!.getData(DRAG_TYPE_BOOKMARK)
    if (bookmarkId) await moveBookmarkWithUndo(bookmarkId, undefined)
    return
  }

  if (types.includes(DRAG_TYPE_FOLDER)) {
    const fid = event.dataTransfer!.getData(DRAG_TYPE_FOLDER)
    if (!fid) return
    if (!folderStore.folders.find((f) => f.id === fid)?.data.parentId) return // already root
    await moveFolderWithUndo(fid, undefined)
  }
}
</script>

<template>
  <div :class="['flex flex-col h-full', props.class]">
    <!-- Folders Section -->
    <div class="flex-1 min-h-0 flex flex-col">
      <div class="overflow-y-auto p-2">
        <div
          class="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm cursor-pointer transition-colors mb-1"
          :class="[
            folderStore.selectedFolderId === null
              ? 'bg-accent text-accent-foreground'
              : 'hover:bg-accent hover:text-accent-foreground text-muted-foreground',
            allBookmarksDragOver
              ? 'ring-2 ring-primary ring-inset'
              : isDraggingBookmark || isDraggingFolder
                ? 'ring-1 ring-primary/30'
                : '',
          ]"
          @click="folderStore.selectFolder(null)"
          @dragover="onAllBookmarksDragOver"
          @dragleave="onAllBookmarksDragLeave"
          @drop="onAllBookmarksDrop"
        >
          <Folder class="h-4 w-4 text-primary" />
          <span data-testid="sidebar-all-bookmarks">{{
            t('sidebar.allBookmarks', { name: collectionStore.collectionName ?? '' })
          }}</span>
        </div>

        <FolderTree class-name="mt-2" @create-subfolder="handleCreateSubfolder" />

        <ButtonCl
          v-if="collectionId"
          variant="ghost"
          size="sm"
          :disabled="offline.isOffline"
          class="w-full justify-start text-muted-foreground hover:text-foreground mt-2"
          @click="
            subfolderParentId = undefined;
            showCreateFolder = true;
          "
        >
          <Plus class="h-4 w-4 mr-2" />
          {{ t('sidebar.newFolder') }}
        </ButtonCl>

        <CreateFolderDialog
          v-if="collectionId"
          v-model:open="showCreateFolder"
          :collection-id="collectionId"
          :parent-id="subfolderParentId"
        />
      </div>
    </div>

    <!-- Properties Section — gated by user pref + non-empty definitions.
         Sits between Folders and Tags; the `mt-auto` on Tags below pushes
         this whole block to the bottom of the sidebar, just like Tags. -->
    <div
      v-if="showPropertiesSidebar && propertyStore.definitions.length > 0"
      class="mt-auto min-h-0 flex flex-col border-t border-border max-h-[35%]"
    >
      <div class="p-3 flex items-center justify-between shrink-0">
        <span class="text-sm font-medium text-muted-foreground flex items-center gap-2">
          <Box class="h-4 w-4" style="color: var(--color-property)" />
          {{ t('sidebar.properties') }}
        </span>
      </div>
      <ul
        class="px-2 pb-2 overflow-y-auto flex-1 min-h-0"
        data-testid="sidebar-properties"
      >
        <li v-for="def in propertyStore.definitions" :key="def.id">
          <button
            type="button"
            class="w-full flex items-center gap-2 rounded-md px-2 py-1 text-sm cursor-pointer transition-colors"
            :class="
              isPropertyKeyInQuery(def.data.name)
                ? 'bg-[color-mix(in_oklab,var(--color-property)_12%,var(--color-secondary))] text-foreground'
                : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
            "
            :data-testid="`sidebar-property-row-${def.data.name}`"
            :data-active="isPropertyKeyInQuery(def.data.name) ? 'true' : 'false'"
            @click="onPropertyClick(def.data.name)"
          >
            <Box class="h-3.5 w-3.5 shrink-0" style="color: var(--color-property)" />
            <span class="font-mono truncate flex-1 text-left">{{ def.data.name }}</span>
            <span class="text-[10px] uppercase text-muted-foreground/60">{{ def.data.type }}</span>
          </button>
        </li>
      </ul>
    </div>

    <!-- Tags Section -->
    <div
      :class="[
        showPropertiesSidebar && propertyStore.definitions.length > 0 ? '' : 'mt-auto',
        'min-h-0 flex flex-col border-t border-border max-h-[50%]',
      ]"
    >
      <div class="p-3 flex items-center justify-between shrink-0">
        <span class="text-sm font-medium text-muted-foreground flex items-center gap-2">
          <Tag class="h-4 w-4" />
          {{ t('sidebar.tags') }}
        </span>
      </div>
      <TagList
        class-name="px-2 pb-2 overflow-y-auto flex-1 min-h-0"
        :collection-id="collectionId"
      />
    </div>
    <div class="border-t border-border shrink-0">
      <div class="p-3 flex items-center justify-between">
        <BuildversionCl />
      </div>
    </div>
  </div>
</template>
