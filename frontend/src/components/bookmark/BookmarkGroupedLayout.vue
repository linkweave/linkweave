<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useFolderStore } from '@/stores/folder'
import type { BookmarkJson, FolderJson } from '@/api/generated'
import BookmarkCard from './BookmarkCard.vue'

const { t } = useI18n()
const folderStore = useFolderStore()

const props = defineProps<{
  bookmarks: BookmarkJson[]
}>()

const emit = defineEmits<{
  edit: [bookmark: BookmarkJson]
  delete: [bookmark: BookmarkJson]
  move: [bookmark: BookmarkJson]
}>()

function getTopmostFolder(folderId: string | null | undefined): FolderJson | null {
  if (!folderId) return null
  let current = folderStore.folders.find(f => f.id === folderId)
  while (current?.data.parentId) {
    const parent = folderStore.folders.find(f => f.id === current!.data.parentId)
    if (!parent) break
    current = parent
  }
  return current ?? null
}

interface Group {
  folder: FolderJson | null
  bookmarks: BookmarkJson[]
}

const groups = computed<Group[]>(() => {
  const map = new Map<string | null, Group>()

  for (const bookmark of props.bookmarks) {
    const topFolder = getTopmostFolder(bookmark.data.folderId)
    const key = topFolder?.id ?? null
    if (!map.has(key)) {
      map.set(key, { folder: topFolder, bookmarks: [] })
    }
    map.get(key)!.bookmarks.push(bookmark)
  }

  return [...map.values()].sort((a, b) => {
    if (a.folder === null) return 1
    if (b.folder === null) return -1
    return a.folder.data.name.localeCompare(b.folder.data.name)
  })
})
</script>

<template>
  <div class="space-y-8">
    <div v-for="group in groups" :key="group.folder?.id ?? 'unfiled'">
      <h2 class="text-sm font-semibold text-muted-foreground uppercase tracking-wider mb-3 flex items-center gap-2">
        <span>{{ group.folder?.data.name ?? t('bookmarkList.unfiled') }}</span>
        <span class="text-xs font-normal opacity-60">({{ group.bookmarks.length }})</span>
      </h2>
      <div class="space-y-3">
        <BookmarkCard
          v-for="bookmark in group.bookmarks"
          :key="bookmark.id"
          :bookmark="bookmark"
          @edit="emit('edit', bookmark)"
          @delete="emit('delete', bookmark)"
          @move="emit('move', bookmark)"
        />
      </div>
    </div>
  </div>
</template>
