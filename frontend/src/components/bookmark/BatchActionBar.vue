<script setup lang="ts">
import type { TagJson } from '@/api/generated'
import MoveBookmarkDialog from '@/components/bookmark/MoveBookmarkDialog.vue'
import { ConfirmDialog, DropdownMenuContentLw, DropdownMenuItemLw } from '@/components/ui'
import { useBookmarkStore } from '@/stores/bookmark'
import { useFolderStore } from '@/stores/folder'
import { useNotificationStore } from '@/stores/notification'
import { useSelectionStore } from '@/stores/selection'
import { useTagStore } from '@/stores/tag'
import { Copy, FolderInput, Tag, Trash2, X } from '@lucide/vue'
import { DropdownMenuRoot, DropdownMenuTrigger } from 'radix-vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const selection = useSelectionStore()
const bookmarkStore = useBookmarkStore()
const folderStore = useFolderStore()
const tagStore = useTagStore()
const notification = useNotificationStore()

const showMoveDialog = ref(false)
const showDeleteConfirm = ref(false)

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
  try {
    await bookmarkStore.batchDelete(ids)
    notification.success(t('batch.deletedToast', { count: ids.length }))
    selection.clearAndExit()
  } catch {
    notification.error(t('batch.deleteError'))
  }
}

async function onPickTag(tag: TagJson) {
  const ids = selectedIdList.value
  try {
    await bookmarkStore.batchAddTag(ids, tag.id)
    notification.success(t('batch.taggedToast', { count: ids.length, tag: tag.data.name }))
    selection.clearAndExit()
  } catch {
    notification.error(t('batch.tagError'))
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
        data-testid="batch-select-all"
        @click="selection.selectAll()"
      >
        {{ t('batch.selectAll', { count: selection.totalCount }) }}
      </button>

      <span class="batch-esc-hint" aria-hidden="true">esc</span>

      <span class="flex-1" />

      <button
        type="button"
        class="batch-btn"
        data-testid="batch-move"
        @click="showMoveDialog = true"
      >
        <FolderInput :size="14" />
        <span class="hidden md:inline">{{ t('batch.move') }}</span>
      </button>

      <DropdownMenuRoot>
        <DropdownMenuTrigger as-child>
          <button type="button" class="batch-btn" data-testid="batch-add-tag">
            <Tag :size="14" />
            <span class="hidden md:inline">{{ t('batch.addTag') }}</span>
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContentLw class="z-50 min-w-[186px]" align="start">
          <div class="batch-tag-header">{{ t('batch.addTagHeader') }}</div>
          <DropdownMenuItemLw
            v-for="tag in tagStore.tags"
            :key="tag.id"
            :data-testid="`batch-tag-option-${tag.data.name}`"
            @select="onPickTag(tag)"
          >
            <span
              class="h-[9px] w-[9px] rounded-full shrink-0"
              :style="{ background: tag.data.color }"
            />
            {{ tag.data.name }}
          </DropdownMenuItemLw>
          <div v-if="tagStore.tags.length === 0" class="batch-tag-empty">
            {{ t('batch.noTags') }}
          </div>
        </DropdownMenuContentLw>
      </DropdownMenuRoot>

      <button type="button" class="batch-btn" data-testid="batch-copy-urls" @click="copyUrls">
        <Copy :size="14" />
        <span class="hidden md:inline">{{ t('batch.copyUrls') }}</span>
      </button>

      <span class="batch-divider" aria-hidden="true" />

      <button
        type="button"
        class="batch-btn batch-btn--destructive"
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

.batch-btn:hover {
  background: var(--color-secondary);
  color: var(--color-foreground);
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

.batch-tag-header {
  padding: 6px 8px 4px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-muted-foreground);
}

.batch-tag-empty {
  padding: 6px 8px;
  font-size: 12.5px;
  color: var(--color-muted-foreground);
}

@media (prefers-reduced-motion: reduce) {
  .batch-bar-clip {
    transition: none;
  }
}
</style>
