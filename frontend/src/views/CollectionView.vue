<script setup lang="ts">
import { ref, computed } from 'vue'
import { useMediaQuery } from '@/composables/useMediaQuery'
import { BookmarkPlus } from 'lucide-vue-next'
import { MainLayout } from '@/components/layout'
import { ButtonCl, SearchBar } from '@/components/ui'
import HeaderSearchMobile from '@/components/ui/HeaderSearchMobile.vue'
import { BookmarkList, SearchActiveChip } from '@/components/bookmark'
import { useBookmarkStore } from '@/stores/bookmark'
import { FolderBreadcrumbCl } from '@/components/folder'
import { useI18n } from 'vue-i18n'
import { useCollectionStore } from '@/stores/collection'
import { useUiStore } from '@/stores/ui'
import { useOfflineStore } from '@/stores/offline'
import CreateBookmarkDialog from '@/components/bookmark/CreateBookmarkDialog.vue'

const { t } = useI18n()
const collectionStore = useCollectionStore()
const bookmarkStore = useBookmarkStore()
const ui = useUiStore()
const offline = useOfflineStore()
const isAddingBookmark = ref(false)
const isDesktop = useMediaQuery('(min-width: 1024px)')
const searchPlaceholder = computed(() =>
  isDesktop.value ? t('search.placeholder') : t('search.placeholderShort')
)

const containerClass = computed(() =>
  ui.bookmarkLayout === 'grouped' || ui.bookmarkLayout === 'grid' ? 'max-w-7xl' : 'max-w-4xl'
)
</script>

<template>
  <MainLayout>
    <template #header-search>
      <SearchBar v-model="bookmarkStore.searchQuery" :placeholder="searchPlaceholder" variant="header" />
    </template>
    <template #header-search-mobile>
      <HeaderSearchMobile />
    </template>
    <template #header-actions>
      <!-- icon-only square on mobile, labelled pill on sm+ -->
      <ButtonCl size="icon" class="sm:hidden" :disabled="offline.isOffline" :aria-label="t('header.addBookmark')" @click="isAddingBookmark = true">
        <BookmarkPlus class="h-4 w-4" />
      </ButtonCl>
      <ButtonCl size="sm" class="hidden sm:inline-flex" :disabled="offline.isOffline" @click="isAddingBookmark = true">
        <BookmarkPlus class="h-4 w-4 mr-2" />
        {{ t('header.addBookmark') }}
      </ButtonCl>
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
