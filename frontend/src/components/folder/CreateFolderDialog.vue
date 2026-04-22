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
import { toRef } from 'vue'

const { t } = useI18n()
const folderStore = useFolderStore()
const notification = useNotificationStore()

interface Props {
  collectionId: string
  parentId?: string
  open?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  created: []
}>()

const { defineField, handleSubmit, errors, resetForm, isSubmitting } = useForm({
  validationSchema: toTypedSchema(folderSaveSchema(t)),
  initialValues: {
    collectionId: props.collectionId,
    parentId: props.parentId,
    name: '',
  },
})

const [name, nameAttrs] = defineField('name')
const [parentId] = defineField('parentId')

const folders = computed(() => folderStore.folders)

useFormDialog(toRef(props, 'open'), () =>
  resetForm({
    values: { collectionId: props.collectionId, parentId: props.parentId, name: '' },
  }),
)

const onSubmit = handleSubmit(async (values) => {
  try {
    await folderStore.createFolder(values)
    emit('update:open', false)
    emit('created')
  } catch (err) {
    notification.handleApiError(err, t('folder.createError'))
  }
})
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('folder.createTitle') }}</template>

    <form @submit.prevent="onSubmit" class="space-y-4">
      <FormFieldCl :label="t('folder.name')" for-id="folder-name" :error="errors.name" required>
        <input
          id="folder-name"
          v-model="name"
          v-bind="nameAttrs"
          type="text"
          :placeholder="t('folder.namePlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </FormFieldCl>

      <FormFieldCl :label="t('folder.parentFolder')" for-id="folder-parent">
        <FolderSelectCl
          id="folder-parent"
          v-model="parentId"
          :folders="folders"
          :placeholder="t('folder.noParent')"
          direction="down"
        />
      </FormFieldCl>

      <div class="flex justify-end gap-2">
        <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.cancel') }}
        </ButtonCl>
        <ButtonCl type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? t('common.loading') : t('common.create') }}
        </ButtonCl>
      </div>
    </form>
  </DialogCl>
</template>
