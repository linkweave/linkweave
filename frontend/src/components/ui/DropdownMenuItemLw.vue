<script setup lang="ts">
import { computed } from 'vue'
import { DropdownMenuItem } from 'radix-vue'

const props = withDefaults(
  defineProps<{
    variant?: 'default' | 'destructive'
    /** Visually mark this row as the currently-selected option (e.g. the active sort field). */
    active?: boolean
  }>(),
  { variant: 'default', active: false },
)

defineOptions({ inheritAttrs: false })

const itemClasses = computed(() => [
  'relative flex cursor-pointer select-none items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-none transition-colors',
  'data-[highlighted]:bg-popover-hover data-[highlighted]:text-popover-foreground',
  'data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
  props.variant === 'destructive' && 'text-destructive data-[highlighted]:text-destructive',
  props.active && 'bg-popover-selected',
])
</script>

<template>
  <DropdownMenuItem
    :class="[itemClasses, $attrs.class as string | undefined]"
    v-bind="{ ...$attrs, class: undefined }"
  >
    <slot />
  </DropdownMenuItem>
</template>
