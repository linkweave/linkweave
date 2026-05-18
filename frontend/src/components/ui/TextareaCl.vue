<script setup lang="ts">
import { computed } from 'vue'

// Mirror of InputCl for multi-line text. Same elevation (`bg-input`), same
// focus ring, same border treatment; `resize-none` by default because forms
// inside dialogs shouldn't grow user-resizable. Caller can override `rows`
// (default 3) and any attribute via $attrs.

const props = defineProps<{ modelValue?: string | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

defineOptions({ inheritAttrs: false })

const BASE =
  'flex w-full rounded-md border border-input bg-input px-3 py-1 text-sm shadow-sm ' +
  'transition-colors placeholder:text-muted-foreground ' +
  'focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring ' +
  'disabled:cursor-not-allowed disabled:opacity-50 resize-none'

const value = computed(() => props.modelValue ?? '')

function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLTextAreaElement).value)
}
</script>

<template>
  <textarea
    :value="value"
    :rows="3"
    :class="[BASE, $attrs.class as string | undefined]"
    v-bind="{ ...$attrs, class: undefined }"
    @input="onInput"
  />
</template>
