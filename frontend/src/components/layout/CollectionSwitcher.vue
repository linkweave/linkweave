<script setup lang="ts">
import { computed } from 'vue'
import {
  DropdownMenuRoot,
  DropdownMenuTrigger,
  DropdownMenuPortal,
  DropdownMenuContent,
} from 'radix-vue'
import { useCollectionStore } from '@/stores/collection'
import { ChevronDown, LayoutGrid, Star, Settings } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import router from '@/router'

const { t } = useI18n()
const collectionStore = useCollectionStore()

const collections = computed(() => collectionStore.collections)
const currentCollectionId = computed(() => collectionStore.currentCollectionId)
const isCurrentDefault = computed(() =>
  collections.value.find(c => c.id === currentCollectionId.value)?.isDefault ?? false
)

function selectCollection(id: string) {
  collectionStore.switchCollection(id)
}

async function setAsDefault() {
  if (isCurrentDefault.value || !currentCollectionId.value) return
  await collectionStore.setDefaultCollection(currentCollectionId.value)
}

function goToManage() {
  router.push({ name: 'manage-collections' })
}
</script>

<template>
  <DropdownMenuRoot>
    <DropdownMenuTrigger as-child>
      <button
        data-testid="collection-switcher-trigger"
        class="flex items-center gap-1.5 text-xl font-semibold text-foreground cursor-pointer rounded-md px-1.5 py-0.5 transition-colors hover:bg-accent/50"
      >
        <span class="truncate max-w-[120px] sm:max-w-[200px] md:max-w-[300px]">{{ collectionStore.collectionName ?? t('app.title') }}</span>
        <ChevronDown class="h-4 w-4 shrink-0 opacity-50 transition-transform duration-150" />
      </button>
    </DropdownMenuTrigger>

    <DropdownMenuPortal>
      <DropdownMenuContent
        class="z-[100] w-64 sm:w-72 rounded-lg border border-border bg-popover text-popover-foreground shadow-lg overflow-hidden data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95"
        align="start"
        :side-offset="8"
      >
        <div class="px-1.5 pt-1.5">
          <div class="px-2 py-1 text-[10px] font-medium uppercase tracking-wider text-muted-foreground/50">
            {{ t('collectionSwitcher.collections') }}
          </div>
        </div>

        <div class="max-h-[60vh] overflow-y-auto px-1.5">
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
            data-testid="collection-manage-btn"
            class="flex items-center gap-2 w-full px-2 py-1.5 rounded text-xs text-muted-foreground hover:bg-accent hover:text-foreground cursor-pointer transition-colors"
            @click="goToManage"
          >
            <Settings class="h-3.5 w-3.5 shrink-0" />
            <span>{{ t('collectionSwitcher.manageCollections') }}</span>
          </button>
        </div>
      </DropdownMenuContent>
    </DropdownMenuPortal>
  </DropdownMenuRoot>
</template>
