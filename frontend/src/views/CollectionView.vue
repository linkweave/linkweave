<script setup lang="ts">
import {
  BookmarkList,
  BookmarkListToolbar,
  BookmarkSortMenu,
  CollectionSettingsModal,
  SearchActiveChip,
} from '@/components/bookmark'
import BookmarkDialog from '@/components/bookmark/BookmarkDialog.vue'
import { MainLayout } from '@/components/layout'
import { ResponsiveButton, SearchBar } from '@/components/ui'
import HeaderSearchMobile from '@/components/ui/HeaderSearchMobile.vue'
import { useMediaQuery } from '@/composables/useMediaQuery'
import { useBookmarkStore } from '@/stores/bookmark'
import { useCollectionStore } from '@/stores/collection'
import { useFolderStore } from '@/stores/folder'
import { useOfflineStore } from '@/stores/offline'
import { useUiStore } from '@/stores/ui'
import { BookmarkPlus, Settings } from '@lucide/vue'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const collectionStore = useCollectionStore()
const bookmarkStore = useBookmarkStore()
const folderStore = useFolderStore()
const ui = useUiStore()
const offline = useOfflineStore()
const isAddingBookmark = ref(false)
const isSettingsOpen = ref(false)
const isDesktop = useMediaQuery('(min-width: 1024px)')
const searchPlaceholder = computed(() =>
  isDesktop.value ? t('search.placeholder') : t('search.placeholderShort'),
)

const effectiveLayout = computed(() => collectionStore.settingsLayout ?? ui.bookmarkLayout)
const containerClass = computed(() => {
  const l = effectiveLayout.value
  return l === 'grouped' || l === 'grid' || l === 'tiles' ? 'max-w-7xl' : 'max-w-4xl'
})

// Fall back from tiles to grid when screenshots get disabled for the current
// collection (e.g. owner toggles it off here or in another tab). Fires on
// collection switch as well as on flag-flip server-side.
watch(
  () => collectionStore.collectionInfo?.screenshotEnabled,
  (enabled) => {
    if (enabled) return
    if (collectionStore.settingsLayout === 'tiles' && collectionStore.currentCollectionId) {
      collectionStore.updateSettings(collectionStore.currentCollectionId, { layout: 'grid' })
    } else if (!collectionStore.settingsLayout && ui.bookmarkLayout === 'tiles') {
      ui.setBookmarkLayout('grid')
    }
  },
  { immediate: true },
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

    <template #toolbar>
      <BookmarkListToolbar>
        <template #sort>
          <BookmarkSortMenu />
        </template>
        <template #extras>
          <!-- 1 px divider between Sort and the gear, per the design spec. -->
          <div class="w-px h-4 bg-border mx-0.5" aria-hidden="true" />
          <button
            type="button"
            class="h-7 w-7 inline-flex items-center justify-center rounded-md text-muted-foreground hover:text-foreground hover:bg-accent transition-colors"
            :aria-label="t('collectionSettings.openSettings')"
            :title="t('collectionSettings.openSettings')"
            data-testid="collection-settings-open"
            @click="isSettingsOpen = true"
          >
            <Settings class="h-3.5 w-3.5" />
          </button>
        </template>
      </BookmarkListToolbar>
    </template>
    <div :class="[containerClass, 'mx-auto space-y-4']">
      <SearchActiveChip />
      <BookmarkList />
    </div>

    <BookmarkDialog
      v-if="collectionStore.currentCollectionId"
      :collection-id="collectionStore.currentCollectionId"
      :preselected-folder-id="folderStore.selectedFolderId ?? undefined"
      v-model:open="isAddingBookmark"
      @saved="isAddingBookmark = false"
    />

    <CollectionSettingsModal v-model:open="isSettingsOpen" />
  </MainLayout>
</template>
