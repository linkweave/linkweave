<script setup lang="ts">
import { DialogLw, DialogFooterLw, FormFieldLw, InputLw } from '@/components/ui'
import { useFormDialog } from '@/composables/useFormDialog'
import { tagSaveSchema } from '@/schemas/tag'
import { useNotificationStore } from '@/stores/notification'
import { useTagStore } from '@/stores/tag'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { toRef } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const tagStore = useTagStore()
const notification = useNotificationStore()

interface Props {
  collectionId: string
  open?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  created: []
}>()

const { defineField, handleSubmit, errors, resetForm, isSubmitting } = useForm({
  validationSchema: toTypedSchema(tagSaveSchema(t)),
  initialValues: { collectionId: props.collectionId, name: '' },
})

const [name, nameAttrs] = defineField('name')

useFormDialog(toRef(props, 'open'), () =>
  resetForm({ values: { collectionId: props.collectionId, name: '' } }),
)

const onSubmit = handleSubmit(async (values) => {
  try {
    await tagStore.createTag(values)
    emit('update:open', false)
    emit('created')
  } catch (err) {
    notification.handleApiError(err, t('tag.createError'))
  }
})
</script>

<template>
  <DialogLw :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('tag.createTitle') }}</template>

    <form id="create-tag-form" @submit.prevent="onSubmit" class="space-y-4">
      <FormFieldLw :label="t('tag.name')" for-id="create-tag-name" :error="errors.name" required>
        <InputLw
          id="create-tag-name"
          v-model="name"
          v-bind="nameAttrs"
          type="text"
          maxlength="50"
          data-testid="create-tag-name-input"
          :placeholder="t('tag.namePlaceholder')"
        />
      </FormFieldLw>
    </form>

    <template #footer>
      <DialogFooterLw
        submit-form="create-tag-form"
        :submit-label="t('common.create')"
        :submitting="isSubmitting"
        submit-testid="create-tag-submit"
        @cancel="emit('update:open', false)"
      />
    </template>
  </DialogLw>
</template>
