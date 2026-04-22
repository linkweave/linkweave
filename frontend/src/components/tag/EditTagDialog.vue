<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { DialogCl, ButtonCl, FormFieldCl, ColorInputCl } from '@/components/ui'
import { useTagStore } from '@/stores/tag'
import { useNotificationStore } from '@/stores/notification'
import { tagSaveSchema } from '@/schemas/tag'
import { useFormDialog } from '@/composables/useFormDialog'
import type { TagJson } from '@/api/generated'
import { toRef } from 'vue'

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

    <form @submit.prevent="onSubmit" class="space-y-4">
      <FormFieldCl :label="t('tag.name')" for-id="edit-tag-name" :error="errors.name" required>
        <input
          id="edit-tag-name"
          v-model="name"
          v-bind="nameAttrs"
          type="text"
          maxlength="50"
          data-testid="edit-tag-name-input"
          :placeholder="t('tag.namePlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
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

      <div class="flex justify-end gap-2">
        <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.cancel') }}
        </ButtonCl>
        <ButtonCl type="submit" data-testid="edit-tag-submit" :disabled="isSubmitting">
          {{ isSubmitting ? t('common.loading') : t('common.save') }}
        </ButtonCl>
      </div>
    </form>
  </DialogCl>
</template>
