<script setup lang="ts">
import { useNotificationStore } from '@/stores/notification'
import { useTagStore } from '@/stores/tag'
import { Plus, Search, X } from '@lucide/vue'
import { PopoverContent, PopoverPortal, PopoverRoot, PopoverTrigger } from 'radix-vue'
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

/**
 * Two-state tag picker for the Add/Edit Bookmark dialog (AI Suggested Tags
 * handoff). Reuses the Batch Tag Editor (UC-074a) combobox pattern reduced to a
 * single target: a click-to-open field showing applied tags as removable chips
 * plus a "+ Find or add tags…" affordance, opening a search + create + checklist
 * popover. Built on radix-vue Popover so it layers correctly inside the modal
 * dialog (a raw teleport would register as an outside-click and close it). New
 * tags get their colour from the backend palette on create (no colour sent),
 * matching the app's other create sites.
 */
const props = defineProps<{
  modelValue: Set<string> | undefined
  collectionId: string
  idPrefix: string
}>()
const emit = defineEmits<{ 'update:modelValue': [value: Set<string>] }>()

const { t } = useI18n()
const tagStore = useTagStore()
const notification = useNotificationStore()

const NEUTRAL_DOT = 'var(--color-muted-foreground)'

const applied = computed(() => props.modelValue ?? new Set<string>())

interface AppliedChip {
  id: string
  name: string
  color: string
}
const appliedChips = computed<AppliedChip[]>(() =>
  tagStore.tags
    .filter((tag) => applied.value.has(tag.id))
    .map((tag) => ({ id: tag.id, name: tag.data.name, color: tag.data.color ?? NEUTRAL_DOT })),
)

// --- Popover open / search / rows ---
const open = ref(false)
const query = ref('')
const creating = ref(false)
const searchInput = ref<HTMLInputElement | null>(null)

interface Row {
  id: string
  name: string
  color: string
  checked: boolean
}
const rows = computed<Row[]>(() => {
  const q = query.value.trim().toLowerCase()
  return tagStore.tags
    .filter((tag) => q === '' || tag.data.name.toLowerCase().includes(q))
    .map<Row>((tag) => ({
      id: tag.id,
      name: tag.data.name,
      color: tag.data.color ?? NEUTRAL_DOT,
      checked: applied.value.has(tag.id),
    }))
    .sort((a, b) => {
      if (a.checked !== b.checked) return a.checked ? -1 : 1
      return a.name.localeCompare(b.name)
    })
})

const exactMatch = computed(() => {
  const q = query.value.trim().toLowerCase()
  return q !== '' && tagStore.tags.some((tag) => tag.data.name.toLowerCase() === q)
})
const showCreate = computed(() => query.value.trim() !== '' && !exactMatch.value)
const isEmpty = computed(() => tagStore.tags.length === 0 && query.value.trim() === '')

function setApplied(next: Set<string>) {
  emit('update:modelValue', next)
}

function toggleRow(id: string) {
  const next = new Set(applied.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  setApplied(next)
}

function removeChip(id: string) {
  const next = new Set(applied.value)
  next.delete(id)
  setApplied(next)
}

async function createInline() {
  const name = query.value.trim()
  if (name === '' || exactMatch.value || creating.value) return
  creating.value = true
  try {
    const created = await tagStore.createTag({ collectionId: props.collectionId, name })
    const next = new Set(applied.value)
    next.add(created.id)
    setApplied(next)
    query.value = ''
    await nextTick()
    searchInput.value?.focus()
  } catch (err) {
    notification.handleApiError(err, t('tag.createError'))
  } finally {
    creating.value = false
  }
}

function onInputEnter() {
  if (showCreate.value) void createInline()
}

function onOpenAutoFocus(e: Event) {
  // Focus the search box instead of the first row.
  e.preventDefault()
  nextTick(() => searchInput.value?.focus())
}

watch(open, (isOpen) => {
  if (!isOpen) query.value = ''
})
</script>

<template>
  <PopoverRoot v-model:open="open">
    <PopoverTrigger as-child>
      <div
        class="tc-field"
        role="button"
        tabindex="0"
        :data-testid="`${idPrefix}-tags-trigger`"
      >
        <span
          v-for="chip in appliedChips"
          :key="chip.id"
          class="tc-chip"
          :data-testid="`applied-tag-${chip.name}`"
        >
          <span class="tc-chip-dot" :style="{ background: chip.color }" />
          <span class="tc-chip-name">{{ chip.name }}</span>
          <button
            type="button"
            class="tc-chip-x"
            :aria-label="t('bookmark.removeTag', { name: chip.name })"
            @click.stop="removeChip(chip.id)"
            @pointerdown.stop
          >
            <X :size="11" />
          </button>
        </span>
        <span class="tc-add">
          <Plus :size="12" />
          {{ t('bookmark.findOrAddTags') }}
        </span>
      </div>
    </PopoverTrigger>

    <PopoverPortal>
      <PopoverContent
        align="start"
        :side-offset="6"
        class="tc-pop z-[300]"
        :style="{ width: 'var(--radix-popover-trigger-width)' }"
        :data-testid="`${idPrefix}-tags-popover`"
        @open-auto-focus="onOpenAutoFocus"
      >
        <div class="tc-search">
          <Search :size="13" class="tc-search-icon" />
          <input
            ref="searchInput"
            v-model="query"
            type="text"
            class="tc-search-input"
            :placeholder="t('bookmark.findOrCreateTag')"
            :aria-label="t('bookmark.findOrCreateTag')"
            :data-testid="`${idPrefix}-tags-search`"
            @keydown.enter.prevent="onInputEnter"
          />
        </div>

        <div class="tc-list" role="group">
          <button
            v-if="showCreate"
            type="button"
            class="tc-create"
            :data-testid="`${idPrefix}-tags-create`"
            @click="createInline"
          >
            <Plus :size="13" class="tc-create-icon" />
            <span>{{ t('bookmark.tagCreate') }} <strong>"{{ query.trim() }}"</strong></span>
          </button>

          <button
            v-for="row in rows"
            :key="row.id"
            type="button"
            class="tc-row"
            role="checkbox"
            :aria-checked="row.checked"
            :data-testid="`tags-row-${row.name}`"
            @click="toggleRow(row.id)"
          >
            <span class="tc-box" :class="{ 'tc-box--on': row.checked }">
              <svg
                v-if="row.checked"
                viewBox="0 0 24 24"
                width="11"
                height="11"
                fill="none"
                stroke="currentColor"
                stroke-width="3"
                stroke-linecap="round"
                stroke-linejoin="round"
                aria-hidden="true"
              >
                <path d="M20 6 9 17l-5-5" />
              </svg>
            </span>
            <span class="tc-dot" :style="{ background: row.color }" />
            <span class="tc-name">{{ row.name }}</span>
          </button>

          <div v-if="isEmpty" class="tc-no-tags">{{ t('bookmark.noTagsYet') }}</div>
        </div>
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>

<style scoped>
.tc-field {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  min-height: 38px;
  padding: 6px 8px;
  border-radius: 8px;
  border: 1px solid var(--color-input);
  background: var(--color-input);
  cursor: text;
}
.tc-field:focus-visible {
  outline: 2px solid var(--color-ring);
  outline-offset: 1px;
}

.tc-chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 24px;
  padding: 0 4px 0 8px;
  border-radius: 9999px;
  background: var(--color-secondary);
  font-size: 12px;
  color: var(--color-foreground);
}
.tc-chip-dot {
  width: 7px;
  height: 7px;
  border-radius: 2px;
  flex-shrink: 0;
}
.tc-chip-name {
  max-width: 12rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tc-chip-x {
  display: grid;
  place-items: center;
  width: 16px;
  height: 16px;
  border: none;
  border-radius: 9999px;
  background: transparent;
  color: var(--color-muted-foreground);
  cursor: pointer;
}
.tc-chip-x:hover {
  background: var(--color-muted);
  color: var(--color-foreground);
}

.tc-add {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-muted-foreground);
}

.tc-pop {
  border-radius: 11px;
  border: 1px solid var(--color-border);
  background: var(--color-popover);
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.45);
  color: var(--color-popover-foreground);
}

.tc-search {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 32px;
  margin: 8px 8px 4px;
  padding: 0 9px;
  border-radius: 7px;
  background: var(--color-input);
  border: 1px solid var(--color-border);
}
.tc-search-icon {
  color: var(--color-muted-foreground);
  flex-shrink: 0;
}
.tc-search-input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  color: var(--color-foreground);
  font-size: 12.5px;
  outline: none;
}
.tc-search-input::placeholder {
  color: var(--color-muted-foreground);
}

.tc-list {
  max-height: 240px;
  overflow-y: auto;
  padding: 0 6px 6px;
}
.tc-create,
.tc-row {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  height: 34px;
  padding: 0 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-foreground);
  font-size: 12.5px;
  text-align: left;
  cursor: pointer;
}
.tc-create:hover,
.tc-row:hover {
  background: var(--color-popover-hover);
}
.tc-create-icon {
  color: var(--color-primary);
  flex-shrink: 0;
}
.tc-create strong {
  font-weight: 600;
}
.tc-box {
  display: grid;
  place-items: center;
  width: 16px;
  height: 16px;
  border-radius: 5px;
  border: 1.5px solid var(--color-border);
  flex-shrink: 0;
}
.tc-box--on {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-primary-foreground);
}
.tc-dot {
  width: 7px;
  height: 7px;
  border-radius: 2px;
  flex-shrink: 0;
}
.tc-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tc-no-tags {
  padding: 18px 8px;
  text-align: center;
  font-size: 12.5px;
  color: var(--color-muted-foreground);
}
</style>
