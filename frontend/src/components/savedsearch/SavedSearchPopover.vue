<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger } from 'radix-vue'
import { useI18n } from 'vue-i18n'
import { ButtonCl, DialogCl, InputCl } from '@/components/ui'
import type { SavedSearchJson } from '@/api/generated'
import { useSearchQueryStore } from '@/stores/searchQuery'
import { useNotificationStore } from '@/stores/notification'
import { useSavedSearchStore } from '@/stores/savedSearch'

/**
 * Unified save/rename UI for saved searches. The visual container differs by mode:
 *
 * - `mode='create'`: anchored popover. The default slot is the trigger element
 *   (wrapped in `PopoverTrigger as-child`). Used by the chip-strip funnel button.
 * - `mode='rename'`: centered `DialogCl` modal. The default slot is unused;
 *   `open` is controlled by the parent. Used by Smart Collections rows.
 *
 * The form content (title, query preview, name input, action buttons) is shared.
 */
const props = defineProps<{
  mode: 'create' | 'rename'
  open: boolean
  savedSearch?: SavedSearchJson | null
}>()
const emit = defineEmits<{
  'update:open': [boolean]
}>()

const { t } = useI18n()
const searchQueryStore = useSearchQueryStore()
const savedSearchStore = useSavedSearchStore()
const notification = useNotificationStore()

const name = ref('')
const submitting = ref(false)
const popoverContentRef = ref<InstanceType<typeof PopoverContent> | null>(null)

const queryPreview = computed(() => {
  if (props.mode === 'rename') return (props.savedSearch?.data.query ?? '').trim()
  return searchQueryStore.searchQuery.trim()
})

const title = computed(() =>
  props.mode === 'rename' ? t('savedSearch.renameTitle') : t('savedSearch.saveTitle'),
)
const submitLabel = computed(() =>
  props.mode === 'rename' ? t('savedSearch.renameSubmit') : t('savedSearch.saveButton'),
)

const canSubmit = computed(() => {
  const trimmed = name.value.trim()
  if (!trimmed) return false
  if (props.mode === 'rename') {
    return trimmed !== props.savedSearch?.data.name
  }
  return queryPreview.value.length > 0
})

watch(
  () => props.open,
  async (isOpen) => {
    if (!isOpen) return
    name.value = props.mode === 'rename' ? props.savedSearch?.data.name ?? '' : ''
    await nextTick()
    const input = document.querySelector<HTMLInputElement>('[data-saved-search-name-input]')
    input?.focus()
    input?.select()
  },
)

function close() {
  emit('update:open', false)
}

async function onSubmit() {
  if (submitting.value || !canSubmit.value) return
  submitting.value = true
  const trimmedName = name.value.trim()
  try {
    if (props.mode === 'rename') {
      if (!props.savedSearch) return
      const updated = await savedSearchStore.updateSavedSearch(props.savedSearch.id, {
        name: trimmedName,
      })
      notification.success(t('savedSearch.renamed', { name: updated.data.name }))
    } else {
      const created = await savedSearchStore.createSavedSearch(trimmedName, queryPreview.value)
      notification.success(t('savedSearch.saved', { name: created.data.name }))
    }
    close()
  } catch (err) {
    notification.handleApiError(
      err,
      t(props.mode === 'rename' ? 'savedSearch.renameError' : 'savedSearch.createError'),
    )
  } finally {
    submitting.value = false
  }
}

function onPopoverKeyDown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    close()
  } else if (event.key === 'Enter') {
    event.preventDefault()
    void onSubmit()
  }
}
</script>

<template>
  <!-- Create mode: anchored popover with the slot as trigger -->
  <PopoverRoot
    v-if="mode === 'create'"
    :open="open"
    @update:open="emit('update:open', $event)"
  >
    <PopoverTrigger as-child>
      <slot />
    </PopoverTrigger>
    <PopoverPortal>
      <PopoverContent
        ref="popoverContentRef"
        side="bottom"
        align="end"
        :side-offset="6"
        class="z-50 w-80 max-w-[calc(100vw-2rem)] rounded-md border border-border bg-popover p-3 text-popover-foreground shadow-xl ring-1 ring-black/5 dark:ring-white/10"
        data-testid="saved-search-popover"
        @keydown="onPopoverKeyDown"
      >
        <div class="space-y-3">
          <h3 class="text-sm font-semibold">{{ title }}</h3>
          <div
            class="rounded border border-border bg-muted/50 px-2 py-1.5 font-mono text-xs text-muted-foreground truncate"
            :title="queryPreview"
          >
            {{ queryPreview || '—' }}
          </div>
          <InputCl
            v-model="name"
            type="text"
            :placeholder="t('savedSearch.namePlaceholder')"
            data-saved-search-name-input
            data-testid="saved-search-name-input"
          />
          <div class="flex justify-end gap-2 pt-1">
            <ButtonCl variant="ghost" size="sm" type="button" @click="close">
              {{ t('savedSearch.cancelButton') }}
            </ButtonCl>
            <ButtonCl
              size="sm"
              type="button"
              :disabled="!canSubmit || submitting"
              data-testid="saved-search-submit"
              @click="onSubmit"
            >
              {{ submitLabel }}
            </ButtonCl>
          </div>
        </div>
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>

  <!-- Rename mode: centered modal dialog, no trigger needed -->
  <DialogCl
    v-else
    :open="open"
    class="max-w-md"
    @update:open="emit('update:open', $event)"
  >
    <template #title>{{ title }}</template>
    <div
      data-testid="saved-search-rename-dialog"
      class="space-y-3"
      @keydown.enter.prevent="onSubmit"
    >
      <div
        class="rounded border border-border bg-muted/50 px-2 py-1.5 font-mono text-xs text-muted-foreground truncate"
        :title="queryPreview"
      >
        {{ queryPreview || '—' }}
      </div>
      <InputCl
        v-model="name"
        type="text"
        :placeholder="t('savedSearch.namePlaceholder')"
        data-saved-search-name-input
        data-testid="saved-search-name-input"
      />
    </div>
    <template #footer>
      <div class="flex justify-end gap-2 px-4 sm:px-6 py-3 border-t border-border">
        <ButtonCl variant="ghost" size="sm" type="button" @click="close">
          {{ t('savedSearch.cancelButton') }}
        </ButtonCl>
        <ButtonCl
          size="sm"
          type="button"
          :disabled="!canSubmit || submitting"
          data-testid="saved-search-submit"
          @click="onSubmit"
        >
          {{ submitLabel }}
        </ButtonCl>
      </div>
    </template>
  </DialogCl>
</template>
