<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { LayoutGrid, LayoutList, Layers } from '@lucide/vue'
import { ButtonLw } from '@/components/ui'
import { useCollectionStore } from '@/stores/collection'
import { useUiStore, type BookmarkLayout } from '@/stores/ui'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const collectionStore = useCollectionStore()
const ui = useUiStore()

const options: ReadonlyArray<{ value: BookmarkLayout; icon: typeof LayoutList; labelKey: string }> = [
  { value: 'grid', icon: LayoutGrid, labelKey: 'toolbar.layoutGrid' },
  { value: 'list', icon: LayoutList, labelKey: 'toolbar.layoutList' },
  { value: 'grouped', icon: Layers, labelKey: 'toolbar.layoutGrouped' },
]

const current = computed<BookmarkLayout>(() => {
  const fromBackend = collectionStore.settings?.layout
  if (fromBackend === 'list' || fromBackend === 'grid' || fromBackend === 'grouped') {
    return fromBackend
  }
  return ui.bookmarkLayout
})

// options is a non-empty const, so options[0] is always defined
const currentOption = computed(() => options.find((o) => o.value === current.value) ?? options[0]!)

const mobileMenuOpen = ref(false)
const mobileRoot = ref<HTMLElement | null>(null)

function select(layout: BookmarkLayout) {
  const id = collectionStore.currentCollectionId
  if (id) {
    collectionStore.updateSettings(id, { layout })
  } else {
    ui.setBookmarkLayout(layout)
  }
  mobileMenuOpen.value = false
}

function onDocumentPointerDown(event: PointerEvent) {
  const target = event.target as Node | null
  if (target && mobileRoot.value && !mobileRoot.value.contains(target)) {
    mobileMenuOpen.value = false
  }
}

function onDocumentKey(event: KeyboardEvent) {
  if (event.key === 'Escape') mobileMenuOpen.value = false
}

watch(mobileMenuOpen, (open) => {
  if (open) {
    document.addEventListener('pointerdown', onDocumentPointerDown)
    document.addEventListener('keydown', onDocumentKey)
  } else {
    document.removeEventListener('pointerdown', onDocumentPointerDown)
    document.removeEventListener('keydown', onDocumentKey)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  document.removeEventListener('keydown', onDocumentKey)
})
</script>

<template>
  <!-- Desktop: segmented button group -->
  <div
    class="hidden sm:flex items-center rounded-md border border-border bg-card overflow-hidden"
    role="group"
    :aria-label="t('toolbar.layout')"
  >
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      class="flex h-7 w-7 items-center justify-center transition-colors"
      :class="current === option.value ? 'bg-accent text-accent-foreground' : 'text-muted-foreground hover:text-foreground'"
      :aria-label="t(option.labelKey)"
      :aria-pressed="current === option.value"
      :title="t(option.labelKey)"
      @click="select(option.value)"
    >
      <component :is="option.icon" class="h-3.5 w-3.5" />
    </button>
  </div>

  <!-- Mobile: icon button opens a menu -->
  <div ref="mobileRoot" class="relative sm:hidden">
    <ButtonLw
      variant="ghost"
      size="icon"
      class="h-7 w-7"
      :aria-label="t('toolbar.changeLayout')"
      :aria-haspopup="'menu'"
      :aria-expanded="mobileMenuOpen"
      @click="mobileMenuOpen = !mobileMenuOpen"
    >
      <component :is="currentOption.icon" class="h-3.5 w-3.5" />
    </ButtonLw>
    <div
      v-if="mobileMenuOpen"
      class="absolute right-0 top-full mt-1 z-40 min-w-[8rem] rounded-md border border-border bg-popover shadow-xl ring-1 ring-black/5 dark:ring-white/10 py-1"
      role="menu"
    >
      <button
        v-for="option in options"
        :key="option.value"
        type="button"
        class="flex w-full items-center gap-2 px-3 py-2 text-sm text-left hover:bg-popover-hover hover:text-popover-foreground"
        :class="current === option.value ? 'text-foreground' : 'text-muted-foreground'"
        role="menuitemradio"
        :aria-checked="current === option.value"
        @click="select(option.value)"
      >
        <component :is="option.icon" class="h-3.5 w-3.5" />
        <span>{{ t(option.labelKey) }}</span>
      </button>
    </div>
  </div>
</template>
