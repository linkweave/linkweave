<script setup lang="ts">
import { ref } from 'vue'
import { BookmarkPlus } from 'lucide-vue-next'
import { MainLayout } from '@/components/layout'
import { ButtonCl } from '@/components/ui'
import { BookmarkList } from '@/components/bookmark'
import { FolderBreadcrumbCl } from '@/components/folder'
import { useI18n } from 'vue-i18n'
import { useCollectionStore } from '@/stores/collection'
import CreateBookmarkDialog from '@/components/bookmark/CreateBookmarkDialog.vue'

const { t } = useI18n()
const collectionStore = useCollectionStore()
const isAddingBookmark = ref(false)
</script>

<template>
  <MainLayout>
    <template #header-title>
      {{ collectionStore.collectionName ?? t('app.title') }}
    </template>

    <template #header-actions>
      <ButtonCl size="sm" @click="isAddingBookmark = true">
        <BookmarkPlus class="h-4 w-4 sm:mr-2" />
        <span class="hidden sm:inline">{{ t('header.addBookmark') }}</span>
      </ButtonCl>
    </template>

    <FolderBreadcrumbCl />

    <BookmarkList />

    <CreateBookmarkDialog
      v-if="collectionStore.currentCollectionId"
      :collection-id="collectionStore.currentCollectionId"
      v-model:open="isAddingBookmark"
      @created="isAddingBookmark = false"
    />
  </MainLayout>
</template>
