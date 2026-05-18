<script setup lang="ts">
import type { BookmarkJson } from '@/api/generated'
import AutoTagRulesDialog from '@/components/autotagrule/AutoTagRulesDialog.vue'
import {
  ButtonCl,
  DialogCl,
  DialogFooterCl,
  FolderSelectCl,
  FormFieldCl,
  InputCl,
  TextareaCl,
} from '@/components/ui'
import { useDuplicateCheck } from '@/composables/useDuplicateCheck'
import { useFormDialog } from '@/composables/useFormDialog'
import { useTagSuggestions } from '@/composables/useTagSuggestions'
import {
  decodePropertyValue,
  encodePropertyValueMap,
  type PropertyFormValue,
} from '@/lib/propertyValueMapper'
import { ensureUrlProtocol } from '@/lib/url'
import { bookmarkSaveSchema } from '@/schemas/bookmark'
import { useAutoTagRuleStore } from '@/stores/autoTagRule'
import { useBookmarkStore } from '@/stores/bookmark'
import { useFolderStore } from '@/stores/folder'
import { useNotificationStore } from '@/stores/notification'
import { usePropertyStore } from '@/stores/property'
import { useTagStore } from '@/stores/tag'
import { toTypedSchema } from '@vee-validate/zod'
import { Box, Pencil } from '@lucide/vue'
import { useForm } from 'vee-validate'
import { computed, reactive, ref, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import BookmarkPropertyInput from './BookmarkPropertyInput.vue'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const folderStore = useFolderStore()
const tagStore = useTagStore()
const autoTagRuleStore = useAutoTagRuleStore()
const propertyStore = usePropertyStore()
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
  validationSchema: toTypedSchema(bookmarkSaveSchema(t)),
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
const [folderId] = defineField('folderId')
const [tagIds] = defineField('tagIds')

const folders = computed(() => folderStore.folders)

// Property values are tracked outside the vee-validate form because the
// property API is a separate endpoint. We hold a `definitionId → primitive`
// map (reactive), seed it from the bookmark on open, and submit it through
// `bookmarkStore.updateProperties` alongside the regular save.
const propertyValuesByDefinitionId = reactive(new Map<string, PropertyFormValue>())
const initialPropertyValuesSnapshot = ref('')

function hydratePropertyValuesFromBookmark(bookmark: BookmarkJson) {
  propertyValuesByDefinitionId.clear()
  const wireById = new Map((bookmark.propertyValues ?? []).map((v) => [v.definitionId, v]))
  for (const def of propertyStore.definitions) {
    const decoded = decodePropertyValue(def.data.type, wireById.get(def.id))
    if (decoded !== undefined) {
      propertyValuesByDefinitionId.set(def.id, decoded)
    }
  }
  initialPropertyValuesSnapshot.value = snapshotPropertyValues()
}

function snapshotPropertyValues(): string {
  // Stable JSON of the current map, used to skip the property PUT when the
  // user didn't touch any property — avoids an unnecessary round-trip.
  const entries = [...propertyValuesByDefinitionId.entries()].sort((a, b) =>
    a[0].localeCompare(b[0]),
  )
  return JSON.stringify(entries)
}

function setPropertyValue(definitionId: string, value: PropertyFormValue) {
  if (value === undefined) {
    propertyValuesByDefinitionId.delete(definitionId)
  } else {
    propertyValuesByDefinitionId.set(definitionId, value)
  }
}

useFormDialog(toRef(props, 'open'), () => {
  if (props.bookmark) {
    resetForm({
      values: {
        collectionId: props.bookmark.data.collectionId,
        title: props.bookmark.data.title,
        url: props.bookmark.data.url,
        description: props.bookmark.data.description ?? '',
        folderId: props.bookmark.data.folderId ?? undefined,
        tagIds: props.bookmark.data.tagIds
          ? new Set(props.bookmark.data.tagIds)
          : new Set<string>(),
      },
    })
    hydratePropertyValuesFromBookmark(props.bookmark)
  }
})

const tagsRef = computed(() => tagStore.tags)
const collectionIdRef = computed(() => props.bookmark?.data.collectionId ?? '')
const customRulesRef = computed(() =>
  autoTagRuleStore.rules.map((r) => ({
    pattern: r.data.pattern,
    tagNames: r.data.tagNames,
    enabled: r.data.enabled,
  })),
)
const {
  suggestions,
  selectedNames,
  toggle: toggleSuggestion,
  acceptInto,
} = useTagSuggestions({
  url,
  tags: tagsRef,
  collectionId: collectionIdRef,
  createTag: tagStore.createTag,
  customRules: customRulesRef,
})

const bookmarkIdRef = computed(() => props.bookmark?.id)
const bookmarksRef = computed(() => bookmarkStore.bookmarks)
const foldersRef = computed(() => folderStore.folders)
const { duplicates } = useDuplicateCheck(url, bookmarksRef, {
  excludeBookmarkId: bookmarkIdRef,
  folders: foldersRef,
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
  if (!props.bookmark) return

  try {
    await bookmarkStore.updateBookmark(props.bookmark.id, values)
    // Only PUT property values when the user actually changed them.
    if (snapshotPropertyValues() !== initialPropertyValuesSnapshot.value) {
      const typesByDefinitionId = new Map(propertyStore.definitions.map((d) => [d.id, d.data.type]))
      const wirePayload = encodePropertyValueMap(
        propertyValuesByDefinitionId as Map<string, PropertyFormValue>,
        typesByDefinitionId,
      )
      await bookmarkStore.updateProperties(props.bookmark.id, wirePayload)
    }
    emit('update:open', false)
    emit('saved')
  } catch (err) {
    notification.handleApiError(err, t('bookmark.updateError'))
  }
})
</script>

<template>
  <DialogCl :open="open" @update:open="emit('update:open', $event)">
    <template #title>
      <span class="inline-flex items-center gap-1.5">
        <Pencil class="h-3.5 w-3.5" aria-hidden="true" />
        {{ t('bookmark.editTitle') }}
      </span>
    </template>

    <form id="edit-bookmark-form" @submit.prevent="onSubmit" class="space-y-4">
      <FormFieldCl
        :label="t('bookmark.url')"
        for-id="edit-bookmark-url"
        :error="errors.url"
        required
      >
        <InputCl
          id="edit-bookmark-url"
          v-model="url"
          v-bind="urlAttrs"
          type="url"
          :placeholder="t('bookmark.urlPlaceholder')"
          @blur="onUrlBlur"
        />
      </FormFieldCl>

      <div
        v-if="duplicates.length > 0"
        data-testid="duplicate-warning"
        class="rounded-md border border-yellow-300 bg-yellow-50 px-3 py-2 text-sm text-yellow-800 dark:border-yellow-700 dark:bg-yellow-950 dark:text-yellow-200"
      >
        <p class="font-medium">
          {{
            duplicates.length === 1
              ? t('bookmark.duplicateWarning')
              : t('bookmark.duplicateWarningPlural', { count: duplicates.length })
          }}
        </p>
        <ul class="mt-1 list-inside list-disc">
          <li v-for="dup in duplicates" :key="dup.id">
            {{ dup.title }}
            <span v-if="dup.folderName" class="text-muted-foreground">
              ({{ t('bookmark.duplicateInFolder', { folder: dup.folderName }) }})
            </span>
          </li>
        </ul>
      </div>

      <FormFieldCl
        :label="t('bookmark.title')"
        for-id="edit-bookmark-title"
        :error="errors.title"
        required
      >
        <InputCl
          id="edit-bookmark-title"
          v-model="title"
          v-bind="titleAttrs"
          type="text"
          :placeholder="t('bookmark.titlePlaceholder')"
        />
      </FormFieldCl>

      <FormFieldCl
        :label="t('bookmark.description')"
        for-id="edit-bookmark-description"
        :error="errors.description"
      >
        <TextareaCl
          id="edit-bookmark-description"
          v-model="description"
          v-bind="descriptionAttrs"
          rows="3"
          :placeholder="t('bookmark.descriptionPlaceholder')"
        />
      </FormFieldCl>

      <FormFieldCl
        :label="t('bookmark.folder')"
        for-id="edit-bookmark-folder"
        :error="errors.folderId"
      >
        <FolderSelectCl
          id="edit-bookmark-folder"
          v-model="folderId"
          :folders="folders"
          :placeholder="t('bookmark.noFolder')"
          direction="down"
        />
      </FormFieldCl>

      <div class="space-y-2">
        <label class="block text-sm font-medium leading-none">{{ t('bookmark.tags') }}</label>
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
      <!-- suggested tags-->
      <div data-testid="suggested-tags-section" class="space-y-2 min-h-[5.5rem]">
        <div class="flex items-center justify-between">
          <label class="block text-sm font-medium leading-none">{{
            t('bookmark.suggestedTags')
          }}</label>
          <!--          prevent default to prevent validation on click-->
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

      <!-- Properties section. Hidden entirely when the collection has no
           definitions, so we don't leave a dangling heading in the dialog. -->
      <template v-if="propertyStore.definitions.length > 0">
        <div class="flex items-center gap-2.5 pt-1">
          <span
            class="inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-[.06em] text-muted-foreground"
          >
            <Box class="h-3 w-3" aria-hidden="true" />
            {{ t('property.sectionHeading') }}
          </span>
          <div class="flex-1 h-px bg-border" />
        </div>
        <div class="space-y-3">
          <BookmarkPropertyInput
            v-for="def in propertyStore.definitions"
            :key="def.id"
            :prop-def="def"
            :model-value="propertyValuesByDefinitionId.get(def.id)"
            @update:model-value="(value) => setPropertyValue(def.id, value)"
            @clear="setPropertyValue(def.id, undefined)"
          />
        </div>
      </template>
    </form>

    <template #footer>
      <DialogFooterCl
        submit-form="edit-bookmark-form"
        :submit-label="t('common.save')"
        :submitting="isSubmitting"
        @cancel="emit('update:open', false)"
      />
    </template>
  </DialogCl>

  <AutoTagRulesDialog
    v-if="bookmark"
    v-model:open="rulesManagerOpen"
    :collection-id="bookmark.data.collectionId"
  />
</template>
