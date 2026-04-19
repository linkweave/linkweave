<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { DialogCl, ButtonCl, FormFieldCl } from '@/components/ui'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import { collectionCreateSchema } from '@/schemas/collection'
import { useFormDialog } from '@/composables/useFormDialog'
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
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('collectionManage.createTitle') }}</template>
    <form @submit.prevent="onSubmit" class="space-y-4">
      <FormFieldCl :label="t('collectionManage.name')" for-id="create-collection-name" :error="errors.name" required>
        <input
          id="create-collection-name"
          v-model="name"
          v-bind="nameAttrs"
          type="text"
          maxlength="255"
          data-testid="create-collection-name-input"
          :placeholder="t('collectionManage.namePlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </FormFieldCl>
      <div class="flex justify-end gap-2">
        <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.cancel') }}
        </ButtonCl>
        <ButtonCl type="submit" data-testid="collection-create-submit-btn" :disabled="isSubmitting">
          {{ isSubmitting ? t('common.loading') : t('common.create') }}
        </ButtonCl>
      </div>
    </form>
  </DialogCl>
</template>
