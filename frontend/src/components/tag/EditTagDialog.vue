<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { DialogCl, ButtonCl } from '@/components/ui'
import { useTagStore } from '@/stores/tag'
import type { TagJson } from '@/api/generated'

const { t } = useI18n()
const tagStore = useTagStore()

interface Props {
  tag: TagJson | null
  open?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  saved: []
}>()

const name = ref('')
const color = ref('')
const error = ref('')
const loading = ref(false)

watch(() => props.open, (val) => {
  if (val && props.tag) {
    name.value = props.tag.data.name
    color.value = props.tag.data.color ?? ''
    error.value = ''
  }
})

async function handleSubmit() {
  if (!props.tag) return

  if (!name.value.trim()) {
    error.value = t('tag.nameRequired')
    return
  }

  loading.value = true
  error.value = ''

  try {
    await tagStore.updateTag(props.tag.id, {
      collectionId: props.tag.data.collectionId,
      name: name.value.trim(),
      color: color.value.trim() || undefined,
    })
    emit('update:open', false)
    emit('saved')
  } catch {
    error.value = t('tag.updateError')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('tag.editTitle') }}</template>

    <form @submit.prevent="handleSubmit" class="space-y-4">
      <div v-if="error" class="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
        {{ error }}
      </div>

      <div class="space-y-2">
        <label for="edit-tag-name" class="text-sm font-medium">{{ t('tag.name') }} *</label>
        <input
          id="edit-tag-name"
          v-model="name"
          type="text"
          required
          maxlength="50"
          data-testid="edit-tag-name-input"
          :placeholder="t('tag.namePlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </div>

      <div class="space-y-2">
        <label for="edit-tag-color" class="text-sm font-medium">{{ t('tag.color') }}</label>
        <div class="flex items-center gap-2">
          <input
            id="edit-tag-color"
            v-model="color"
            type="text"
            maxlength="7"
            placeholder="#ef4444"
            class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          />
          <span
            v-if="color"
            class="h-9 w-9 shrink-0 rounded-md border border-input"
            :style="{ backgroundColor: color }"
          />
        </div>
      </div>

      <div class="flex justify-end gap-2">
        <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.cancel') }}
        </ButtonCl>
        <ButtonCl type="submit" data-testid="edit-tag-submit" :disabled="loading">
          {{ loading ? t('common.loading') : t('common.save') }}
        </ButtonCl>
      </div>
    </form>
  </DialogCl>
</template>
