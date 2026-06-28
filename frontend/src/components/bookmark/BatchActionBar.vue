<script setup lang="ts">
import { config } from '@/api'
import { ExportResourceApi } from '@/api/generated'
import BatchTagEditor from '@/components/bookmark/BatchTagEditor.vue'
import MoveBookmarkDialog from '@/components/bookmark/MoveBookmarkDialog.vue'
import { ConfirmDialog } from '@/components/ui'
import { useBookmarkStore } from '@/stores/bookmark'
import { useCollectionStore } from '@/stores/collection'
import { useFolderStore } from '@/stores/folder'
import { useNotificationStore } from '@/stores/notification'
import { useSelectionStore } from '@/stores/selection'
import { downloadFromResponse } from '@/utils/download'
import { Copy, Download, FolderInput, Tag, Trash2, X } from '@lucide/vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const selection = useSelectionStore()
const bookmarkStore = useBookmarkStore()
const collectionStore = useCollectionStore()
const folderStore = useFolderStore()
const notification = useNotificationStore()
const exportApi = new ExportResourceApi(config)

const showMoveDialog = ref(false)
const showDeleteConfirm = ref(false)
const showTagEditor = ref(false)
const tagButton = ref<HTMLButtonElement | null>(null)
const isExporting = ref(false)
const isDeleting = ref(false)

const selectedIdList = computed(() => [...selection.selectedIds])

// ---------------------------------------------------------------------------
// Actions. Success → toast + clear selection (exits mode); failure → error
// toast, selection retained for retry (the backend rolled back atomically;
// the move dialog toasts its own failure). Copy URLs never clears the
// selection (non-mutating). Esc/⌘A shortcuts live in useSelectionShortcuts,
// mounted by CollectionView.
// ---------------------------------------------------------------------------

function onMoved(folderId?: string | null) {
  const count = selection.count
  const folderName = folderId ? folderStore.folders.find((f) => f.id === folderId)?.data.name : null
  notification.success(
    folderName
      ? t('batch.movedToast', { count, folder: folderName })
      : t('batch.movedToastRoot', { count }),
  )
  selection.clearAndExit()
}

async function onDeleteConfirmed() {
  const ids = selectedIdList.value
  isDeleting.value = true
  try {
    await bookmarkStore.batchDelete(ids)
    notification.success(t('batch.deletedToast', { count: ids.length }))
    selection.clearAndExit()
  } catch {
    notification.error(t('batch.deleteError'))
  } finally {
    isDeleting.value = false
  }
}

async function copyUrls() {
  const urls = selection.selectedBookmarks.map((b) => b.data.url)
  try {
    await navigator.clipboard.writeText(urls.join('\n'))
    notification.success(t('batch.copiedToast', { count: urls.length }))
  } catch {
    notification.error(t('batch.copyError'))
  }
}

// Partial export (UC-096 export counterpart): POSTs the current batch
// selection and downloads the resulting Netscape HTML. Non-mutating, so the
// selection is retained — same as copy URLs.
async function exportSelection() {
  const collectionId = collectionStore.currentCollectionId
  if (!collectionId) return
  const ids = selectedIdList.value
  isExporting.value = true
  try {
    const { raw } = await exportApi.apiCollectionsCollectionIdExportPartialPostRaw({
      collectionId,
      bookmarkBatchExportJson: { bookmarkIds: ids },
    })
    // The server drops soft-deleted ids, so trust its count for the toast and
    // fall back to the selection size if the header is absent/malformed.
    const reported = Number(raw.headers.get('X-Exported-Count'))
    const exportedCount = raw.headers.get('X-Exported-Count') !== null && Number.isFinite(reported)
      ? reported
      : ids.length
    await downloadFromResponse(raw, 'bookmarks.html')
    notification.success(t('batch.exportedToast', { count: exportedCount }))
  } catch {
    notification.error(t('batch.exportError'))
  } finally {
    isExporting.value = false
  }
}
</script>

<template>
  <!-- `inert` keeps the clipped (closed) bar out of the tab order and the
       accessibility tree — max-height clipping alone hides it only visually. -->
  <div
    class="batch-bar-clip sticky top-11 z-20"
    :class="{ 'is-open': selection.count > 0 }"
    :inert="selection.count === 0"
    data-testid="batch-action-bar"
  >
    <div class="batch-bar" role="toolbar" :aria-label="t('batch.toolbarLabel')">
      <button
        type="button"
        class="batch-clear"
        :aria-label="t('batch.clearSelection')"
        :title="t('batch.clearSelection')"
        data-testid="batch-clear"
        @click="selection.clearAndExit()"
      >
        <X :size="15" />
      </button>

      <span class="batch-count" aria-live="polite" data-testid="batch-count">
        {{ t('batch.selected', { count: selection.count }) }}
      </span>

      <button
        v-if="!selection.allSelected"
        type="button"
        class="batch-select-all"
        :aria-label="t('batch.selectAll', { count: selection.totalCount })"
        data-testid="batch-select-all"
        @click="selection.selectAll()"
      >
        <span class="hidden md:inline">{{ t('batch.selectAll', { count: selection.totalCount }) }}</span>
        <span class="md:hidden">{{ t('batch.selectAllShort', { count: selection.totalCount }) }}</span>
      </button>

      <span class="batch-esc-hint hidden md:inline-block" aria-hidden="true">esc</span>

      <span class="flex-1" />

      <button
        type="button"
        class="batch-btn"
        :aria-label="t('batch.move')"
        :title="t('batch.move')"
        data-testid="batch-move"
        @click="showMoveDialog = true"
      >
        <FolderInput :size="14" />
        <span class="hidden md:inline">{{ t('batch.move') }}</span>
      </button>

      <button
        ref="tagButton"
        type="button"
        class="batch-btn"
        :aria-label="t('batch.tags')"
        :title="t('batch.tags')"
        :aria-haspopup="true"
        :aria-expanded="showTagEditor"
        data-testid="batch-add-tag"
        @click="showTagEditor = !showTagEditor"
      >
        <Tag :size="14" />
        <span class="hidden md:inline">{{ t('batch.tags') }}</span>
      </button>

      <button
        type="button"
        class="batch-btn"
        :aria-label="t('batch.copyUrls')"
        :title="t('batch.copyUrls')"
        data-testid="batch-copy-urls"
        @click="copyUrls"
      >
        <Copy :size="14" />
        <span class="hidden md:inline">{{ t('batch.copyUrls') }}</span>
      </button>

      <button
        type="button"
        class="batch-btn"
        :aria-label="t('batch.export')"
        :title="t('batch.export')"
        :disabled="isExporting"
        data-testid="batch-export"
        @click="exportSelection"
      >
        <Download :size="14" />
        <span class="hidden md:inline">{{ t('batch.export') }}</span>
      </button>

      <span class="batch-divider" aria-hidden="true" />

      <button
        type="button"
        class="batch-btn batch-btn--destructive"
        :aria-label="t('batch.delete')"
        :title="t('batch.delete')"
        :disabled="isDeleting"
        data-testid="batch-delete"
        @click="showDeleteConfirm = true"
      >
        <Trash2 :size="14" />
        <span class="hidden md:inline">{{ t('batch.delete') }}</span>
      </button>
    </div>
  </div>

  <MoveBookmarkDialog
    :bookmark="null"
    :bookmark-ids="selectedIdList"
    v-model:open="showMoveDialog"
    @moved="onMoved"
  />

  <ConfirmDialog
    v-model:open="showDeleteConfirm"
    :title="t('batch.deleteTitle', { count: selection.count })"
    :message="t('batch.deleteBody')"
    :confirm-label="t('batch.deleteConfirm')"
    @confirmed="onDeleteConfirmed"
  />

  <BatchTagEditor v-model:open="showTagEditor" :anchor="tagButton" />
</template>

<style scoped>
.batch-bar-clip {
  overflow: hidden;
  max-height: 0;
  transition: max-height 0.18s cubic-bezier(0.2, 0.7, 0.3, 1);
}

.batch-bar-clip.is-open {
  max-height: 52px;
}

.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 46px;
  padding: 0 18px;
  background: color-mix(in oklab, var(--color-primary) 9%, var(--color-background));
  border-bottom: 1px solid color-mix(in oklab, var(--color-primary) 90%, black);
}

.batch-clear {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-foreground);
  cursor: pointer;
}

.batch-clear:hover {
  background: color-mix(in oklab, var(--color-foreground) 10%, transparent);
}

.batch-count {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-foreground);
  font-variant-numeric: tabular-nums;
}

.batch-select-all {
  border: none;
  background: transparent;
  font-size: 12.5px;
  font-weight: 600;
  color: color-mix(in oklab, var(--color-primary) 65%, white);
  cursor: pointer;
  padding: 2px 4px;
}

.batch-select-all:hover {
  text-decoration: underline;
}

.batch-esc-hint {
  font-family: ui-monospace, monospace;
  font-size: 10.5px;
  color: var(--color-muted-foreground);
  border: 1px solid var(--color-border);
  border-radius: 4px;
  padding: 1px 5px;
}

.batch-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 10px;
  border-radius: 7px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-muted-foreground);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition:
    background-color 0.15s,
    color 0.15s;
}

.batch-btn:not(:disabled):hover {
  background: var(--color-secondary);
  color: var(--color-foreground);
}

.batch-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.batch-btn--destructive {
  color: #f87171;
}

.batch-btn--destructive:hover {
  color: #f87171;
}

.batch-divider {
  width: 1px;
  height: 20px;
  background: var(--color-border);
  margin: 0 4px;
}

/* On touch-sized viewports the labels collapse to icons, so grow the tap
   targets toward the ~44px guideline to keep the icon-only buttons hittable. */
@media (max-width: 767px) {
  .batch-bar-clip.is-open {
    max-height: 60px;
  }

  .batch-bar {
    height: 54px;
    padding: 0 12px;
    gap: 6px;
  }

  .batch-clear {
    width: 40px;
    height: 40px;
  }

  .batch-btn {
    height: 40px;
    padding: 0 12px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .batch-bar-clip {
    transition: none;
  }
}
</style>
