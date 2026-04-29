<script setup lang="ts">
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { DialogCl, ButtonCl, FormFieldCl } from '@/components/ui'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import { collectionUpdateSchema } from '@/schemas/collection'
import { useFormDialog } from '@/composables/useFormDialog'
import { toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { CollectionResourceApi } from '@/api/generated'
import { config } from '@/api'

const props = defineProps<{
  open: boolean
  collectionId: string
  currentName: string
  isOwner?: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  saved: []
}>()

const { t } = useI18n()
const collectionStore = useCollectionStore()
const notification = useNotificationStore()
const collectionApi = new CollectionResourceApi(config)

const { defineField, handleSubmit, errors, resetForm, isSubmitting } = useForm({
  validationSchema: toTypedSchema(collectionUpdateSchema(t)),
  initialValues: { name: '', faviconAllowlist: '' },
})

const [name, nameAttrs] = defineField('name')
const [faviconAllowlist, faviconAllowlistAttrs] = defineField('faviconAllowlist')

useFormDialog(toRef(props, 'open'), async () => {
  resetForm({ values: { name: props.currentName, faviconAllowlist: '' } })
  if (!props.collectionId) return
  try {
    const info = await collectionApi.apiCollectionsIdGet({ id: props.collectionId })
    resetForm({
      values: {
        name: info.name ?? props.currentName,
        faviconAllowlist: info.faviconAllowlist ?? '',
      },
    })
  } catch (err) {
    console.error('Failed to load collection allowlist:', err)
    // keep defaults; backend errors surface on submit
  }
})

const onSubmit = handleSubmit(async (values) => {
  const allowlist = values.faviconAllowlist.trim() ? values.faviconAllowlist : ''
  const ok = await collectionStore.updateCollection(props.collectionId, values.name, allowlist)
  if (ok) {
    emit('update:open', false)
    notification.success(t('collectionManage.editTitle'))
  }
})
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('collectionManage.editTitle') }}</template>
    <form @submit.prevent="onSubmit" class="space-y-4">
      <FormFieldCl :label="t('collectionManage.name')" for-id="edit-collection-name" :error="errors.name" required>
        <input
          id="edit-collection-name"
          v-model="name"
          v-bind="nameAttrs"
          type="text"
          maxlength="255"
          data-testid="edit-collection-name-input"
          :placeholder="t('collectionManage.namePlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </FormFieldCl>
      <FormFieldCl
        v-if="props.isOwner"
        :label="t('collectionManage.faviconAllowlist')"
        for-id="edit-collection-favicon-allowlist"
        :error="errors.faviconAllowlist"
      >
        <textarea
          id="edit-collection-favicon-allowlist"
          v-model="faviconAllowlist"
          v-bind="faviconAllowlistAttrs"
          rows="4"
          maxlength="2000"
          data-testid="edit-collection-favicon-allowlist-input"
          :placeholder="t('collectionManage.faviconAllowlistPlaceholder')"
          class="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm font-mono shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
        <p class="text-xs text-muted-foreground mt-1">{{ t('collectionManage.faviconAllowlistHelp') }}</p>
      </FormFieldCl>
      <div class="flex justify-end gap-2">
        <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.cancel') }}
        </ButtonCl>
        <ButtonCl type="submit" data-testid="collection-edit-submit-btn" :disabled="isSubmitting">
          {{ isSubmitting ? t('common.loading') : t('common.save') }}
        </ButtonCl>
      </div>
    </form>
  </DialogCl>
</template>
