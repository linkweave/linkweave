<script setup lang="ts">
import type { TagJson } from '@/api/generated'
import { ColorInputCl, DialogCl, DialogFooterCl, FormFieldCl, InputCl } from '@/components/ui'
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
  tag: TagJson | null
  open?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  saved: []
}>()

const { defineField, handleSubmit, errors, resetForm, isSubmitting } = useForm({
  validationSchema: toTypedSchema(tagSaveSchema(t)),
  initialValues: { collectionId: '', name: '' },
})

const [name, nameAttrs] = defineField('name')
const [color, colorAttrs] = defineField('color')

useFormDialog(toRef(props, 'open'), () => {
  if (props.tag) {
    resetForm({
      values: {
        collectionId: props.tag.data.collectionId,
        name: props.tag.data.name,
        color: props.tag.data.color ?? '',
      },
    })
  }
})

const onSubmit = handleSubmit(async (values) => {
  if (!props.tag) return

  try {
    await tagStore.updateTag(props.tag.id, values)
    emit('update:open', false)
    emit('saved')
  } catch (err) {
    notification.handleApiError(err, t('tag.updateError'))
  }
})
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('tag.editTitle') }}</template>

    <form id="edit-tag-form" @submit.prevent="onSubmit" class="space-y-4">
      <FormFieldCl :label="t('tag.name')" for-id="edit-tag-name" :error="errors.name" required>
        <InputCl
          id="edit-tag-name"
          v-model="name"
          v-bind="nameAttrs"
          type="text"
          maxlength="50"
          data-testid="edit-tag-name-input"
          :placeholder="t('tag.namePlaceholder')"
        />
      </FormFieldCl>

      <FormFieldCl :label="t('tag.color')" for-id="edit-tag-color" :error="errors.color">
        <ColorInputCl
          :model-value="color"
          :attrs="colorAttrs"
          input-id="edit-tag-color"
          @update:model-value="color = $event"
        />
      </FormFieldCl>
    </form>

    <template #footer>
      <DialogFooterCl
        submit-form="edit-tag-form"
        :submit-label="t('common.save')"
        :submitting="isSubmitting"
        submit-testid="edit-tag-submit"
        @cancel="emit('update:open', false)"
      />
    </template>
  </DialogCl>
</template>
