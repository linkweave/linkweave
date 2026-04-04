<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { Plus } from 'lucide-vue-next'
import { MainLayout } from '@/components/layout'
import { ButtonCl } from '@/components/ui'
import { BookmarkList } from '@/components/bookmark'
import { FolderBreadcrumbCl } from '@/components/folder'
import { useI18n } from 'vue-i18n'
import { useCollectionStore } from '@/stores/collection'
import { useBookmarkStore } from '@/stores/bookmark'
import CreateBookmarkDialog from '@/components/bookmark/CreateBookmarkDialog.vue'

const { t } = useI18n()
const collectionStore = useCollectionStore()
const bookmarkStore = useBookmarkStore()
const isAddingBookmark = ref(false)

function loadBookmarks() {
  if (collectionStore.currentCollectionId) {
    bookmarkStore.fetchBookmarks(collectionStore.currentCollectionId)
  }
}

onMounted(loadBookmarks)
watch(() => collectionStore.currentCollectionId, loadBookmarks)
</script>

<template>
  <MainLayout>
    <template #header-title>
      {{ collectionStore.collectionName ?? t('app.title') }}
    </template>

    <template #header-actions>
      <ButtonCl size="sm" @click="isAddingBookmark = true">
        <Plus class="h-4 w-4 mr-2" />
        {{ t('header.addBookmark') }}
      </ButtonCl>
    </template>

    <FolderBreadcrumbCl />

    <BookmarkList />

    <CreateBookmarkDialog
      v-if="collectionStore.currentCollectionId"
      :collection-id="collectionStore.currentCollectionId"
      v-model:open="isAddingBookmark"
      @created="isAddingBookmark = false; loadBookmarks()"
    />
  </MainLayout>
</template>
