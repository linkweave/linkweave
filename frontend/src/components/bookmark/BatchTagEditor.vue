<script setup lang="ts">
import {
  ariaCheckedFor,
  baseState,
  buildToastMessage,
  changeSummary,
  computeChanges,
  markFor,
  nextIntent,
  type Base,
  type Intent,
} from '@/components/bookmark/batchTagModel'
import { useBookmarkStore } from '@/stores/bookmark'
import { useCollectionStore } from '@/stores/collection'
import { useNotificationStore } from '@/stores/notification'
import { useSelectionStore } from '@/stores/selection'
import { useTagStore } from '@/stores/tag'
import { Plus, Search } from '@lucide/vue'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

/**
 * Tri-state batch tag editor (UC-074a). Anchored under the batch-bar "Tags"
 * button. Every editable tag shows its state across the selection (on all /
 * some / none); clicking a row cycles a draft of add/remove intents that
 * commit together on Apply in one atomic batch. Selection is retained after
 * Apply (deliberate exception to the UC-074 "leaving clears selection" rule)
 * so tagging can stay iterative.
 */
const props = defineProps<{ open: boolean; anchor: HTMLElement | null }>()
const emit = defineEmits<{ 'update:open': [boolean] }>()

const { t } = useI18n()
const selection = useSelectionStore()
const tagStore = useTagStore()
const bookmarkStore = useBookmarkStore()
const collectionStore = useCollectionStore()
const notification = useNotificationStore()

// Neutral dot for tags without a colour: inline-created tags get their real
// colour from the backend on Apply (TagColorPalette.autoAssignColor), matching
// CreateTagDialog / useTagSuggestions — so until then the dot is a placeholder.
const NEUTRAL_DOT = 'var(--color-muted-foreground)'

interface NewTag { id: string; name: string }

const query = ref('')
const draft = ref<Record<string, Intent>>({})
const createdThisSession = ref<NewTag[]>([])
const applying = ref(false)
let tmpSeq = 0

const searchInput = ref<HTMLInputElement | null>(null)
const popoverEl = ref<HTMLElement | null>(null)
const posStyle = ref<Record<string, string>>({})

// ---------------------------------------------------------------------------
// Derived selection state
// ---------------------------------------------------------------------------

const n = computed(() => selection.count)

function countFor(tagId: string): number {
  let c = 0
  for (const b of selection.selectedBookmarks) {
    if (b.data.tagIds?.has(tagId)) c++
  }
  return c
}

// Universe = inline-created (this session, not yet committed) + every real tag
// in the collection, so a tag none of the selection has can still be added.
interface UniverseRow { id: string; name: string; color: string; isNew: boolean }
const universe = computed<UniverseRow[]>(() => [
  ...createdThisSession.value.map((c) => ({ id: c.id, name: c.name, color: NEUTRAL_DOT, isNew: true })),
  ...tagStore.tags.map((tag) => ({
    id: tag.id,
    name: tag.data.name,
    color: tag.data.color ?? NEUTRAL_DOT,
    isNew: false,
  })),
])

interface RowView extends UniverseRow {
  count: number
  base: Base
  intent: Intent | undefined
}

const rows = computed<RowView[]>(() => {
  const q = query.value.trim().toLowerCase()
  const list = universe.value
    .filter((r) => q === '' || r.name.toLowerCase().includes(q))
    .map<RowView>((r) => {
      const count = r.isNew ? 0 : countFor(r.id)
      const base: Base = baseState(count, n.value)
      return { ...r, count, base, intent: draft.value[r.id] }
    })
  // Tags on at least one selected item sort above `none` tags; alpha tiebreak.
  return list.sort((a, b) => {
    const ra = a.base === 'none' ? 1 : 0
    const rb = b.base === 'none' ? 1 : 0
    if (ra !== rb) return ra - rb
    return a.name.localeCompare(b.name)
  })
})

const exactMatch = computed(() => {
  const q = query.value.trim().toLowerCase()
  return q !== '' && universe.value.some((r) => r.name.toLowerCase() === q)
})
const showCreate = computed(() => query.value.trim() !== '' && !exactMatch.value)
const isEmpty = computed(() => universe.value.length === 0 && query.value.trim() === '')

// Net (non-zero-effect) change set, keyed by tag.
const changes = computed(() =>
  computeChanges(
    draft.value,
    universe.value.map((r) => ({
      id: r.id,
      name: r.name,
      isNew: r.isNew,
      count: r.isNew ? 0 : countFor(r.id),
    })),
    n.value,
  ),
)

const hasChanges = computed(
  () => changes.value.adds.length > 0 || changes.value.removes.length > 0,
)

const summary = computed(() => changeSummary(changes.value.adds, changes.value.removes))

// ---------------------------------------------------------------------------
// Per-row display helpers
// ---------------------------------------------------------------------------

function mark(row: RowView) {
  return markFor(row.base, row.intent)
}

function ariaChecked(row: RowView) {
  return ariaCheckedFor(row.base, row.intent)
}

interface Hint { text: string; cls: string }
function hint(row: RowView): Hint | null {
  if (row.intent === 'add') {
    return { text: t('batchTag.addTo', { k: n.value - row.count }), cls: 'hint-add' }
  }
  if (row.intent === 'remove') {
    return { text: t('batchTag.removeFrom', { k: row.count }), cls: 'hint-remove' }
  }
  if (row.base === 'some') {
    return { text: t('batchTag.cOfN', { c: row.count, n: n.value }), cls: 'hint-some' }
  }
  if (row.base === 'all') {
    return { text: t('batchTag.allN', { n: n.value }), cls: 'hint-all' }
  }
  return null
}

// ---------------------------------------------------------------------------
// Interaction
// ---------------------------------------------------------------------------

function cycle(row: RowView): void {
  const intent = nextIntent(row.base, draft.value[row.id])
  const next = { ...draft.value }
  if (intent === undefined) delete next[row.id]
  else next[row.id] = intent
  draft.value = next
}

function createInline(): void {
  const name = query.value.trim()
  if (name === '' || exactMatch.value) return
  const id = `tmp-${tmpSeq++}`
  createdThisSession.value = [...createdThisSession.value, { id, name }]
  draft.value = { ...draft.value, [id]: 'add' }
  query.value = ''
  searchInput.value?.focus()
}

function onInputEnter(): void {
  if (showCreate.value) createInline()
}

async function apply(): Promise<void> {
  if (!hasChanges.value || applying.value) return
  // The editor is only reachable with a loaded collection; guard the invariant
  // explicitly rather than posting an empty collectionId.
  const collectionId = collectionStore.currentCollectionId
  if (!collectionId) return
  applying.value = true
  const { adds, removes } = changes.value
  const idMap = new Map<string, string>() // temp id -> real id
  const createdReal: string[] = []
  try {
    // Commit inline-created tags first, then map their temp ids to real ids.
    for (const a of adds) {
      if (a.isNew) {
        const tmp = createdThisSession.value.find((c) => c.id === a.id)
        if (!tmp) continue
        // No colour sent — the backend auto-assigns from its palette, matching
        // the app's other create sites (CreateTagDialog, useTagSuggestions).
        const real = await tagStore.createTag({ collectionId, name: tmp.name })
        idMap.set(a.id, real.id)
        createdReal.push(real.id)
      }
    }
    const addTagIds = adds.map((a) => idMap.get(a.id) ?? a.id)
    const removeTagIds = removes.map((r) => r.id)
    await bookmarkStore.batchEditTags([...selection.selectedIds], addTagIds, removeTagIds)
    notification.success(
      buildToastMessage(adds, removes, (key, params) => t(`batchTag.${key}`, params)),
    )
    resetDraft()
    close()
  } catch {
    // Roll back any inline-created tags so a failed batch leaves no orphans.
    // If a rollback itself fails the tag is left persisted; surface it rather
    // than swallowing silently so the orphan isn't invisible.
    for (const id of createdReal) {
      try {
        await tagStore.deleteTag(id)
      } catch (rollbackErr) {
        console.error(`Failed to roll back inline-created tag ${id}; it may be orphaned.`, rollbackErr)
      }
    }
    notification.error(t('batchTag.applyError'))
    close()
  } finally {
    applying.value = false
  }
}

function resetDraft(): void {
  draft.value = {}
  query.value = ''
  createdThisSession.value = []
}

function close(): void {
  emit('update:open', false)
}

function cancel(): void {
  resetDraft()
  close()
}

// ---------------------------------------------------------------------------
// Positioning + lifecycle (fixed, clamped to viewport; close on outside / Esc)
// ---------------------------------------------------------------------------

const POPOVER_WIDTH = 288
const VIEWPORT_MARGIN = 8
const ANCHOR_GAP = 6

function reposition(): void {
  const a = props.anchor
  if (!a) return
  const r = a.getBoundingClientRect()
  const left = Math.min(r.left, window.innerWidth - POPOVER_WIDTH - VIEWPORT_MARGIN)
  // Default below the anchor; flip above it if there isn't room below. Falls
  // back to an estimate before the popover has rendered (height unknown).
  const height = popoverEl.value?.offsetHeight ?? 360
  const below = r.bottom + ANCHOR_GAP
  const fitsBelow = below + height <= window.innerHeight - VIEWPORT_MARGIN
  const top = fitsBelow ? below : Math.max(VIEWPORT_MARGIN, r.top - ANCHOR_GAP - height)
  posStyle.value = { left: `${Math.max(VIEWPORT_MARGIN, left)}px`, top: `${top}px` }
}

function onDocMousedown(e: MouseEvent): void {
  const target = e.target as Node
  if (popoverEl.value?.contains(target)) return
  if (props.anchor?.contains(target)) return // let the Tags button toggle itself
  close()
}

function onKeydown(e: KeyboardEvent): void {
  if (e.key === 'Escape') {
    e.preventDefault() // keep Esc from also clearing the selection underneath
    e.stopPropagation()
    close()
    props.anchor?.focus()
  }
}

function addListeners(): void {
  document.addEventListener('mousedown', onDocMousedown, true)
  document.addEventListener('keydown', onKeydown, true)
  window.addEventListener('resize', reposition)
  window.addEventListener('scroll', reposition, true)
}

function removeListeners(): void {
  document.removeEventListener('mousedown', onDocMousedown, true)
  document.removeEventListener('keydown', onKeydown, true)
  window.removeEventListener('resize', reposition)
  window.removeEventListener('scroll', reposition, true)
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      resetDraft()
      reposition() // immediate (estimated height) to avoid a flash at 0,0
      addListeners()
      nextTick(() => {
        reposition() // accurate now that the popover is in the DOM
        setTimeout(() => searchInput.value?.focus(), 40)
      })
    } else {
      removeListeners()
    }
  },
)

onBeforeUnmount(removeListeners)
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      ref="popoverEl"
      class="tag-editor"
      role="dialog"
      aria-modal="false"
      :aria-label="t('batchTag.title')"
      :style="posStyle"
      data-testid="batch-tag-editor"
    >
    <header class="te-header">
      <div class="te-header-row">
        <span class="te-title">{{ t('batchTag.title') }}</span>
        <span class="te-count">{{ t('batch.selected', { count: n }) }}</span>
      </div>
      <div class="te-search">
        <Search :size="13" class="te-search-icon" />
        <input
          ref="searchInput"
          v-model="query"
          type="text"
          class="te-search-input"
          :placeholder="t('batchTag.searchPlaceholder')"
          :aria-label="t('batchTag.searchPlaceholder')"
          data-testid="batch-tag-search"
          @keydown.enter.prevent="onInputEnter"
        />
      </div>
    </header>

    <div class="te-list" role="group" :aria-label="t('batchTag.title')">
      <button
        v-if="showCreate"
        type="button"
        class="te-create"
        data-testid="batch-tag-create"
        @click="createInline"
      >
        <Plus :size="13" class="te-create-icon" />
        <span>{{ t('batchTag.create') }} <strong>"{{ query.trim() }}"</strong></span>
      </button>

      <button
        v-for="row in rows"
        :key="row.id"
        type="button"
        class="te-row"
        role="checkbox"
        :aria-checked="ariaChecked(row)"
        :aria-label="`${row.name}${hint(row) ? `, ${hint(row)!.text}` : ''}`"
        :data-testid="`batch-tag-row-${row.name}`"
        @click="cycle(row)"
      >
        <span class="te-box" :class="`te-box--${mark(row)}`">
          <svg
            v-if="mark(row) === 'check'"
            class="te-check"
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
          <span v-else-if="mark(row) === 'dash'" class="te-dash" aria-hidden="true" />
          <span v-else-if="mark(row) === 'empty-x'" class="te-x" aria-hidden="true">×</span>
        </span>
        <span class="te-dot" :style="{ background: row.color }" />
        <span class="te-name" :class="{ 'te-name--dim': mark(row) === 'empty' }">{{ row.name }}</span>
        <span v-if="hint(row)" class="te-hint" :class="hint(row)!.cls">{{ hint(row)!.text }}</span>
      </button>

      <div v-if="isEmpty" class="te-no-tags">{{ t('batchTag.noTagsYet') }}</div>
    </div>

    <footer class="te-footer">
      <span class="te-summary" :class="{ 'te-summary--clean': !hasChanges }" data-testid="batch-tag-summary">
        {{ hasChanges ? summary : t('batchTag.noChanges') }}
      </span>
      <button type="button" class="te-cancel" data-testid="batch-tag-cancel" @click="cancel">
        {{ t('batch.cancel') }}
      </button>
      <button
        type="button"
        class="te-apply"
        :disabled="!hasChanges || applying"
        data-testid="batch-tag-apply"
        @click="apply"
      >
        {{ t('batchTag.apply') }}
      </button>
    </footer>
    </div>
  </Teleport>
</template>

<style scoped>
.tag-editor {
  position: fixed;
  z-index: 60;
  width: 288px;
  border-radius: 11px;
  border: 1px solid var(--color-border);
  background: var(--color-card);
  box-shadow: 0 18px 50px rgba(0, 0, 0, 0.45);
  color: var(--color-foreground);
  animation: te-pop 0.14s cubic-bezier(0.2, 0.7, 0.3, 1);
}

@keyframes te-pop {
  from {
    opacity: 0;
    transform: scale(0.96);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.te-header {
  padding: 10px 12px 8px;
}

.te-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.te-title {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.05em;
  color: var(--color-muted-foreground);
  text-transform: uppercase;
}

.te-count {
  font-size: 11px;
  color: var(--color-muted-foreground);
  font-variant-numeric: tabular-nums;
}

.te-search {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 30px;
  margin-top: 8px;
  padding: 0 9px;
  border-radius: 7px;
  background: var(--color-input);
  border: 1px solid var(--color-border);
}

.te-search-icon {
  color: var(--color-muted-foreground);
  flex-shrink: 0;
}

.te-search-input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  color: var(--color-foreground);
  font-size: 12.5px;
  outline: none;
}

.te-search-input::placeholder {
  color: var(--color-muted-foreground);
}

.te-list {
  max-height: 228px;
  overflow-y: auto;
  padding: 0 6px;
}

.te-create {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 32px;
  padding: 0 8px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-foreground);
  font-size: 12.5px;
  text-align: left;
  cursor: pointer;
}

.te-create:hover {
  background: var(--color-secondary);
}

.te-create-icon {
  color: var(--color-primary);
  flex-shrink: 0;
}

.te-create strong {
  font-weight: 600;
  color: var(--color-foreground);
}

.te-row {
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
  cursor: pointer;
  text-align: left;
}

.te-row:hover {
  background: var(--color-secondary);
}

.te-box {
  display: grid;
  place-items: center;
  width: 16px;
  height: 16px;
  border-radius: 4px;
  border: 1.5px solid var(--color-border);
  flex-shrink: 0;
}

.te-box--check {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-primary-foreground);
}

.te-box--dash {
  border-color: var(--color-muted-foreground);
}

.te-box--empty-x {
  border-color: var(--color-remove);
}

.te-dash {
  width: 8px;
  height: 2px;
  background: var(--color-muted-foreground);
}

.te-x {
  color: var(--color-remove);
  font-size: 12px;
  line-height: 1;
}

.te-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  flex-shrink: 0;
}

.te-name {
  flex: 1;
  min-width: 0;
  font-size: 12.5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.te-name--dim {
  opacity: 0.7;
}

.te-hint {
  font-size: 10.5px;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.hint-add {
  color: var(--color-primary);
}

.hint-remove {
  color: var(--color-remove);
}

.hint-some,
.hint-all {
  color: var(--color-muted-foreground);
}

.te-no-tags {
  padding: 18px 8px;
  text-align: center;
  font-size: 12.5px;
  color: var(--color-muted-foreground);
}

.te-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-top: 1px solid var(--color-border);
}

.te-summary {
  flex: 1;
  min-width: 0;
  font-size: 11.5px;
  color: var(--color-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.te-summary--clean {
  color: var(--color-muted-foreground);
}

.te-cancel {
  height: 28px;
  padding: 0 12px;
  border-radius: 7px;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-foreground);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.te-cancel:hover {
  background: var(--color-secondary);
}

.te-apply {
  height: 28px;
  padding: 0 14px;
  border-radius: 7px;
  border: none;
  background: var(--color-primary);
  color: var(--color-primary-foreground);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
}

.te-apply:hover:not(:disabled) {
  background: color-mix(in oklab, var(--color-primary) 88%, black);
}

.te-apply:disabled {
  background: var(--color-muted);
  color: var(--color-muted-foreground);
  cursor: default;
}

@media (prefers-reduced-motion: reduce) {
  .tag-editor {
    animation: none;
  }
}
</style>
