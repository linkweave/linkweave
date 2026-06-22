<script setup lang="ts">
import { DialogLw, DialogFooterLw, FormFieldLw, InputLw } from '@/components/ui'
import { useFormDialog } from '@/composables/useFormDialog'
import { collectionCreateSchema } from '@/schemas/collection'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { toRef } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  created: []
}>()

const { t } = useI18n()
const collectionStore = useCollectionStore()
const notification = useNotificationStore()

const { defineField, handleSubmit, errors, resetForm, isSubmitting } = useForm({
  validationSchema: toTypedSchema(collectionCreateSchema(t)),
  initialValues: { name: '' },
})

const [name, nameAttrs] = defineField('name')

useFormDialog(toRef(props, 'open'), () => resetForm({ values: { name: '' } }))

const onSubmit = handleSubmit(async (values) => {
  const result = await collectionStore.createCollection(values.name)
  if (result) {
    emit('update:open', false)
    notification.success(t('collectionManage.createTitle'))
  }
})
</script>

<template>
  <DialogLw :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('collectionManage.createTitle') }}</template>
    <form id="create-collection-form" @submit.prevent="onSubmit" class="space-y-4">
      <FormFieldLw
        :label="t('collectionManage.name')"
        for-id="create-collection-name"
        :error="errors.name"
        required
      >
        <InputLw
          id="create-collection-name"
          v-model="name"
          v-bind="nameAttrs"
          type="text"
          maxlength="255"
          data-testid="create-collection-name-input"
          :placeholder="t('collectionManage.namePlaceholder')"
        />
      </FormFieldLw>
    </form>

    <template #footer>
      <DialogFooterLw
        submit-form="create-collection-form"
        :submit-label="t('common.create')"
        :submitting="isSubmitting"
        submit-testid="collection-create-submit-btn"
        @cancel="emit('update:open', false)"
      />
    </template>
  </DialogLw>
</template>
