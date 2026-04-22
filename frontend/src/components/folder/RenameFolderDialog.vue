<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { DialogCl, ButtonCl, FormFieldCl, FolderSelectCl } from '@/components/ui'
import { useFolderStore } from '@/stores/folder'
import { useNotificationStore } from '@/stores/notification'
import { folderSaveSchema } from '@/schemas/folder'
import { useFormDialog } from '@/composables/useFormDialog'
import type { FolderJson } from '@/api/generated'
import { toRef } from 'vue'

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
  initialValues: { collectionId: '', name: '' },
})

const [name, nameAttrs] = defineField('name')
const [parentId] = defineField('parentId')

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
      },
    })
  }
})

const onSubmit = handleSubmit(async (values) => {
  if (!props.folder) return

  try {
    await folderStore.renameFolder(props.folder.id, values)
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
      <FormFieldCl :label="t('folder.name')" for-id="rename-folder-name" :error="errors.name" required>
        <input
          id="rename-folder-name"
          v-model="name"
          v-bind="nameAttrs"
          type="text"
          :placeholder="t('folder.namePlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
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
