<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useBookmarkStore } from '@/stores/bookmark'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import { useUiStore } from '@/stores/ui'
import BookmarkCard from './BookmarkCard.vue'
import BookmarkGroupedLayout from './BookmarkGroupedLayout.vue'
import EditBookmarkDialog from './EditBookmarkDialog.vue'
import MoveBookmarkDialog from './MoveBookmarkDialog.vue'
import NeverOpenedDivider from './NeverOpenedDivider.vue'
import { ConfirmDialog } from '@/components/ui'
import type { BookmarkJson } from '@/api/generated'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const notification = useNotificationStore()
const ui = useUiStore()
const collectionStore = useCollectionStore()
const effectiveLayout = computed(() => collectionStore.settingsLayout ?? ui.bookmarkLayout)

// Index at which the "Never opened" divider should be rendered.
// `neverOpenedCount` is 0 for non-click-based sorts, so the divider never
// renders in those cases.
const neverOpenedDividerAt = computed(() => {
  const n = bookmarkStore.neverOpenedCount
  if (n === 0) return -1
  return bookmarkStore.filteredBookmarks.length - n
})

const editingBookmark = ref<BookmarkJson | null>(null)
const showEditDialog = ref(false)
const movingBookmark = ref<BookmarkJson | null>(null)
const showMoveDialog = ref(false)
const deletingBookmark = ref<BookmarkJson | null>(null)
const showDeleteConfirm = ref(false)

function handleEdit(bookmark: BookmarkJson) {
  editingBookmark.value = bookmark
  showEditDialog.value = true
}

function handleEditDialogUpdate(open: boolean) {
  showEditDialog.value = open
  if (!open) editingBookmark.value = null
}

function handleMove(bookmark: BookmarkJson) {
  movingBookmark.value = bookmark
  showMoveDialog.value = true
}

function handleMoveDialogUpdate(open: boolean) {
  showMoveDialog.value = open
  if (!open) movingBookmark.value = null
}

function handleDelete(bookmark: BookmarkJson) {
  deletingBookmark.value = bookmark
  showDeleteConfirm.value = true
}

function handleDeleteDialogUpdate(open: boolean) {
  showDeleteConfirm.value = open
  if (!open) deletingBookmark.value = null
}

async function confirmDelete() {
  if (!deletingBookmark.value) return
  try {
    await bookmarkStore.deleteBookmark(deletingBookmark.value.id)
  } catch (err) {
    void notification.handleApiError(err, t('bookmark.deleteError'))
  } finally {
    deletingBookmark.value = null
    showDeleteConfirm.value = false
  }
}
</script>

<template>
  <div v-if="bookmarkStore.loading" class="flex items-center justify-center py-12">
    <p class="text-muted-foreground">{{ t('bookmarkList.loading') }}</p>
  </div>

  <div v-else-if="bookmarkStore.filteredBookmarks.length === 0" class="flex flex-col items-center justify-center py-12 text-center">
    <p class="text-muted-foreground">{{ t('bookmarkList.empty') }}</p>
  </div>

  <div v-else-if="effectiveLayout === 'list'" class="space-y-3">
    <template v-for="(bookmark, idx) in bookmarkStore.filteredBookmarks" :key="bookmark.id">
      <NeverOpenedDivider
        v-if="idx === neverOpenedDividerAt"
        :count="bookmarkStore.neverOpenedCount"
      />
      <BookmarkCard
        :bookmark="bookmark"
        :show-stats="true"
        @edit="handleEdit"
        @delete="handleDelete"
        @move="handleMove"
      />
    </template>
  </div>

  <div v-else-if="effectiveLayout === 'grid'" class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
    <template v-for="(bookmark, idx) in bookmarkStore.filteredBookmarks" :key="bookmark.id">
      <NeverOpenedDivider
        v-if="idx === neverOpenedDividerAt"
        :count="bookmarkStore.neverOpenedCount"
      />
      <BookmarkCard
        :bookmark="bookmark"
        @edit="handleEdit"
        @delete="handleDelete"
        @move="handleMove"
      />
    </template>
  </div>

  <BookmarkGroupedLayout
    v-else
    :bookmarks="bookmarkStore.filteredBookmarks"
    @edit="handleEdit"
    @delete="handleDelete"
    @move="handleMove"
  />

  <EditBookmarkDialog
    :bookmark="editingBookmark"
    v-model:open="showEditDialog"
    @update:open="handleEditDialogUpdate"
    @saved="handleEditDialogUpdate(false)"
  />

  <MoveBookmarkDialog
    :bookmark="movingBookmark"
    v-model:open="showMoveDialog"
    @update:open="handleMoveDialogUpdate"
    @moved="handleMoveDialogUpdate(false)"
  />

  <ConfirmDialog
    v-model:open="showDeleteConfirm"
    :title="t('bookmark.deleteTitle')"
    :message="t('bookmark.deleteConfirm')"
    @confirmed="confirmDelete"
    @update:open="handleDeleteDialogUpdate"
  />
</template>
