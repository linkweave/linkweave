<script setup lang="ts">
import type { BookmarkJson } from '@/api/generated'
import AutoTagRulesDialog from '@/components/autotagrule/AutoTagRulesDialog.vue'
import {
  CollapsibleLw,
  DialogLw,
  DialogFooterLw,
  FolderSelectLw,
  FormFieldLw,
  InputLw,
  TextareaLw,
} from '@/components/ui'
import { useDuplicateCheck } from '@/composables/useDuplicateCheck'
import { useFormDialog } from '@/composables/useFormDialog'
import { usePropsExpandedPref } from '@/composables/usePropsExpandedPref'
import SuggestedTagsSection from '@/components/bookmark/SuggestedTagsSection.vue'
import TagCombobox from '@/components/bookmark/TagCombobox.vue'
import {
  decodePropertyValue,
  encodePropertyValueMap,
  type PropertyFormValue,
} from '@/lib/propertyValueMapper'
import { ensureUrlProtocol } from '@/lib/url'
import { bookmarkSaveSchema } from '@/schemas/bookmark'
import { useBookmarkStore } from '@/stores/bookmark'
import { useFolderStore } from '@/stores/folder'
import { useNotificationStore } from '@/stores/notification'
import { usePropertyStore } from '@/stores/property'
import { toTypedSchema } from '@vee-validate/zod'
import { Box, ChevronDown, Pencil, Plus } from '@lucide/vue'
import { useForm } from 'vee-validate'
import { computed, reactive, ref, toRef } from 'vue'
import { useI18n } from 'vue-i18n'
import BookmarkPropertyInput from './BookmarkPropertyInput.vue'

const { t } = useI18n()
const bookmarkStore = useBookmarkStore()
const folderStore = useFolderStore()
const propertyStore = usePropertyStore()
const notification = useNotificationStore()

interface Props {
  /** When provided → edit mode. When null/undefined → create mode. */
  bookmark?: BookmarkJson | null
  /** Required in create mode; ignored in edit mode. */
  collectionId?: string
  /** Optional pre-selected folder in create mode. */
  preselectedFolderId?: string
  open?: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  saved: []
}>()

const isEdit = computed(() => props.bookmark != null)
// Stable per-mode prefix used for form/input IDs so e2e selectors keep working.
const idPrefix = computed(() => (isEdit.value ? 'edit-bookmark' : 'create-bookmark'))
const formId = computed(() => `${idPrefix.value}-form`)

const { defineField, handleSubmit, errors, resetForm, isSubmitting } = useForm({
  validationSchema: toTypedSchema(bookmarkSaveSchema(t)),
  initialValues: {
    collectionId: props.collectionId ?? '',
    title: '',
    url: '',
    description: '',
    folderId: props.preselectedFolderId as string | undefined,
    tagIds: new Set<string>(),
  },
})

const [title, titleAttrs] = defineField('title')
const [url, urlAttrs] = defineField('url')
const [description, descriptionAttrs] = defineField('description')
const [folderId] = defineField('folderId')
const [tagIds] = defineField('tagIds')

const folders = computed(() => folderStore.folders)

// Property values are tracked outside vee-validate because the property API
// is a separate endpoint. We hold a `definitionId → primitive` map (reactive)
// and submit it via `bookmarkStore.updateProperties` after create or edit.
const propertyValuesByDefinitionId = reactive(new Map<string, PropertyFormValue>())
const initialPropertyValuesSnapshot = ref('')

function hydratePropertyValuesFromBookmark(bookmark: BookmarkJson | null) {
  propertyValuesByDefinitionId.clear()
  const wireById = new Map(
    (bookmark?.propertyValues ?? []).map((v) => [v.definitionId, v]),
  )
  for (const def of propertyStore.definitions) {
    const decoded = decodePropertyValue(def.data.type, wireById.get(def.id))
    if (decoded !== undefined) {
      propertyValuesByDefinitionId.set(def.id, decoded)
    }
  }
  initialPropertyValuesSnapshot.value = snapshotPropertyValues()
}

function snapshotPropertyValues(): string {
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
  } else {
    resetForm({
      values: {
        collectionId: props.collectionId ?? '',
        folderId: props.preselectedFolderId,
        title: '',
        url: '',
        description: '',
        tagIds: new Set<string>(),
      },
    })
    hydratePropertyValuesFromBookmark(null)
  }
})

const effectiveCollectionId = computed(
  () => props.bookmark?.data.collectionId ?? props.collectionId ?? '',
)
const appliedTagIds = computed(() => tagIds.value ?? new Set<string>())

function onAddTags(ids: string[]) {
  const next = new Set(tagIds.value ?? new Set<string>())
  for (const id of ids) next.add(id)
  tagIds.value = next
}

const bookmarkIdRef = computed(() => props.bookmark?.id)
const bookmarksRef = computed(() => bookmarkStore.bookmarks)
const foldersRef = computed(() => folderStore.folders)
const { duplicates } = useDuplicateCheck(url, bookmarksRef, {
  excludeBookmarkId: bookmarkIdRef,
  folders: foldersRef,
})

function onUrlBlur() {
  if (typeof url.value === 'string') {
    url.value = ensureUrlProtocol(url.value)
  }
}

const rulesManagerOpen = ref(false)

const filledCount = computed(() =>
  propertyStore.definitions.filter((pd) => {
    const v = propertyValuesByDefinitionId.get(pd.id)
    return v !== undefined && v !== null && v !== ''
  }).length,
)

const PROPS_COLLAPSE_THRESHOLD = 6
const propsIsCollapsible = computed(() => propertyStore.definitions.length >= PROPS_COLLAPSE_THRESHOLD)
const propsExpandedStorage = usePropsExpandedPref(effectiveCollectionId)
const propsIsExpanded = computed(() => !propsIsCollapsible.value || propsExpandedStorage.value)
function toggleProps() {
  if (propsIsCollapsible.value) propsExpandedStorage.value = !propsExpandedStorage.value
}

function buildWirePropertyValues() {
  const typesByDefinitionId = new Map(
    propertyStore.definitions.map((d) => [d.id, d.data.type]),
  )
  return encodePropertyValueMap(
    propertyValuesByDefinitionId as Map<string, PropertyFormValue>,
    typesByDefinitionId,
  )
}

const onSubmit = handleSubmit(async (values) => {
  try {
    if (props.bookmark) {
      await bookmarkStore.updateBookmark(props.bookmark.id, values)
      if (snapshotPropertyValues() !== initialPropertyValuesSnapshot.value) {
        await bookmarkStore.updateProperties(props.bookmark.id, buildWirePropertyValues())
      }
    } else {
      const created = await bookmarkStore.createBookmark(values)
      if (propertyValuesByDefinitionId.size > 0) {
        await bookmarkStore.updateProperties(created.id, buildWirePropertyValues())
      }
    }
    emit('update:open', false)
    emit('saved')
  } catch (err) {
    notification.handleApiError(
      err,
      t(props.bookmark ? 'bookmark.updateError' : 'bookmark.createError'),
    )
  }
})
</script>

<template>
  <DialogLw :open="open" @update:open="emit('update:open', $event)">
    <template #title>
      <span class="inline-flex items-center gap-1.5">
        <component
          :is="isEdit ? Pencil : Plus"
          class="h-3.5 w-3.5"
          aria-hidden="true"
        />
        {{ isEdit ? t('bookmark.editTitle') : t('bookmark.createTitle') }}
      </span>
    </template>

    <form :id="formId" @submit.prevent="onSubmit" class="space-y-4">
      <FormFieldLw
        :label="t('bookmark.url')"
        :for-id="`${idPrefix}-url`"
        :error="errors.url"
        required
      >
        <InputLw
          :id="`${idPrefix}-url`"
          v-model="url"
          v-bind="urlAttrs"
          type="url"
          :placeholder="t('bookmark.urlPlaceholder')"
          @blur="onUrlBlur"
        />
      </FormFieldLw>

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

      <FormFieldLw
        :label="t('bookmark.title')"
        :for-id="`${idPrefix}-title`"
        :error="errors.title"
        required
      >
        <InputLw
          :id="`${idPrefix}-title`"
          v-model="title"
          v-bind="titleAttrs"
          type="text"
          :placeholder="t('bookmark.titlePlaceholder')"
        />
      </FormFieldLw>

      <FormFieldLw
        :label="t('bookmark.description')"
        :for-id="`${idPrefix}-description`"
        :error="errors.description"
      >
        <TextareaLw
          :id="`${idPrefix}-description`"
          v-model="description"
          v-bind="descriptionAttrs"
          rows="3"
          :placeholder="t('bookmark.descriptionPlaceholder')"
        />
      </FormFieldLw>

      <FormFieldLw
        :label="t('bookmark.folder')"
        :for-id="`${idPrefix}-folder`"
        :error="errors.folderId"
      >
        <FolderSelectLw
          :id="`${idPrefix}-folder`"
          v-model="folderId"
          :folders="folders"
          :placeholder="t('bookmark.noFolder')"
          direction="down"
        />
      </FormFieldLw>

      <div class="space-y-2">
        <div class="flex items-center justify-between">
          <label class="block text-sm font-medium leading-none">{{ t('bookmark.tags') }}</label>
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
        <TagCombobox
          v-model="tagIds"
          :collection-id="effectiveCollectionId"
          :id-prefix="idPrefix"
        />
      </div>

      <SuggestedTagsSection
        :open="!!open"
        :is-edit="isEdit"
        :collection-id="effectiveCollectionId"
        :title="title"
        :url="url"
        :description="description"
        :applied-tag-ids="appliedTagIds"
        @add-tags="onAddTags"
      />

      <template v-if="propertyStore.definitions.length > 0">
        <!-- Collapsible header (6+ props) or static divider (≤5 props) -->
        <component
          :is="propsIsCollapsible ? 'button' : 'div'"
          v-bind="
            propsIsCollapsible
              ? { type: 'button', 'aria-expanded': propsIsExpanded, onClick: toggleProps, 'data-testid': 'properties-toggle' }
              : {}
          "
          class="flex items-center gap-2.5 pt-1 w-full text-left"
          :class="propsIsCollapsible ? 'cursor-pointer group' : ''"
        >
          <span
            class="inline-flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-[.06em] text-muted-foreground shrink-0"
          >
            <Box class="h-3 w-3" aria-hidden="true" />
            {{ t('property.sectionHeading') }}
          </span>
          <span
            v-if="propsIsCollapsible"
            class="inline-flex items-center justify-center rounded-full bg-muted px-1.5 py-0.5 text-[10px] font-semibold leading-none text-muted-foreground shrink-0"
          >
            {{ propertyStore.definitions.length }}
          </span>
          <span
            v-if="propsIsCollapsible && !propsIsExpanded && filledCount > 0"
            class="text-[10px] text-muted-foreground shrink-0"
          >
            {{ t('property.filledHint', { n: filledCount }) }}
          </span>
          <div class="flex-1 h-px bg-border" />
          <ChevronDown
            v-if="propsIsCollapsible"
            class="h-3.5 w-3.5 text-muted-foreground shrink-0 transition-transform duration-200"
            :class="propsIsExpanded ? 'rotate-180' : ''"
            aria-hidden="true"
          />
        </component>

        <!-- Collapse wrapper using CSS grid trick; always open when not collapsible -->
        <CollapsibleLw :open="propsIsExpanded">
          <div class="space-y-3" :class="propsIsCollapsible ? 'pt-[14px]' : ''">
            <BookmarkPropertyInput
              v-for="def in propertyStore.definitions"
              :key="def.id"
              :prop-def="def"
              :model-value="propertyValuesByDefinitionId.get(def.id)"
              @update:model-value="(value: PropertyFormValue) => setPropertyValue(def.id, value)"
              @clear="setPropertyValue(def.id, undefined)"
            />
          </div>
        </CollapsibleLw>
      </template>
    </form>

    <template #footer>
      <DialogFooterLw
        :submit-form="formId"
        :submit-label="isEdit ? t('common.save') : t('common.create')"
        :submitting="isSubmitting"
        @cancel="emit('update:open', false)"
      />
    </template>
  </DialogLw>

  <AutoTagRulesDialog
    v-if="effectiveCollectionId"
    v-model:open="rulesManagerOpen"
    :collection-id="effectiveCollectionId"
  />
</template>
