<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { DialogCl, ButtonCl } from '@/components/ui'
import { useBookmarkStore } from '@/stores/bookmark'
import { useFolderStore } from '@/stores/folder'
import { useTagStore } from '@/stores/tag'
import { useNotificationStore } from '@/stores/notification'
import type { BookmarkSaveJson } from '@/api/generated'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const folderStore = useFolderStore()
const tagStore = useTagStore()
const notification = useNotificationStore()

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
const selectedTagIds = ref<Set<string>>(new Set())
const loading = ref(false)

const folderOptions = computed(() =>
  folderStore.folders.map((f) => ({ id: f.id, name: f.data.name })),
)

watch(
  () => props.open,
  (val) => {
    if (val) {
      url.value = ''
      title.value = ''
      description.value = ''
      selectedFolderId.value = props.folderId ?? undefined
      selectedTagIds.value = new Set()
    }
  },
)

function isValidUrl(urlString: string): boolean {
  try {
    new URL(urlString)
    return urlString.startsWith('http://') || urlString.startsWith('https://')
  } catch {
    return false
  }
}

function toggleTagId(tagId: string) {
  const next = new Set(selectedTagIds.value)
  if (next.has(tagId)) {
    next.delete(tagId)
  } else {
    next.add(tagId)
  }
  selectedTagIds.value = next
}

async function handleSubmit() {
  if (!url.value.trim()) {
    notification.warning(t('bookmark.urlRequired'))
    return
  }

  if (!isValidUrl(url.value.trim())) {
    notification.warning(t('bookmark.urlInvalid'))
    return
  }

  if (!title.value.trim()) {
    notification.warning(t('bookmark.titleRequired'))
    return
  }

  loading.value = true

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

  if (selectedTagIds.value.size > 0) {
    data.tagIds = selectedTagIds.value
  }

  try {
    await bookmarkStore.createBookmark(data)
    emit('update:open', false)
    emit('created')
  } catch (err) {
    notification.handleApiError(err, t('bookmark.createError'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('bookmark.createTitle') }}</template>

    <form @submit.prevent="handleSubmit" class="space-y-4">
      <div class="space-y-2">
        <label for="create-bookmark-title" class="text-sm font-medium"
          >{{ t('bookmark.title') }} *</label
        >
        <input
          id="create-bookmark-title"
          v-model="title"
          type="text"
          required
          data-testid="create-bookmark-title"
          :placeholder="t('bookmark.titlePlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </div>

      <div class="space-y-2">
        <label for="create-bookmark-url" class="text-sm font-medium"
          >{{ t('bookmark.url') }} *</label
        >
        <input
          id="create-bookmark-url"
          v-model="url"
          type="url"
          required
          data-testid="create-bookmark-url"
          :placeholder="t('bookmark.urlPlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </div>

      <div class="space-y-2">
        <label for="create-bookmark-description" class="text-sm font-medium">{{
          t('bookmark.description')
        }}</label>
        <textarea
          id="create-bookmark-description"
          v-model="description"
          rows="3"
          :placeholder="t('bookmark.descriptionPlaceholder')"
          class="flex w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
        />
      </div>

      <div class="space-y-2">
        <label for="create-bookmark-folder" class="text-sm font-medium">{{
          t('bookmark.folder')
        }}</label>
        <select
          id="create-bookmark-folder"
          v-model="selectedFolderId"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        >
          <option :value="undefined">{{ t('bookmark.noFolder') }}</option>
          <option v-for="opt in folderOptions" :key="opt.id" :value="opt.id">
            {{ opt.name }}
          </option>
        </select>
      </div>

      <div class="space-y-2">
        <label class="text-sm font-medium">{{ t('bookmark.tags') }}</label>
        <div class="flex flex-wrap gap-1.5">
          <button
            v-for="tag in tagStore.tags"
            :key="tag.id"
            type="button"
            class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs transition-opacity"
            :class="selectedTagIds.has(tag.id) ? 'opacity-100' : 'opacity-40'"
            :style="{ backgroundColor: tag.data.color ?? '#64748b', color: 'white' }"
            @click="toggleTagId(tag.id)"
          >
            {{ tag.data.name }}
          </button>
        </div>
        <p v-if="tagStore.tags.length === 0" class="text-xs text-muted-foreground">
          {{ t('tag.none') }}
        </p>
      </div>

      <div class="flex justify-end gap-2">
        <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.cancel') }}
        </ButtonCl>
        <ButtonCl type="submit" data-testid="create-bookmark-submit" :disabled="loading">
          {{ loading ? t('common.loading') : t('common.create') }}
        </ButtonCl>
      </div>
    </form>
  </DialogCl>
</template>
