<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onUnmounted } from 'vue'
import { Search, X } from '@lucide/vue'
import SearchAutocompleteDropdown from './SearchAutocompleteDropdown.vue'
import { useSearchAutocomplete, type AcResult, type AcItem } from '@/composables/useSearchAutocomplete'

const props = withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
  variant?: 'default' | 'header'
}>(), {
  placeholder: 'Search...',
  variant: 'default',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const inputRef = ref<HTMLInputElement | null>(null)

// ── Autocomplete ──────────────────────────────────────────────────────────
const { parseQueryForAutoCompl } = useSearchAutocomplete()
const acResult = ref<AcResult | null>(null)
const acIdx = ref(0)
const acMouseDown = ref(false)

function refreshAc(val: string, pos?: number) {
  const c = pos ?? inputRef.value?.selectionStart ?? val.length
  const r = parseQueryForAutoCompl(val, c)
  acResult.value = r && r.items.length > 0 ? r : null
  acIdx.value = 0
}

function onInput(e: Event) {
  const target = e.target as HTMLInputElement
  emit('update:modelValue', target.value)
  refreshAc(target.value, target.selectionStart ?? undefined)
}

function onClick() {
  refreshAc(props.modelValue, inputRef.value?.selectionStart ?? undefined)
}

// Caret-moving keys re-evaluate suggestions for the token the cursor lands on,
// so arrowing back into an operator token reopens the dropdown (parity with
// clicking into it). Handled on keyup, after the browser has moved the caret.
// ArrowUp/ArrowDown are excluded — when the dropdown is open they navigate the
// list (and are preventDefault-ed in onAcKeyDown, so the caret never moves).
const CARET_KEYS = new Set(['ArrowLeft', 'ArrowRight', 'Home', 'End'])
function onKeyUp(e: KeyboardEvent) {
  if (CARET_KEYS.has(e.key)) {
    refreshAc(props.modelValue, inputRef.value?.selectionStart ?? undefined)
  }
}

function onBlur() {
  // Selecting a suggestion blurs the input before the click lands. `acMouseDown`
  // (set in onAcMouseDown) tells us the blur was caused by pressing inside the
  // dropdown, so we keep it open until the click runs. We deliberately avoid
  // `@mousedown.prevent` on the dropdown — preventDefault on a synthesized
  // mousedown can swallow the follow-up click on some touch browsers.
  if (!acMouseDown.value) acResult.value = null
}

function onAcKeyDown(e: KeyboardEvent) {
  if (!acResult.value) return
  const n = acResult.value.items.length
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    acIdx.value = Math.min(acIdx.value + 1, n - 1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    acIdx.value = Math.max(acIdx.value - 1, 0)
  } else if ((e.key === 'Enter' || e.key === 'Tab') && n > 0) {
    const item = acResult.value.items[acIdx.value]
    if (item) {
      e.preventDefault()
      commit(item)
    }
  } else if (e.key === 'Escape') {
    e.preventDefault()
    acResult.value = null
  }
}

function commit(item: AcItem) {
  if (!acResult.value) return
  const [s, e] = acResult.value.range
  const q = props.modelValue
  // No trailing space for property:key= — the user types the value next.
  const suffix = item.insert.endsWith('=') ? '' : ' '
  const tail = q.slice(e).replace(/^\s+/, '')
  const newQuery = q.slice(0, s) + item.insert + suffix + tail
  const newCursor = s + item.insert.length + suffix.length

  emit('update:modelValue', newQuery)
  acResult.value = null

  nextTick(() => {
    inputRef.value?.focus()
    inputRef.value?.setSelectionRange(newCursor, newCursor)
    // Chain off `newQuery` (the value we just emitted), not props.modelValue:
    // this is a controlled input, so the displayed value is whatever we emit,
    // and the follow-up suggestions must align with the cursor we just set.
    const follow = parseQueryForAutoCompl(newQuery, newCursor)
    if (follow && follow.items.length > 0) {
      acResult.value = follow
      acIdx.value = 0
    }
  })
}

function onAcMouseDown() {
  acMouseDown.value = true
  setTimeout(() => {
    acMouseDown.value = false
  }, 200)
}

const shortcutKeys = computed(() => {
  if (navigator.userAgent.includes('Mac')) return ['⌘', 'K']
  return ['Ctrl', 'K']
})

function handleShortcut(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault()
    inputRef.value?.focus()
  } else if (e.key === '/' && !e.metaKey && !e.ctrlKey && !e.altKey) {
    const tag = (e.target as HTMLElement).tagName
    if (tag !== 'INPUT' && tag !== 'TEXTAREA' && tag !== 'SELECT') {
      e.preventDefault()
      inputRef.value?.focus()
    }
  }
}

function clear() {
  emit('update:modelValue', '')
  inputRef.value?.focus()
}

onMounted(() => {
  globalThis.addEventListener('keydown', handleShortcut)
})
onUnmounted(() => {
  globalThis.removeEventListener('keydown', handleShortcut)
})
</script>

<template>
  <div class="relative">
    <Search
      class="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none"
    />
    <input
      ref="inputRef"
      type="text"
      :value="props.modelValue"
      :placeholder="props.placeholder"
      data-search-input
      :class="[
        'flex w-full rounded-md border bg-secondary pl-10 pr-20 py-1 text-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring',
        props.variant === 'header' ? 'h-9' : 'h-10',
        props.modelValue ? 'border-primary/30 bg-primary/5' : 'border-border',
      ]"
      @input="onInput"
      @keydown="onAcKeyDown"
      @keyup="onKeyUp"
      @click="onClick"
      @blur="onBlur"
    />
    <kbd
      v-if="!props.modelValue"
      class="absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none inline-flex items-center gap-0.5 select-none"
    >
      <template v-for="(key, i) in shortcutKeys" :key="i">
        <span v-if="i > 0" class="text-[10px] text-muted-foreground/60">+</span>
        <span
          class="bg-background dark:bg-muted-foreground/20 text-foreground
          dark:text-muted-foreground
          shadow-[0_1px_0_1px_rgba(0,0,0,0.08)]
          dark:shadow-[]
          inline-flex h-5 min-w-5 items-center justify-center rounded-sm border border-border dark:border-muted-foreground/30 px-1.5 font-sans text-[11px] font-medium"
          >{{ key }}</span
        >
      </template>
    </kbd>
    <button
      v-if="props.modelValue"
      class="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
      @click="clear"
    >
      <X class="h-4 w-4" />
    </button>

    <SearchAutocompleteDropdown
      v-if="acResult"
      :result="acResult"
      :active-idx="acIdx"
      @select="commit"
      @mousedown="onAcMouseDown"
    />
  </div>
</template>
