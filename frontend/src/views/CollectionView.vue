<script setup lang="ts">
import { ref } from 'vue'
import { Plus } from 'lucide-vue-next'
import { MainLayout } from '@/components/layout'
import { ButtonCl } from '@/components/ui'
import { useI18n } from 'vue-i18n'
import { useCollectionStore } from '@/stores/collection'
import CreateBookmarkDialog from '@/components/bookmark/CreateBookmarkDialog.vue'

const { t } = useI18n()
const collectionStore = useCollectionStore()
const isAddingBookmark = ref(false)
</script>

<template>
  <MainLayout>
    <template #header-actions>
      <ButtonCl size="sm" @click="isAddingBookmark = true">
        <Plus class="h-4 w-4 mr-2" />
        {{ t('header.addBookmark') }}
      </ButtonCl>
    </template>

    <div class="max-w-4xl mx-auto space-y-6">
      <div class="flex flex-col items-center justify-center py-12 text-center">
        <p class="text-muted-foreground">
          {{ t('home.noBookmarks') }}
        </p>
      </div>
    </div>

    <CreateBookmarkDialog
      v-if="collectionStore.currentCollectionId"
      :collection-id="collectionStore.currentCollectionId"
      v-model:open="isAddingBookmark"
      @created="isAddingBookmark = false"
    />
  </MainLayout>
</template>
