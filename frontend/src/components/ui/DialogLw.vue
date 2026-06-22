<script setup lang="ts">
import { cn } from '@/lib/utils'
import { X } from '@lucide/vue'
import {
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogOverlay,
  DialogPortal,
  DialogRoot,
  DialogTitle,
  useForwardPropsEmits,
} from 'radix-vue'

interface Props {
  open?: boolean
  class?: string
}

const props = defineProps<Props>()
const emits = defineEmits<{
  'update:open': [value: boolean]
}>()

const forwarded = useForwardPropsEmits(props, emits)
</script>

<template>
  <DialogRoot v-bind="forwarded">
    <DialogPortal>
      <DialogOverlay
        class="fixed inset-0 z-[200] bg-black/80 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0"
      />
      <!-- 3-row grid (auto / 1fr / auto): header and footer are auto-sized;
           the middle row gets all remaining space and is the ONLY scroll
           container. That gives the design's scrollbar that starts under
           the header and stops above the footer, with no kinetic bounce on -->
      <DialogContent
        :class="
          cn(
            'fixed left-1/2 top-1/2 z-[200] grid grid-rows-[auto_1fr_auto] w-full max-w-lg max-h-[90dvh] overflow-hidden -translate-x-1/2 -translate-y-1/2 border border-border bg-card shadow-lg duration-200 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[state=closed]:slide-out-to-left-1/2 data-[state=closed]:slide-out-to-top-48% data-[state=open]:slide-in-from-left-1/2 data-[state=open]:slide-in-from-top-48% sm:rounded-lg',
            props.class,
          )
        "
      >
        <!-- Header row: auto-sized. `pb-3` below the title separates it
             from the body; when `#header-extras` is present (e.g. a tab
             bar) the extras live in the same row and the title's bottom
             border is dropped so the extras can supply their own. -->
        <div
          v-if="$slots.title || $slots.description || $slots['header-extras']"
          class="px-4 sm:px-6 pt-4 sm:pt-6"
        >
          <div
            v-if="$slots.title || $slots.description"
            class="flex flex-col gap-1.5 text-center sm:text-left pb-3"
            :class="{ 'border-b border-border': !$slots['header-extras'] }"
          >
            <DialogTitle
              v-if="$slots.title"
              class="text-lg font-semibold leading-none tracking-tight"
            >
              <slot name="title" />
            </DialogTitle>
            <DialogDescription v-if="$slots.description" class="text-sm text-muted-foreground">
              <slot name="description" />
            </DialogDescription>
          </div>
          <slot name="header-extras" />
        </div>
        <!-- Else still render an empty header row so the grid has 3 rows
             and the body sits in row 2. Without this the body would collapse
             into row 1 and the footer wouldn't align. -->
        <div v-else />

        <!-- Body row: the only scroll container. `min-h-0` is needed for
             flex/grid children with overflow to size correctly. Content
             padding sits here so it scrolls with the content. -->
        <div class="min-h-0 overflow-y-auto px-4 sm:px-6 py-4">
          <slot />
        </div>

        <!-- Footer row: auto-sized. Default slot inside the footer area
             can be DialogFooterLw, a plain button row, or nothing. -->
        <div v-if="$slots.footer">
          <slot name="footer" />
        </div>

        <DialogClose
          class="absolute right-4 top-4 rounded-sm opacity-70 ring-offset-background transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:pointer-events-none data-[state=open]:bg-accent data-[state=open]:text-muted-foreground"
        >
          <X class="h-4 w-4" />
        </DialogClose>
      </DialogContent>
    </DialogPortal>
  </DialogRoot>
</template>
