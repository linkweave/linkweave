<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useCollectionStore } from '@/stores/collection'
import { ChevronDown, LayoutGrid, Star, Settings } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import router from '@/router'

const { t } = useI18n()
const collectionStore = useCollectionStore()
const isOpen = ref(false)
const switcherRef = ref<HTMLElement | null>(null)

const collections = computed(() => collectionStore.collections)
const currentCollectionId = computed(() => collectionStore.currentCollectionId)
const isCurrentDefault = computed(() =>
  collections.value.find(c => c.id === currentCollectionId.value)?.isDefault ?? false
)

function toggleDropdown() {
  isOpen.value = !isOpen.value
}

function closeDropdown() {
  isOpen.value = false
}

function selectCollection(id: string) {
  collectionStore.switchCollection(id)
  closeDropdown()
}

async function setAsDefault() {
  if (isCurrentDefault.value || !currentCollectionId.value) return
  await collectionStore.setDefaultCollection(currentCollectionId.value)
}

function handleClickOutside(event: MouseEvent) {
  if (switcherRef.value && !switcherRef.value.contains(event.target as Node)) {
    closeDropdown()
  }
}

function handleKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    closeDropdown()
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside, true)
  document.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside, true)
  document.removeEventListener('keydown', handleKeyDown)
})
</script>

<template>
  <div ref="switcherRef" class="relative px-3 py-2.5 border-b border-border">
    <button
      data-testid="collection-switcher-trigger"
      class="flex items-center gap-1.5 w-full rounded-md px-2 py-1.5 text-xs font-medium text-foreground bg-accent/50 border border-border cursor-pointer transition-colors hover:bg-accent"
      @click="toggleDropdown"
    >
      <LayoutGrid class="h-3.5 w-3.5 shrink-0 opacity-50" />
      <span class="flex-1 truncate text-left">{{ collectionStore.collectionName ?? t('collectionSwitcher.selectCollection') }}</span>
      <ChevronDown
        class="h-3 w-3 shrink-0 opacity-50 transition-transform duration-150"
        :class="{ 'rotate-180': isOpen }"
      />
    </button>

    <Transition
      enter-active-class="transition duration-150 ease-out"
      enter-from-class="opacity-0 scale-95"
      enter-to-class="opacity-100 scale-100"
      leave-active-class="transition duration-100 ease-in"
      leave-from-class="opacity-100 scale-100"
      leave-to-class="opacity-0 scale-95"
    >
      <div
        v-if="isOpen"
        class="absolute left-3 right-3 top-full mt-1 z-50 rounded-lg border border-border bg-popover text-popover-foreground shadow-lg overflow-hidden"
      >
        <div class="p-1.5">
          <div class="px-2 py-1 text-[10px] font-medium uppercase tracking-wider text-muted-foreground/50">
            {{ t('collectionSwitcher.collections') }}
          </div>

          <button
            v-for="col in collections"
            :key="col.id"
            :data-testid="`collection-item-${col.id}`"
            class="flex items-center gap-2 w-full px-2 py-1.5 rounded text-xs cursor-pointer transition-colors"
            :class="
              col.id === currentCollectionId
                ? 'bg-primary/15 text-foreground'
                : 'text-muted-foreground hover:bg-accent hover:text-foreground'
            "
            @click="selectCollection(col.id!)"
          >
            <LayoutGrid
              class="h-3.5 w-3.5 shrink-0"
              :class="col.id === currentCollectionId ? 'text-primary' : 'opacity-35'"
            />
            <span class="flex-1 truncate">{{ col.name }}</span>
            <span
              v-if="col.isDefault"
              :data-testid="`collection-default-badge-${col.id}`"
              class="text-[9px] bg-primary/25 text-primary px-1.5 py-0.5 rounded shrink-0"
            >
              {{ t('collectionSwitcher.default') }}
            </span>
          </button>
        </div>

        <div class="border-t border-border p-1.5">
          <div class="px-2 py-1 text-[10px] font-medium uppercase tracking-wider text-muted-foreground/50">
            {{ t('collectionSwitcher.activeCollection') }}
          </div>

          <button
            data-testid="collection-set-default-btn"
            class="flex items-center gap-2 w-full px-2 py-1.5 rounded text-xs transition-colors"
            :class="
              isCurrentDefault
                ? 'text-muted-foreground/30 cursor-default'
                : 'text-muted-foreground hover:bg-accent hover:text-foreground cursor-pointer'
            "
            :disabled="isCurrentDefault"
            @click="setAsDefault"
          >
            <Star class="h-3.5 w-3.5 shrink-0" />
            <span>{{ isCurrentDefault ? t('collectionSwitcher.thisIsDefault') : t('collectionSwitcher.setAsDefault') }}</span>
          </button>

          <button
            class="flex items-center gap-2 w-full px-2 py-1.5 rounded text-xs text-muted-foreground hover:bg-accent hover:text-foreground cursor-pointer transition-colors"
            @click="closeDropdown(); router.push({ name: 'manage-collections' })"
          >
            <Settings class="h-3.5 w-3.5 shrink-0" />
            <span>{{ t('collectionSwitcher.manageCollections') }}</span>
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>
