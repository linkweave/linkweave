<script setup lang="ts">
import { FolderBreadcrumbCl } from '@/components/folder'
import BookmarkLayoutToggle from './BookmarkLayoutToggle.vue'
</script>

<template>
  <div
    class="sticky top-0 z-30 flex items-center gap-3 h-11 px-4 sm:px-6
           border-b border-border bg-background/80 backdrop-blur sm:bg-background
           before:absolute before:-top-px before:inset-x-0 before:h-px before:bg-background sm:before:hidden"
    data-testid="bookmark-list-toolbar"
  >
    <div class="flex-1 min-w-0 overflow-hidden">
      <FolderBreadcrumbCl />
    </div>

    <div class="flex items-center gap-1 shrink-0">
      <BookmarkLayoutToggle />
      <slot name="sort" />
      <slot name="extras" />
    </div>
  </div>
</template>
