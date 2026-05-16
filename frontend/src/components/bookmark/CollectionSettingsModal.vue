<script setup lang="ts">
import type { PropertyDefinitionJson } from '@/api/generated'
import { PropertyType } from '@/api/generated'
import {
  ButtonCl,
  ConfirmDialog,
  DialogCl,
  DialogFooterCl,
  FormFieldCl,
  InputCl,
  SwitchCl,
} from '@/components/ui'
import { useFormDialog } from '@/composables/useFormDialog'
import {
  useShowPropertiesSidebar,
  useShowPropertyBadges,
} from '@/composables/usePropertyDisplayPrefs'
import { propertyDefinitionSaveSchema } from '@/schemas/property'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import { usePropertyStore } from '@/stores/property'
import { type BookmarkLayout, useUiStore } from '@/stores/ui'
import { toTypedSchema } from '@vee-validate/zod'
import { Layers, LayoutGrid, LayoutList, Pencil, Plus, Trash2 } from 'lucide-vue-next'
import { useForm } from 'vee-validate'
import { computed, ref, toRef } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const propertyStore = usePropertyStore()
const collectionStore = useCollectionStore()
const notification = useNotificationStore()
const ui = useUiStore()
const showBadges = useShowPropertyBadges()
const showSidebar = useShowPropertiesSidebar()

// Layout chooser — same card-button visual as the user-menu Settings dialog,
// wired to the collection-scoped setting when a collection is loaded (falling
// back to the global UI default for the no-collection case).
const layouts: { value: BookmarkLayout; icon: typeof LayoutList; labelKey: string }[] = [
  { value: 'list', icon: LayoutList, labelKey: 'settings.layoutList' },
  { value: 'grid', icon: LayoutGrid, labelKey: 'settings.layoutGrid' },
  { value: 'grouped', icon: Layers, labelKey: 'settings.layoutGrouped' },
]

const currentLayout = computed<BookmarkLayout>(() => {
  const fromBackend = collectionStore.settings?.layout
  if (fromBackend === 'list' || fromBackend === 'grid' || fromBackend === 'grouped') {
    return fromBackend
  }
  return ui.bookmarkLayout
})

function selectLayout(layout: BookmarkLayout) {
  const id = collectionStore.currentCollectionId
  if (id) {
    collectionStore.updateSettings(id, { layout })
  } else {
    ui.setBookmarkLayout(layout)
  }
}

const props = defineProps<{
  open: boolean
}>()
const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

type Tab = 'display' | 'properties'
const activeTab = ref<Tab>('display')

// All known PropertyType enum values, with i18n keys for the type-selector.
const propertyTypes: ReadonlyArray<{ value: PropertyType; labelKey: string }> = [
  { value: PropertyType.Text, labelKey: 'property.typeText' },
  { value: PropertyType.Number, labelKey: 'property.typeNumber' },
  { value: PropertyType.Boolean, labelKey: 'property.typeBoolean' },
  { value: PropertyType.Select, labelKey: 'property.typeSelect' },
  { value: PropertyType.MultiSelect, labelKey: 'property.typeMultiSelect' },
  { value: PropertyType.Date, labelKey: 'property.typeDate' },
]

function needsOptions(t: PropertyType | undefined) {
  return t === PropertyType.Select || t === PropertyType.MultiSelect
}

// --- Add / edit form ------------------------------------------------------
// The form starts hidden — the user opens it via the "Add property" button,
// or by clicking the edit icon on an existing row. `editingId === null` means
// "create new"; non-null means we're editing that definition. `showAddForm`
// only matters in create mode; edit mode keeps the form visible regardless.
const editingId = ref<string | null>(null)
const showAddForm = ref(false)

const isEditing = computed(() => editingId.value !== null)

function emptyInitialValues() {
  return {
    collectionId: collectionStore.currentCollectionId ?? '',
    name: '',
    type: PropertyType.Text,
    sortOrder: propertyStore.definitions.length,
    allowedValues: undefined as string | undefined,
  }
}

const {
  defineField,
  handleSubmit,
  errors,
  resetForm: vvResetForm,
  isSubmitting,
} = useForm({
  validationSchema: toTypedSchema(propertyDefinitionSaveSchema(t)),
  initialValues: emptyInitialValues(),
})

const [name, nameAttrs] = defineField('name')
const [type, typeAttrs] = defineField('type')
const [allowedValues, allowedValuesAttrs] = defineField('allowedValues')

// Reset the entire form when the modal opens — guarantees no stale half-typed
// state survives across openings. Same hook the other dialogs use.
useFormDialog(toRef(props, 'open'), () => {
  vvResetForm({ values: emptyInitialValues() })
  editingId.value = null
  showAddForm.value = false
})

function cancelForm() {
  vvResetForm({ values: emptyInitialValues() })
  editingId.value = null
  showAddForm.value = false
}

function startAdd() {
  showAddForm.value = true
  editingId.value = null
  vvResetForm({ values: emptyInitialValues() })
}

function startEdit(def: PropertyDefinitionJson) {
  editingId.value = def.id
  showAddForm.value = true
  vvResetForm({
    values: {
      collectionId: def.data.collectionId,
      name: def.data.name,
      type: def.data.type,
      sortOrder: def.data.sortOrder,
      allowedValues: def.data.allowedValues ?? undefined,
    },
  })
  activeTab.value = 'properties'
}

const onSubmit = handleSubmit(async (values) => {
  try {
    if (editingId.value) {
      await propertyStore.updateDefinition(editingId.value, values)
    } else {
      await propertyStore.createDefinition(values)
    }
    cancelForm()
  } catch (err) {
    void notification.handleApiError(err, t('property.saveError'))
  }
})

// --- Delete confirmation --------------------------------------------------
const deletingDef = ref<PropertyDefinitionJson | null>(null)
const showDeleteConfirm = ref(false)

function startDelete(def: PropertyDefinitionJson) {
  deletingDef.value = def
  showDeleteConfirm.value = true
}

async function confirmDelete() {
  if (!deletingDef.value) return
  try {
    await propertyStore.deleteDefinition(deletingDef.value.id)
    if (editingId.value === deletingDef.value.id) cancelForm()
  } catch (err) {
    void notification.handleApiError(err, t('property.deleteError'))
  } finally {
    deletingDef.value = null
    showDeleteConfirm.value = false
  }
}

function onOpenChange(value: boolean) {
  if (!value) {
    activeTab.value = 'display'
  }
  emit('update:open', value)
}

function optionsPreview(allowedValues: string | undefined): string {
  if (!allowedValues) return ''
  return allowedValues
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .join(' · ')
}
</script>

<template>
  <DialogCl :open="open" class="!max-w-[560px]" @update:open="onOpenChange">
    <template #title>
      {{ t('collectionSettings.title') }}
    </template>
    <!-- Sub-title in the dialog header — repeats the collection name so the
         user always sees which collection these settings apply to. -->
    <template v-if="collectionStore.collectionName" #description>
      <span class="font-mono">{{ collectionStore.collectionName }}</span>
    </template>

    <!-- Tab bar. Lives in DialogCl's `#header-extras` slot so it stays
         pinned to the top alongside the title while the body content
         scrolls. The active underline is an absolute-positioned span
         instead of `border-b-2`, because the border-overlap trick proved
         finicky across Tailwind/browser combos and kept dropping the line. -->
    <template #header-extras>
      <div class="relative flex items-center gap-1 border-b border-border">
        <button
          type="button"
          class="relative px-3.5 py-2 text-sm font-medium transition-colors"
          :class="
            activeTab === 'display'
              ? 'text-foreground'
              : 'text-muted-foreground hover:text-foreground'
          "
          data-testid="collection-settings-tab-display"
          @click="activeTab = 'display'"
        >
          {{ t('collectionSettings.tabDisplay') }}
          <span
            v-if="activeTab === 'display'"
            class="absolute -bottom-px left-0 right-0 h-[2px] bg-primary"
            aria-hidden="true"
          />
        </button>
        <button
          type="button"
          class="relative px-3.5 py-2 text-sm font-medium transition-colors inline-flex items-center gap-1.5"
          :class="
            activeTab === 'properties'
              ? 'text-foreground'
              : 'text-muted-foreground hover:text-foreground'
          "
          data-testid="collection-settings-tab-properties"
          @click="activeTab = 'properties'"
        >
          {{ t('collectionSettings.tabProperties') }}
          <span
            v-if="propertyStore.definitions.length > 0"
            class="px-1.5 py-0 rounded-full bg-secondary text-muted-foreground text-[10px] tabular-nums"
          >
            {{ propertyStore.definitions.length }}
          </span>
          <span
            v-if="activeTab === 'properties'"
            class="absolute -bottom-px left-0 right-0 h-[2px] bg-primary"
            aria-hidden="true"
          />
        </button>
      </div>
    </template>

    <!-- Both tab bodies share a `min-h-[380px]` so switching tabs doesn't make
         the dialog reflow vertically. Tall enough for the Display tab; the
         Properties tab grows past it when the form is open. -->
    <div v-if="activeTab === 'display'" class="min-h-[380px] space-y-5">
      <section>
        <div
          class="text-[11px] font-semibold uppercase tracking-[.06em] text-muted-foreground mb-2"
        >
          {{ t('collectionSettings.sectionLayout') }}
        </div>
        <div class="flex gap-2">
          <button
            v-for="option in layouts"
            :key="option.value"
            type="button"
            class="flex flex-1 flex-col items-center gap-2 rounded-md border px-4 py-3 text-sm transition-colors"
            :class="
              currentLayout === option.value
                ? 'border-primary bg-primary/10 text-foreground'
                : 'border-border bg-card text-muted-foreground hover:bg-accent'
            "
            :data-testid="`collection-settings-layout-${option.value}`"
            :aria-pressed="currentLayout === option.value"
            @click="selectLayout(option.value)"
          >
            <component :is="option.icon" class="h-5 w-5" />
            {{ t(option.labelKey) }}
          </button>
        </div>
      </section>

      <section>
        <div
          class="text-[11px] font-semibold uppercase tracking-[.06em] text-muted-foreground mb-2"
        >
          {{ t('collectionSettings.sectionProperties') }}
        </div>
        <div class="space-y-2">
          <div
            class="flex items-center justify-between gap-3 px-2.5 py-2 rounded-md bg-secondary/60"
          >
            <div class="min-w-0">
              <div class="text-sm font-medium">{{ t('property.badgesToggle') }}</div>
              <div class="text-xs text-muted-foreground mt-0.5">
                {{ t('property.badgesToggleDesc') }}
              </div>
            </div>
            <SwitchCl
              v-model="showBadges"
              :aria-label="t('property.badgesToggle')"
              data-testid="toggle-show-badges"
            />
          </div>
          <div
            class="flex items-center justify-between gap-3 px-2.5 py-2 rounded-md bg-secondary/60"
          >
            <div class="min-w-0">
              <div class="text-sm font-medium">{{ t('property.sidebarToggle') }}</div>
              <div class="text-xs text-muted-foreground mt-0.5">
                {{ t('property.sidebarToggleDesc') }}
              </div>
            </div>
            <SwitchCl
              v-model="showSidebar"
              :aria-label="t('property.sidebarToggle')"
              data-testid="toggle-show-sidebar"
            />
          </div>
        </div>
        <p class="text-xs text-muted-foreground mt-2">
          {{ t('collectionSettings.localPersistedHint') }}
        </p>
      </section>
    </div>

    <!-- Properties tab -->
    <div v-else class="min-h-[380px] space-y-3">
      <i18n-t
        keypath="property.manageSectionHintWithSyntax"
        tag="p"
        class="text-xs text-muted-foreground leading-relaxed"
      >
        <template #syntax>
          <code
            class="font-mono text-[10.5px] bg-secondary border border-border text-foreground px-1.5 py-0.5 rounded"
          >
            property:name=value
          </code>
        </template>
      </i18n-t>

      <div
        v-if="propertyStore.definitions.length === 0 && !isEditing"
        class="text-sm text-muted-foreground py-1"
      >
        {{ t('property.noDefinitions') }}
      </div>

      <ul v-else-if="propertyStore.definitions.length > 0" class="divide-y divide-border/50">
        <li
          v-for="def in propertyStore.definitions"
          :key="def.id"
          class="group flex items-center gap-2 py-1.5"
          :data-testid="`property-row-${def.data.name}`"
        >
          <span class="font-mono text-[12.5px] font-medium text-foreground flex-1 truncate">
            {{ def.data.name }}
          </span>
          <span
            class="text-[10px] font-semibold uppercase tracking-[.04em] px-1.5 py-0.5 rounded bg-secondary text-muted-foreground border border-border"
          >
            {{ def.data.type }}
          </span>
          <span
            v-if="needsOptions(def.data.type) && def.data.allowedValues"
            class="text-[11px] text-muted-foreground truncate max-w-[120px]"
            :title="def.data.allowedValues"
          >
            {{ optionsPreview(def.data.allowedValues) }}
          </span>
          <button
            type="button"
            class="h-6 w-6 inline-flex items-center justify-center rounded text-muted-foreground hover:bg-secondary hover:text-foreground transition-colors"
            :aria-label="t('property.editProperty')"
            :title="t('property.editProperty')"
            :data-testid="`property-edit-${def.data.name}`"
            @click="startEdit(def)"
          >
            <Pencil class="h-3.5 w-3.5" />
          </button>
          <button
            type="button"
            class="h-6 w-6 inline-flex items-center justify-center rounded text-muted-foreground hover:bg-destructive/10 hover:text-destructive transition-colors"
            :aria-label="t('common.delete')"
            :title="t('common.delete')"
            :data-testid="`property-delete-${def.data.name}`"
            @click="startDelete(def)"
          >
            <Trash2 class="h-3.5 w-3.5" />
          </button>
        </li>
      </ul>

      <!-- Inline add/edit form -->
      <form
        v-if="isEditing || showAddForm"
        class="rounded-lg p-3 bg-primary/[0.04] border border-dashed border-primary/30 space-y-3"
        @submit.prevent="onSubmit"
      >
        <div class="text-[11px] font-semibold uppercase tracking-[.06em] text-foreground">
          {{ isEditing ? t('property.editProperty') : t('property.newProperty') }}
        </div>

        <FormFieldCl
          :label="t('property.fieldName')"
          for-id="property-name"
          :error="errors.name"
          required
        >
          <InputCl
            id="property-name"
            v-model="name"
            v-bind="nameAttrs"
            type="text"
            maxlength="50"
            :placeholder="t('property.namePlaceholder')"
            data-testid="property-name-input"
            class="font-mono"
          />
        </FormFieldCl>

        <div>
          <label class="block text-sm font-medium leading-none mb-2">
            {{ t('property.fieldType') }}
          </label>
          <div class="flex flex-wrap gap-1.5">
            <button
              v-for="opt in propertyTypes"
              :key="opt.value"
              type="button"
              class="px-2.5 py-1 text-[11.5px] rounded border transition-colors"
              :class="
                type === opt.value
                  ? 'bg-primary/10 border-primary text-primary'
                  : 'bg-secondary border-border text-muted-foreground hover:border-primary/40 hover:text-foreground'
              "
              :data-testid="`property-type-${opt.value}`"
              @click="type = opt.value"
              @blur="typeAttrs.onBlur"
            >
              {{ t(opt.labelKey) }}
            </button>
          </div>
        </div>

        <FormFieldCl
          v-if="needsOptions(type)"
          :label="`${t('property.fieldOptions')} ${t('property.fieldOptionsHint')}`"
          for-id="property-options"
          :error="errors.allowedValues"
          required
        >
          <InputCl
            id="property-options"
            v-model="allowedValues"
            v-bind="allowedValuesAttrs"
            type="text"
            :placeholder="t('property.optionsPlaceholder')"
            data-testid="property-options-input"
          />
        </FormFieldCl>

        <div class="flex justify-end gap-2">
          <ButtonCl type="button" variant="outline" size="sm" @click="cancelForm">
            {{ t('common.cancel') }}
          </ButtonCl>
          <ButtonCl type="submit" size="sm" :disabled="isSubmitting" data-testid="property-submit">
            {{ isEditing ? t('property.saveChanges') : t('property.addProperty') }}
          </ButtonCl>
        </div>
      </form>

      <button
        v-else
        type="button"
        class="inline-flex items-center gap-1 text-xs font-medium text-primary hover:opacity-80 transition-opacity py-1"
        data-testid="property-add-toggle"
        @click="startAdd"
      >
        <Plus class="h-3.5 w-3.5" />
        {{ t('property.addProperty') }}
      </button>
    </div>

    <!-- Single-button footer: this modal performs CRUD immediately on every
         action, so there's nothing to "submit". Reuses DialogFooterCl's
         chrome (bg-card / border-t) via its default slot, keeping the
         bottom rail consistent with form dialogs. -->
    <template #footer>
      <DialogFooterCl>
        <ButtonCl
          type="button"
          variant="default"
          size="sm"
          data-testid="collection-settings-done"
          @click="onOpenChange(false)"
        >
          {{ t('common.done') }}
        </ButtonCl>
      </DialogFooterCl>
    </template>

    <ConfirmDialog
      v-model:open="showDeleteConfirm"
      :title="t('property.deleteTitle')"
      :message="t('property.deleteConfirm', { name: deletingDef?.data.name ?? '' })"
      @confirmed="confirmDelete"
      @update:open="
        (value: unknown) => {
          if (!value) {
            deletingDef = null
          }
        }
      "
    />
  </DialogCl>
</template>
