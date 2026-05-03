<script setup lang="ts">
import { ref } from 'vue'
import { PopoverRoot, PopoverTrigger, PopoverPortal, PopoverContent, PopoverArrow } from 'radix-vue'
import { HelpCircle } from 'lucide-vue-next'

defineProps<{
  ariaLabel?: string
  width?: string
}>()

const open = ref(false)

function close() {
  open.value = false
}

defineExpose({ close })
</script>

<template>
  <PopoverRoot v-model:open="open">
    <PopoverTrigger
      type="button"
      :aria-label="ariaLabel ?? 'Help'"
      class="inline-flex h-5 w-5 items-center justify-center rounded text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
    >
      <HelpCircle class="h-4 w-4" />
    </PopoverTrigger>
    <PopoverPortal>
      <PopoverContent
        side="bottom"
        align="end"
        :side-offset="4"
        class="z-50 rounded-md border border-border bg-popover p-3 text-popover-foreground shadow-md text-xs overflow-auto"
        :style="{ width: width ?? '22rem', maxWidth: 'calc(100vw - 2rem)' }"
      >
        <slot />
        <PopoverArrow class="fill-popover" />
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>
