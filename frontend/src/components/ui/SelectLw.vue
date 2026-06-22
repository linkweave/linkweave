<script setup lang="ts">
import { computed } from 'vue'
import { ChevronDown } from '@lucide/vue'

const props = defineProps<{ modelValue?: string | number | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

defineOptions({ inheritAttrs: false })

// Mirrors InputLw: same height, background, border, focus ring — but with
// `appearance-none` so we can place a consistent chevron via an overlay.
// `pr-8` reserves room for the chevron so option text never collides with it.
const BASE =
  'flex h-9 w-full appearance-none rounded-md border border-input bg-input pl-3 pr-8 py-1 text-sm shadow-sm ' +
  'transition-colors ' +
  'focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring ' +
  'disabled:cursor-not-allowed disabled:opacity-50'

const value = computed(() => props.modelValue ?? '')

function onChange(event: Event) {
  emit('update:modelValue', (event.target as HTMLSelectElement).value)
}
</script>

<template>
  <div class="relative inline-block w-full">
    <select
      :value="value"
      :class="[BASE, $attrs.class as string | undefined]"
      v-bind="{ ...$attrs, class: undefined }"
      @change="onChange"
    >
      <slot />
    </select>
    <ChevronDown
      class="pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 h-4 w-4 opacity-50"
      aria-hidden="true"
    />
  </div>
</template>
