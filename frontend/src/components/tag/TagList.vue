<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Pencil, Trash2, X } from 'lucide-vue-next'
import { useTagStore } from '@/stores/tag'
import { useBookmarkStore } from '@/stores/bookmark'
import { useNotificationStore } from '@/stores/notification'
import CreateTagDialog from './CreateTagDialog.vue'
import EditTagDialog from './EditTagDialog.vue'
import { ConfirmDialog } from '@/components/ui'
import type { TagJson } from '@/api/generated'

const { t } = useI18n()
const tagStore = useTagStore()
const bookmarkStore = useBookmarkStore()
const notification = useNotificationStore()

const bookmarkCountByTag = computed(() => {
  const counts = new Map<string, number>()
  for (const tag of tagStore.tags) {
    counts.set(tag.id, 0)
  }
  for (const bookmark of bookmarkStore.bookmarks) {
    if (bookmark.data.tagIds) {
      for (const tagId of bookmark.data.tagIds) {
        counts.set(tagId, (counts.get(tagId) ?? 0) + 1)
      }
    }
  }
  return counts
})

interface Props {
  className?: string
  collectionId?: string
}

const props = defineProps<Props>()

const showCreateDialog = ref(false)
const editingTag = ref<TagJson | null>(null)
const showEditDialog = ref(false)
const deletingTag = ref<TagJson | null>(null)
const showDeleteConfirm = ref(false)

function handleEdit(tag: TagJson) {
  editingTag.value = tag
  showEditDialog.value = true
}

function handleEditDialogUpdate(open: boolean) {
  showEditDialog.value = open
  if (!open) editingTag.value = null
}

function handleDelete(tag: TagJson) {
  deletingTag.value = tag
  showDeleteConfirm.value = true
}

function handleDeleteDialogUpdate(open: boolean) {
  showDeleteConfirm.value = open
  if (!open) deletingTag.value = null
}

async function confirmDelete() {
  if (!deletingTag.value) return
  try {
    await tagStore.deleteTag(deletingTag.value.id)
  } catch (err) {
    void notification.handleApiError(err, t('tag.deleteError'))
  } finally {
    deletingTag.value = null
    showDeleteConfirm.value = false
  }
}
</script>

<template>
  <div :class="className">
    <div v-if="tagStore.tags.length === 0" class="text-sm text-muted-foreground p-2">
      {{ t('tag.none') }}
    </div>

    <div v-else class="space-y-0.5">
      <button
        v-for="tag in tagStore.tags"
        :key="tag.id"
        :data-testid="`tag-row-${tag.data.name}`"
        class="group w-full flex items-center gap-2 rounded-md px-2 py-1.5 text-sm cursor-pointer transition-colors text-left"
        :class="tagStore.selectedTagIds.has(tag.id)
          ? 'bg-accent text-accent-foreground'
          : 'hover:bg-accent hover:text-accent-foreground text-muted-foreground'"
        @click="tagStore.toggleTag(tag.id)"
      >
        <span
          class="h-3 w-3 shrink-0 rounded-full"
          :style="{ backgroundColor: tag.data.color ?? '#64748b' }"
        />
        <span class="truncate flex-1">{{ tag.data.name }}</span>
        <span v-if="(bookmarkCountByTag.get(tag.id) ?? 0) > 0" class="text-xs opacity-40 shrink-0">{{ bookmarkCountByTag.get(tag.id) }}</span>
        <span class="inline-flex gap-0.5 sm:opacity-0 sm:group-hover:opacity-100 transition-opacity">
          <button
            :aria-label="t('common.edit')"
            data-testid="tag-edit-btn"
            class="h-5 w-5 inline-flex items-center justify-center rounded hover:bg-primary hover:text-primary-foreground"
            @click.stop="handleEdit(tag)"
          >
            <Pencil class="h-3 w-3" />
          </button>
          <button
            :aria-label="t('common.delete')"
            data-testid="tag-delete-btn"
            class="h-5 w-5 inline-flex items-center justify-center rounded hover:bg-destructive hover:text-destructive-foreground"
            @click.stop="handleDelete(tag)"
          >
            <Trash2 class="h-3 w-3" />
          </button>
        </span>
      </button>

      <button
        v-if="tagStore.selectedTagIds.size > 0"
        class="w-full flex items-center gap-2 rounded-md px-2 py-1 text-xs cursor-pointer transition-colors text-muted-foreground hover:text-foreground"
        @click="tagStore.clearTagFilter()"
      >
        <X class="h-3 w-3" />
        {{ t('tag.clearFilter') }}
      </button>
    </div>

    <button
      v-if="props.collectionId"
      data-testid="new-tag-btn"
      class="w-full flex items-center gap-2 rounded-md px-2 py-1.5 text-sm cursor-pointer transition-colors text-muted-foreground hover:text-foreground mt-1"
      @click="showCreateDialog = true"
    >
      <Plus class="h-3.5 w-3.5" />
      {{ t('tag.createShort') }}
    </button>

    <CreateTagDialog
      v-if="props.collectionId"
      :collection-id="props.collectionId"
      v-model:open="showCreateDialog"
    />

    <EditTagDialog
      :tag="editingTag"
      v-model:open="showEditDialog"
      @update:open="handleEditDialogUpdate"
    />

    <ConfirmDialog
      v-model:open="showDeleteConfirm"
      :title="t('tag.deleteTitle')"
      :message="t('tag.deleteConfirm')"
      @confirmed="confirmDelete"
      @update:open="handleDeleteDialogUpdate"
    />
  </div>
</template>
