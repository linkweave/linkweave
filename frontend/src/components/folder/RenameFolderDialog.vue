<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { DialogCl, ButtonCl } from '@/components/ui'
import { useFolderStore } from '@/stores/folder'
import type { FolderJson } from '@/api/generated'

const { t } = useI18n()
const folderStore = useFolderStore()

interface Props {
  folder: FolderJson | null
  open?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  saved: []
}>()

const name = ref('')
const error = ref('')
const loading = ref(false)

watch(() => props.open, (val) => {
  if (val && props.folder) {
    name.value = props.folder.data.name
    error.value = ''
  }
})

async function handleSubmit() {
  if (!props.folder) return

  if (!name.value.trim()) {
    error.value = t('folder.nameRequired')
    return
  }

  loading.value = true
  error.value = ''

  try {
    await folderStore.renameFolder(props.folder.id, {
      collectionId: props.folder.data.collectionId,
      parentId: props.folder.data.parentId ?? undefined,
      name: name.value.trim(),
    })
    emit('update:open', false)
    emit('saved')
  } catch {
    error.value = t('folder.renameError')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('folder.renameTitle') }}</template>

    <form @submit.prevent="handleSubmit" class="space-y-4">
      <div v-if="error" class="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
        {{ error }}
      </div>

      <div class="space-y-2">
        <label for="rename-folder-name" class="text-sm font-medium">{{ t('folder.name') }}</label>
        <input
          id="rename-folder-name"
          v-model="name"
          type="text"
          required
          :placeholder="t('folder.namePlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </div>

      <div class="flex justify-end gap-2">
        <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.cancel') }}
        </ButtonCl>
        <ButtonCl type="submit" :disabled="loading">
          {{ loading ? t('common.loading') : t('common.save') }}
        </ButtonCl>
      </div>
    </form>
  </DialogCl>
</template>
