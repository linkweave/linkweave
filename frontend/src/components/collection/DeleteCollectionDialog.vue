<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { DialogCl, ButtonCl, FormFieldCl } from '@/components/ui'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import { collectionDeleteSchema } from '@/schemas/collection'
import { useFormDialog } from '@/composables/useFormDialog'
import { toRef, computed } from 'vue'
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
  validationSchema: toTypedSchema(collectionDeleteSchema),
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
    <form class="space-y-4" @submit.prevent="onSubmit">
      <p class="text-sm text-muted-foreground">{{ t('collectionManage.deleteConfirm') }}</p>
      <FormFieldCl
        :label="t('collectionManage.typeToConfirm', { name: collectionName })"
        for-id="delete-confirm-name"
        :error="errors.confirmName"
      >
        <input
          id="delete-confirm-name"
          v-model="confirmName"
          v-bind="confirmNameAttrs"
          type="text"
          data-testid="delete-confirm-name-input"
          :placeholder="collectionName"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </FormFieldCl>
      <div class="flex justify-end gap-2">
        <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.cancel') }}
        </ButtonCl>
        <ButtonCl
          type="submit"
          variant="destructive"
          data-testid="collection-delete-submit-btn"
          :disabled="isSubmitting || !canDelete"
        >
          {{ isSubmitting ? t('common.loading') : t('common.delete') }}
        </ButtonCl>
      </div>
    </form>
  </DialogCl>
</template>
