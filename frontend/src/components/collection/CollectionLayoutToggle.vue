<script setup lang="ts">
import { useCollectionStore } from '@/stores/collection'
import { useUiStore, type BookmarkLayout } from '@/stores/ui'
import { LayoutGrid, LayoutList, Layers } from 'lucide-vue-next'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const collectionStore = useCollectionStore()
const ui = useUiStore()

const options: { value: BookmarkLayout; icon: typeof LayoutList; labelKey: string }[] = [
  { value: 'list', icon: LayoutList, labelKey: 'settings.layoutList' },
  { value: 'grid', icon: LayoutGrid, labelKey: 'settings.layoutGrid' },
  { value: 'grouped', icon: Layers, labelKey: 'settings.layoutGrouped' },
]

const current = computed<BookmarkLayout>(() => {
  const fromBackend = collectionStore.settings?.layout
  if (fromBackend === 'list' || fromBackend === 'grid' || fromBackend === 'grouped') {
    return fromBackend
  }
  return ui.bookmarkLayout
})

async function select(layout: BookmarkLayout) {
  const id = collectionStore.currentCollectionId
  if (!id) return
  await collectionStore.updateSettings(id, { layout })
}
</script>

<template>
  <div
    class="inline-flex items-center rounded-md border border-border bg-background"
    role="group"
    :aria-label="t('settings.layout')"
  >
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      class="flex h-8 w-8 items-center justify-center text-muted-foreground transition-colors first:rounded-l-md last:rounded-r-md hover:text-foreground"
      :class="current === option.value ? 'bg-muted text-foreground' : ''"
      :aria-pressed="current === option.value"
      :title="t(option.labelKey)"
      @click="select(option.value)"
    >
      <component :is="option.icon" class="h-4 w-4" />
    </button>
  </div>
</template>
