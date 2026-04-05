<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { DialogCl, ButtonCl } from '@/components/ui'
import { useFolderStore } from '@/stores/folder'
import { useNotificationStore } from '@/stores/notification'
import type { FolderSaveJson } from '@/api/generated'

const { t } = useI18n()
const folderStore = useFolderStore()
const notification = useNotificationStore()

interface Props {
  collectionId: string
  parentId?: string
  open?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  created: []
}>()

const name = ref('')
const selectedParentId = ref<string | undefined>(undefined)
const loading = ref(false)

const parentOptions = computed(() =>
  folderStore.folders.map(f => ({ id: f.id, name: f.data.name }))
)

watch(() => props.open, (val) => {
  if (val) {
    name.value = ''
    selectedParentId.value = props.parentId ?? undefined
  }
})

async function handleSubmit() {
  if (!name.value.trim()) {
    notification.warning(t('folder.nameRequired'))
    return
  }

  loading.value = true

  const data: FolderSaveJson = {
    collectionId: props.collectionId,
    name: name.value.trim(),
  }

  if (selectedParentId.value) {
    data.parentId = selectedParentId.value
  }

  try {
    await folderStore.createFolder(data)
    emit('update:open', false)
    emit('created')
  } catch (err) {
    notification.handleApiError(err, t('folder.createError'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('folder.createTitle') }}</template>

    <form @submit.prevent="handleSubmit" class="space-y-4">
      <div class="space-y-2">
        <label for="folder-name" class="text-sm font-medium">{{ t('folder.name') }}</label>
        <input
          id="folder-name"
          v-model="name"
          type="text"
          required
          data-testid="create-folder-name"
          :placeholder="t('folder.namePlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </div>

      <div class="space-y-2">
        <label for="folder-parent" class="text-sm font-medium">{{ t('folder.parentFolder') }}</label>
        <select
          id="folder-parent"
          v-model="selectedParentId"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        >
          <option :value="undefined">{{ t('folder.noParent') }}</option>
          <option v-for="opt in parentOptions" :key="opt.id" :value="opt.id">
            {{ opt.name }}
          </option>
        </select>
      </div>

      <div class="flex justify-end gap-2">
        <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.cancel') }}
        </ButtonCl>
        <ButtonCl type="submit" data-testid="create-folder-submit" :disabled="loading">
          {{ loading ? t('common.loading') : t('common.create') }}
        </ButtonCl>
      </div>
    </form>
  </DialogCl>
</template>
