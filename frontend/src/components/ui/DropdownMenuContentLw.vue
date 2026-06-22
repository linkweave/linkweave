<script setup lang="ts">
import { DropdownMenuContent, DropdownMenuPortal } from 'radix-vue'

withDefaults(
  defineProps<{
    align?: 'start' | 'end' | 'center'
    sideOffset?: number
  }>(),
  { align: 'end', sideOffset: 4 },
)

defineOptions({ inheritAttrs: false })

// Shared "popover skin": surface, border, padding, shadow + ring (for hi-contrast
// boundary against the page), and the radix open/close animations. Width and
// z-index are intentionally NOT in the baseline — consumers pass them via the
// fallthrough `class` so positioning intent stays at the call site.
const SKIN =
  'rounded-md border border-border bg-popover p-1 text-popover-foreground shadow-xl ring-1 ring-black/5 dark:ring-white/10 ' +
  'data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 ' +
  'data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95'
</script>

<template>
  <DropdownMenuPortal>
    <DropdownMenuContent
      :align="align"
      :side-offset="sideOffset"
      :class="[SKIN, $attrs.class as string | undefined]"
      v-bind="{ ...$attrs, class: undefined }"
    >
      <slot />
    </DropdownMenuContent>
  </DropdownMenuPortal>
</template>
