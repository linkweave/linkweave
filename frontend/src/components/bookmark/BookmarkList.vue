<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useEffectiveLayout } from '@/composables/useEffectiveLayout'
import { useBookmarkStore } from '@/stores/bookmark'
import { useNotificationStore } from '@/stores/notification'
import BookmarkCard from './BookmarkCard.vue'
import BookmarkGroupedLayout from './BookmarkGroupedLayout.vue'
import BookmarkDialog from './BookmarkDialog.vue'
import MoveBookmarkDialog from './MoveBookmarkDialog.vue'
import NeverOpenedDivider from './NeverOpenedDivider.vue'
import BookmarkPreviewPopup from './BookmarkPreviewPopup.vue'
import { ConfirmDialog } from '@/components/ui'
import type { BookmarkJson } from '@/api/generated'
import { provideBookmarkPreviewHover } from '@/composables/useBookmarkPreviewHover'

// One hover-intent controller drives a single popup shared by every list
// row. Per-row popups would cascade as the pointer sweeps the list; the
// shared controller enforces the dwell/warm/grace timing in one place.
const previewHover = provideBookmarkPreviewHover()

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const notification = useNotificationStore()
const effectiveLayout = useEffectiveLayout()

// List and grid layouts render the same `<BookmarkCard>` children — only the
// container class and the `show-stats` flag differ.
const classForCards = computed(() =>
  effectiveLayout.value === 'grid'
    ? 'grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4'
    : 'space-y-3',
)
const showStats = computed(() => effectiveLayout.value === 'list')

// Index at which the "Never opened" divider should be rendered.
// `neverOpenedCount` is 0 for non-click-based sorts, so the divider never
// renders in those cases.
const neverOpenedDividerAt = computed(() => {
  const n = bookmarkStore.neverOpenedCount
  if (n === 0) return -1
  return bookmarkStore.filteredBookmarks.length - n
})

// for the TransitionGroup we need to have a way to add bookmarks
// and the 'never-opened' divider in one single list. Hence this types
// gets exactly one keyed element per iteration — fragment children
// (template v-for with multiple roots) are not supported there.
type RenderItem =
  | { key: string; kind: 'divider' }
  | { key: string; kind: 'bookmark'; bookmark: BookmarkJson }

const renderItems = computed<RenderItem[]>(() => {
  const items: RenderItem[] = []
  bookmarkStore.filteredBookmarks.forEach((bookmark, idx) => {
    if (idx === neverOpenedDividerAt.value) {
      items.push({ key: 'never-opened-divider', kind: 'divider' })
    }
    items.push({ key: bookmark.id, kind: 'bookmark', bookmark })
  })
  return items
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

  <!-- TransitionGroup animates batch/single removals out (UC-074: grid
       fade + scale, list fade + slide-left, ~240ms). -->
  <TransitionGroup
    v-else-if="effectiveLayout !== 'grouped'"
    tag="div"
    :class="classForCards"
    :name="effectiveLayout === 'grid' ? 'bm-grid' : 'bm-list'"
  >
    <template v-for="item in renderItems" :key="item.key">
      <NeverOpenedDivider
        v-if="item.kind === 'divider'"
        :count="bookmarkStore.neverOpenedCount"
      />
      <BookmarkCard
        v-else
        :bookmark="item.bookmark"
        :layout="effectiveLayout === 'list' ? 'list' : 'grid'"
        :show-stats="showStats"
        @edit="handleEdit"
        @delete="handleDelete"
        @move="handleMove"
      />
    </template>
  </TransitionGroup>

  <BookmarkGroupedLayout
    v-else
    :bookmarks="bookmarkStore.filteredBookmarks"
    @edit="handleEdit"
    @delete="handleDelete"
    @move="handleMove"
  />

  <BookmarkDialog
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

  <BookmarkPreviewPopup :controller="previewHover" />
</template>

<style scoped>
.bm-grid-leave-active,
.bm-list-leave-active {
  transition: opacity 0.24s ease, transform 0.24s ease;
}

.bm-grid-leave-to {
  opacity: 0;
  transform: scale(0.92);
}

.bm-list-leave-to {
  opacity: 0;
  transform: translateX(-12px);
}

.bm-grid-move,
.bm-list-move {
  transition: transform 0.24s ease;
}

@media (prefers-reduced-motion: reduce) {
  .bm-grid-leave-active,
  .bm-list-leave-active,
  .bm-grid-move,
  .bm-list-move {
    transition: none;
  }
}
</style>
