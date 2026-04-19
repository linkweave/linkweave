<script setup lang="ts">
import { computed, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { DialogCl, ButtonCl, FormFieldCl } from '@/components/ui'
import { useBookmarkStore } from '@/stores/bookmark'
import { useFolderStore } from '@/stores/folder'
import { useTagStore } from '@/stores/tag'
import { useNotificationStore } from '@/stores/notification'
import { bookmarkSaveSchema } from '@/schemas/bookmark'
import { useFormDialog } from '@/composables/useFormDialog'
import type { BookmarkJson } from '@/api/generated'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const folderStore = useFolderStore()
const tagStore = useTagStore()
const notification = useNotificationStore()

interface Props {
  bookmark: BookmarkJson | null
  open?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  saved: []
}>()

const { defineField, handleSubmit, errors, resetForm, isSubmitting } = useForm({
  validationSchema: toTypedSchema(bookmarkSaveSchema),
  initialValues: {
    collectionId: '',
    title: '',
    url: '',
    description: '',
    folderId: undefined as string | undefined,
    tagIds: new Set<string>(),
  },
})

const [title, titleAttrs] = defineField('title')
const [url, urlAttrs] = defineField('url')
const [description, descriptionAttrs] = defineField('description')
const [folderId, folderIdAttrs] = defineField('folderId')
const [tagIds] = defineField('tagIds')

const folderOptions = computed(() =>
  folderStore.folders.map(f => ({ id: f.id, name: f.data.name }))
)

useFormDialog(toRef(props, 'open'), () => {
  if (props.bookmark) {
    resetForm({
      values: {
        collectionId: props.bookmark.data.collectionId,
        title: props.bookmark.data.title,
        url: props.bookmark.data.url,
        description: props.bookmark.data.description ?? '',
        folderId: props.bookmark.data.folderId ?? undefined,
        tagIds: props.bookmark.data.tagIds ? new Set(props.bookmark.data.tagIds) : new Set<string>(),
      },
    })
  }
})

function toggleTagId(tagId: string) {
  const current = tagIds.value ?? new Set<string>()
  const next = new Set(current)
  if (next.has(tagId)) {
    next.delete(tagId)
  } else {
    next.add(tagId)
  }
  tagIds.value = next
}

const onSubmit = handleSubmit(async (values) => {
  if (!props.bookmark) return

  try {
    await bookmarkStore.updateBookmark(props.bookmark.id, values)
    emit('update:open', false)
    emit('saved')
  } catch (err) {
    notification.handleApiError(err, t('bookmark.updateError'))
  }
})
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('bookmark.editTitle') }}</template>

    <form @submit.prevent="onSubmit" class="space-y-4">
      <FormFieldCl :label="t('bookmark.url')" for-id="edit-bookmark-url" :error="errors.url" required>
        <input
          id="edit-bookmark-url"
          v-model="url"
          v-bind="urlAttrs"
          type="url"
          :placeholder="t('bookmark.urlPlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </FormFieldCl>

      <FormFieldCl :label="t('bookmark.title')" for-id="edit-bookmark-title" :error="errors.title" required>
        <input
          id="edit-bookmark-title"
          v-model="title"
          v-bind="titleAttrs"
          type="text"
          :placeholder="t('bookmark.titlePlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </FormFieldCl>

      <FormFieldCl :label="t('bookmark.description')" for-id="edit-bookmark-description" :error="errors.description">
        <textarea
          id="edit-bookmark-description"
          v-model="description"
          v-bind="descriptionAttrs"
          rows="3"
          :placeholder="t('bookmark.descriptionPlaceholder')"
          class="flex w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
        />
      </FormFieldCl>

      <FormFieldCl :label="t('bookmark.folder')" for-id="edit-bookmark-folder" :error="errors.folderId">
        <select
          id="edit-bookmark-folder"
          v-model="folderId"
          v-bind="folderIdAttrs"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        >
          <option :value="undefined">{{ t('bookmark.noFolder') }}</option>
          <option v-for="opt in folderOptions" :key="opt.id" :value="opt.id">
            {{ opt.name }}
          </option>
        </select>
      </FormFieldCl>

      <div class="space-y-2">
        <label class="text-sm font-medium">{{ t('bookmark.tags') }}</label>
        <div class="flex flex-wrap gap-1.5">
          <button
            v-for="tag in tagStore.tags"
            :key="tag.id"
            type="button"
            class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs transition-opacity"
            :class="tagIds?.has(tag.id) ? 'opacity-100' : 'opacity-40'"
            :style="{ backgroundColor: tag.data.color ?? '#64748b', color: 'white' }"
            @click="toggleTagId(tag.id)"
          >
            {{ tag.data.name }}
          </button>
        </div>
        <p v-if="tagStore.tags.length === 0" class="text-xs text-muted-foreground">{{ t('tag.none') }}</p>
      </div>

      <div class="flex justify-end gap-2">
        <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.cancel') }}
        </ButtonCl>
        <ButtonCl type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? t('common.loading') : t('common.save') }}
        </ButtonCl>
      </div>
    </form>
  </DialogCl>
</template>
