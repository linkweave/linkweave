<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Search, X } from '@lucide/vue'

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

function onFocusSearch() {
  inputRef.value?.focus()
}

function clear() {
  emit('update:modelValue', '')
  inputRef.value?.focus()
}

onMounted(() => {
  globalThis.addEventListener('keydown', handleShortcut)
  globalThis.addEventListener('chainlink:focus-search', onFocusSearch)
})
onUnmounted(() => {
  globalThis.removeEventListener('keydown', handleShortcut)
  globalThis.removeEventListener('chainlink:focus-search', onFocusSearch)
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
      @input="emit('update:modelValue', ($event.target as HTMLInputElement).value)"
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
  </div>
</template>
