<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { DialogCl, ButtonCl } from '@/components/ui'
import { useFolderStore } from '@/stores/folder'
import { useNotificationStore } from '@/stores/notification'
import type { FolderJson } from '@/api/generated'

const { t } = useI18n()
const folderStore = useFolderStore()
const notification = useNotificationStore()

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
const selectedParentId = ref<string | undefined>(undefined)
const loading = ref(false)

function getDescendantIds(folderId: string): Set<string> {
  const ids = new Set<string>()
  const queue = [folderId]
  while (queue.length > 0) {
    const current = queue.pop()!
    ids.add(current)
    for (const f of folderStore.folders) {
      if (f.data.parentId === current && !ids.has(f.id)) {
        queue.push(f.id)
      }
    }
  }
  return ids
}

const parentOptions = computed(() => {
  if (!props.folder) return []
  const excluded = getDescendantIds(props.folder.id)
  return folderStore.folders.filter(f => !excluded.has(f.id))
})

watch(() => props.open, (val) => {
  if (val && props.folder) {
    name.value = props.folder.data.name
    selectedParentId.value = props.folder.data.parentId ?? undefined
  }
})

async function handleSubmit() {
  if (!props.folder) return

  if (!name.value.trim()) {
    notification.warning(t('folder.nameRequired'))
    return
  }

  loading.value = true

  try {
    await folderStore.renameFolder(props.folder.id, {
      collectionId: props.folder.data.collectionId,
      parentId: selectedParentId.value,
      name: name.value.trim(),
    })
    emit('update:open', false)
    emit('saved')
  } catch (err) {
    notification.handleApiError(err, t('folder.renameError'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('folder.renameTitle') }}</template>

    <form @submit.prevent="handleSubmit" class="space-y-4">
      <div class="space-y-2">
        <label for="rename-folder-name" class="text-sm font-medium">{{ t('folder.name') }}</label>
        <input
          id="rename-folder-name"
          v-model="name"
          type="text"
          required
          data-testid="rename-folder-name"
          :placeholder="t('folder.namePlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </div>

      <div class="space-y-2">
        <label for="rename-folder-parent" class="text-sm font-medium">{{ t('folder.parentFolder') }}</label>
        <select
          id="rename-folder-parent"
          v-model="selectedParentId"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        >
          <option :value="undefined">{{ t('folder.noParent') }}</option>
          <option v-for="opt in parentOptions" :key="opt.id" :value="opt.id">
            {{ opt.data.name }}
          </option>
        </select>
      </div>

      <div class="flex justify-end gap-2">
        <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.cancel') }}
        </ButtonCl>
        <ButtonCl type="submit" data-testid="rename-folder-submit" :disabled="loading">
          {{ loading ? t('common.loading') : t('common.save') }}
        </ButtonCl>
      </div>
    </form>
  </DialogCl>
</template>
