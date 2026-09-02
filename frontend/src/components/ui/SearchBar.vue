<script setup lang="ts">
// A plain search input: value, placeholder, clear button, ⌘K / `/` focus
// shortcut. It knows no query grammar — the UC-070 bookmark grammar lives in
// `components/bookmark/BookmarkSearchBar.vue`, which drives this one through
// `caret` / `keydown` / `blur` and renders its dropdown into the `overlay`
// slot. Keeping the two apart is what lets `CollectionManageView` filter
// collection names by substring without inheriting bookmark autocomplete.
import { ref, computed, onMounted, onUnmounted, useId } from 'vue'
import { Search, X } from '@lucide/vue'
import { useI18n } from 'vue-i18n'

const props = withDefaults(defineProps<{
  modelValue: string
  placeholder?: string
  variant?: 'default' | 'header'
  /**
   * Paint the input destructive — "something in here is wrong", at the source.
   * What counts as wrong is the caller's business; this component only shows it.
   */
  invalid?: boolean
  /**
   * Why the value is invalid. Colour alone is not a signal a screen-reader or
   * colour-blind user receives, so this is announced politely and wired to the
   * input through `aria-describedby` whenever `invalid` is set.
   */
  invalidMessage?: string
}>(), {
  placeholder: 'Search...',
  variant: 'default',
  invalid: false,
  invalidMessage: '',
})

const { t } = useI18n()

const messageId = useId()
const describedBy = computed(() =>
  props.invalid && props.invalidMessage ? messageId : undefined,
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
  /**
   * The value and caret position, whenever either may have moved — everything
   * an overlay needs to follow the token under the cursor.
   */
  caret: [value: string, cursor: number]
  keydown: [event: KeyboardEvent]
  blur: []
}>()

const inputRef = ref<HTMLInputElement | null>(null)

function caretPos(): number {
  return inputRef.value?.selectionStart ?? props.modelValue.length
}

function onInput(e: Event) {
  const target = e.target as HTMLInputElement
  emit('update:modelValue', target.value)
  emit('caret', target.value, target.selectionStart ?? target.value.length)
}

function onClick() {
  emit('caret', props.modelValue, caretPos())
}

// Caret-moving keys re-report the position, so arrowing back into a token
// reopens whatever the overlay shows for it (parity with clicking into it).
// Handled on keyup, after the browser has moved the caret. ArrowUp/ArrowDown
// are excluded — an open dropdown navigates its list with them (and
// preventDefault-s in its `keydown` handler, so the caret never moves).
const CARET_KEYS = new Set(['ArrowLeft', 'ArrowRight', 'Home', 'End'])
function onKeyUp(e: KeyboardEvent) {
  if (CARET_KEYS.has(e.key)) emit('caret', props.modelValue, caretPos())
}

/**
 * Focus the input, optionally placing the caret. Exposed for overlays that
 * rewrite the value and must put the cursor where the user will type next.
 */
function focusAt(pos?: number) {
  inputRef.value?.focus()
  if (pos !== undefined) inputRef.value?.setSelectionRange(pos, pos)
}
defineExpose({ focusAt })

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
  // Report the emptied value too: an overlay reading the token under the
  // cursor must close rather than hang over a field it no longer describes.
  // Clicking a button does not blur the input in every browser, so the blur
  // handler cannot be relied on here.
  emit('caret', '', 0)
  focusAt()
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
      :aria-invalid="props.invalid ? 'true' : undefined"
      :aria-describedby="describedBy"
      :class="[
        'flex w-full rounded-md border bg-secondary pl-10 pr-20 py-1 text-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1',
        props.variant === 'header' ? 'h-9' : 'h-10',
        props.modelValue && props.invalid
          ? 'border-destructive/40 bg-destructive/5 focus-visible:ring-destructive/50'
          : props.modelValue
            ? 'border-primary/30 bg-primary/5 focus-visible:ring-ring'
            : 'border-border focus-visible:ring-ring',
      ]"
      @input="onInput"
      @keydown="emit('keydown', $event)"
      @keyup="onKeyUp"
      @click="onClick"
      @blur="emit('blur')"
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
    <!-- An icon-only control needs a name: without one it reads as "button". -->
    <button
      v-if="props.modelValue"
      type="button"
      data-testid="search-clear"
      :aria-label="t('search.clear')"
      class="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
      @click="clear"
    >
      <X class="h-4 w-4" />
    </button>

    <!-- The invalid state reaches assistive tech here: `aria-invalid` marks the
         field, and this politely-announced message says what is wrong. It is
         visually hidden because the sighted equivalent is the destructive
         border plus whatever the caller renders elsewhere. -->
    <p v-if="describedBy" :id="messageId" class="sr-only" role="status">
      {{ props.invalidMessage }}
    </p>

    <!-- Rendered inside the positioned container so an overlay can anchor to
         the input with plain `absolute top-full`. -->
    <slot name="overlay" />
  </div>
</template>
