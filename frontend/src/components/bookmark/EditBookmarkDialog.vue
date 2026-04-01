<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { DialogCl, ButtonCl } from '@/components/ui'
import { useBookmarkStore } from '@/stores/bookmark'
import { useFolderStore } from '@/stores/folder'
import type { BookmarkJson, BookmarkSaveJson } from '@/api/generated'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const folderStore = useFolderStore()

interface Props {
  bookmark: BookmarkJson | null
  open?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  saved: []
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
  if (val && props.bookmark) {
    url.value = props.bookmark.data.url
    title.value = props.bookmark.data.title
    description.value = props.bookmark.data.description ?? ''
    selectedFolderId.value = props.bookmark.data.folderId ?? undefined
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
  if (!props.bookmark) return

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
    collectionId: props.bookmark.data.collectionId,
    title: title.value.trim(),
    url: url.value.trim(),
    folderId: selectedFolderId.value,
    description: description.value.trim() || undefined,
    tagIds: props.bookmark.data.tagIds,
  }

  try {
    await bookmarkStore.updateBookmark(props.bookmark.id, data)
    emit('update:open', false)
    emit('saved')
  } catch {
    error.value = t('bookmark.updateError')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('bookmark.editTitle') }}</template>

    <form @submit.prevent="handleSubmit" class="space-y-4">
      <div v-if="error" class="rounded-md bg-destructive/10 p-3 text-sm text-destructive">
        {{ error }}
      </div>

      <div class="space-y-2">
        <label for="edit-bookmark-url" class="text-sm font-medium">{{ t('bookmark.url') }} *</label>
        <input
          id="edit-bookmark-url"
          v-model="url"
          type="url"
          required
          :placeholder="t('bookmark.urlPlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </div>

      <div class="space-y-2">
        <label for="edit-bookmark-title" class="text-sm font-medium">{{ t('bookmark.title') }} *</label>
        <input
          id="edit-bookmark-title"
          v-model="title"
          type="text"
          required
          :placeholder="t('bookmark.titlePlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </div>

      <div class="space-y-2">
        <label for="edit-bookmark-description" class="text-sm font-medium">{{ t('bookmark.description') }}</label>
        <textarea
          id="edit-bookmark-description"
          v-model="description"
          rows="3"
          :placeholder="t('bookmark.descriptionPlaceholder')"
          class="flex w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
        />
      </div>

      <div class="space-y-2">
        <label for="edit-bookmark-folder" class="text-sm font-medium">{{ t('bookmark.folder') }}</label>
        <select
          id="edit-bookmark-folder"
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
          {{ loading ? t('common.loading') : t('common.save') }}
        </ButtonCl>
      </div>
    </form>
  </DialogCl>
</template>
