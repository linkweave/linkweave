<script setup lang="ts">
import { MainLayout } from '@/components/layout'
import { useI18n } from 'vue-i18n'
import { useCollectionStore } from '@/stores/collection'

const { t } = useI18n()
const collectionStore = useCollectionStore()
</script>

<template>
  <MainLayout>
    <template #header-title>
      {{ t('collectionSwitcher.manageCollections') }}
    </template>

    <div class="max-w-4xl mx-auto space-y-6 p-6 bg-accent/20 rounded-lg border border-border">
      <h2 class="text-lg font-semibold">{{ t('collectionSwitcher.collections') }}</h2>
      <ul class="divide-y divide-border">
        <li v-for="col in collectionStore.collections" :key="col.id" class="py-3 flex items-center justify-between">
          <div class="flex items-center gap-3">
             <span class="font-medium text-sm">{{ col.name }}</span>
             <span v-if="col.isDefault" class="text-[10px] bg-primary/25 text-primary px-1.5 py-0.5 rounded font-medium">
               {{ t('collectionSwitcher.default') }}
             </span>
          </div>
          <span class="text-xs text-muted-foreground italic">Management UI coming soon...</span>
        </li>
      </ul>
    </div>
  </MainLayout>
</template>
