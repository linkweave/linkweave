<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { DialogCl, ButtonCl } from '@/components/ui'
import { useBookmarkStore } from '@/stores/bookmark'
import { useFolderStore } from '@/stores/folder'
import type { BookmarkSaveJson } from '@/api/generated'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const folderStore = useFolderStore()

interface Props {
  collectionId: string
  folderId?: string
  open?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  created: []
}>()

const url = ref('')
const title = ref('')
const description = ref('')
const selectedFolderId = ref<string | undefined>(undefined)
const error = ref('')
const loading = ref(false)

const folderOptions = computed(() =>
  folderStore.folders.map(f => ({ id: f.id, name: f.data.name }))
)

watch(() => props.open, (val) => {
  if (val) {
    url.value = ''
    title.value = ''
    description.value = ''
    selectedFolderId.value = props.folderId ?? undefined
    error.value = ''
  }
})

function isValidUrl(urlString: string): boolean {
  try {
    new URL(urlString)
    return urlString.startsWith('http://') || urlString.startsWith('https://')
  } catch {
    return false
  }
}

async function handleSubmit() {
  if (!url.value.trim()) {
    error.value = t('bookmark.urlRequired')
    return
  }

  if (!isValidUrl(url.value.trim())) {
    error.value = t('bookmark.urlInvalid')
    return
  }

  if (!title.value.trim()) {
    error.value = t('bookmark.titleRequired')
    return
  }

  loading.value = true
  error.value = ''

  const data: BookmarkSaveJson = {
    collectionId: props.collectionId,
    title: title.value.trim(),
    url: url.value.trim(),
  }

  if (selectedFolderId.value) {
    data.folderId = selectedFolderId.value
  }

  if (description.value.trim()) {
    data.description = description.value.trim()
  }

  try {
    await bookmarkStore.createBookmark(data)
    emit('update:open', false)
    emit('created')
  } catch {
    error.value = t('bookmark.createError')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('bookmark.createTitle') }}</template>

    <form @submit.prevent="handleSubmit" class="space-y-4">
      <div v-if="error" class="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
        {{ error }}
      </div>

      <div class="space-y-2">
        <label for="bookmark-url" class="text-sm font-medium">{{ t('bookmark.url') }} *</label>
        <input
          id="bookmark-url"
          v-model="url"
          type="url"
          required
          :placeholder="t('bookmark.urlPlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </div>

      <div class="space-y-2">
        <label for="bookmark-title" class="text-sm font-medium">{{ t('bookmark.title') }} *</label>
        <input
          id="bookmark-title"
          v-model="title"
          type="text"
          required
          :placeholder="t('bookmark.titlePlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </div>

      <div class="space-y-2">
        <label for="bookmark-description" class="text-sm font-medium">{{ t('bookmark.description') }}</label>
        <textarea
          id="bookmark-description"
          v-model="description"
          rows="3"
          :placeholder="t('bookmark.descriptionPlaceholder')"
          class="flex w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
        />
      </div>

      <div class="space-y-2">
        <label for="bookmark-folder" class="text-sm font-medium">{{ t('bookmark.folder') }}</label>
        <select
          id="bookmark-folder"
          v-model="selectedFolderId"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        >
          <option :value="undefined">{{ t('bookmark.noFolder') }}</option>
          <option v-for="opt in folderOptions" :key="opt.id" :value="opt.id">
            {{ opt.name }}
          </option>
        </select>
      </div>

      <div class="flex justify-end gap-2">
        <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.cancel') }}
        </ButtonCl>
        <ButtonCl type="submit" :disabled="loading">
          {{ loading ? t('common.loading') : t('common.create') }}
        </ButtonCl>
      </div>
    </form>
  </DialogCl>
</template>
