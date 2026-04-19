<script setup lang="ts">
import { ref, computed } from 'vue'
import { BookmarkPlus } from 'lucide-vue-next'
import { MainLayout } from '@/components/layout'
import { ButtonCl } from '@/components/ui'
import { BookmarkList, SearchBar } from '@/components/bookmark'
import { FolderBreadcrumbCl } from '@/components/folder'
import { useI18n } from 'vue-i18n'
import { useCollectionStore } from '@/stores/collection'
import { useUiStore } from '@/stores/ui'
import CreateBookmarkDialog from '@/components/bookmark/CreateBookmarkDialog.vue'

const { t } = useI18n()
const collectionStore = useCollectionStore()
const ui = useUiStore()
const isAddingBookmark = ref(false)

const containerClass = computed(() =>
  ui.bookmarkLayout === 'grouped' ? 'max-w-7xl' : 'max-w-4xl'
)
</script>

<template>
  <MainLayout>
    <template #header-actions>
      <ButtonCl size="sm" @click="isAddingBookmark = true">
        <BookmarkPlus class="h-4 w-4 sm:mr-2" />
        <span class="hidden sm:inline">{{ t('header.addBookmark') }}</span>
      </ButtonCl>
    </template>

    <div :class="[containerClass, 'mx-auto space-y-6']">
      <FolderBreadcrumbCl />
      <SearchBar />
      <BookmarkList />
    </div>

    <CreateBookmarkDialog
      v-if="collectionStore.currentCollectionId"
      :collection-id="collectionStore.currentCollectionId"
      v-model:open="isAddingBookmark"
      @created="isAddingBookmark = false"
    />
  </MainLayout>
</template>
