<script setup lang="ts">
import { computed, ref, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { Search, X } from 'lucide-vue-next'
import ButtonCl from './ButtonCl.vue'
import SearchBar from './SearchBar.vue'
import { useBookmarkStore } from '@/stores/bookmark'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const open = ref(false)
const overlayPanelRef = ref<HTMLElement | null>(null)
const hasQuery = computed(() => bookmarkStore.searchQuery.trim().length > 0)

watch(open, (val) => {
  if (val) {
    nextTick(() => overlayPanelRef.value?.querySelector<HTMLInputElement>('[data-search-input]')?.focus())
  }
})

function onKey(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
    if (window.innerWidth < 640) {
      e.preventDefault()
      open.value = true
    }
  } else if (e.key === 'Escape' && open.value) {
    open.value = false
  }
}

function onFocusSearch() {
  if (window.innerWidth < 640) open.value = true
}

onMounted(() => {
  window.addEventListener('keydown', onKey)
  window.addEventListener('chainlink:focus-search', onFocusSearch)
})
onUnmounted(() => {
  window.removeEventListener('keydown', onKey)
  window.removeEventListener('chainlink:focus-search', onFocusSearch)
})
</script>

<template>
  <ButtonCl
    variant="ghost"
    size="icon"
    class="sm:hidden relative"
    :class="hasQuery ? 'bg-primary/10 border border-primary/30 text-primary hover:bg-primary/15' : ''"
    :aria-label="t('search.placeholder')"
    data-testid="mobile-search-trigger"
    @click="open = true"
  >
    <Search class="h-5 w-5" />
    <span
      v-if="hasQuery"
      class="absolute top-1.5 right-1.5 h-2 w-2 rounded-full bg-primary ring-2 ring-card"
      aria-hidden="true"
    />
  </ButtonCl>

  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-[70] sm:hidden">
      <div class="absolute inset-0 bg-background/40" data-testid="mobile-search-backdrop" @click="open = false" />
      <div ref="overlayPanelRef" class="relative bg-card border-b border-border p-3 flex items-center gap-2">
        <SearchBar
          v-model="bookmarkStore.searchQuery"
          :placeholder="t('search.placeholder')"
          class="flex-1"
        />
        <ButtonCl variant="ghost" size="icon" :aria-label="t('common.cancel')" @click="open = false">
          <X class="h-5 w-5" />
        </ButtonCl>
      </div>
    </div>
  </Teleport>
</template>
