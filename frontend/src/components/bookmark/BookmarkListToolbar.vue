<script setup lang="ts">
import { FolderBreadcrumbCl } from '@/components/folder'
import { computed } from 'vue'
import { useEffectiveLayout } from '@/composables/useEffectiveLayout'
import { useCollectionStore } from '@/stores/collection'
import BookmarkLayoutToggle from './BookmarkLayoutToggle.vue'
import BookmarkPreviewsToggle from './BookmarkPreviewsToggle.vue'
import BookmarkSelectToggle from './BookmarkSelectToggle.vue'

const collectionStore = useCollectionStore()
const effectiveLayout = useEffectiveLayout()

// Only surface the toolbar toggle in collections where previews are even
// possible. If the per-collection setting forbids captures this control is useless
const previewsAvailable = computed(
  () => collectionStore.collectionInfo?.screenshotEnabled ?? false,
)

// Batch select (UC-074) is specified for the grid and list layouts only;
// the grouped layout has its own row component without selection support.
const selectAvailable = computed(() => effectiveLayout.value !== 'grouped')
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
      <BookmarkSelectToggle v-if="selectAvailable" />
      <BookmarkPreviewsToggle v-if="previewsAvailable" />
      <BookmarkLayoutToggle />
      <slot name="sort" />
      <slot name="extras" />
    </div>
  </div>
</template>
