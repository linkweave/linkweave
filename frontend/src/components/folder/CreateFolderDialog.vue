<script setup lang="ts">
import {
  ColorInputLw,
  DialogLw,
  DialogFooterLw,
  FolderSelectLw,
  FormFieldLw,
  InputLw,
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
    color: '',
  },
})

const [name, nameAttrs] = defineField('name')
const [selectedParentId] = defineField('parentId')
const [color, colorAttrs] = defineField('color')

const folders = computed(() => folderStore.folders)

useFormDialog(toRef(props, 'open'), () =>
  resetForm({
    values: { collectionId: props.collectionId, parentId: props.parentId, name: '', color: '' },
  }),
)

const onSubmit = handleSubmit(async (values) => {
  try {
    await folderStore.createFolder({ ...values, color: values.color || undefined })
    emit('update:open', false)
    emit('created')
  } catch (err) {
    notification.handleApiError(err, t('folder.createError'))
  }
})
</script>

<template>
  <DialogLw :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('folder.createTitle') }}</template>

    <form id="create-folder-form" @submit.prevent="onSubmit" class="space-y-4">
      <FormFieldLw :label="t('folder.name')" for-id="folder-name" :error="errors.name" required>
        <InputLw
          id="folder-name"
          v-model="name"
          v-bind="nameAttrs"
          type="text"
          :placeholder="t('folder.namePlaceholder')"
        />
      </FormFieldLw>

      <FormFieldLw :label="t('folder.parentFolder')" for-id="folder-parent">
        <FolderSelectLw
          id="folder-parent"
          v-model="selectedParentId"
          :folders="folders"
          :placeholder="t('folder.noParent')"
          direction="down"
        />
      </FormFieldLw>

      <FormFieldLw :label="t('folder.color')" for-id="folder-color" :error="errors.color">
        <ColorInputLw
          :model-value="color"
          :attrs="colorAttrs"
          input-id="folder-color"
          @update:model-value="color = $event"
        />
      </FormFieldLw>
    </form>

    <template #footer>
      <DialogFooterLw
        submit-form="create-folder-form"
        :submit-label="t('common.create')"
        :submitting="isSubmitting"
        @cancel="emit('update:open', false)"
      />
    </template>
  </DialogLw>
</template>
