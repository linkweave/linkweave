<script setup lang="ts">
import { computed } from 'vue'
import { Search } from 'lucide-vue-next'
import { useBookmarkStore } from '@/stores/bookmark'
import { useI18n } from 'vue-i18n'
import FilterPill from './FilterPill.vue'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()

const tokens = computed(() => bookmarkStore.queryTokens)
const resultCount = computed(() => bookmarkStore.filteredBookmarks.length)

function clear() {
  bookmarkStore.searchQuery = ''
}
</script>

<template>
  <output
    v-if="tokens.length > 0"
    :class="[
      'flex flex-wrap items-center gap-2 py-2 text-sm border border-primary/30',
      // mobile/tablet: sticky just below the toolbar (h-11), full-bleed, blur over scrolling cards
      'sticky top-11 z-20 -mx-3 px-3 rounded-none border-x-0 bg-primary/10 backdrop-blur supports-[backdrop-filter]:bg-primary/10',
      'sm:top-11 sm:-mx-6 sm:px-6',
      // desktop: static, contained, normal corners
      'md:static md:top-auto md:mx-0 md:px-3 md:rounded-md md:border-x md:bg-primary/5 md:backdrop-blur-none',
    ]"
  >
    <span class="inline-flex items-center gap-1.5 text-primary text-xs font-medium shrink-0">
      <Search class="h-3.5 w-3.5" />
      {{ t('search.filters') }}
    </span>

    <FilterPill
      v-for="(token, i) in tokens"
      :key="i"
      :token="token"
      @remove="bookmarkStore.removeQueryTokenAt(i)"
    />

    <span class="ml-auto text-muted-foreground text-xs shrink-0">
      {{ t('search.resultCount', { n: resultCount }) }}
    </span>
    <button
      type="button"
      class="text-xs text-muted-foreground hover:text-foreground px-1.5 py-0.5 rounded transition-colors"
      :aria-label="t('search.clear')"
      @click="clear"
    >
      {{ t('search.clearAll') }}
    </button>
  </output>
</template>
