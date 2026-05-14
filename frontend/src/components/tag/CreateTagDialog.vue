<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { DialogCl, DialogFooterCl, FormFieldCl, InputCl } from '@/components/ui'
import { useTagStore } from '@/stores/tag'
import { useNotificationStore } from '@/stores/notification'
import { tagSaveSchema } from '@/schemas/tag'
import { useFormDialog } from '@/composables/useFormDialog'
import { toRef } from 'vue'

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
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('tag.createTitle') }}</template>

    <form @submit.prevent="onSubmit" class="space-y-4">
      <FormFieldCl :label="t('tag.name')" for-id="create-tag-name" :error="errors.name" required>
        <InputCl
          id="create-tag-name"
          v-model="name"
          v-bind="nameAttrs"
          type="text"
          maxlength="50"
          data-testid="create-tag-name-input"
          :placeholder="t('tag.namePlaceholder')"
        />
      </FormFieldCl>

      <DialogFooterCl
        :submit-label="t('common.create')"
        :submitting="isSubmitting"
        submit-testid="create-tag-submit"
        @cancel="emit('update:open', false)"
      />
    </form>
  </DialogCl>
</template>
