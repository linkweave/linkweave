<script setup lang="ts">
import { DialogCl, DialogFooterCl, FormFieldCl, InputCl } from '@/components/ui'
import { useFormDialog } from '@/composables/useFormDialog'
import { collectionDeleteSchema } from '@/schemas/collection'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { computed, toRef } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{
  open: boolean
  collectionId: string
  collectionName: string
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  deleted: []
}>()

const { t } = useI18n()
const collectionStore = useCollectionStore()
const notification = useNotificationStore()

const { defineField, handleSubmit, errors, resetForm, isSubmitting } = useForm({
  validationSchema: toTypedSchema(collectionDeleteSchema(t)),
  initialValues: { confirmName: '', expectedName: '' },
})

const [confirmName, confirmNameAttrs] = defineField('confirmName')

const canDelete = computed(() => confirmName.value === props.collectionName)

useFormDialog(toRef(props, 'open'), () =>
  resetForm({ values: { confirmName: '', expectedName: props.collectionName } }),
)

const onSubmit = handleSubmit(async () => {
  const ok = await collectionStore.deleteCollection(props.collectionId)
  if (ok) {
    emit('update:open', false)
    notification.success(t('collectionManage.deleteTitle'))
  }
})
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('collectionManage.deleteTitle') }}</template>
    <form id="delete-collection-form" class="space-y-4" @submit.prevent="onSubmit">
      <p class="text-sm text-muted-foreground">{{ t('collectionManage.deleteConfirm') }}</p>
      <FormFieldCl
        :label="t('collectionManage.typeToConfirm', { name: collectionName })"
        for-id="delete-confirm-name"
        :error="errors.confirmName"
      >
        <InputCl
          id="delete-confirm-name"
          v-model="confirmName"
          v-bind="confirmNameAttrs"
          type="text"
          data-testid="delete-confirm-name-input"
          :placeholder="collectionName"
        />
      </FormFieldCl>
    </form>

    <template #footer>
      <DialogFooterCl
        submit-form="delete-collection-form"
        :submit-label="t('common.delete')"
        :submitting="isSubmitting"
        :submit-disabled="!canDelete"
        submit-variant="destructive"
        submit-testid="collection-delete-submit-btn"
        @cancel="emit('update:open', false)"
      />
    </template>
  </DialogCl>
</template>
