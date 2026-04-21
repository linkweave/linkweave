<script setup lang="ts">
import { useExtensionStore } from '../stores/extension'
import BookmarkItem from '../components/BookmarkItem.vue'
import TagSelect from '../components/TagSelect.vue'
import FolderSelect from '../components/FolderSelect.vue'
import { Search, X } from 'lucide-vue-next'

const store = useExtensionStore()

function extractHostname(url: string) {
  try { return new URL(url).hostname } catch { return '' }
}

function folderModelValue(id: string | null): string | undefined {
  return id ?? undefined
}
function onFolderChange(val: string | undefined) {
  store.selectedFolderId = val ?? null
}
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- Search bar -->
    <div class="px-3 pt-3 pb-2">
      <div class="relative">
        <Search class="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground pointer-events-none" />
        <input
          :value="store.searchQuery"
          type="text"
          placeholder="Search bookmarks…"
          class="flex h-8 w-full rounded-md border border-input bg-secondary pl-8 pr-7 text-xs placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          @input="store.searchQuery = ($event.target as HTMLInputElement).value"
        />
        <button
          v-if="store.searchQuery"
          class="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
          @click="store.searchQuery = ''"
        >
          <X class="h-3.5 w-3.5" />
        </button>
      </div>
    </div>

    <!-- Tag + folder filters -->
    <div v-if="store.tags.length > 0 || store.folders.length > 0" class="px-3 pb-2 flex gap-2">
      <div v-if="store.tags.length > 0" class="flex-1 min-w-0">
        <TagSelect
          :tags="store.tags"
          :selected="store.selectedTagIds"
          direction="down"
          @toggle="store.toggleTag"
          @clear="store.selectedTagIds = new Set()"
        />
      </div>
      <div v-if="store.folders.length > 0" :class="store.tags.length > 0 ? 'w-36 shrink-0' : 'flex-1'">
        <FolderSelect
          :folders="store.folders"
          :model-value="folderModelValue(store.selectedFolderId)"
          direction="down"
          placeholder="All folders"
          @update:model-value="onFolderChange"
        />
      </div>
    </div>

    <!-- Clear filters -->
    <div
      v-if="store.selectedTagIds.size > 0 || store.selectedFolderId || store.searchQuery"
      class="px-3 pb-1.5"
    >
      <button
        class="text-[10px] text-muted-foreground hover:text-foreground underline"
        @click="store.clearFilters()"
      >
        Clear all filters
      </button>
    </div>

    <!-- Bookmark list -->
    <div class="flex-1 overflow-y-auto px-2 pt-1 pb-2 space-y-1">
      <p
        v-if="store.loading || store.collectionLoading"
        class="text-center text-xs text-muted-foreground py-8"
      >
        Loading…
      </p>

      <p
        v-else-if="store.error"
        class="text-center text-xs text-destructive py-8"
      >
        {{ store.error }}
      </p>

      <p
        v-else-if="store.filteredBookmarks.length === 0"
        class="text-center text-xs text-muted-foreground py-8"
      >
        {{ store.searchQuery || store.selectedTagIds.size || store.selectedFolderId
          ? 'No bookmarks match your filters.'
          : 'No bookmarks yet.' }}
      </p>

      <div
        v-for="bookmark in store.filteredBookmarks"
        :key="bookmark.id"
        class="rounded-md"
        :class="store.alreadySavedBookmark?.id === bookmark.id
          ? 'ring-1 ring-primary/50 bg-primary/5'
          : ''"
      >
        <BookmarkItem
          :bookmark="bookmark"
          :hostname="extractHostname(bookmark.data.url)"
          :tags="store.tags.filter(t => bookmark.data.tagIds?.has(t.id))"
          :folder-name="store.folders.find(f => f.id === bookmark.data.folderId)?.data.name"
          @click="store.trackClick(bookmark.id)"
        />
      </div>
    </div>
  </div>
</template>
