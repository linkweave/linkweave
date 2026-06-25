<script setup lang="ts">
import type { FolderState } from '@/composables/useImportSelection'
import { Check, Minus } from '@lucide/vue'
import { onMounted, ref, watch } from 'vue'

const props = defineProps<{
  state: FolderState
  label: string
  dense?: boolean
}>()

const emit = defineEmits<{ toggle: [] }>()

// A real <input type="checkbox"> carries the interaction and accessible name;
// the styled box is purely visual (aria-hidden). `indeterminate` is a DOM
// property (not an attribute), so reflect the 'some' tri-state imperatively so
// assistive tech announces "mixed" for a partially-selected folder.
const input = ref<HTMLInputElement | null>(null)

function syncIndeterminate() {
  if (input.value) input.value.indeterminate = props.state === 'some'
}

watch(() => props.state, syncIndeterminate)
onMounted(syncIndeterminate)
</script>

<template>
  <span
    class="relative grid shrink-0 place-items-center"
    :class="dense ? 'h-4 w-4' : 'h-[17px] w-[17px]'"
  >
    <input
      ref="input"
      type="checkbox"
      :checked="state === 'all'"
      :aria-label="label"
      class="absolute inset-0 z-10 cursor-pointer opacity-0"
      @change="emit('toggle')"
    />
    <span
      aria-hidden="true"
      class="grid h-full w-full place-items-center rounded-[5px] border-[1.5px] transition-colors"
      :class="
        state === 'none'
          ? 'border-muted-foreground'
          : 'border-primary bg-primary text-primary-foreground'
      "
    >
      <Check v-if="state === 'all'" class="h-3 w-3" />
      <Minus v-else-if="state === 'some'" class="h-3 w-3" />
    </span>
  </span>
</template>
