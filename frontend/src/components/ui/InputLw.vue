<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ modelValue?: string | number | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

defineOptions({ inheritAttrs: false })

// Use `bg-input` (the dedicated input shade) rather than `bg-transparent` so
// the field reads consistently regardless of the surrounding surface — in
// particular, on top of `bg-card` dialogs the input now sits a step brighter,
// matching the elevation pattern in the design system.
const BASE =
  'flex h-9 w-full rounded-md border border-input bg-input px-3 py-1 text-sm shadow-sm ' +
  'transition-colors placeholder:text-muted-foreground ' +
  'focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring ' +
  'disabled:cursor-not-allowed disabled:opacity-50'

const value = computed(() => props.modelValue ?? '')

function onInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).value)
}
</script>

<template>
  <input
    :value="value"
    :class="[BASE, $attrs.class as string | undefined]"
    v-bind="{ ...$attrs, class: undefined }"
    @input="onInput"
  />
</template>
