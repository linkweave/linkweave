<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { DialogCl, ButtonCl, FormFieldCl } from '@/components/ui'
import { useBookmarkStore } from '@/stores/bookmark'
import { useFolderStore } from '@/stores/folder'
import { useNotificationStore } from '@/stores/notification'
import { bookmarkMoveSchema } from '@/schemas/bookmark'
import { useFormDialog } from '@/composables/useFormDialog'
import type { BookmarkJson } from '@/api/generated'
import { toRef } from 'vue'

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

const { defineField, handleSubmit, resetForm, isSubmitting } = useForm({
  validationSchema: toTypedSchema(bookmarkMoveSchema(t)),
  initialValues: { collectionId: '' },
})

const [folderId, folderIdAttrs] = defineField('folderId')

const folderOptions = computed(() =>
  folderStore.folders.map(f => ({ id: f.id, name: f.data.name }))
)

useFormDialog(toRef(props, 'open'), () => {
  if (props.bookmark) {
    resetForm({
      values: {
        collectionId: props.bookmark.data.collectionId,
        folderId: props.bookmark.data.folderId ?? undefined,
      },
    })
  }
})

const onSubmit = handleSubmit(async (values) => {
  if (!props.bookmark) return

  try {
    await bookmarkStore.moveBookmarkToFolder(props.bookmark.id, values)
    emit('update:open', false)
    emit('moved')
  } catch (err) {
    notification.handleApiError(err, t('bookmark.moveError'))
  }
})
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('bookmark.moveToFolder') }}</template>

    <form @submit.prevent="onSubmit" class="space-y-4">
      <FormFieldCl :label="t('bookmark.folder')" for-id="move-bookmark-folder">
        <select
          id="move-bookmark-folder"
          v-model="folderId"
          v-bind="folderIdAttrs"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        >
          <option :value="undefined">{{ t('bookmark.noFolder') }}</option>
          <option v-for="opt in folderOptions" :key="opt.id" :value="opt.id">
            {{ opt.name }}
          </option>
        </select>
      </FormFieldCl>

      <div class="flex justify-end gap-2">
        <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.cancel') }}
        </ButtonCl>
        <ButtonCl type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? t('common.loading') : t('common.save') }}
        </ButtonCl>
      </div>
    </form>
  </DialogCl>
</template>
