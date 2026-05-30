<script setup lang="ts">
import type { BookmarkJson } from '@/api/generated'
import BookmarkTile from './BookmarkTile.vue'

defineProps<{
  bookmarks: BookmarkJson[]
}>()

defineEmits<{
  edit: [bookmark: BookmarkJson]
  delete: [bookmark: BookmarkJson]
  move: [bookmark: BookmarkJson]
}>()
</script>

<template>
  <div class="grid gap-4 grid-cols-[repeat(auto-fill,minmax(180px,1fr))]">
    <BookmarkTile
      v-for="bookmark in bookmarks"
      :key="bookmark.id"
      :bookmark="bookmark"
      @edit="$emit('edit', $event)"
      @delete="$emit('delete', $event)"
      @move="$emit('move', $event)"
    />
  </div>
</template>
