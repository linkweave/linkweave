<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { DialogCl, ButtonCl } from '@/components/ui'
import { useTagStore } from '@/stores/tag'
import { useNotificationStore } from '@/stores/notification'

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

const name = ref('')
const loading = ref(false)

watch(() => props.open, (val) => {
  if (val) {
    name.value = ''
  }
})

async function handleSubmit() {
  if (!name.value.trim()) {
    notification.warning(t('tag.nameRequired'))
    return
  }

  loading.value = true

  try {
    await tagStore.createTag({
      collectionId: props.collectionId,
      name: name.value.trim(),
    })
    emit('update:open', false)
    emit('created')
  } catch (err) {
    notification.handleApiError(err, t('tag.createError'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('tag.createTitle') }}</template>

    <form @submit.prevent="handleSubmit" class="space-y-4">
      <div class="space-y-2">
        <label for="create-tag-name" class="text-sm font-medium">{{ t('tag.name') }} *</label>
        <input
          id="create-tag-name"
          v-model="name"
          type="text"
          required
          maxlength="50"
          data-testid="create-tag-name-input"
          :placeholder="t('tag.namePlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </div>

      <div class="flex justify-end gap-2">
        <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.cancel') }}
        </ButtonCl>
        <ButtonCl type="submit" data-testid="create-tag-submit" :disabled="loading">
          {{ loading ? t('common.loading') : t('common.create') }}
        </ButtonCl>
      </div>
    </form>
  </DialogCl>
</template>
