<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { Search, X } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import { useBookmarkStore } from '@/stores/bookmark'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const inputRef = ref<HTMLInputElement | null>(null)

function handleShortcut(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault()
    inputRef.value?.focus()
  } else if (e.key === '/' && !e.metaKey && !e.ctrlKey && !e.altKey) {
    const tag = (e.target as HTMLElement).tagName
    if (tag !== 'INPUT' && tag !== 'TEXTAREA' && tag !== 'SELECT') {
      e.preventDefault()
      inputRef.value?.focus()
    }
  }
}

function clear() {
  bookmarkStore.clearSearchQuery()
  inputRef.value?.focus()
}

onMounted(() => globalThis.addEventListener('keydown', handleShortcut))
onUnmounted(() => globalThis.removeEventListener('keydown', handleShortcut))
</script>

<template>
  <div class="relative">
    <Search
      class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none"
    />
    <input
      ref="inputRef"
      type="text"
      :value="bookmarkStore.searchQuery"
      :placeholder="t('search.placeholder')"
      class="flex h-10 w-full rounded-md border border-border bg-secondary pl-10 pr-8 py-1 text-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
      @input="bookmarkStore.setSearchQuery(($event.target as HTMLInputElement).value)"
    />
    <button
      v-if="bookmarkStore.searchQuery"
      class="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
      @click="clear"
    >
      <X class="h-4 w-4" />
    </button>
  </div>
</template>
