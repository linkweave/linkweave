<script setup lang="ts">
import { computed, ref, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { DialogCl, ButtonCl, FormFieldCl, FolderSelectCl } from '@/components/ui'
import { useBookmarkStore } from '@/stores/bookmark'
import { useFolderStore } from '@/stores/folder'
import { useTagStore } from '@/stores/tag'
import { useAutoTagRuleStore } from '@/stores/autoTagRule'
import { useNotificationStore } from '@/stores/notification'
import { bookmarkSaveSchema } from '@/schemas/bookmark'
import { useFormDialog } from '@/composables/useFormDialog'
import { useTagSuggestions } from '@/composables/useTagSuggestions'
import { ensureUrlProtocol } from '@/lib/url'
import AutoTagRulesDialog from '@/components/autotagrule/AutoTagRulesDialog.vue'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const folderStore = useFolderStore()
const tagStore = useTagStore()
const autoTagRuleStore = useAutoTagRuleStore()
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

// todo homa learn about these deconstructions
const { defineField, handleSubmit, errors, resetForm, isSubmitting } = useForm({
  validationSchema: toTypedSchema(bookmarkSaveSchema(t)),
  initialValues: {
    collectionId: props.collectionId,
    folderId: props.folderId,
    title: '',
    url: '',
    description: '',
    tagIds: new Set<string>(),
  },
})

const [title, titleAttrs] = defineField('title')
const [url, urlAttrs] = defineField('url')
const [description, descriptionAttrs] = defineField('description')
const [folderId, folderIdAttrs] = defineField('folderId')
const [tagIds] = defineField('tagIds')

const folders = computed(() => folderStore.folders)

useFormDialog(toRef(props, 'open'), () =>
  resetForm({
    values: {
      collectionId: props.collectionId,
      folderId: props.folderId,
      title: '',
      url: '',
      description: '',
      tagIds: new Set<string>(),
    },
  }),
)

const tagsRef = computed(() => tagStore.tags)
const collectionIdRef = computed(() => props.collectionId)

const customRulesRef = computed(() =>
  autoTagRuleStore.rules.map((r) => ({
    pattern: r.data.pattern,
    tagNames: r.data.tagNames,
    enabled: r.data.enabled,
  })),
)
const { suggestions, selectedNames, toggle: toggleSuggestion, acceptInto } =
  useTagSuggestions({
    url,
    tags: tagsRef,
    collectionId: collectionIdRef,
    createTag: tagStore.createTag,
    customRules: customRulesRef,
  })

async function onAcceptSuggestions() {
  try {
    await acceptInto(tagIds)
  } catch (err) {
    notification.handleApiError(err, t('bookmark.tagSuggestionError'))
  }
}

function onUrlBlur() {
  if (typeof url.value === 'string') {
    url.value = ensureUrlProtocol(url.value)
  }
}

const rulesManagerOpen = ref(false)

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
  try {
    await bookmarkStore.createBookmark(values)
    emit('update:open', false)
    emit('created')
  } catch (err) {
    notification.handleApiError(err, t('bookmark.createError'))
  }
})
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>{{ t('bookmark.createTitle') }}</template>

    <form @submit.prevent="onSubmit" class="space-y-4">
<!--      title-->
      <FormFieldCl :label="t('bookmark.title')" for-id="create-bookmark-title" :error="errors.title" required>
        <input
          id="create-bookmark-title"
          v-model="title"
          v-bind="titleAttrs"
          type="text"
          :placeholder="t('bookmark.titlePlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
        />
      </FormFieldCl>
<!--url -->
      <FormFieldCl :label="t('bookmark.url')" for-id="create-bookmark-url" :error="errors.url" required>
        <input
          id="create-bookmark-url"
          v-model="url"
          v-bind="urlAttrs"
          type="url"
          :placeholder="t('bookmark.urlPlaceholder')"
          class="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          @blur="onUrlBlur"
        />
      </FormFieldCl>
<!--  description-->
      <FormFieldCl :label="t('bookmark.description')" for-id="create-bookmark-description" :error="errors.description">
        <textarea
          id="create-bookmark-description"
          v-model="description"
          v-bind="descriptionAttrs"
          rows="3"
          :placeholder="t('bookmark.descriptionPlaceholder')"
          class="flex w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring resize-none"
        />
      </FormFieldCl>
<!--folder -->
      <FormFieldCl :label="t('bookmark.folder')" for-id="create-bookmark-folder" :error="errors.folderId">
        <FolderSelectCl
          id="create-bookmark-folder"
          v-model="folderId"
          :folders="folders"
          :placeholder="t('bookmark.noFolder')"
          direction="down"
        />
      </FormFieldCl>
<!--save -->
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
        <p v-if="tagStore.tags.length === 0" class="text-xs text-muted-foreground">
          {{ t('tag.none') }}
        </p>
      </div>
<!--Tag autosuggestions -->
      <div data-testid="suggested-tags-section" class="space-y-2 min-h-[5.5rem]">
        <div class="flex items-center justify-between">
          <label class="text-sm font-medium">{{ t('bookmark.suggestedTags') }}</label>
<!--          manage rules, prevent default to prevent validation -->
          <button
            type="button"
            class="text-xs text-primary hover:underline"
            data-testid="manage-auto-tag-rules"
            @mousedown.prevent
            @click="rulesManagerOpen = true"
          >
            {{ t('bookmark.manageRules') }}
          </button>
        </div>
        <template v-if="suggestions.length > 0">
        <div class="flex flex-wrap gap-1.5">
          <button
            v-for="suggestion in suggestions"
            :key="suggestion.name"
            type="button"
            :data-testid="`suggested-tag-${suggestion.name}`"
            class="inline-flex items-center gap-1 rounded-full border border-dashed border-input px-2.5 py-0.5 text-xs transition-opacity"
            :class="selectedNames.has(suggestion.name) ? 'opacity-100' : 'opacity-40'"
            @click="toggleSuggestion(suggestion.name)"
          >
            <span>{{ suggestion.name }}</span>
            <span v-if="!suggestion.existingTagId" class="text-muted-foreground">
              {{ t('bookmark.suggestionWillCreate') }}
            </span>
          </button>
        </div>
        <div class="flex justify-end">
          <ButtonCl
            type="button"
            variant="outline"
            size="sm"
            data-testid="accept-suggestions-btn"
            :disabled="selectedNames.size === 0"
            @click="onAcceptSuggestions"
          >
            {{ t('bookmark.acceptSuggestions') }}
          </ButtonCl>
        </div>
        </template>
      </div>

      <div class="flex justify-end gap-2">
        <ButtonCl type="button" variant="outline" @click="emit('update:open', false)">
          {{ t('common.cancel') }}
        </ButtonCl>
        <ButtonCl type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? t('common.loading') : t('common.create') }}
        </ButtonCl>
      </div>
    </form>
  </DialogCl>

  <AutoTagRulesDialog
    v-model:open="rulesManagerOpen"
    :collection-id="collectionId"
  />
</template>
