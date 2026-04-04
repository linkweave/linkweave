<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { DialogCl, ButtonCl } from '@/components/ui'
import { useBookmarkStore } from '@/stores/bookmark'
import { useFolderStore } from '@/stores/folder'
import { useNotificationStore } from '@/stores/notification'
import type { BookmarkJson, BookmarkMoveJson } from '@/api/generated'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const folderStore = useFolderStore()
const notification = useNotificationStore()

interface Props {
  bookmark: BookmarkJson | null
  open?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  moved: []
}>()

const selectedFolderId = ref<string | undefined>(undefined)
const loading = ref(false)

const folderOptions = computed(() =>
  folderStore.folders.map(f => ({ id: f.id, name: f.data.name }))
)

watch(() => props.open, (val) => {
  if (val && props.bookmark) {
    selectedFolderId.value = props.bookmark.data.folderId ?? undefined
  }
})

async function handleSubmit() {
  if (!props.bookmark) return

  loading.value = true

  const data: BookmarkMoveJson = {
    collectionId: props.bookmark.data.collectionId,
    folderId: selectedFolderId.value,
  }

  try {
    await bookmarkStore.moveBookmarkToFolder(props.bookmark.id, data)
    emit('update:open', false)
    emit('moved')
  } catch (err) {
    void notification.handleApiError(err, t('bookmark.moveError'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('bookmark.moveToFolder') }}</template>

    <form @submit.prevent="handleSubmit" class="space-y-4">
      <div class="space-y-2">
        <label for="move-bookmark-folder" class="text-sm font-medium">{{ t('bookmark.folder') }}</label>
        <select
          id="move-bookmark-folder"
          v-model="selectedFolderId"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        >
          <option :value="undefined">{{ t('bookmark.noFolder') }}</option>
          <option v-for="opt in folderOptions" :key="opt.id" :value="opt.id">
            {{ opt.name }}
          </option>
        </select>
      </div>

      <div class="flex justify-end gap-2">
        <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.cancel') }}
        </ButtonCl>
        <ButtonCl type="submit" :disabled="loading">
          {{ loading ? t('common.loading') : t('common.save') }}
        </ButtonCl>
      </div>
    </form>
  </DialogCl>
</template>
