<script setup lang="ts">
import { DialogCl, DialogFooterCl, FormFieldCl, InputCl, SelectCl } from '@/components/ui'
import { useFormDialog } from '@/composables/useFormDialog'
import { apiKeyCreateSchema } from '@/schemas/apiKey'
import { useApiKeyStore } from '@/stores/apiKey'
import { useNotificationStore } from '@/stores/notification'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { toRef } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{ open: boolean }>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  created: [rawKey: string]
}>()

const { t } = useI18n()
const notification = useNotificationStore()
const store = useApiKeyStore()

const { defineField, handleSubmit, errors, resetForm, isSubmitting } = useForm({
  validationSchema: toTypedSchema(apiKeyCreateSchema(t)),
  initialValues: { name: '', expiresIn: '' },
})

const [name, nameAttrs] = defineField('name')
const [expiresIn] = defineField('expiresIn')

useFormDialog(toRef(props, 'open'), () => resetForm({ values: { name: '', expiresIn: '' } }))

const onSubmit = handleSubmit(async (values) => {
  try {
    const rawKey = await store.create({
      name: values.name.trim(),
      expiresIn: values.expiresIn || undefined,
    })
    emit('update:open', false)
    emit('created', rawKey)
  } catch {
    notification.handleApiError(null, t('apiKeys.createError'))
  }
})
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('apiKeys.createTitle') }}</template>
    <template #description>{{ t('apiKeys.createDescription') }}</template>

    <form id="create-api-key-form" class="space-y-4" @submit.prevent="onSubmit">
      <FormFieldCl :label="t('apiKeys.fieldName')" for-id="api-key-name" :error="errors.name" required>
        <InputCl
          id="api-key-name"
          v-model="name"
          v-bind="nameAttrs"
          type="text"
          maxlength="100"
          :placeholder="t('apiKeys.namePlaceholder')"
          data-testid="api-key-name-input"
          autocomplete="off"
        />
      </FormFieldCl>

      <FormFieldCl :label="t('apiKeys.fieldExpires')" for-id="api-key-expires">
        <SelectCl id="api-key-expires" v-model="expiresIn">
          <option value="">{{ t('apiKeys.expiresNever') }}</option>
          <option value="30d">{{ t('apiKeys.expires30d') }}</option>
          <option value="90d">{{ t('apiKeys.expires90d') }}</option>
          <option value="1y">{{ t('apiKeys.expires1y') }}</option>
        </SelectCl>
      </FormFieldCl>
    </form>

    <template #footer>
      <DialogFooterCl
        submit-form="create-api-key-form"
        :submit-label="t('apiKeys.createSubmit')"
        :submitting="isSubmitting"
        submit-testid="api-key-create-submit"
        @cancel="emit('update:open', false)"
      />
    </template>
  </DialogCl>
</template>
