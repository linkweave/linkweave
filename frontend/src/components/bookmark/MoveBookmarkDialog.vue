<script setup lang="ts">
import type { BookmarkJson } from '@/api/generated'
import { DialogLw, DialogFooterLw, FolderSelectLw, FormFieldLw } from '@/components/ui'
import { useFormDialog } from '@/composables/useFormDialog'
import { bookmarkMoveSchema } from '@/schemas/bookmark'
import { useBookmarkStore } from '@/stores/bookmark'
import { useCollectionStore } from '@/stores/collection'
import { useFolderStore } from '@/stores/folder'
import { useNotificationStore } from '@/stores/notification'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { computed, toRef } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const folderStore = useFolderStore()
const collectionStore = useCollectionStore()
const notification = useNotificationStore()

// Same dialog for the single-bookmark move and the batch move:
// pass `bookmark` for single, `bookmarkIds` (without `bookmark`) for batch.
interface Props {
  bookmark: BookmarkJson | null
  bookmarkIds?: string[]
  open?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  moved: [folderId?: string | null]
}>()

const isBatchMode = computed(() => !props.bookmark)

const { defineField, handleSubmit, resetForm, isSubmitting } = useForm({
  validationSchema: toTypedSchema(bookmarkMoveSchema(t)),
  initialValues: { collectionId: '' },
})

const [folderId] = defineField('folderId')

useFormDialog(toRef(props, 'open'), () => {
  resetForm({
    values: props.bookmark
      ? {
          collectionId: props.bookmark.data.collectionId,
          folderId: props.bookmark.data.folderId ?? undefined,
        }
      : {
          collectionId: collectionStore.currentCollectionId ?? '',
          folderId: undefined,
        },
  })
})

const onSubmit = handleSubmit(async (values) => {
  const bookmark = props.bookmark
  if (bookmark) {
    try {
      await bookmarkStore.moveBookmarkToFolder(bookmark.id, values)
      emit('update:open', false)
      emit('moved', values.folderId ?? null)
    } catch (err) {
      notification.handleApiError(err, t('bookmark.moveError'))
    }
    return
  }

  if (!props.bookmarkIds?.length) return
  try {
    await bookmarkStore.batchMove(props.bookmarkIds, values.folderId ?? null)
    emit('update:open', false)
    emit('moved', values.folderId ?? null)
  } catch {
    // Atomic rollback on the backend (BR-097): toast, close the dialog; the
    // selection stays untouched so the user can retry.
    notification.error(t('batch.moveError'))
    emit('update:open', false)
  }
})
</script>

<template>
  <DialogLw :open="open" @update:open="emit('update:open', $event)">
    <template #title>
      {{ isBatchMode ? t('batch.moveTitle', { count: bookmarkIds?.length ?? 0 }) : t('bookmark.moveToFolder') }}
    </template>
    <template v-if="isBatchMode" #description>{{ t('batch.moveSubtitle') }}</template>

    <form id="move-bookmark-form" @submit.prevent="onSubmit" class="space-y-4">
      <FormFieldLw :label="t('bookmark.folder')" for-id="move-bookmark-folder">
        <FolderSelectLw
          id="move-bookmark-folder"
          v-model="folderId"
          :folders="folderStore.folders"
          :placeholder="isBatchMode ? t('batch.noFolderRoot') : t('bookmark.noFolder')"
          direction="down"
        />
      </FormFieldLw>
    </form>

    <template #footer>
      <DialogFooterLw
        submit-form="move-bookmark-form"
        :submit-label="isBatchMode ? t('batch.moveHere') : t('common.save')"
        :submitting="isSubmitting"
        @cancel="emit('update:open', false)"
      />
    </template>
  </DialogLw>
</template>
