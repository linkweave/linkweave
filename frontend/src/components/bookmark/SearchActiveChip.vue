<script setup lang="ts">
import { computed, ref } from 'vue'
import { Filter, Search } from '@lucide/vue'
import { useBookmarkStore } from '@/stores/bookmark'
import { useI18n } from 'vue-i18n'
import FilterPill from './FilterPill.vue'
import SavedSearchPopover from '@/components/savedsearch/SavedSearchPopover.vue'
import SavedSearchPill from '@/components/savedsearch/SavedSearchPill.vue'
import { useSavedSearchStore } from '@/stores/savedSearch'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const savedSearchStore = useSavedSearchStore()

const tokens = computed(() => bookmarkStore.queryTokens)
const resultCount = computed(() => bookmarkStore.filteredBookmarks.length)
const hasActiveSavedSearch = computed(() => savedSearchStore.activeSavedSearchId !== null)

const saveOpen = ref(false)

function clear() {
  bookmarkStore.searchQuery = ''
}
</script>

<template>
  <output
    v-if="tokens.length > 0"
    data-testid="filter-strip"
    :class="[
      'flex flex-wrap items-center gap-2 py-2 text-sm border border-primary/30',
      'sticky top-11 z-20 -mx-3 px-3 rounded-none border-x-0 bg-primary/10 backdrop-blur supports-[backdrop-filter]:bg-primary/10',
      'sm:top-11 sm:-mx-6 sm:px-6',
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
    <SavedSearchPill v-if="hasActiveSavedSearch" />
    <SavedSearchPopover v-else v-model:open="saveOpen" mode="create">
      <button
        type="button"
        data-testid="saved-search-save-trigger"
        :aria-label="t('savedSearch.saveTooltip')"
        :title="t('savedSearch.saveTooltip')"
        class="inline-flex h-6 w-6 items-center justify-center rounded-sm border transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring shrink-0"
        :style="{
          borderColor: 'var(--color-ss)',
          backgroundColor: 'color-mix(in oklab, var(--color-ss) 6%, transparent)',
          color: 'var(--color-ss)',
        }"
      >
        <Filter class="h-3.5 w-3.5" />
      </button>
    </SavedSearchPopover>
    <button
      type="button"
      data-testid="filter-clear-all"
      class="text-xs text-muted-foreground hover:text-foreground px-1.5 py-0.5 rounded transition-colors"
      :aria-label="t('search.clear')"
      @click="clear"
    >
      {{ t('search.clearAll') }}
    </button>
  </output>
</template>
