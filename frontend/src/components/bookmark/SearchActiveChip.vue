<script setup lang="ts">
import { computed } from 'vue'
import { Search, X } from 'lucide-vue-next'
import { ButtonCl } from '@/components/ui'
import { useBookmarkStore } from '@/stores/bookmark'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const hasQuery = computed(() => bookmarkStore.searchQuery.trim().length > 0)
const resultCount = computed(() => bookmarkStore.filteredBookmarks.length)

function reopen() {
  window.dispatchEvent(new CustomEvent('chainlink:focus-search'))
}

function clear() {
  bookmarkStore.searchQuery = ''
}
</script>

<template>
  <div
    v-if="hasQuery"
    :class="[
      'flex items-center gap-2 py-2 text-sm border border-primary/30',
      // mobile: sticky flush with header (-top-6 offsets main's pt-6 so chip lands at header edge),
      // full-bleed (-mx-6 px-6 cancels main's px-6), blur over scrolling cards
      'sticky -top-3 z-30 -mx-3 px-3 rounded-none border-x-0 bg-primary/10 backdrop-blur supports-[backdrop-filter]:bg-primary/10',
      'sm:-top-6 sm:-mx-6 sm:px-6',
      // desktop: static, contained, normal corners
      'md:static md:top-auto md:mx-0 md:px-3 md:rounded-md md:border-x md:bg-primary/5 md:backdrop-blur-none',
    ]"
    role="status"
    aria-live="polite"
  >
    <Search class="h-4 w-4 text-primary shrink-0" />
    <button
      type="button"
      data-testid="search-chip-reopen"
      class="flex-1 min-w-0 text-left truncate text-foreground hover:text-primary transition-colors"
      @click="reopen"
    >
      <span class="font-medium">"{{ bookmarkStore.searchQuery }}"</span>
      <span class="text-muted-foreground"> · {{ t('search.resultCount', { n: resultCount }) }}</span>
    </button>
    <ButtonCl variant="ghost" size="icon" class="h-7 w-7 shrink-0" :aria-label="t('search.clear')" @click="clear">
      <X class="h-4 w-4" />
    </ButtonCl>
  </div>
</template>
