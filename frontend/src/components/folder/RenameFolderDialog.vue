<script setup lang="ts">
import type { FolderJson } from '@/api/generated'
import {
  ColorInputCl,
  DialogCl,
  DialogFooterCl,
  FolderSelectCl,
  FormFieldCl,
  InputCl,
} from '@/components/ui'
import { useFormDialog } from '@/composables/useFormDialog'
import { folderSaveSchema } from '@/schemas/folder'
import { useFolderStore } from '@/stores/folder'
import { useNotificationStore } from '@/stores/notification'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { computed, toRef } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const folderStore = useFolderStore()
const notification = useNotificationStore()

interface Props {
  folder: FolderJson | null
  open?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  saved: []
}>()

const { defineField, handleSubmit, errors, resetForm, isSubmitting } = useForm({
  validationSchema: toTypedSchema(folderSaveSchema(t)),
  initialValues: { collectionId: '', name: '', color: '' },
})

const [name, nameAttrs] = defineField('name')
const [parentId] = defineField('parentId')
const [color, colorAttrs] = defineField('color')

function getDescendantIds(folderId: string): Set<string> {
  const ids = new Set<string>()
  const queue = [folderId]
  while (queue.length > 0) {
    const current = queue.pop()!
    ids.add(current)
    for (const f of folderStore.folders) {
      if (f.data.parentId === current && !ids.has(f.id)) {
        queue.push(f.id)
      }
    }
  }
  return ids
}

const excludeIds = computed(() => {
  if (!props.folder) return new Set<string>()
  return getDescendantIds(props.folder.id)
})

useFormDialog(toRef(props, 'open'), () => {
  if (props.folder) {
    resetForm({
      values: {
        collectionId: props.folder.data.collectionId,
        name: props.folder.data.name,
        parentId: props.folder.data.parentId ?? undefined,
        color: props.folder.data.color ?? '',
      },
    })
  }
})

const onSubmit = handleSubmit(async (values) => {
  if (!props.folder) return

  try {
    await folderStore.renameFolder(props.folder.id, { ...values, color: values.color || undefined })
    emit('update:open', false)
    emit('saved')
  } catch (err) {
    notification.handleApiError(err, t('folder.renameError'))
  }
})
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('folder.renameTitle') }}</template>

    <form @submit.prevent="onSubmit" class="space-y-4">
      <FormFieldCl
        :label="t('folder.name')"
        for-id="rename-folder-name"
        :error="errors.name"
        required
      >
        <InputCl
          id="rename-folder-name"
          v-model="name"
          v-bind="nameAttrs"
          type="text"
          :placeholder="t('folder.namePlaceholder')"
        />
      </FormFieldCl>

      <FormFieldCl :label="t('folder.parentFolder')" for-id="rename-folder-parent">
        <FolderSelectCl
          id="rename-folder-parent"
          v-model="parentId"
          :folders="folderStore.folders"
          :exclude-ids="excludeIds"
          :placeholder="t('folder.noParent')"
          direction="down"
        />
      </FormFieldCl>

      <FormFieldCl :label="t('folder.color')" for-id="rename-folder-color" :error="errors.color">
        <ColorInputCl
          :model-value="color"
          :attrs="colorAttrs"
          input-id="rename-folder-color"
          @update:model-value="color = $event"
        />
      </FormFieldCl>

      <DialogFooterCl
        :submit-label="t('common.save')"
        :submitting="isSubmitting"
        @cancel="emit('update:open', false)"
      />
    </form>
  </DialogCl>
</template>
