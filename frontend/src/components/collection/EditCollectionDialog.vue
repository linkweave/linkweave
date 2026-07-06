<script setup lang="ts">
import { config } from '@/api'
import { CollectionResourceApi } from '@/api/generated'
import { DialogFooterLw, DialogLw, FormFieldLw, InputLw } from '@/components/ui'
import { useFormDialog } from '@/composables/useFormDialog'
import { collectionUpdateSchema } from '@/schemas/collection'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { ref, toRef } from 'vue'
import { useI18n } from 'vue-i18n'

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

// Set when the initial GET fails: the form then holds placeholder defaults
// (empty allowlist, screenshot off), so saving would silently wipe real config.
const loadFailed = ref(false)

const { defineField, handleSubmit, errors, resetForm, isSubmitting } = useForm({
  validationSchema: toTypedSchema(collectionUpdateSchema(t)),
  initialValues: { name: '', browserFetchAllowlist: '', screenshotEnabled: false },
})

const [name, nameAttrs] = defineField('name')
const [browserFetchAllowlist, browserFetchAllowlistAttrs] = defineField('browserFetchAllowlist')
// Registered (not bound to any input) so its loaded value round-trips on save;
// the actual toggle lives in the collection-settings Preview tab.
defineField('screenshotEnabled')

useFormDialog(toRef(props, 'open'), async () => {
  loadFailed.value = false
  resetForm({
    values: { name: props.currentName, browserFetchAllowlist: '', screenshotEnabled: false },
  })
  if (!props.collectionId) return
  try {
    const info = await collectionApi.apiCollectionsIdGet({ id: props.collectionId })
    resetForm({
      values: {
        name: info.name ?? props.currentName,
        browserFetchAllowlist: info.browserFetchAllowlist ?? '',
        screenshotEnabled: info.screenshotEnabled ?? false,
      },
    })
  } catch (err) {
    console.error('Failed to load collection allowlist:', err)
    // Don't let the user save placeholder defaults over real config they never saw.
    loadFailed.value = true
    notification.error(t('collectionManage.editLoadError'))
  }
})

const onSubmit = handleSubmit(async (values) => {
  if (loadFailed.value) {
    notification.error(t('collectionManage.editLoadError'))
    return
  }
  const allowlist = values.browserFetchAllowlist.trim() ? values.browserFetchAllowlist : ''
  const ok = await collectionStore.updateCollection(
    props.collectionId,
    values.name,
    allowlist,
    values.screenshotEnabled,
  )
  if (ok) {
    emit('update:open', false)
    notification.success(t('collectionManage.editTitle'))
  }
})
</script>

<template>
  <DialogLw :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('collectionManage.editTitle') }}</template>
    <form id="edit-collection-form" @submit.prevent="onSubmit" class="space-y-4">
      <FormFieldLw
        :label="t('collectionManage.name')"
        for-id="edit-collection-name"
        :error="errors.name"
        required
      >
        <InputLw
          id="edit-collection-name"
          v-model="name"
          v-bind="nameAttrs"
          type="text"
          maxlength="255"
          :disabled="!(props.isOwner ?? false)"
          data-testid="edit-collection-name-input"
          :placeholder="t('collectionManage.namePlaceholder')"
        />
      </FormFieldLw>
      <FormFieldLw
        :label="t('collectionManage.faviconAllowlist')"
        for-id="edit-collection-favicon-allowlist"
        :error="errors.browserFetchAllowlist"
      >
        <textarea
          id="edit-collection-favicon-allowlist"
          v-model="browserFetchAllowlist"
          v-bind="browserFetchAllowlistAttrs"
          rows="4"
          maxlength="2000"
          data-testid="edit-collection-favicon-allowlist-input"
          :placeholder="t('collectionManage.faviconAllowlistPlaceholder')"
          class="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm font-mono shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
        <p class="text-xs text-muted-foreground mt-1">
          {{ t('collectionManage.faviconAllowlistHelp') }}
        </p>
      </FormFieldLw>
    </form>

    <template #footer>
      <DialogFooterLw
        submit-form="edit-collection-form"
        :submit-label="t('common.save')"
        :submitting="isSubmitting"
        submit-testid="collection-edit-submit-btn"
        @cancel="emit('update:open', false)"
      />
    </template>
  </DialogLw>
</template>
