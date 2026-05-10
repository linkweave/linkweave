<script setup lang="ts">
import { BookmarkList, SearchActiveChip } from '@/components/bookmark'
import CreateBookmarkDialog from '@/components/bookmark/CreateBookmarkDialog.vue'
import { FolderBreadcrumbCl } from '@/components/folder'
import { MainLayout } from '@/components/layout'
import { ResponsiveButton, SearchBar } from '@/components/ui'
import HeaderSearchMobile from '@/components/ui/HeaderSearchMobile.vue'
import { useMediaQuery } from '@/composables/useMediaQuery'
import { useBookmarkStore } from '@/stores/bookmark'
import { useCollectionStore } from '@/stores/collection'
import { useOfflineStore } from '@/stores/offline'
import { useUiStore } from '@/stores/ui'
import { BookmarkPlus } from 'lucide-vue-next'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const collectionStore = useCollectionStore()
const bookmarkStore = useBookmarkStore()
const ui = useUiStore()
const offline = useOfflineStore()
const isAddingBookmark = ref(false)
const isDesktop = useMediaQuery('(min-width: 1024px)')
const searchPlaceholder = computed(() =>
  isDesktop.value ? t('search.placeholder') : t('search.placeholderShort'),
)

const containerClass = computed(() =>
  ui.bookmarkLayout === 'grouped' || ui.bookmarkLayout === 'grid' ? 'max-w-7xl' : 'max-w-4xl',
)
</script>

<template>
  <MainLayout>
    <template #header-search>
      <SearchBar
        v-model="bookmarkStore.searchQuery"
        :placeholder="searchPlaceholder"
        variant="header"
      />
    </template>
    <template #header-search-mobile>
      <HeaderSearchMobile />
    </template>
    <template #header-actions>
      <ResponsiveButton
        :label="t('header.addBookmark')"
        :disabled="offline.isOffline"
        @click="isAddingBookmark = true"
      >
        <BookmarkPlus />
      </ResponsiveButton>
    </template>

    <div :class="[containerClass, 'mx-auto space-y-4']">
      <FolderBreadcrumbCl />
      <SearchActiveChip />
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
